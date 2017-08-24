package org.dcs.spark.sender

import org.apache.avro.Schema
import org.dcs.commons.serde.AvroImplicits._
import org.dcs.commons.serde.AvroSchemaStore
import org.dcs.spark.SparkUtils


object TestSender {
  def apply(): PrintSender = {
    new PrintSender("org.dcs.core.processor.SparkBasicStatsProcessor")
  }
}


object PrintSender {
  def apply(schemaId: String) : PrintSender =
    new PrintSender(schemaId)
}

class PrintSender(schemaId: String)
  extends Sender[Array[Array[Byte]]] with Serializable {

  override def createNewConnection(): Sender[Array[Array[Byte]]] = this

  override def send(output: Array[Array[Byte]]): Unit = {
    val schema: Option[Schema] = AvroSchemaStore.get(schemaId)
    val gr = output.apply(1).deSerToGenericRecord(schema, schema)
    SparkUtils.appLogger.warn("Sender ===>" + gr)
  }

  override def close(): Unit = {

  }
}
