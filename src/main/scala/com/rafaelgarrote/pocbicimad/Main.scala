package com.rafaelgarrote.pocbicimad

import akka.actor.ActorSystem
import akka.cluster.sharding.{ClusterSharding, ClusterShardingSettings}
import akka.http.scaladsl.Http
import akka.management.scaladsl.AkkaManagement
import com.rafaelgarrote.pocbicimad.controllers.TripController
import com.rafaelgarrote.pocbicimad.entities.TripActor
import com.rafaelgarrote.pocbicimad.utils.{KafkaClient, KafkaProperties}
import org.slf4j.LoggerFactory

class Main extends App {
  val log = LoggerFactory.getLogger(this.getClass)

  implicit val system: ActorSystem = ActorSystem("BiciMad")

  AkkaManagement(system).start()

  val blockingDispatcher = system.dispatchers.lookup("blocking-dispatcher")

  val kafkaProperties = KafkaProperties(
    bootstrapServers = "127.0.0.1:9092",
    registryUrl = "127.0.0.1:8081",
    producerId = "apibicimad",
    topic = "bicimad",
    partitions = 10,
    replicas = 3
  )

  val kafkaClient = KafkaClient(kafkaProperties)

  val trips = ClusterSharding(system).start(
    "trips",
    TripActor.props(kafkaClient),
    ClusterShardingSettings(system),
    TripActor.entityIdExtractor,
    TripActor.shardIdExtractor
  )

  val tripRoutes = new TripController(trips)(system.dispatcher)

  Http().bindAndHandle(tripRoutes.routes, "localhost")
}
