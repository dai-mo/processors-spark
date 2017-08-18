package org.dcs.spark

import java.util
import java.util.{Map => JavaMap}

import com.esotericsoftware.kryo.io.{Input, Output}
import com.esotericsoftware.kryo.{Kryo, Serializer}
import org.apache.avro.data.Json
import org.apache.avro.generic.{GenericData, GenericRecord}
import org.apache.spark.SparkConf
import org.apache.spark.serializer.KryoRegistrator
import org.apache.spark.streaming.dstream.{DStream, MapWithStateDStream}
import org.apache.spark.streaming.receiver.Receiver
import org.apache.spark.streaming.{Duration, Minutes, Seconds, State, StateSpec, StreamingContext, Time}
import org.apache.spark.util.CollectionAccumulator
import org.dcs.api.processor.{CoreProperties, RelationshipType, RemoteProcessor, StatefulRemoteProcessor}
import org.dcs.commons.error.ErrorResponse
import org.dcs.commons.serde.AvroImplicits._
import org.dcs.commons.serde.AvroSchemaStore
import org.dcs.spark.receiver.TestReceiver
import org.dcs.spark.sender.{PrintSender, TestSender}

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
    ssc.checkpoint("/tmp/checkpoint")
    ssc.remember(Minutes(1))
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

    val settings = SparkStreamingBase.localSettings()
    val receiver: Receiver[(Int, Array[Byte])] = System.getProperty("org.dcs.spark.receiver") match {
      case "org.dcs.spark.receiver.TestReceiver" => {
        props.putAll(TestReceiver.props)
        TestReceiver(props, 1000, 100)
      }
      case _ => throw new IllegalArgumentException("No known receiver has been set (org.dcs.spark.receiver)")
    }

    val sender:Sender[Array[Array[Byte]]] =  System.getProperty("org.dcs.spark.sender") match {
      case "org.dcs.spark.sender.TestSender" => TestSender()
      case _ => throw new IllegalArgumentException("No known sender has been set (org.dcs.spark.sender)")
    }

    RunSpark.launch(settings,
      receiver,
      sender,
      sparkStreamingBase,
      props)
  }

}

object RunSpark {

  def launch(settings: SparkStreamingSettings,
             receiver: Receiver[(Int, Array[Byte])],
             sender: Sender[Array[Array[Byte]]],
             ssb: SparkStreamingBase,
             props: JavaMap[String, String],
             awaitTermination: Boolean = true): Unit = {
    init(settings, ssb, props)
    ssb.setup(settings, receiver, sender)
    start(settings, awaitTermination)
  }

  def init(settings: SparkStreamingSettings,
           ssb: SparkStreamingBase,
           props: JavaMap[String, String]) = {
    ssb.init(props,
      settings.ssc.sparkContext.collectionAccumulator[Throwable])
  }


  def start(settings: SparkStreamingSettings,
            awaitTermination: Boolean = true): Unit = {
    settings.ssc.start
    if(awaitTermination)
      settings.ssc.awaitTermination
  }
}


trait SparkStreamingBase  extends RemoteProcessor with Serializable {


  var props: JavaMap[String, String] = _
  var errors: CollectionAccumulator[Throwable] = _


  def init(props: JavaMap[String, String],
           errors: CollectionAccumulator[Throwable]) {
    this.props = props
    this.errors = errors
  }

  def setup(settings: SparkStreamingSettings,
            receiver: Receiver[(Int,Array[Byte])],
            sender: Sender[Array[Array[Byte]]]) {
    val stream: DStream[(Int, Array[Byte])] = settings.ssc.receiverStream(receiver)
    send(trigger(stream), sender)
  }

  override def execute(record: Option[GenericRecord], propertyValues: util.Map[String, String]): List[Either[ErrorResponse, (String, GenericRecord)]] = {
    throw new UnsupportedOperationException
  }

  def trigger(stream: DStream[(Int, Array[Byte])]): DStream[(Int, Array[Byte])] = {
    stream
  }

  def deser(stream: DStream[(Int,Array[Byte])]): DStream[(Int, GenericRecord)] = {
    stream.flatMap(data => {
      val (rs, ws) = resolveSchemas(true, props)
      val fe = Try((data._1, inputToGenericRecord(data._2, rs, ws).get))
      val check = fe match {
        case Failure(t) =>
          errors.add(t)
          fe
        case t:Try[(Int, GenericRecord)] => t
      }
      check.toOption
    })
  }


  def send(stream: DStream[(Int, Array[Byte])],
           sender: Sender[Array[Array[Byte]]]): Unit = {
    stream
      .foreachRDD {rdd =>
        rdd.foreachPartition {partitionOfRecords =>
          val connection = sender.createNewConnection ()
          partitionOfRecords.foreach (out =>
            sender.send(Array(RelationshipType.Success.id.getBytes, out._2))
          )
          connection.close()
        }
      }
  }
}

trait SparkStreamingStateBase extends SparkStreamingBase {

  override def setup(settings: SparkStreamingSettings,
            receiver: Receiver[(Int,Array[Byte])],
            sender: Sender[Array[Array[Byte]]]) {
    val stream: DStream[(Int, Array[Byte])] = settings.ssc.receiverStream(receiver)
    stateSend(stateTrigger(stream), sender)
  }

  def stateTrigger(stream: DStream[(Int, Array[Byte])]): MapWithStateDStream[Int,Array[Byte], Array[Byte], (Int,Array[Byte])] = {
    trigger(stream).map(in => (in._1 % 2, in._2))
      .mapWithState(StateSpec
        .function(stateUpdateFunction(props) _)
        .numPartitions(2)
        .timeout(Seconds(60)))
  }

  def stateSend(stream: MapWithStateDStream[Int,Array[Byte], Array[Byte], (Int, Array[Byte])],
                sender: Sender[Array[Array[Byte]]]): Unit = {
    send(stream.stateSnapshots().reduce(reduceStateFunction _), sender)
  }


  def stateUpdateFunction(props: JavaMap[String, String])
                         (batchTime: Time,
                          id: Int,
                          record: Option[Array[Byte]],
                          state: State[Array[Byte]]): Option[(Int, Array[Byte])] = {
    val (rs, ws) = resolveSchemas(true, props)
    val schema = AvroSchemaStore.get(schemaId)
    val grState = state.getOption().flatMap(s => inputToGenericRecord(s, schema, schema)).getOrElse(initialState())

    val out = record.flatMap(r => stateUpdate(props)(batchTime, id, inputToGenericRecord(r, rs, ws), grState)
      .map(res => {
        res.serToBytes(schema)
      }))

    out.foreach(o => {
      state.update(o)
    }
    )
    out.map((id, _))
  }

  def reduceStateFunction(r1: (Int, Array[Byte]), r2: (Int, Array[Byte])): (Int, Array[Byte]) = {
    val schema = AvroSchemaStore.get(schemaId)
    (r2._1, stateReduce(inputToGenericRecord(r1._2, schema, schema).get, inputToGenericRecord(r2._2, schema, schema).get)
      .serToBytes(schema))
  }

  def stateUpdate(props: JavaMap[String, String])(batchTime: Time,id: Int, record: Option[GenericRecord], state: GenericRecord): Option[GenericRecord]

  def stateReduce(gr1: GenericRecord, gr2: GenericRecord): GenericRecord

  def initialState(): GenericRecord
}
