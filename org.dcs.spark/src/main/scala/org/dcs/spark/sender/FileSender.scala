package org.dcs.spark.sender

import java.io.FileWriter

import org.apache.avro.Schema
import org.dcs.commons.serde.AvroImplicits._
import org.dcs.commons.serde.AvroSchemaStore
import org.dcs.spark.SparkUtils

object TestFileSender {
  def apply(fileName: String): FileSender = {
    new FileSender("org.dcs.spark.processor.SparkBasicStatsProcessor", fileName)
  }
}

object FileSender {
  def apply(schemaId: String) : PrintSender =
    new PrintSender(schemaId)
}

class FileSender(schemaId: String, fileName: String)
  extends Sender[Array[Array[Byte]]] with Serializable {

  var fileWriter = new FileWriter(fileName, true)

  override def createNewConnection(): Sender[Array[Array[Byte]]] = {
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
}
