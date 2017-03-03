package org.dcs.data.slick


import java.sql.Timestamp
import java.util.{List => JavaList}

import org.dcs.api.data.{FlowDataContent, FlowDataProvenance}
import org.dcs.api.processor.RemoteProcessor
import org.dcs.api.service.Provenance
import org.dcs.data.IntermediateResultsAdapter
import slick.driver.JdbcDriver

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future




/**
  * Created by cmathew on 17.02.17.
  */
class SlickIntermediateResults(val driver: JdbcDriver, dbType: String) extends IntermediateResultsAdapter {
  import driver.api._
  val db = Database.forConfig(dbType, classLoader = this.getClass.getClassLoader)

  private[this] var _logQueries: Boolean = false

  private def logQueries: Boolean = _logQueries

  private def logQueries_=(value: Boolean): Unit = {
    _logQueries = value
  }

  private def uniqueForId[T](seq: Seq[T], id : String): Option[T] = {
    if(seq.size > 1)
      throw new IllegalStateException("More than one content record for given id " + id)
    seq.headOption
  }

  private def getContentQuery(contentId : Rep[String]) = {
    Tables.FlowDataContent.filter(_.id === contentId)
  }

  private lazy val getContentQueryCompiled = Compiled(getContentQuery _)

  override def getContent(contentId : String): Future[Option[Tables.FlowDataContentRow]] = {
    val contentAction = getContentQueryCompiled(contentId).result
    db.run(contentAction).map(uniqueForId(_, contentId))
  }


  override def createContent(fdc: FlowDataContent): Future[Unit] = {
    val contentRow = Tables.FlowDataContentRow(fdc.id,
      Option(fdc.claimCount),
      Option(new Timestamp(fdc.timestamp.getTime)),
      Option(fdc.data))

    val createContentAction = DBIO.seq(
      Tables.FlowDataContent += contentRow
    )

    db.run(createContentAction)
  }

  private def claimaintCountQuery(contentId : Rep[String]) = {
    Tables.FlowDataContent.filter(_.id === contentId).map(_.claimCount)
  }

  private lazy val claimaintCountQueryCompiled = Compiled(claimaintCountQuery _)

  private def updateClaimaintCount(contentId : String, updateBy: Int): Future[Option[Int]] = {

    var updatedClaimCount: Option[Int] = None
    val incrementClaimaintCountAction = getContentQueryCompiled(contentId).result.
      flatMap(content => {
        updatedClaimCount = Some(content.headOption.get.claimCount.get + updateBy)
        claimaintCountQueryCompiled(contentId).update(updatedClaimCount)
      })

    db.run(incrementClaimaintCountAction).
      map {
        case 0 => None
        case _ => updatedClaimCount
      }
  }

  override def incrementClaimaintCount(contentId : String): Future[Option[Int]] = updateClaimaintCount(contentId, 1)
  override def decrementClaimaintCount(contentId : String): Future[Option[Int]] = updateClaimaintCount(contentId, -1)


  override def getClaimantCount(contentId: String): Future[Option[Int]] = {
    db.run(claimaintCountQueryCompiled(contentId).result).
      map(uniqueForId(_, contentId).get)
  }

  override def getContentSize: Future[Int] = {
    db.run(Tables.FlowDataContent.length.result)
  }

  override def deleteContent(contentId: String): Future[Int] = {
    db.run(getContentQueryCompiled(contentId).delete)
  }

  override def purgeContent(): Future[Int] = db.run(Tables.FlowDataContent.delete)



  override def createProvenance(fdp: FlowDataProvenance): Future[Unit] = {
    val provenanceRow = BigTables.BigFlowDataProvenanceRow(fdp.id,
      1,
      Option(fdp.eventTime),
      Option(fdp.flowFileEntryDate),
      Option(fdp.lineageStartEntryDate),
      Option(fdp.fileSize),
      Option(fdp.previousFileSize),
      Option(fdp.eventDuration),
      Option(fdp.eventType),
      Option(fdp.attributes),
      Option(fdp.previousAttributes),
      Option(fdp.updatedAttributes),
      Option(fdp.componentId),
      Option(fdp.componentType),
      Option(fdp.transitUri),
      Option(fdp.sourceSystemFlowFileIdentifier),
      Option(fdp.flowFileUuid),
      Option(fdp.parentUuids),
      Option(fdp.childUuids),
      Option(fdp.alternateIdentifierUri),
      Option(fdp.details),
      Option(fdp.relationship),
      Option(fdp.sourceQueueIdentifier),
      Option(fdp.contentClaimIdentifier),
      Option(fdp.previousContentClaimIdentifier))

    val createProvenance = DBIO.seq(
      Tables.FlowDataProvenance += provenanceRow
    )

    db.run(createProvenance)
  }

  private def getProvenanceByComponentIdQuery(cid: Rep[String], maxResults: ConstColumn[Long]) = {
    (for {
      provenance <- Tables.FlowDataProvenance if provenance.componentId === cid
      content <- Tables.FlowDataContent if provenance.contentClaimIdentifier === content.id

    } yield (content.id, "", "", content.data, content.timestamp, provenance.attributes, provenance.updatedAttributes))
      .sortBy(_._5.desc).take(maxResults)
  }

  private lazy val getProvenanceByComponentIdQueryCompiled = Compiled(getProvenanceByComponentIdQuery _)

  override def getProvenanceByComponentId(cid: String, maxResults: Int): Future[List[Provenance]] = {
    import org.dcs.api.data.FlowData._

    val provenanceByComponentIdAction = getProvenanceByComponentIdQueryCompiled(cid, maxResults).result

    (for (provList <- db.run(provenanceByComponentIdAction) ) yield provList.map(prov => {
      // FIXME: The avro deserialisation should finally move to the client side,
      //        with the schema store exposed as a service
      val updatedSchema = stringToMap(prov._7.getOrElse("")).get(RemoteProcessor.SchemaIdKey)
      val schema: String =
        if(updatedSchema.isEmpty)
          stringToMap(prov._6.getOrElse("")).getOrElse(RemoteProcessor.SchemaIdKey, "")
        else
          updatedSchema.get
      Provenance(prov._1, prov._2, prov._3, prov._4.getOrElse(Array[Byte]()), "", schema, prov._5.get)
    })).map(_.toList)
  }

  private def getProvenanceEventsByEventIdQuery(eventId: Rep[Long], maxResults: ConstColumn[Long]) = {
    Tables.FlowDataProvenance.filter(fdp =>
      fdp.eventId >= eventId && fdp.eventId < (eventId + maxResults))
  }

  private lazy val getProvenanceEventsByEventIdQueryCompiled = Compiled(getProvenanceEventsByEventIdQuery _)

  override def getProvenanceEventsByEventId(eventId: Long, maxResults: Int): Future[List[BigTables.BigFlowDataProvenanceRow]] = {
    db.run(getProvenanceEventsByEventIdQueryCompiled(eventId, maxResults).result).map(_.toList)
  }

  private def getProvenanceEventByEventIdQuery(eventId: Rep[Long]) = {
    Tables.FlowDataProvenance.filter(fdp =>fdp.eventId === eventId)
  }

  private lazy val getProvenanceEventByEventIdQueryCompiled = Compiled(getProvenanceEventByEventIdQuery _)

  override def getProvenanceEventByEventId(eventId: Long): Future[Option[BigTables.BigFlowDataProvenanceRow]] = {
    db.run(getProvenanceEventByEventIdQueryCompiled(eventId).result).map(uniqueForId(_, eventId.toString))
  }

  private def getProvenanceEventByIdQuery(id: Rep[String]) = {
    Tables.FlowDataProvenance.filter(fdp => fdp.id === id)
  }

  private lazy val getProvenanceEventByIdQueryCompiled = Compiled(getProvenanceEventByIdQuery _)

  override def getProvenanceEventById(id: String): Future[Option[BigTables.BigFlowDataProvenanceRow]] = {
    db.run(getProvenanceEventByIdQueryCompiled(id).result).map(uniqueForId(_, id))
  }

  override def getProvenanceSize: Future[Int] = {
    db.run(Tables.FlowDataProvenance.length.result)
  }

  override def getProvenanceEventsByEventType(eventType: String): Future[List[BigTables.BigFlowDataProvenanceRow]] =
    db.run(Tables.FlowDataProvenance.filter(fdp => fdp.eventType === eventType).result).map(_.toList)

  override def getProvenanceEventsByFlowFileUuid(flowFileUuid: String): Future[List[BigTables.BigFlowDataProvenanceRow]] =
    db.run(Tables.FlowDataProvenance.filter(fdp => fdp.flowFileUuid === flowFileUuid).result).map(_.toList)

  override def getProvenanceEventsByComponentId(componentId: String): Future[List[BigTables.BigFlowDataProvenanceRow]] =
    db.run(Tables.FlowDataProvenance.filter(fdp => fdp.componentId === componentId).result).map(_.toList)

  override def getProvenanceEventsByRelationship(relationship: String): Future[List[BigTables.BigFlowDataProvenanceRow]] =
    db.run(Tables.FlowDataProvenance.filter(fdp => fdp.relationship === relationship).result).map(_.toList)

  override def deleteProvenanceByComponentId(cid: String): Future[Int] = {
    // FIXME: Deleting each content by id is not optimal
    //        Need to add a foreign key constraint to enable cascade deletion
    val contentIdsAction= Tables.FlowDataProvenance.filter(_.componentId === cid).map(prov => prov.contentClaimIdentifier).result
    val contentIds = db.run(contentIdsAction)

    contentIds.foreach(ids => ids.foreach(id => db.run(Tables.FlowDataContent.filter(_.id === id.get).delete)))

    val deleteProvenanceAction = Tables.FlowDataProvenance.filter(_.componentId === cid).delete
    db.run(deleteProvenanceAction)
  }


  override def purgeProvenance(): Future[Int] = db.run(Tables.FlowDataProvenance.delete)

  override def purge(): Future[Int] = {
    val purgeFdcAction = Tables.FlowDataContent.delete
    val purgeFdpAction = Tables.FlowDataProvenance.delete
    db.run((purgeFdcAction andThen purgeFdpAction).transactionally)
  }
}


