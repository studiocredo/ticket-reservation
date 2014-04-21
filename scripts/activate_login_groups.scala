def activate_login_groups() = {
  val inactive = us.listInactive
  val inactive_with_partially_active_login_group = inactive.filter {i =>
    val o =  us.findOtherUsers(i.user)
    o.length > 1 && o.exists{_.active}
  }
  inactive_with_partially_active_login_group.foreach{i => println(i.username+" "+i.id+" "+i.name+" -> ("+us.findOtherUsers(i.user).map{_.name}.mkString(", ")+")")}
}
