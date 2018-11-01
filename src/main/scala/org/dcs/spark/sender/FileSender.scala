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

import java.io.FileWriter

import org.apache.avro.Schema
import org.dcs.commons.serde.AvroImplicits._
import org.dcs.commons.serde.AvroSchemaStore
import org.dcs.spark.SparkUtils

object TestFileSender {
  def apply(wa: String, fileName: String): FileSender = {
    new FileSender(wa, "org.dcs.spark.processor.SparkBasicStatsProcessor", fileName)
  }
}

object FileSender {
  def apply(wa: String, schemaId: String) : PrintSender =
    new PrintSender(wa, schemaId)
}

class FileSender(wa: String, schemaId: String, fileName: String)
  extends SparkSender[Array[Array[Byte]]] with Serializable {

  var fileWriter = new FileWriter(fileName, true)

  override def createNewConnection(): SparkSender[Array[Array[Byte]]] = {
    fileWriter = new FileWriter(fileName, true)
    this
  }

  override def send(output: Array[Array[Byte]]): Unit = {

    val schema: Option[Schema] = AvroSchemaStore.get(schemaId)
    val gr = output.apply(1).deSerToGenericRecord(schema, schema)
    SparkUtils.appLogger.warn("Sender ===>" + gr)
    fileWriter.write(gr.toString)
  }

  override def close(): Unit = {
    fileWriter.close()
  }

  override def key(): String = wa
}
