package models

import models.ids.VenueId
import org.joda.time.DateTime

case class NewShow(venueId: VenueId, date:DateTime)
