package org.dcs.spark.sender

import org.apache.avro.Schema
import org.dcs.spark.{Sender, SparkUtils}
import org.dcs.commons.serde.AvroImplicits._
import org.dcs.commons.serde.AvroSchemaStore

object PrintSender {
  def apply(schemaId: String) : PrintSender =
    new PrintSender(schemaId)
}

class PrintSender(schemaId: String)

  extends Sender[Array[Array[Byte]]] with Serializable {

  override def createNewConnection(): Sender[Array[Array[Byte]]] = this

  override def send(output: Array[Array[Byte]]): Unit = {
    val schema: Option[Schema] = AvroSchemaStore.get(schemaId)
    SparkUtils.appLogger.warn("Record ===>" + output.apply(1).deSerToGenericRecord(schema, schema))
  }

  override def close(): Unit = {

  }
}
