package be.studiocredo.aws

object AwsConfigKeys {
  val accessKey: String = "aws.access-key"
  val secretKey: String = "aws.secret-key"
  val keyPairId: String = "aws.key-pair.id"
  val keyPairPrivateKeyResource: String = "aws.key-pair.private-key.resource"
  val keyPairPrivateKeyPath: String = "aws.key-pair.private-key.path"
  val s3BucketName: String = "aws.s3.bucket-name"
  val s3LogBucketName: String = "aws.s3.log-bucket-name"
  val s3Region: String = "aws.s3.region"
  val cfDnsAlias: String = "aws.cf.dns-alias"
  val cfUseAlias: String = "aws.cf.use-alias"
  val cfRegion: String = "aws.cf.region"
  val cfUrlValidity: String = "aws.cf.url-validity"
}