package com.rafaelgarrote.pocbicimad.entities

import java.sql.Timestamp
import java.time.Instant

import akka.actor.{Actor, ActorLogging, Props, Stash, Status}
import akka.pattern.pipe
import akka.cluster.sharding.ShardRegion
import akka.cluster.sharding.ShardRegion.{ExtractEntityId, ExtractShardId}
import com.rafaelgarrote.pocbicimad.entities.TripActor._
import com.rafaelgarrote.pocbicimad.utils.{KafkaClient, RecordKey}
import com.sksamuel.avro4s.{Record, RecordFormat}

import scala.concurrent.Future

object TripActor {

  def props(kafkaClient: KafkaClient): Props = {
    Props(classOf[TripActor], kafkaClient)
  }

  // Messages
  sealed trait Command //extends SerializableMessage

  sealed trait AvroSerializable[T] {
    final lazy val format = RecordFormat[T]

    def serialize: Record = format.to(this.asInstanceOf[T])
  }

  case class Envelope(tripId: TripId, command: Command) //extends SerializableMessage
  // Messages
  case class TripLoaded(trip: Option[Trip])

  case class StartTrip(
                        _id: String,
                        user_day_code: String,
                        user_type: Int,
                        ageRange: Int,
                        zip_code: String,
                        idunplug_base: Int,
                        idunplug_station: Int,
                        unplug_hourTime: String )
    extends Command with AvroSerializable[StartTrip]

  case class TripStarted(trip: Trip) extends Command

  case class Track(
                    _id: String,
                    user_day_code: String,
                    latitude: Float,
                    longitude: Float,
                    speed: Float,
                    secondsfromstart: Int
                  ) extends Command with AvroSerializable[Track]
  case class PositionTracked(trip: Trip) extends Command

  case class FinishTrip(
                       _id: String,
                       user_day_code: String,
                       idplug_base: Int,
                       idplug_station: Int,
                       travel_time: Int )
    extends Command with AvroSerializable[FinishTrip]

  case class TripFinished(trip: Trip) extends Command

  // Exceptions
  case class TripNotFoundException(tripId: TripId) extends IllegalStateException(s"Trip Not Found: $tripId")
  case class DuplicateTripException(tripId: TripId) extends IllegalStateException(s"Duplicate Trip: $tripId")

  val entityIdExtractor: ExtractEntityId = {
    case Envelope(id, cmd) => (id.value.toString, cmd)
  }

  val shardIdExtractor: ExtractShardId = {
    case Envelope(id, _) => Math.abs(id.value.toString.hashCode % 30).toString
    case ShardRegion.StartEntity(entityId) => Math.abs(entityId.hashCode % 30).toString
  }
}

class TripActor(kafkaClient: KafkaClient) extends Actor with Stash with ActorLogging {

  import context.dispatcher

  private val tripId: TripId = TripId(context.self.path.name)
  private var state: Option[Trip] = None
  private var recordKey: Option[RecordKey[Int]] = None

  override def receive: Receive = loading

  private def loading: Receive = {
    case TripLoaded(trip) =>
      unstashAll()
      state = trip
      context.become(tracking)
    case Status.Failure(ex) =>
      log.error(ex, s"[${tripId.value}] FAILURE: ${ex.getMessage}")
      throw ex
    case _ =>
      stash()
  }

  private def waiting: Receive = {
    case evt @ TripStarted(trip) =>
      state = Some(trip)
      unstashAll()
      sender() ! evt
      context.become(tracking)
    case evt @ PositionTracked(trip) =>
      state = Some(trip)
      unstashAll()
      sender() ! evt
      context.become(tracking)
    case evt @ TripFinished(trip) =>
      state = Some(trip)
      unstashAll()
      sender() ! evt
      context.become(tracking)
    case failure @ Status.Failure(ex) =>
      log.error(ex, s"[${tripId.value}] FAILURE: ${ex.getMessage}")
      sender() ! failure
      throw ex
    case _ =>
      stash()
  }

  private def tracking: Receive = {
    case ev:StartTrip =>
      state match {
        case Some(_) =>
          Future.failed(DuplicateTripException(tripId)).pipeTo(sender())
        case None =>
          context.become(waiting)
          createTrip(ev).pipeTo(self)(sender())
          recordKey = Some(RecordKey[Int](ev.idunplug_station))
          recordKey.map(key => kafkaClient.sendEvent(key.serialize, ev.serialize))
      }
    case ev:Track =>
      state match {
        case Some(trip) =>
          context.become(waiting)
          addTrack(ev, trip).pipeTo(self)(sender())
          recordKey.map(key => kafkaClient.sendEvent(key.serialize, ev.serialize))
        case None =>
          Future.failed(TripNotFoundException(tripId)).pipeTo(sender())
      }
    case ev:FinishTrip =>
      state match {
        case Some(trip) =>
          context.become(waiting)
          finishTrip(ev, trip).pipeTo(self)(sender())
          recordKey.map(key => kafkaClient.sendEvent(key.serialize, ev.serialize))
        case None =>
          Future.failed(TripNotFoundException(tripId)).pipeTo(sender())
      }
  }

  private def createTrip(ev: StartTrip): Future[TripStarted] = Future {
    TripStarted(
      Trip(
        id = TripId(ev._id),
        user = User(
          userDayCode = ev.user_day_code,
          `type` = ev.user_type,
          ageRange = ev.ageRange,
          zipCode = ev.zip_code),
        unplugStation = Station(
          stationId = ev.idunplug_station,
          baseId = ev.idunplug_base),
        plugStation = None,
        unplugTime = Timestamp.from(Instant.parse(ev.unplug_hourTime)),
        travelDuration = 0,
        tracking = Seq.empty[TrackPosition]
      )
    )
  }

  private def addTrack(track: Track, trip: Trip): Future[PositionTracked] = Future {
    val trackPosition = TrackPosition(
      latitude = track.latitude,
      longitude = track.longitude,
      speed = track.speed,
      timeFormStart = track.secondsfromstart
    )
    PositionTracked(trip.addTrackPosition(trackPosition))
  }

  private def finishTrip(ev: FinishTrip, trip: Trip): Future[TripFinished] = Future {
    val station: Station = Station(stationId = ev.idplug_station, baseId = ev.idplug_base)
    TripFinished(trip.endTravel(station, ev.travel_time))
  }

}
