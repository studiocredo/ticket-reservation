val userAdmin = us.insert(UserEdit("Admin", "admin", Passwords.hash("qsdfghjklm"), true), UserDetailEdit(Some("sven@studiocredo.be"), None, None))
us.addRole(userAdmin, Roles.Admin)

val thomas = us.insert(UserFormData("Thomas", "thomas", Some("selckin@selckin.be"), None, None, true))
val sven = us.insert(UserFormData("sven", "sven",Some("sven@example.com"), None, None, true))
val jantje = us.insert(UserFormData("Jantje", "jantje", Some("selckin@selckin.be"), Some("veldstraat 20 gent"), Some("09/2345435453435"), true))

val event1 = es.insert(EventEdit("xmas special", "", preReservationStart = Some(new DateTime(2013, 12, 9, 0 ,0)), preReservationEnd = Some(new DateTime(2013, 12, 20, 0, 0)), reservationStart = Some(new DateTime(2013, 12, 21, 0 ,0)), reservationEnd = Some(new DateTime(2013, 12, 24, 0 ,0)), archived = false)).fold(error => None, success => Some(success)).get
val event2 = es.insert(EventEdit("Big show 2013", "", preReservationStart = Some(new DateTime(2013, 12, 9, 0 ,0)), preReservationEnd = Some(new DateTime(2013, 12, 20, 0, 0)), reservationStart = Some(new DateTime(2013, 12, 21, 0 ,0)), reservationEnd = Some(new DateTime(2013, 12, 24, 0 ,0)), archived = false)).fold(error => None, success => Some(success)).get

val ven1 = vs.insert(VenueEdit("Big room 1", "", archived = false))
val ven2 = vs.insert(VenueEdit("Big room 2", "", archived = false))
val ven3 = vs.insert(VenueEdit("Small room", "", archived = false))

def norm(name: String, pref: Int): Seat = { Seat(SeatId(name), SeatType.Normal, pref) }
def vip(name: String, pref: Int): Seat = { Seat(SeatId(name), SeatType.Vip, pref) }
def disa(name: String, pref: Int): Seat = { Seat(SeatId(name), SeatType.Disabled, pref) }

vs.update(ven1, FloorPlan(List(
    Row(List(norm("A1",1), norm("A2",1), norm("A3",1), norm("A4",1), norm("A5",1), norm("A6",1), norm("A7",1), disa("A8",1)), 0),
    Row(List(norm("B1",1), norm("B2",1), norm("B3",1), norm("B4",1), norm("B5",1), norm("B6",1), norm("B7",1), disa("B8",1)), 0),
    Row(List(norm("C1",1), norm("C2",1), norm("C3",1), norm("C4",1), norm("C5",1), norm("C6",1), norm("C7",1), disa("C8",1)), 0),
    Row(List(norm("D1",1), norm("D2",1), norm("D3",1), norm("D4",1), norm("D5",1), norm("D6",1), norm("D7",1), disa("D8",1)), 0),
    Row(List(norm("E1",1), norm("E2",1), norm("E3",1), norm("E4",1), norm("E5",1), norm("E6",1), norm("E7",1), disa("E8",1)), 0),
    Row(List(Spacer(1), norm("F1",1), norm("F2",1), norm("F3",1), norm("F4",1), norm("F5",1), norm("F6",1), disa("F7",1)), 1),
    Row(List(Spacer(2), norm("G1",1), norm("G2",1), norm("G3",1), norm("G4",1), norm("G5",1), disa("G6",1)), 0),
    Row(List(Spacer(2), vip("H1",1), vip("H2",1), vip("H3",1), vip("H4",1), vip("H5",1), disa("H6",1)), 0),
    Row(List(Spacer(4), vip("I1",1), vip("I2",1), vip("I3",1), vip("I4",1)), 0)
)))
vs.update(ven2, FloorPlan(List(
  Row(List(norm("D1",1), norm("D2",1), norm("D3",1), norm("D4",1), norm("D5",1), norm("D6",1), norm("D7",1), disa("D8",1)), 0),
  Row(List(norm("C1",1), norm("C2",1), norm("C3",1), norm("C4",1), norm("C5",1), norm("C6",1), norm("C7",1), disa("C8",1)), 0),
  Row(List(norm("B1",1), norm("B2",1), norm("B3",1), norm("B4",1), norm("B5",1), norm("B6",1), norm("B7",1), disa("B8",1)), 1),
  Row(List(norm("A1",1), norm("A2",1), norm("A3",1), norm("A4",1), norm("A5",1), norm("A6",1), norm("A7",1), disa("A8",1)), 0)
)))
vs.update(ven3, FloorPlan(List(
  	Row(List(norm("D1",1), norm("D2",1), norm("D3",1), norm("D4",1), norm("D5",1), norm("D6",1), norm("D7",1), disa("D8",1)), 0),
    Row(List(norm("C1",1), norm("C2",1), norm("C3",1), norm("C4",1), norm("C5",1), norm("C6",1), norm("C7",1), disa("C8",1)), 0),
    Row(List(norm("B1",1), norm("B2",1), norm("B3",1), norm("B4",1), norm("B5",1), norm("B6",1), norm("B7",1), disa("B8",1)), 0),
    Row(List(norm("A1",1), norm("A2",1), norm("A3",1), norm("A4",1), norm("A5",1), norm("A6",1), norm("A7",1), disa("A8",1)), 0)
)))

ss.insert(event1, ShowEdit(ven1, new DateTime(2013, 12, 5, 17, 0)))
ss.insert(event1, ShowEdit(ven1, new DateTime(2013, 12, 5, 19, 0)))
ss.insert(event1, ShowEdit(ven1, new DateTime(2013, 12, 6, 17, 0)))
ss.insert(event1, ShowEdit(ven1, new DateTime(2014, 2, 6, 19, 0)))
ss.insert(event1, ShowEdit(ven2, new DateTime(2013, 12, 9, 19, 0)))

ss.insert(event2, ShowEdit(ven2, new DateTime(2013, 12, 5, 17, 0)))
ss.insert(event2, ShowEdit(ven3, new DateTime(2013, 12, 5, 19, 0)))
ss.insert(event2, ShowEdit(ven1, new DateTime(2014, 4, 6, 17, 0)))
ss.insert(event2, ShowEdit(ven1, new DateTime(2014, 5, 6, 19, 0)))

val sven  = us.findByUserName("sven").get.user
us.changePassword(sven.id, Passwords.hash("sven"))

val show = ss.get(ShowId(1)).get


val orderId = os.insert(OrderEdit(sven.id, new DateTime(2013, 12, 5, 17, 0), "Dhr. X van Y", "adresje in Gent"))
val ticketOrderId = os.insert(orderId, show.id)
os.insert(List(TicketSeatOrder(ticketOrderId, show.id, Some(sven.id), SeatId("A4"), Money(12.5)), TicketSeatOrder(ticketOrderId, show.id, Some(sven.id), SeatId("B4"), Money(15))))

val show2 = ss.get(ShowId(2)).get

val rq1 = prs.insert(ReservationQuotum(show.eventId, sven.id, 13))
val rq2 = prs.insert(ReservationQuotum(EventId(2), sven.id, 6))

prs.quotaByUsers(List(sven.id))
prs.unusedQuotaByUsers(List(sven.id))
prs.preReservationsByUser(List(sven.id))
prs.pendingPrereservationsByUsers(List(sven.id))

val pr1 = prs.insert(ShowPrereservation(show.id, sven.id, 3))

prs.quotaByUsers(List(sven.id))
prs.unusedQuotaByUsers(List(sven.id))
prs.preReservationsByUsers(List(sven.id))
prs.pendingPrereservationsByUsers(List(sven.id))

val jantje  = us.findByUserName("jantje").get.user
us.changePassword(jantje.id, Passwords.hash("jantje"))
val rq3 = prs.insert(ReservationQuotum(show.eventId, jantje.id, 17))

us.createLoginGroup(List(sven.id, jantje.id))