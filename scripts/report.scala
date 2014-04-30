import models.HumanDateTime
import java.io._

def printToFile(f: java.io.File)(op: java.io.PrintWriter => Unit) {
  val p = new java.io.PrintWriter(f)
  try { op(p) } finally { p.close() }
}

printToFile(new File("report.txt"))(out => {

val events = es.list

events.foreach { event =>
  val ed = es.eventDetails(event.id).get
  ed.shows.foreach { vshow =>
    val venue = vshow.venue
    val orderMap = vshow.shows.map{s => (s.id, os.detailsByShowId(s.id))}.toMap
    vshow.shows.foreach { show =>
      val eventShow = ss.getEventShow(show.id)
  	  out.println(eventShow.name)
  	  out.println(HumanDateTime.formatDateTime(eventShow.date))
  	  val floorplan = venue.floorplan.get
  	  floorplan.rows.reverse.foreach { row =>
  	  	row.content.collect{case seat:Seat => seat}.foreach { seat =>
  	  		out.print(seat.id.name)
  	  		val order = orderMap(show.id).find(o => o.ticketSeatOrders.exists(tso => tso.ticketSeatOrder.seat == seat.id && tso.ticketSeatOrder.showId == show.id))
  	  		if (order.isDefined) {
  	  			out.print(";")
  	  			out.print(order.get.order.billingName)
  	  			val payment = ps.find(order.get.order.id)
  	  			if (!payment.isEmpty) {
  	  				out.print(";")
  	  				out.print(HumanDateTime.formatDateCompact(payment.last.date))
  	  				out.print(";")
  	  				out.print(payment.last.debtor)
  	  			} else {
  	  				out.print(";;")
  	  			}
  	  			val tickets = ts.find(order.get.order.id)
  	  			if (!tickets.isEmpty) {
  	  				out.print(";")
  	  				out.print(tickets.map(_.reference).mkString(";"))
  	  			}
  	  		}
  	  		out.println()
  	  	}
  	  }
  	  
  	  	out.println(eventShow.name)
  	  	out.println(HumanDateTime.formatDateTime(eventShow.date))
  	  	orderMap(show.id).sortBy(x => (x.order.billingName, x.order.id.id)).foreach { order =>
  	  		out.print(order.order.billingName)
  	  		out.print(";")
  	  		out.print(order.ticketSeatOrders.map(tso => tso.ticketSeatOrder.seat.name).sorted.mkString(","))
  	  		val payment = ps.find(order.order.id)
  	  		if (!payment.isEmpty) {
  	  		  out.print(";")
  	  		  out.print(HumanDateTime.formatDateCompact(payment.last.date))
  	  		  out.print(";")
  	  		  out.print(payment.last.debtor)
  	  		} else {
  	  		  out.print(";;")
  	  		}
  	  		val tickets = ts.find(order.order.id)
  	  		if (!tickets.isEmpty) {
  	  		  out.print(";")
  	  		  out.print(tickets.map(_.reference).mkString(";"))
  	  	    }	
  	  	    out.println()
  	  	}
  	  	
  	}
  }
}

})