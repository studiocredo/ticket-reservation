package be.studiocredo.aws

import java.nio.file.{Files, Paths}
import java.security.spec.PKCS8EncodedKeySpec
import java.security.{KeyFactory, PrivateKey}

import be.studiocredo.{AssetService, Service}
import com.amazonaws.auth.AWSCredentials
import com.amazonaws.regions.Regions
import com.amazonaws.services.cloudfront.model.{DistributionSummary, ListDistributionsRequest}
import com.amazonaws.services.cloudfront.{AmazonCloudFrontClient, CloudFrontUrlSigner}
import com.amazonaws.services.s3.model.ListObjectsRequest
import com.amazonaws.services.s3.{AmazonS3, AmazonS3Client}
import com.amazonaws.util.Base64
import com.google.common.io.Resources
import com.google.inject.Inject
import models.entities.{Asset, User}
import models.ids.UserId
import org.apache.commons.io.FilenameUtils
import org.apache.http.client.utils.URIBuilder
import org.joda.time.format.ISOPeriodFormat
import org.joda.time.{DateTime, Period}
import play.api.{Configuration, Play}

import scala.util.{Failure, Try}

object Logger {
  val logger = play.api.Logger("be.studiocredo.aws.download")
}

object DownloadConfiguration {
  def init(configuration: Configuration): Option[DownloadConfiguration] = {
    Try(new DownloadConfiguration(configuration)).recoverWith { case t: Throwable =>
      Logger.logger.error("Failed to initialize download configuration", t)
      Failure(t)
    }.toOption
  }

  val CUSTOMER_ID_PARAMETER: String = "CustomerId"
}

class DownloadConfiguration(val configuration: Configuration) extends AWSCredentials {
  val accessKey: String = configuration.getString(AwsConfigKeys.accessKey).get
  val secretKey: String = configuration.getString(AwsConfigKeys.secretKey).get

  val s3: AmazonS3Client = Try {
    configuration.getString(AwsConfigKeys.s3Region).map(Regions.fromName).foldLeft(new AmazonS3Client(this)) { (client, region) => client.withRegion(region).asInstanceOf[AmazonS3Client] }
  }.recoverWith { case t: Throwable =>
    Logger.logger.error("Failed to initialize AWS S3 client", t)
    Failure(t)
  }.get

  val cloudFront: AmazonCloudFrontClient = Try {
    configuration.getString(AwsConfigKeys.cfRegion).map(Regions.fromName).foldLeft(new AmazonCloudFrontClient(this)) { (client, region) => client.withRegion(region).asInstanceOf[AmazonCloudFrontClient] }
  }.recoverWith { case t: Throwable =>
    Logger.logger.error("Failed to initialize AWS CloudFront client", t)
    Failure(t)
  }.get

  val bucketName: String = configuration.getString(AwsConfigKeys.s3BucketName).get
  val distributionDomain: String = {
    val distribution: DistributionSummary = getCloudFrontDistribution(cloudFront, bucketName)
    if (configuration.getBoolean(AwsConfigKeys.cfUseAlias).get && !distribution.getAliases.getItems.isEmpty) distribution.getAliases.getItems.get(0)
    else distribution.getDomainName
  }
  val validPeriod: Period = configuration.getString(AwsConfigKeys.cfUrlValidity).map(ISOPeriodFormat.standard.parsePeriod).get
  val expirationDate: DateTime = new DateTime().plus(validPeriod)

  val keyPairId: String = configuration.getString(AwsConfigKeys.keyPairId).get
  val keyPairPrivateKeyResource: Option[String] = configuration.getString(AwsConfigKeys.keyPairPrivateKeyResource)
  val keyPairPrivateKeyPath: Option[String] = configuration.getString(AwsConfigKeys.keyPairPrivateKeyPath)
  val derPrivateKey: Array[Byte] = keyPairPrivateKeyResource.fold {
    val path = keyPairPrivateKeyPath.get
    Files.readAllBytes(Paths.get(path))
  } { resource =>
    Resources.toByteArray(Resources.getResource(resource))
  }

  val keySpec: PKCS8EncodedKeySpec = new PKCS8EncodedKeySpec(derPrivateKey)
  val keyFactory: KeyFactory = KeyFactory.getInstance("RSA")
  val key: PrivateKey = keyFactory.generatePrivate(keySpec)

  override def getAWSAccessKeyId: String = accessKey

  override def getAWSSecretKey: String = secretKey

  protected def getCloudFrontDistribution(cloudFront: AmazonCloudFrontClient, bucketName: String): DistributionSummary = {
    import scala.collection.JavaConverters._
    val originId = getCloudFrontOriginId(bucketName)
    val summary = Try {
      cloudFront.listDistributions(new ListDistributionsRequest).getDistributionList.getItems.asScala.find { summary =>
        summary.getOrigins.getItems.asScala.exists { origin =>
          origin.getId == originId
        }
      }.get
    } recoverWith { case t: Throwable =>
      Logger.logger.error(s"CloudFront distribution for origin $originId not found", t)
      Failure(t)
    }
    summary.get
  }

  protected def getCloudFrontOriginId(bucketName: String): String = s"s3-$bucketName"
}

class DownloadService @Inject()(assetService: AssetService) extends Service {
  var configuration: Option[DownloadConfiguration] = None

  override def onStart() {
    Logger.logger.debug("Starting download service")
    configuration = DownloadConfiguration.init(Play.current.configuration)
  }

  def getDownloadUrl(asset: Asset, user: User): Option[String] = asset.objectKey.flatMap { objectKey =>
    configuration.flatMap { c =>
      findObject(c.s3, c.bucketName, objectKey).flatMap { s3Object =>
        val fileName: String = FilenameUtils.getName(objectKey)
        val fileSize: Long = s3Object.getSize
        getSignedUrlForObjectKey(c, objectKey, user, fileName, fileSize)
      }
    }
  }

  override def onStop(): Unit = {
    Logger.logger.debug("Stopping download service")
  }

  private def findObject(s3: AmazonS3, bucketName: String, objectKey: String) = {
    import scala.collection.JavaConverters._
    s3.listObjects(new ListObjectsRequest().withBucketName(bucketName).withPrefix(objectKey)).getObjectSummaries.asScala.headOption
  }

  def getCustomerId(user: User): String = Seq(user.id.toString, user.username, user.name).mkString("/")

  private def getSignedUrlForObjectKey(configuration: DownloadConfiguration, objectKey: String, user: User, fileName: String, size: Long): Option[String] = {
    val maybeUrl = Try {
      // Signed URLs for a private distribution
      // Note that Java only supports SSL certificates in DER format,
      // so you will need to convert your PEM-formatted file to DER format.
      // To do this, you can use openssl:
      // openssl pkcs8 -topk8 -nocrypt -in origin.pem -inform PEM -out new.der
      //    -outform DER
      // So the encoder works correctly, you should also add the bouncy castle jar
      // to your project and then add the provider.
      val path = if (objectKey.startsWith("/")) objectKey else "/" + objectKey
      val customerId = getCustomerId(user)
      val policyResourcePath: String = new URIBuilder().setScheme("http").setHost(configuration.distributionDomain).setPath(path).addParameter(DownloadConfiguration.CUSTOMER_ID_PARAMETER, Base64.encodeAsString(customerId.getBytes: _*)).build.toString
      CloudFrontUrlSigner.getSignedURLWithCannedPolicy(policyResourcePath, configuration.keyPairId, configuration.key, configuration.expirationDate.toDate)
    } recoverWith { case t: Throwable =>
      Logger.logger.error("Could not generate signed url", t)
      Failure(t)
    }
    maybeUrl.toOption
  }
}