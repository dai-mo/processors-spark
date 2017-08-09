package org.dcs.spark

import java.util
import java.util.{Map => JavaMap}

import com.esotericsoftware.kryo.io.{Input, Output}
import com.esotericsoftware.kryo.{Kryo, Serializer}
import org.apache.avro.Schema
import org.apache.avro.generic.{GenericData, GenericRecord}
import org.apache.spark.SparkConf
import org.apache.spark.serializer.KryoRegistrator
import org.apache.spark.streaming.dstream.DStream
import org.apache.spark.streaming.receiver.Receiver
import org.apache.spark.streaming.{Duration, Seconds, StreamingContext}
import org.apache.spark.util.CollectionAccumulator
import org.dcs.api.processor.{CoreProperties, RelationshipType, StatefulRemoteProcessor}
import org.dcs.commons.error.ErrorResponse
import org.dcs.commons.serde.AvroImplicits._
import org.dcs.commons.serde.AvroSchemaStore
import org.dcs.spark.receiver.{ListReceiver, TestReceiver}
import org.dcs.spark.sender.{AccSender, PrintSender, ResultAccumulator}

import scala.util.{Failure, Try}

trait Sender[T] {
  def createNewConnection(): Sender[T]
  def send(record: T)
  def close()
}

case class SparkStreamingSettings(conf: SparkConf,
                                  ssc: StreamingContext) {
  SparkStreamingBase.updateConf(conf)
}

class DCSRegistrator extends KryoRegistrator {
  override def registerClasses(kryo: Kryo) {
    kryo.register(classOf[GenericData.Record], new GenRecSerializer())
  }
}

class GenRecSerializer extends Serializer[GenericData.Record] {
  override def read(kryo: Kryo, input: Input, `type`: Class[GenericData.Record]): GenericData.Record =
    input.getInputStream.deSerToGenericRecord().asInstanceOf[GenericData.Record]

  override def write(kryo: Kryo, output: Output, `object`: GenericData.Record): Unit =
    `object`.serToOutputStream(output.getOutputStream)
}

object SparkStreamingBase {
  def localSettings(confSettings: Map[String, String] = Map(),
                    batchDuration: Duration = Seconds(1)): SparkStreamingSettings = {
    val conf = new SparkConf().setMaster("local[2]").setAppName("AlambeekSparkLocal")
    confSettings.foreach(cf => conf.setIfMissing(cf._1, cf._2))
    val ssc = new StreamingContext(conf, Seconds(1))
    SparkStreamingSettings(conf, ssc)
  }

  def updateConf(conf: SparkConf): SparkConf = {
    conf
    //      .set("spark.serializer", "org.apache.spark.serializer.KryoSerializer")
    //      .set("spark.kryo.registrator", "org.dcs.spark.DCSRegistrator")
    //      .set("spark.kryo.registrationRequired", "true")
    //      .set("spark.kryo.classesToRegister","org.apache.avro.generic.GenericData.Record")
    //      .registerKryoClasses(Array(classOf[GenericData.Record]))
    //      .registerAvroSchemas(Json.SCHEMA)
  }


  def main(sparkStreamingBase: SparkStreamingBase,
           args: Array[String]) = {
    val props: JavaMap[String, String] = new util.HashMap()
    props.put(CoreProperties.ReadSchemaIdKey, TestReceiver.PersonSchemaId)

    val settings = SparkStreamingBase.localSettings()
    val receiver = System.getProperty("org.dcs.spark.receiver") match {
      case "org.dcs.spark.receiver.TestReceiver" => TestReceiver(props, 2000)
      case _ => throw new IllegalArgumentException("No known receiver has been set (org.dcs.spark.receiver)")
    }

    // val receiver = TestReceiver(props, 2000)

    val sender = PrintSender(TestReceiver.PersonSchemaId)

    RunSpark.launch(settings,
      receiver,
      sender,
      sparkStreamingBase,
      props)
  }

}

object RunSpark {

  def launch(settings: SparkStreamingSettings,
             receiver: Receiver[Array[Byte]],
             sender: Sender[Array[Array[Byte]]],
             ssb: SparkStreamingBase,
             props: JavaMap[String, String],
             awaitTermination: Boolean = true): Unit = {
    init(settings, ssb, props)
    setup(settings, receiver, sender, ssb, props)
    start(settings, awaitTermination)
  }

  def init(settings: SparkStreamingSettings,
           ssb: SparkStreamingBase,
           props: JavaMap[String, String]) = {
    ssb.init(props,
      settings.ssc.sparkContext.collectionAccumulator[Throwable])
  }

  def setup(settings: SparkStreamingSettings,
            receiver: Receiver[Array[Byte]],
            sender: Sender[Array[Array[Byte]]],
            ssb: SparkStreamingBase,
            props: JavaMap[String, String]) {
    val stream: DStream[Array[Byte]] = settings.ssc.receiverStream(receiver)
    ssb.send(ssb.trigger(stream), sender)
  }

  def start(settings: SparkStreamingSettings,
            awaitTermination: Boolean = true): Unit = {
    settings.ssc.start
    if(awaitTermination)
      settings.ssc.awaitTermination
  }
}


trait SparkStreamingBase  extends StatefulRemoteProcessor with Serializable {


  var props: JavaMap[String, String] = _
  var errors: CollectionAccumulator[Throwable] = _


  def init(props: JavaMap[String, String],
           errors: CollectionAccumulator[Throwable]) {
    this.props = props
    this.errors = errors
  }


  override def execute(record: Option[GenericRecord], propertyValues: util.Map[String, String]): List[Either[ErrorResponse, (String, GenericRecord)]] = {
    throw new UnsupportedOperationException
  }

  def trigger(stream: DStream[Array[Byte]] ): DStream[Array[Array[Byte]]] = {
    val grStream = deser(stream)
    execute(grStream)
      .map(genRec => {
        val (rs, ws) = resolveSchemas(true, props)
        resultToOutput (true, Right ((RelationshipType.Success.id, genRec) ), rs, ws)
      } )
  }

  def deser(stream: DStream[Array[Byte]]): DStream[GenericRecord] = {
    stream.flatMap(data => {
      val (rs, ws) = resolveSchemas(true, props)
      val fe = Try(inputToGenericRecord(data, rs, ws).get)
      val check = fe match {
        case Failure(t) =>
          errors.add(t)
          fe
        case t:Try[GenericRecord] => t
      }
      check.toOption
    })
  }

  def send(stream: DStream[Array[Array[Byte]]], sender: Sender[Array[Array[Byte]]]): Unit = {
    stream
      .foreachRDD {rdd =>
        rdd.foreachPartition {partitionOfRecords =>
          val connection = sender.createNewConnection ()
          partitionOfRecords.foreach (out => sender.send(out))
          connection.close()
        }
      }
  }


  def execute(record: DStream[GenericRecord]): DStream[GenericRecord]

}
