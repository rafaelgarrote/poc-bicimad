package com.rafaelgarrote.pocbicimad.utils

import java.util.Properties
import java.util.concurrent.TimeUnit
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.concurrent.Promise

import com.typesafe.scalalogging.LazyLogging
import org.apache.kafka.clients.producer.Callback
import org.apache.kafka.clients.producer.KafkaProducer
import org.apache.kafka.clients.producer.ProducerConfig
import org.apache.kafka.clients.producer.ProducerRecord
import org.apache.kafka.clients.producer.RecordMetadata

object KafkaMessageSender {

  private val ACKS_CONFIG = "all" // Blocking on the full commit of the record
  private val RETRIES_CONFIG = "1" // Number of retries on put
  private val BATCH_SIZE_CONFIG = "1024" // Buffers for unsent records for each partition - controlls batching
  private val LINGER_MS_CONFIG = "1" // Timeout for more records to arive - controlls batching
  private val MAX_BLOCK_MS_CONFIG = "3000" // Timeout to establish connection with broker

  private val BUFFER_MEMORY_CONFIG = "1024000"
  // Controls the total amount of memory available to the producer for buffering.
  // If records are sent faster than they can be transmitted to the server then this
  // buffer space will be exhausted. When the buffer space is exhausted additional
  // send calls will block. The threshold for time to block is determined by max.block.ms
  // after which it throws a TimeoutException.

  def kafkaProperties(producerId: String,
                      brokers: String,
                      keySerializer: String,
                      valueSerializer: String,
                      registryUrl: Option[String]): Properties = {

    val props = new Properties
    props.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, brokers)
    props.put(ProducerConfig.ACKS_CONFIG, ACKS_CONFIG)
    props.put(ProducerConfig.RETRIES_CONFIG, RETRIES_CONFIG)
    props.put(ProducerConfig.BATCH_SIZE_CONFIG, BATCH_SIZE_CONFIG)
    props.put(ProducerConfig.LINGER_MS_CONFIG, LINGER_MS_CONFIG)
    props.put(ProducerConfig.BUFFER_MEMORY_CONFIG, BUFFER_MEMORY_CONFIG)
    props.put(ProducerConfig.CLIENT_ID_CONFIG, producerId)
    props.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, keySerializer)
    props.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, valueSerializer)
    props.put(ProducerConfig.MAX_BLOCK_MS_CONFIG, MAX_BLOCK_MS_CONFIG)
    registryUrl.map(url => props.put("schema.registry.url", url))
    props
  }

  def apply[K, V](producerId: String,
                  brokers: String,
                  keySerializer: String,
                  valueSerializer: String,
                  registryUrl: Option[String] = None): KafkaMessageSender[K, V] =
    new KafkaMessageSender[K, V](producerId, brokers, keySerializer, valueSerializer, registryUrl)
}

class KafkaMessageSender[K, V](val producerId: String,
                               val brokers: String,
                               val keySerializer: String,
                               val valueSerializer: String,
                               registryUrl: Option[String]) extends LazyLogging {

  import KafkaMessageSender._


  val producer = new KafkaProducer[K, V](
    kafkaProperties(producerId, brokers, keySerializer, valueSerializer, registryUrl))

  def writeKeyValue(topic: String, key: K, value: V): Future[RecordMetadata] = {

    val result: Promise[RecordMetadata] = Promise[RecordMetadata]()

    def resolveResult(result: Promise[RecordMetadata]) = new Callback {
      override def onCompletion(metadata: RecordMetadata, exception: Exception): Unit = {
        Option(metadata) match {
          case Some(_) =>
            result success metadata
          //            producer.flush()
          case None =>
            logger.error(s"Error inserting data into kafka: ${exception.getMessage}")
            logger.trace(s"Error inserting data into kafka: ${exception.getMessage}", exception)
            result failure exception
        }
      }
    }

    producer.send(new ProducerRecord[K, V](topic, key, value), resolveResult(result))
    result.future
  }

  def batchWriteKeyValue(topic: String, key: K, batch: Seq[V]): Future[Seq[RecordMetadata]] = {
    val result: Seq[Future[RecordMetadata]] = batch.map(value => writeKeyValue(topic, key, value))
    Future.sequence(result)
  }

  def writeValue(topic: String, value: V): Future[RecordMetadata] =
    writeKeyValue(topic, null.asInstanceOf[K], value)

  def batchWriteValue(topic: String, batch: Seq[V]): Future[Seq[RecordMetadata]] =
    batchWriteKeyValue(topic, null.asInstanceOf[K], batch)

  def close(): Unit =
    producer.close(10000L, TimeUnit.MILLISECONDS)
}

