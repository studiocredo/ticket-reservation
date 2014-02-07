package be.studiocredo.util

import play.api.i18n.{Lang, Messages}

object ServiceReturnValues {

  type ServiceSuccess = (String, Seq[Any])
  type ServiceFailure = (String, Seq[Any])

  def serviceSuccess(message: String, args: Seq[Any] = Nil): ServiceSuccess = (message, args)
  def serviceFailure(message: String, args: Seq[Any] = Nil): ServiceFailure = (message, args)

  //http://stackoverflow.com/questions/3568002/scala-tuple-unpacking
  def serviceMessage(m: (String, Seq[Any]))(implicit lang: Lang): String = (Messages.apply _).tupled(m)

}
