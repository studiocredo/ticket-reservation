package models

import models.entities.{SeatType, ShowOverview}

object AvailabilityFormat {
  def format(showOverview: ShowOverview): String = {
    showOverview.availability.byType(SeatType.Normal) match {
      case 0 => "Uitverkocht"
      case 1 => "Nog 1 ticket"
      case other => s"Nog $other tickets"
    }

  }
}
