package org.dcs.spark.sender

import org.apache.spark.util.AccumulatorV2
import org.dcs.commons.serde.AvroSchemaStore
import org.dcs.spark.{Sender, SparkUtils}
import org.dcs.commons.serde.AvroImplicits._

import scala.collection.mutable

class ResultAccumulator extends AccumulatorV2[Array[Array[Byte]], Array[Array[Byte]]] {

  private var records: mutable.Queue[Array[Array[Byte]]] = mutable.Queue()

  override def isZero: Boolean = records.isEmpty

  override def copy(): AccumulatorV2[Array[Array[Byte]], Array[Array[Byte]]] = {
    val ta = new ResultAccumulator()
    records.foreach(r => ta.records +=  r)
    ta
  }

  override def reset(): Unit = records = mutable.Queue()

  override def add(v: Array[Array[Byte]]): Unit = records += v

  override def merge(other: AccumulatorV2[Array[Array[Byte]], Array[Array[Byte]]]): Unit =
    records ++= other.asInstanceOf[ResultAccumulator].records

  override def value: Array[Array[Byte]] = records.last

}

object AccSender {
  def apply(resultAcc: ResultAccumulator): AccSender = new AccSender(resultAcc)
}

class AccSender(resultAcc: ResultAccumulator) extends Sender[Array[Array[Byte]]] with Serializable {

  override def createNewConnection(): Sender[Array[Array[Byte]]] = this

  override def send(record: Array[Array[Byte]]): Unit = {
    val schema = AvroSchemaStore.get("org.dcs.spark.processor.SparkBasicStatsProcessor")
    val gr = record.apply(1).deSerToGenericRecord(schema, schema)
    SparkUtils.appLogger.warn("Record ===>" + gr)
    resultAcc.add(record)
  }

  override def close(): Unit = {

  }

  def result: Array[Array[Byte]] = resultAcc.value
}
