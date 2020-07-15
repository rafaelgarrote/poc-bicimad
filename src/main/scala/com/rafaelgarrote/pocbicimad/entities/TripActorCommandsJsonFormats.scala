package com.rafaelgarrote.pocbicimad.entities

import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport
import com.rafaelgarrote.pocbicimad.entities.TripActor.{FinishTrip, StartTrip, Track}
import spray.json.{DefaultJsonProtocol, RootJsonFormat}

trait TripActorCommandsJsonFormats extends SprayJsonSupport with DefaultJsonProtocol {

  implicit val startTripFormat: RootJsonFormat[StartTrip] = jsonFormat8(StartTrip)
  implicit val finishTripFormat: RootJsonFormat[FinishTrip] = jsonFormat5(FinishTrip)
  implicit val trackFormat: RootJsonFormat[Track] = jsonFormat6(Track)

}
