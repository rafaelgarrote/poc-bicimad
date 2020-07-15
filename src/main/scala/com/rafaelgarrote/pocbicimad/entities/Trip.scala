package com.rafaelgarrote.pocbicimad.entities

import java.sql.Timestamp

import com.sksamuel.avro4s.{Record, RecordFormat}


case class TripId(value: String)
case class User(userDayCode: String, `type`: Int, ageRange: Int, zipCode: String)
case class Station(stationId: Int, baseId: Int)
case class TrackPosition(latitude: Float, longitude: Float, speed: Float, timeFormStart: Int)

case class Trip(
                 id: TripId,
                 user: User,
                 unplugStation: Station,
                 plugStation: Option[Station],
                 unplugTime: Timestamp, // Long UTC 0
                 travelDuration: Float,
                 tracking: Seq[TrackPosition]
               ) {

  final lazy val format = RecordFormat[Trip]

  def addTrackPosition(trackPosition: TrackPosition): Trip = this.copy(
    tracking = tracking :+ trackPosition,
    travelDuration = travelDuration + trackPosition.timeFormStart
  )

  def endTravel(plugStation: Station, travelDuration: Float): Trip = this.copy(
    plugStation = Some(plugStation),
    travelDuration = travelDuration
  )

  def serialize: Record = format.to(this)

}
