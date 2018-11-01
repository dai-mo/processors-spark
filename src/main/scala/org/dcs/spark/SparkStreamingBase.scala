/*
 * Copyright (c) 2017-2018 brewlabs SAS
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */

package org.dcs.spark

import java.util
import java.util.{List => JavaList, Map => JavaMap}

import com.esotericsoftware.kryo.io.{Input, Output}
import com.esotericsoftware.kryo.{Kryo, Serializer}
import org.apache.avro.generic.{GenericData, GenericRecord}
import org.apache.spark.SparkConf
import org.apache.spark.serializer.KryoRegistrator
import org.apache.spark.streaming.dstream.{DStream, MapWithStateDStream}
import org.apache.spark.streaming.receiver.Receiver
import org.apache.spark.streaming._
import org.apache.spark.util.CollectionAccumulator
import org.dcs.api.Constants
import org.dcs.api.processor._
import org.dcs.commons.serde.AvroImplicits._
import org.dcs.commons.serde.AvroSchemaStore
import org.dcs.spark.receiver.SparkReceiver
import org.dcs.spark.sender.SparkSender

import scala.collection.JavaConverters._



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


  def settings(confSettings: Map[String, String] = Map(),
               batchDuration: Duration = Seconds(1)): SparkStreamingSettings = {
    val conf = new SparkConf()
    confSettings.foreach(cf => conf.setIfMissing(cf._1, cf._2))

    val master = conf.get(Constants.SparkMasterConfKey, Constants.DefaultMaster)
    val appName = conf.get(Constants.SparkMasterConfKey, Constants.DefaultAppName)
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


  def removeSparkPrefix(property: String): String = {
    property.replaceFirst("^" + Constants.SparkPrefix, "")
  }

  def main(sparkStreamingBase: SparkStreamingBase,
           args: Array[String]) = {
    val props: JavaMap[String, String] = new util.HashMap()
    System.getProperties.asScala.foreach(p => props.put(removeSparkPrefix(p._1), p._2))

    val settings = SparkStreamingBase.settings()

    SparkUtils.appLogger.warn("System Properties ===>" + props)

    val receiver: SparkReceiver = SparkReceiver.get(props)

    val sender: SparkSender[Array[Array[Byte]]] = SparkSender.get(props)

    RunSpark.launch(settings,
      receiver,
      sender.key,
      sparkStreamingBase,
      props)
  }

}

object RunSpark {

  def launch(settings: SparkStreamingSettings,
             receiver: SparkReceiver,
             senderWithArgs: String,
             ssb: SparkStreamingBase,
             props: JavaMap[String, String],
             awaitTermination: Boolean = true): Unit = {
    init(settings, ssb, props)
    ssb.setup(settings, receiver, senderWithArgs)
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


trait SparkStreamingBase  extends Serializable
  with External {

  protected var props: JavaMap[String, String] = _
  protected var errors: CollectionAccumulator[Throwable] = _


  def init(props: JavaMap[String, String],
           errors: CollectionAccumulator[Throwable]) {
    this.props = props
    this.errors = errors
  }

  def setup(settings: SparkStreamingSettings,
            receiver: SparkReceiver,
            senderClassName: String) {
    val stream: DStream[(Int, Array[Byte])] = receiver.stream(settings.ssc)
    send(trigger(stream), senderClassName)
  }




  def trigger(stream: DStream[(Int, Array[Byte])]): DStream[(Int, Array[Byte])] = {
    stream
  }

//  def deser(stream: DStream[(Int,Array[Byte])]): DStream[(Int, GenericRecord)] = {
//    stream.flatMap(data => {
//      val (rs, ws) = resolveSchemas(true, props)
//      val fe = Try((data._1, inputToGenericRecord(data._2, rs, ws).get))
//      val check = fe match {
//        case Failure(t) =>
//          errors.add(t)
//          fe
//        case t:Try[(Int, GenericRecord)] => t
//      }
//      check.toOption
//    })
//  }


  def send(stream: DStream[(Int, Array[Byte])], senderWithArgs: String): Unit = {
    stream
      .foreachRDD {rdd =>
        rdd.foreachPartition {partitionOfRecords =>
          val sender: SparkSender[Array[Array[Byte]]] = SparkSender.get(senderWithArgs)
          sender.createNewConnection()
          partitionOfRecords.foreach (out =>
            sender.send(Array(RelationshipType.Success.id.getBytes, out._2))
          )
          sender.close()
        }
      }
  }


  override def metadata(): MetaData = MetaData("Spark Job")

  override def schemaId: String = {
    props.get(CoreProperties.SchemaIdKey)
  }
}

trait SparkStreamingStateBase extends SparkStreamingBase {

  override def setup(settings: SparkStreamingSettings,
                     receiver: SparkReceiver,
                     senderClassName: String) {
    val stream: DStream[(Int, Array[Byte])] = receiver.stream(settings.ssc)
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
    val processorClassName = props.get(CoreProperties.ProcessorClassKey)
    val (rs, ws) = RemoteProcessor.resolveSchemas(true, props, processorClassName, schemaId)
    val grState = state.getOption().flatMap(s => RemoteProcessor.inputToGenericRecord(s, ws, ws)).getOrElse(stateZero())

    val out = record.flatMap(r => stateUpdate(props)(batchTime, id, RemoteProcessor.inputToGenericRecord(r, rs, ws), grState)
      .map(res => {
        res.serToBytes(ws)
      }))

    out.foreach(o => {
      state.update(o)
    }
    )
    out.map((id, _))
  }

  def reduceStateFunction(r1: (Int, Array[Byte]), r2: (Int, Array[Byte])): (Int, Array[Byte]) = {

    val schema = AvroSchemaStore.get(schemaId)

    (r2._1, stateReduce(RemoteProcessor.inputToGenericRecord(r1._2, schema, schema).get,
      RemoteProcessor.inputToGenericRecord(r2._2, schema, schema).get)
      .serToBytes(schema))

  }

  def stateUpdate(props: JavaMap[String, String])(batchTime: Time,id: Int, record: Option[GenericRecord], state: GenericRecord): Option[GenericRecord]

  def stateReduce(gr1: GenericRecord, gr2: GenericRecord): GenericRecord

  def stateZero(): GenericRecord
}
