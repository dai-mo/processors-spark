package org.dcs.spark.sender

import org.apache.avro.Schema
import org.dcs.commons.serde.AvroImplicits._
import org.dcs.commons.serde.AvroSchemaStore
import org.dcs.spark.SparkUtils


object TestSender {
  def apply(wa: String): PrintSender = {
    new PrintSender(wa, "org.dcs.core.processor.SparkBasicStatsProcessor")
  }
}


object PrintSender {
  def apply(wa: String, schemaId: String) : PrintSender =
    new PrintSender(wa, schemaId)
}

class PrintSender(wa: String, schemaId: String)
  extends SparkSender[Array[Array[Byte]]] with Serializable {

  override def createNewConnection(): SparkSender[Array[Array[Byte]]] = this

  override def send(output: Array[Array[Byte]]): Unit = {
    val schema: Option[Schema] = AvroSchemaStore.get(schemaId)
    val gr = output.apply(1).deSerToGenericRecord(schema, schema)
    SparkUtils.appLogger.warn("Sender ===>" + gr)
  }

  override def close(): Unit = {

  }

  override def key(): String = wa
}
