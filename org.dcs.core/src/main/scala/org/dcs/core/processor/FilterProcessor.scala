package org.dcs.core.processor

/**
  * Created by cmathew on 19.11.16.
  */


import java.util

import org.apache.avro.generic.GenericRecord
import org.dcs.api.processor._
import org.dcs.commons.error.ErrorResponse

import scala.collection.JavaConverters._

object FilterProcessor {

  def apply(): FilterProcessor = {
    new FilterProcessor()
  }

  val ContainsCmd = "contains"
  val StartsWithCmd = "starts with"

}

/**
  * Created by cmathew on 09.11.16.
  */
class FilterProcessor extends RemoteProcessor
  with Worker
  with FieldActions {

  import FilterProcessor._

  override def execute(record: Option[GenericRecord], propertyValues: util.Map[String, String]): List[Either[ErrorResponse, GenericRecord]] = {

    val isValid: Boolean = actions(propertyValues).map(a => a.cmd match {
      case ContainsCmd => a.fromJsonPath(record).value.asString.exists(s => s.contains(a.args))
      case StartsWithCmd => a.fromJsonPath(record).value.asString.exists(s => s.contains(a.args))
      case _ => false
    }).forall(identity)

    if(isValid)
      List(Right(record.get))
    else
      List(Right(null))
  }


  override def _relationships(): Set[RemoteRelationship] = {
    Set(RelationshipType.SUCCESS, RelationshipType.FAILURE)
  }

  override def metadata(): MetaData =
    MetaData(description =  "Filter Processor",
      tags = List("filter").asJava)

  override def _properties(): List[RemoteProperty] = Nil //List(FilterTermProperty, FilterProperty)

  override def cmds: List[String] = List("contains", "starts with")
}

