package models

case class User(id: Option[Long], name: String, email: String, password: String)
