package org.dcs.spark

import java.io.File
import java.util
import java.util.{List => JavaList, Map => JavaMap}

import buildinfo.BuildInfo
import com.esotericsoftware.kryo.io.{Input, Output}
import com.esotericsoftware.kryo.{Kryo, Serializer}
import org.apache.avro.generic.{GenericData, GenericRecord}
import org.apache.spark.SparkConf
import org.apache.spark.launcher.{SparkAppHandle, SparkLauncher}
import org.apache.spark.serializer.KryoRegistrator
import org.apache.spark.streaming.dstream.{DStream, MapWithStateDStream}
import org.apache.spark.streaming.receiver.Receiver
import org.apache.spark.streaming.{Duration, Minutes, Seconds, State, StateSpec, StreamingContext, Time}
import org.apache.spark.util.CollectionAccumulator
import org.dcs.api.processor._
import org.dcs.commons.error.ErrorResponse
import org.dcs.commons.serde.AvroImplicits._
import org.dcs.commons.serde.AvroSchemaStore
import org.dcs.spark.receiver.TestReceiver
import org.dcs.spark.sender.Sender

import scala.collection.JavaConverters._
import scala.util.{Failure, Try}



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
  val ReceiverKey = "org.dcs.spark.receiver"
  val SenderKey = "org.dcs.spark.sender"

  val SparkNameConfKey = "spark.app.name"
  val SparkMasterConfKey = "spark.master"

  val DefaultMaster = "local[2]"
  val DefaultAppName = "AlambeekSparkLocal"

  val SparkPrefix = "spark."

  def settings(confSettings: Map[String, String] = Map(),
               batchDuration: Duration = Seconds(1)): SparkStreamingSettings = {
    val conf = new SparkConf()
    confSettings.foreach(cf => conf.setIfMissing(cf._1, cf._2))

    val master = conf.get(SparkMasterConfKey, DefaultMaster)
    val appName = conf.get(SparkMasterConfKey, DefaultAppName)
    conf
      .setMaster(master)
      .setAppName(appName)
    conf.getAll.foreach(c => SparkUtils.appLogger.warn("SparkConf ===>" + c))

    val ssc = new StreamingContext(conf, batchDuration)

    // FIXME: Make checkpoint dir configurable
    ssc.checkpoint("/tmp/checkpoint")

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

  def appendSparkPrefix(property: String): String = {
    SparkPrefix + property
  }

  def removeSparkPrefix(property: String): String = {
    property.replaceFirst("^" + SparkPrefix, "")
  }

  def main(sparkStreamingBase: SparkStreamingBase,
           args: Array[String]) = {
    val props: JavaMap[String, String] = System.getProperties.asScala.map(p => (removeSparkPrefix(p._1), p._2)).asJava


    val settings = SparkStreamingBase.settings()

    SparkUtils.appLogger.warn("System Properties ===>" + props)

    val receiver: Receiver[(Int, Array[Byte])] = props.get(ReceiverKey) match {
      case "org.dcs.spark.receiver.TestReceiver" => {
        TestReceiver(1000, 100)
      }
      case _ => throw new IllegalArgumentException("No known receiver has been set (org.dcs.spark.receiver)")
    }

    val sender: Sender[Array[Array[Byte]]] = Sender.get(props)

    RunSpark.launch(settings,
      receiver,
      sender.getClass.getName,
      sparkStreamingBase,
      props)
  }

}

object RunSpark {

  def launch(settings: SparkStreamingSettings,
             receiver: Receiver[(Int, Array[Byte])],
             senderClassName: String,
             ssb: SparkStreamingBase,
             props: JavaMap[String, String],
             awaitTermination: Boolean = true): Unit = {
    init(settings, ssb, props)
    ssb.setup(settings, receiver, senderClassName)
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


trait SparkStreamingBase  extends StatefulRemoteProcessor with Serializable {
  import SparkStreamingBase._

  protected var props: JavaMap[String, String] = _
  protected var errors: CollectionAccumulator[Throwable] = _

  private var sparkHandle: SparkAppHandle = _
  private var sparkLauncher: SparkLauncher = _

  val SPARK_HOME = "SPARK_HOME"

  override def initState(): Unit = {
    // NOTE: This requires the SPARK_HOME to be set
    //       and the dcs spark jar be copied to the
    //       SPARK_HOME dir
    val sparkHome = sys.env(SPARK_HOME)
    val dcsSparkJar = "org.dcs.spark-assembly-" + BuildInfo.version + ".jar"
    sparkLauncher = new SparkLauncher()
      .setAppResource(sparkHome + File.separator + dcsSparkJar)
      .setMainClass(this.className)
      .setMaster(DefaultMaster)
      .setAppName(DefaultAppName)
      .setConf(SparkLauncher.DRIVER_MEMORY, "2g")
  }

  override def onSchedule(propertyValues: JavaMap[RemoteProperty, String]): Boolean = {
    val receiver = propertyValues.asScala.find(_._1.getName == ReceiverKey).map(_._2)
    val sender = propertyValues.asScala.find(_._1.getName == SenderKey).map(_._2)
    val readSchemaId = propertyValues.asScala.find(_._1.getName == CoreProperties.ReadSchemaIdKey).map(_._2)

    if(receiver.isDefined && sender.isDefined && readSchemaId.isDefined) {
      propertyValues.asScala.foreach(pv =>
        sparkLauncher = sparkLauncher.setConf(appendSparkPrefix(pv._1.getName()), pv._2)
      )
      sparkHandle = sparkLauncher.startApplication()
      true
    } else
      throw new IllegalArgumentException
  }

  override def onStop(propertyValues: JavaMap[RemoteProperty, String]): Boolean = {
    true
  }

  override def onShutdown(propertyValues: JavaMap[RemoteProperty, String]): Boolean = {
    onRemove(propertyValues)
  }

  override def onRemove(propertyValues: JavaMap[RemoteProperty, String]): Boolean = {
    if(sparkHandle.getState == SparkAppHandle.State.RUNNING) {
      // FIXME: Need to figure out why stop does not work
      // sparkHandle.stop()
      sparkHandle.kill()
    }
    true
  }

  def init(props: JavaMap[String, String],
           errors: CollectionAccumulator[Throwable]) {
    this.props = props
    this.errors = errors
  }

  def setup(settings: SparkStreamingSettings,
            receiver: Receiver[(Int,Array[Byte])],
            senderClassName: String) {
    val stream: DStream[(Int, Array[Byte])] = settings.ssc.receiverStream(receiver)
    send(trigger(stream), senderClassName)
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


  def send(stream: DStream[(Int, Array[Byte])], senderClassName: String): Unit = {
    stream
      .foreachRDD {rdd =>
        rdd.foreachPartition {partitionOfRecords =>

          val sender: Sender[Array[Array[Byte]]] = Sender.get(Some(senderClassName))
          sender.createNewConnection()
          partitionOfRecords.foreach (out =>
            sender.send(Array(RelationshipType.Success.id.getBytes, out._2))
          )
          sender.close()
        }
      }
  }


}

trait SparkStreamingStateBase extends SparkStreamingBase {

  override def setup(settings: SparkStreamingSettings,
                     receiver: Receiver[(Int,Array[Byte])],
                     senderClassName: String) {
    val stream: DStream[(Int, Array[Byte])] = settings.ssc.receiverStream(receiver)
    stateSend(stateTrigger(stream), senderClassName)
  }

  def stateTrigger(stream: DStream[(Int, Array[Byte])]): MapWithStateDStream[Int,Array[Byte], Array[Byte], (Int,Array[Byte])] = {
    trigger(stream).map(in => (in._1 % 2, in._2))
      .mapWithState(StateSpec
        .function(stateUpdateFunction(props) _)
        .numPartitions(2)
        .timeout(Seconds(60)))
  }

  def stateSend(stream: MapWithStateDStream[Int,Array[Byte], Array[Byte], (Int, Array[Byte])], senderClassName: String): Unit = {
    send(stream.stateSnapshots().reduce(reduceStateFunction _), senderClassName)
  }


  def stateUpdateFunction(props: JavaMap[String, String])
                         (batchTime: Time,
                          id: Int,
                          record: Option[Array[Byte]],
                          state: State[Array[Byte]]): Option[(Int, Array[Byte])] = {
    val (rs, ws) = resolveSchemas(true, props)
    val schema = AvroSchemaStore.get(schemaId)
    val grState = state.getOption().flatMap(s => inputToGenericRecord(s, schema, schema)).getOrElse(stateZero())

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

  def stateZero(): GenericRecord
}
