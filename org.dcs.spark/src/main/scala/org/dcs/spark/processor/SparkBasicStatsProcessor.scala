package org.dcs.spark.processor

import java.util
import java.util.{Map => JavaMap}

import org.apache.avro.generic.{GenericRecord, GenericRecordBuilder}
import org.apache.spark.streaming.{State, Time}
import org.dcs.api.processor.RelationshipType._
import org.dcs.api.processor._
import org.dcs.commons.serde.AvroSchemaStore
import org.dcs.spark.{SparkStreamingBase, SparkStreamingStateBase, SparkUtils}

import scala.collection.JavaConverters._


object SparkBasicStatsProcessor {
  val AverageKey = "average"
  val CountKey = "count"

  def apply(): SparkBasicStatsProcessor = {
    new SparkBasicStatsProcessor()
  }

  def main(args: Array[String]): Unit = {
    SparkStreamingBase.main(new SparkBasicStatsProcessor(), args)
  }
}

/**
  * Created by cmathew on 09.11.16.
  */
class SparkBasicStatsProcessor extends SparkStreamingStateBase
  with FieldsToMap
  with Ingestion {

  import SparkBasicStatsProcessor._


  override def stateZero(): GenericRecord = {
    AvroSchemaStore.get(schemaId).map(s =>
      new GenericRecordBuilder(s)
        .set(CountKey, 0)
        .set(AverageKey, new util.HashMap())
        .build()
    ).get
  }

  override def stateUpdate(props: JavaMap[String, String])
                 (batchTime: Time,
                  id: Int,
                  record: Option[GenericRecord],
                  state: GenericRecord): Option[GenericRecord] = {
    val m = record.mappings(props)
    val avgs = m.get(AverageKey).asList[(String, Int)]

    val count = Option(state.get(CountKey)).asInt.getOrElse(0)
    val currentAvgs = Option(state.get(AverageKey)).asMap[String, Double].getOrElse(Map())

    val cavgs = avgs.map(ma =>
      ma.map(a =>{
        val cavg = currentAvgs.getOrElse(a._1, 0.0)
        val avg = (a._2 + cavg * count) / (count + 1)
        (a._1, avg)
      })
    ).map(_.toMap).getOrElse(Map()).asJava

    val out = AvroSchemaStore.get(schemaId).map(s =>
      new GenericRecordBuilder(s)
        .set(CountKey, count + 1)
        .set(AverageKey, cavgs)
        .build()
    )
    out
  }

  override def stateReduce(gr1: GenericRecord, gr2: GenericRecord): GenericRecord = {
    val count1 = Option(gr1.get(CountKey)).asInt.getOrElse(0)
    val count2 = Option(gr2.get(CountKey)).asInt.getOrElse(0)
    val currentTotalCount = count1 + count2

    val currentAvgs1 = Option(gr1.get(AverageKey)).asMap[String, Double].getOrElse(Map())
    val currentAvgs2 = Option(gr2.get(AverageKey)).asMap[String, Double].getOrElse(Map())

    val currentAvgs = currentAvgs1.map(cat1 =>
      (cat1._1, currentAvgs2.get(cat1._1)
        .map(ca2 => ((count1 * cat1._2) + (count2 * ca2)) / currentTotalCount ).getOrElse(0.0))).asJava

    val out = AvroSchemaStore.get(schemaId).map(s =>
      new GenericRecordBuilder(s)
        .set(CountKey, currentTotalCount)
        .set(AverageKey, currentAvgs)
        .build()
    ).get
    out
  }


  override def _relationships(): Set[RemoteRelationship] = {
    Set(Success)
  }


  override def metadata(): MetaData =
    MetaData(description =  "Spark Basic Statistics Processor",
      tags = List("Spark", "Statistics"))



  override def _properties(): List[RemoteProperty] = List()

  override def fields: Set[ProcessorSchemaField] = Set(ProcessorSchemaField(AverageKey, PropertyType.Double))


}

