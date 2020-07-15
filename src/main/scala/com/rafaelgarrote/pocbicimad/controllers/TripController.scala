package com.rafaelgarrote.pocbicimad.controllers

import akka.actor.ActorRef
import akka.http.scaladsl.model.{HttpResponse, StatusCodes}
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.{ExceptionHandler, Route}
import akka.util.Timeout
import akka.pattern.ask
import com.rafaelgarrote.pocbicimad.entities.TripActor.{FinishTrip, StartTrip, Track}
import com.rafaelgarrote.pocbicimad.entities.{TripActor, TripActorCommandsJsonFormats, TripId}
import com.rafaelgarrote.pocbicimad.utils.TripJsonFormats

import scala.concurrent.ExecutionContext
import scala.concurrent.duration._

class TripController(tripActors: ActorRef)(implicit ec: ExecutionContext)
  extends TripActorCommandsJsonFormats with TripJsonFormats {

  private implicit val timeout: Timeout = Timeout(5.seconds)

  private def exceptionHandler: ExceptionHandler = ExceptionHandler {
    case ex: TripActor.TripNotFoundException =>
      complete(HttpResponse(StatusCodes.NotFound, entity = ex.getMessage))
    case ex =>
      complete(HttpResponse(StatusCodes.InternalServerError, entity = ex.getMessage))
  }

  lazy val routes: Route =
    handleExceptions(exceptionHandler) {
      pathPrefix("trip") {
        post {
          entity(as[StartTrip]) { startTrip =>
            complete {
              val tripId = TripId(startTrip._id)

              (tripActors ? TripActor.Envelope(tripId, startTrip))
                .mapTo[TripActor.TripStarted]
                .map(_.trip)
            }
          }
        }  ~
        pathPrefix(Segment) { id =>
          val tripId = TripId(id)
          post {
            entity(as[FinishTrip]) { finishTrip =>
              complete {
                (tripActors ? TripActor.Envelope(tripId, finishTrip))
                  .mapTo[TripActor.TripFinished]
                  .map(_.trip)
              }
            }
          } ~
          path("track") {
            post {
              entity(as[Track]) { trackPossition =>
                complete {
                  (tripActors ? TripActor.Envelope(tripId, trackPossition))
                    .mapTo[TripActor.PositionTracked]
                    .map(_.trip)
                }
              }
            }
          }
        }

    }
}