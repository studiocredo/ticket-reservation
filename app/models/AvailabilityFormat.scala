package models

import models.entities.{SeatType, ShowAvailability}

object AvailabilityFormat {
  def format(availability: ShowAvailability): String = {
    availability.byType(SeatType.Normal) match {
      case 0 => "Uitverkocht"
      case 1 => "Nog 1 ticket"
      case other => s"Nog $other tickets"
    }

  }
}
