package com.rafaelgarrote.pocbicimad.utils

import com.sksamuel.avro4s.{Record, RecordFormat}
import io.confluent.kafka.serializers.KafkaAvroSerializer
import org.apache.avro.generic.GenericRecord
import org.apache.kafka.clients.producer.RecordMetadata

import scala.concurrent.Future

case class KafkaProperties(bootstrapServers: String,
                           registryUrl: String,
                           producerId: String,
                           topic: String,
                           partitions: Int,
                           replicas: Int)

case class RecordKey[T](id: T) {

  final lazy val format = RecordFormat[T]

  def serialize: Record = format.to(id)
}

case class KafkaClient(properties: KafkaProperties) {

  lazy val kafkaSender: KafkaMessageSender[GenericRecord, GenericRecord] =
    KafkaMessageSender[GenericRecord, GenericRecord](
      producerId = properties.producerId,
      brokers = properties.bootstrapServers,
      keySerializer = classOf[KafkaAvroSerializer].getName,
      valueSerializer = classOf[KafkaAvroSerializer].getName,
      registryUrl = Some(properties.registryUrl))

  def sendEvent(key: GenericRecord, value: GenericRecord): Future[RecordMetadata] = {
    for {
      result <- kafkaSender.writeKeyValue(properties.topic, key, value)
    } yield {
      result
    }
  }

}
