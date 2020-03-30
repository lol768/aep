package system

import warwick.sso.RoleName

object Roles {
  val Masquerader: RoleName = RoleName("masquerader")
  val Sysadmin: RoleName = RoleName("sysadmin")
  val Approver: RoleName = RoleName("approver")
  val Admin: RoleName = RoleName("admin")
}
