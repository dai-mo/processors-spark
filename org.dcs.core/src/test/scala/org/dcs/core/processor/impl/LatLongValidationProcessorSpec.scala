package org.dcs.core.processor.impl

import org.apache.avro.generic.GenericData
import org.dcs.api.processor.CoreProperties._
import org.dcs.api.processor.{ProcessorField, PropertyType, RelationshipType}
import org.dcs.commons.serde.AvroSchemaStore
import org.dcs.commons.serde.JsonSerializerImplicits._
import org.dcs.core.processor.LatLongValidationProcessor
import org.dcs.core.{BaseProcessorUnitSpec, CoreUnitWordSpec}

import scala.collection.JavaConverters._
/**
  * Created by cmathew on 22.03.17.
  */
class LatLongValidationProcessorSpec  extends CoreUnitWordSpec
  with BaseProcessorUnitSpec {

  val defaultSchemaId = "org.dcs.core.processor.LatLong"
  addSchemaToStore(defaultSchemaId)

  "The LatLongValidation Processor" should  {
    val processor = new LatLongValidationProcessor()
    val schema = AvroSchemaStore.get(defaultSchemaId)
    val mappings = List(ProcessorField("latitude", PropertyType.String, "$.latitude"),
      ProcessorField("longitude", PropertyType.String, "$.longitude")).toJson


    "return valid response for valid lat / longs" in {
      assert {
        val in = new GenericData.Record(schema.get)
        in.put("latitude", 50.0)
        in.put("longitude", 50.0)

        val response = processor
          .execute(Some(in),
            Map(ReadSchemaIdKey -> defaultSchemaId, FieldsToMapKey -> mappings).asJava)
        val out = response.head.right.get
        out._2.get("latitude").asInstanceOf[Double] == 50 &&
          out._2.get("longitude").asInstanceOf[Double] == 50 &&
          out._1 == RelationshipType.Valid.id
      }
    }

    "return invalid response for invalid lat / longs" in {
      assert { val in = new GenericData.Record(schema.get)
      in.put("latitude", -100.0)
      in.put("longitude", 190.0)

      val response = processor
        .execute(Some(in),
          Map(ReadSchemaIdKey -> defaultSchemaId, FieldsToMapKey -> mappings).asJava)
        response.head.right.get._1 == RelationshipType.Invalid.id
      }
    }
  }
}