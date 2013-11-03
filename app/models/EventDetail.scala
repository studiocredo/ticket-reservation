package models

import models.entities.{Show, Venue, Event}

case class EventDetail(event: Event, shows: Map[Venue, List[Show]]) {
  def id = event.id
}
