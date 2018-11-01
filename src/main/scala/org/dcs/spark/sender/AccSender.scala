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

package org.dcs.spark.sender

import org.apache.spark.util.AccumulatorV2
import org.dcs.commons.serde.AvroImplicits._
import org.dcs.commons.serde.AvroSchemaStore
import org.dcs.spark.SparkUtils

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
  def apply(wa: String, resultAcc: ResultAccumulator, schemaId: String): AccSender = new AccSender(wa, resultAcc, schemaId)
}

class AccSender(wa: String, resultAcc: ResultAccumulator, schemaId: String) extends SparkSender[Array[Array[Byte]]] with Serializable {

  override def createNewConnection(): SparkSender[Array[Array[Byte]]] = this

  override def send(record: Array[Array[Byte]]): Unit = {
    val schema = AvroSchemaStore.get(schemaId)
    val gr = record.apply(1).deSerToGenericRecord(schema, schema)
    SparkUtils.appLogger.warn("Record ===>" + gr)
    resultAcc.add(record)
  }

  override def close(): Unit = {

  }

  def result: Array[Array[Byte]] = resultAcc.value

  override def key(): String = wa
}
