package com.rafaelgarrote.pocbicimad.utils

import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport
import com.rafaelgarrote.pocbicimad.entities._
import spray.json.{DefaultJsonProtocol, RootJsonFormat}

trait TripJsonFormats extends SprayJsonSupport with DefaultJsonProtocol {

  implicit val tripIdFormat: RootJsonFormat[TripId] = jsonFormat1(TripId)
  implicit val userFormat: RootJsonFormat[User] = jsonFormat4(User)
  implicit val stationFormat: RootJsonFormat[Station] = jsonFormat2(Station)
  implicit val trackPositionFormat: RootJsonFormat[TrackPosition] = jsonFormat4(TrackPosition)
  implicit val tripFormat: RootJsonFormat[Trip] = jsonFormat7(Trip)
}
