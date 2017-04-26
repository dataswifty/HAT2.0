/*
 * Copyright (C) 2017 HAT Data Exchange Ltd
 * SPDX-License-Identifier: AGPL-3.0
 *
 * This file is part of the Hub of All Things project (HAT).
 *
 * HAT is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License
 * as published by the Free Software Foundation, version 3 of
 * the License.
 *
 * HAT is distributed in the hope that it will be useful, but
 * WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See
 * the GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General
 * Public License along with this program. If not, see
 * <http://www.gnu.org/licenses/>.
 *
 * Written by Andrius Aucinas <andrius.aucinas@hatdex.org>
 * 4 / 2017
 */

package org.hatdex.hat.api.service

import java.security.MessageDigest
import java.sql.Timestamp
import java.util.UUID

import com.github.tminglei.slickpg.TsVector
import org.hatdex.hat.api.json.HatJsonFormats
import org.hatdex.hat.dal.ModelTranslation
import org.hatdex.hat.dal.Tables._
import org.hatdex.hat.dal.SlickPostgresDriver.api._
import org.hatdex.hat.dal.SlickPostgresDriver.api.Database
import org.joda.time.{ DateTime, LocalDateTime }
import play.api.Logger
import play.api.libs.functional.syntax._
import play.api.libs.json._

import scala.annotation.tailrec
import scala.collection.immutable.HashMap
import scala.concurrent.Future

trait RichDataJsonFormats extends HatJsonFormats {

  implicit val endpointDataFormat: Format[EndpointData] = (
    (__ \ "endpoint").format[String] and
    (__ \ "recordId").formatNullable[UUID] and
    (__ \ "data").format[JsValue] and
    (__ \ "links").lazyFormatNullable(implicitly[Format[Seq[EndpointData]]]))(EndpointData.apply, unlift(EndpointData.unapply))

  private val fieldTransDateTimeExtractFormat = Json.format[FieldTransformation.DateTimeExtract]
  private val fieldTransTimestampExtractFormat = Json.format[FieldTransformation.TimestampExtract]

  private implicit val apiFieldTransformationFormat: Format[FieldTransformation.Transformation] = new Format[FieldTransformation.Transformation] {
    def reads(json: JsValue): JsResult[FieldTransformation.Transformation] = (json \ "transformation").as[String] match {
      case "identity"         => JsSuccess(FieldTransformation.Identity())
      case "datetimeExtract"  => Json.fromJson[FieldTransformation.DateTimeExtract](json)(fieldTransDateTimeExtractFormat)
      case "timestampExtract" => Json.fromJson[FieldTransformation.TimestampExtract](json)(fieldTransTimestampExtractFormat)
      case "searchable"       => JsSuccess(FieldTransformation.Searchable())
      case transformation     => JsError(s"Unexpected JSON value $transformation in $json")
    }

    def writes(transformation: FieldTransformation.Transformation): JsValue = {
      val (transformed, tType) = transformation match {
        case _: FieldTransformation.Identity          => (Json.obj(), JsString("identity"))
        case ds: FieldTransformation.DateTimeExtract  => (Json.toJson(ds)(fieldTransDateTimeExtractFormat), JsString("datetimeExtract"))
        case ds: FieldTransformation.TimestampExtract => (Json.toJson(ds)(fieldTransTimestampExtractFormat), JsString("timestampExtract"))
        case _: FieldTransformation.Searchable        => (Json.obj(), JsString("searchable"))
      }
      transformed.as[JsObject] + (("transformation", tType))
    }
  }

  private val filterOperatorContainsFormat = Json.format[FilterOperator.Contains]
  private val filterOperatorInFormat = Json.format[FilterOperator.In]
  private val filterOperatorBetweenFormat = Json.format[FilterOperator.Between]
  private val filterOperatorFindFormat = Json.format[FilterOperator.Find]

  private implicit val apiFilterOperatorFormat: Format[FilterOperator.Operator] = new Format[FilterOperator.Operator] {
    def reads(json: JsValue): JsResult[FilterOperator.Operator] = (json \ "operator").as[String] match {
      case "contains"     => Json.fromJson[FilterOperator.Contains](json)(filterOperatorContainsFormat)
      case "in"           => Json.fromJson[FilterOperator.In](json)(filterOperatorInFormat)
      case "between"      => Json.fromJson[FilterOperator.Between](json)(filterOperatorBetweenFormat)
      case "find"         => Json.fromJson[FilterOperator.Find](json)(filterOperatorFindFormat)
      case transformation => JsError(s"Unexpected JSON value $transformation in $json")
    }

    def writes(transformation: FilterOperator.Operator): JsValue = {
      val (transformed, tType) = transformation match {
        case ds: FilterOperator.Contains => (Json.toJson(ds)(filterOperatorContainsFormat), JsString("contains"))
        case ds: FilterOperator.In       => (Json.toJson(ds)(filterOperatorInFormat), JsString("in"))
        case ds: FilterOperator.Between  => (Json.toJson(ds)(filterOperatorBetweenFormat), JsString("between"))
        case ds: FilterOperator.Find     => (Json.toJson(ds)(filterOperatorFindFormat), JsString("find"))
      }
      transformed.as[JsObject] + (("operator", tType))
    }
  }

  implicit val endpointQueryFilterFormat: Format[EndpointQueryFilter] = Json.format[EndpointQueryFilter]

  implicit val endpointQueryFormat: Format[EndpointQuery] = (
    (__ \ "endpoint").format[String] and
    (__ \ "mapping").formatNullable[JsValue] and
    (__ \ "filters").formatNullable[Seq[EndpointQueryFilter]] and
    (__ \ "links").lazyFormatNullable(implicitly[Format[Seq[EndpointQuery]]]))(EndpointQuery.apply, unlift(EndpointQuery.unapply))

  implicit val propertyQueryFormat: Format[PropertyQuery] = Json.format[PropertyQuery]
}

case class EndpointData(
  endpoint: String,
  recordId: Option[UUID],
  data: JsValue,
  links: Option[Seq[EndpointData]])

object FilterOperator {
  trait Operator {
    val operator: String
  }

  case class In(value: JsValue) extends Operator {
    val operator = "in"
  }
  case class Contains(value: JsValue) extends Operator {
    val operator = "contains"
  }
  case class Between(lower: JsValue, upper: JsValue) extends Operator {
    val operator = "between"
  }
  case class Find(search: JsValue) extends Operator {
    val operator = "matches"
  }
}

object FieldTransformation extends PgFunctions {
  trait Transformation {
    def transform(value: Rep[JsValue]): Rep[JsValue] = value
  }

  case class Identity() extends Transformation

  case class DateTimeExtract(part: String) extends Transformation {
    override def transform(value: Rep[JsValue]): Rep[JsValue] = {
      toJson(datePart(part, value.asColumnOf[String].asColumnOf[DateTime]))
    }
  }

  case class TimestampExtract(part: String) extends Transformation {
    override def transform(value: Rep[JsValue]): Rep[JsValue] = {
      toJson(datePartTimestamp(part, toTimestamp(value.asColumnOf[Double])))
    }
  }

  case class Searchable() extends Transformation {
    def transformCustom(value: Rep[JsValue]): Rep[TsVector] = {
      toTsVector(value.asColumnOf[String])
    }
  }

}

trait PgFunctions {
  val toJson = SimpleFunction.unary[String, JsValue]("to_jsonb")
  val toTimestamp = SimpleFunction.unary[Double, Timestamp]("to_timestamp")
  val datePart = SimpleFunction.binary[String, DateTime, String]("date_part")
  val datePartTimestamp = SimpleFunction.binary[String, Timestamp, String]("date_part")
}

case class EndpointQueryFilter(
    field: String,
    transformation: Option[FieldTransformation.Transformation],
    operator: FilterOperator.Operator) {
  def originalField: List[String] = {
    field.split('.').toList
  }
}

case class EndpointQuery(
    endpoint: String,
    mapping: Option[JsValue],
    filters: Option[Seq[EndpointQueryFilter]],
    links: Option[Seq[EndpointQuery]]) {
  def originalField(field: String): Option[List[String]] = {
    mapping.flatMap { m =>
      (m \ field)
        .toOption
        .map(_.as[String].split('.').toList)
    } orElse {
      Some(field.split('.').toList)
    }
  }
}

case class PropertyQuery(
  endpoints: List[EndpointQuery],
  orderBy: Option[String],
  limit: Int)

/*

  {
    "name": {
      "endpoints": [
        {
          "endpoint": "/rumpel/profile",
          "mapping": {
            "name": "owner.name.firstName",
            "date": "record.lastUpdated"
          }
        },
        {
          "endpoint": "/bheard/profile",
          "mapping": {
            "name": "user.name",
            "date": "profile.updated"
          }
        },
      ],
      "orderBy": "date",
      limit: 1
    }
  }

 */

class RichDataService extends DalExecutionContext with PgFunctions {

  val logger = Logger(this.getClass)

  private def dbDataRow(endpoint: String, userId: UUID, data: JsValue, recordId: Option[UUID] = None): DataJsonRow = {
    val md = MessageDigest.getInstance("SHA-256")
    val digest = md.digest(data.toString.getBytes)
    DataJsonRow(recordId.getOrElse(UUID.randomUUID()), endpoint, userId, LocalDateTime.now(), data, digest)
  }

  def saveData(userId: UUID, endpointData: Seq[EndpointData])(implicit db: Database): Future[Seq[EndpointData]] = {
    val queries = endpointData map { endpointDataGroup =>
      val endpointRow = dbDataRow(endpointDataGroup.endpoint, userId, endpointDataGroup.data)

      val linkedRows = endpointDataGroup.links
        .map(_.toList).getOrElse(List())
        .map(i => dbDataRow(i.endpoint, userId, i.data))

      val recordIds = endpointRow.recordId :: linkedRows.map(_.recordId)

      val (groupRow, groupRecordRows) = if (recordIds.length > 1) {
        val groupRow = DataJsonGroupsRow(UUID.randomUUID(), userId, LocalDateTime.now())
        val groupRecordRows = recordIds.map(DataJsonGroupRecordsRow(groupRow.groupId, _))
        (Seq(groupRow), groupRecordRows)
      }
      else {
        (Seq(), Seq())
      }

      for {
        endpointData <- (DataJson returning DataJson) += endpointRow
        linkedData <- (DataJson returning DataJson) ++= linkedRows
        group <- DataJsonGroups ++= groupRow
        groupRecords <- DataJsonGroupRecords ++= groupRecordRows
      } yield (endpointData, linkedData, group, groupRecords)
    }

    db.run(DBIO.sequence(queries).transactionally)
      .map {
        _.map { inserted =>
          ModelTranslation.fromDbModel(inserted._1, inserted._2)
        }
      }
  }

  def deleteRecords(userId: UUID, recordIds: Seq[UUID])(implicit db: Database): Future[Unit] = {
    val query = for {
      deletedGroupRecords <- DataJsonGroupRecords.filter(_.recordId inSet recordIds).delete // delete links between records and groups
      deletedGroups <- DataJsonGroups.filterNot(g => (g.owner === userId) && (g.groupId in DataJsonGroupRecords.map(_.groupId))).delete // delete any groups that have become empty
      deletedRecords <- DataJson.filter(r => (r.owner === userId) && (r.recordId inSet recordIds)).delete if deletedRecords == recordIds.length // delete the records, but only if all requested records are found
    } yield (deletedGroupRecords, deletedGroups, deletedRecords)

    db.run(query.transactionally).map(_ => ())
  }

  def updateRecords(userId: UUID, records: Seq[EndpointData])(implicit db: Database): Future[Seq[EndpointData]] = {
    val updateRows = records.map { record =>
      dbDataRow(record.endpoint, userId, record.data, record.recordId)
    }

    val updateQueries = updateRows map { record =>
      for {
        updated <- DataJson.filter(r => r.recordId === record.recordId && r.owner === userId)
          .map(r => (r.data, r.date, r.hash))
          .update((record.data, record.date, record.hash)) if updated == 1
      } yield updated
    }

    db.run(DBIO.sequence(updateQueries).transactionally).map { _ =>
      updateRows.map(ModelTranslation.fromDbModel)
    }
  }

  def saveRecordGroup(userId: UUID, recordIds: Seq[UUID])(implicit db: Database): Future[UUID] = {
    val groupRow = DataJsonGroupsRow(UUID.randomUUID(), userId, LocalDateTime.now())
    val groupRecordRows = recordIds.map(DataJsonGroupRecordsRow(groupRow.groupId, _))

    val query = for {
      group <- DataJsonGroups += groupRow
      groupRecords <- DataJsonGroupRecords ++= groupRecordRows
    } yield (group, groupRecords)

    db.run(query.transactionally)
      .map { _ =>
        groupRow.groupId
      }
  }

  private def queryMappers(endpointQueries: Seq[EndpointQuery]): HashMap[String, Reads[JsObject]] = {
    val mappers = endpointQueries.zipWithIndex flatMap {
      case (endpointQuery, index) =>
        val id = index.toString
        val transformer = endpointQuery.mapping collect {
          case m: JsObject => id -> JsonDataTransformer.mappingTransformer(m)
        }
        val subTransformers = endpointQuery.links.getOrElse(Seq()).zipWithIndex.map {
          case (link, subIndex) =>
            link.mapping collect {
              case m: JsObject => s"$id-$subIndex" -> JsonDataTransformer.mappingTransformer(m)
            }
        }
        (subTransformers :+ transformer).flatten
    }
    HashMap(mappers: _*)
  }

  protected[service] def generatedDataQuery(endpointQuery: EndpointQuery, query: Query[DataJson, DataJsonRow, Seq]): Query[DataJson, DataJsonRow, Seq] = {
    val q = query.filter(_.source === endpointQuery.endpoint)
    endpointQuery.filters map { filters =>
      generateDataQueryFiltered(filters, q)
    } getOrElse {
      q
    }
  }

  private def generateDataQueryFiltered(filters: Seq[EndpointQueryFilter], query: Query[DataJson, DataJsonRow, Seq]): Query[DataJson, DataJsonRow, Seq] = {
    import FilterOperator._
    if (filters.isEmpty) {
      query
    }
    else {
      val currentFilter = filters.head
      val currentTransformation = currentFilter.transformation.getOrElse(FieldTransformation.Identity())
      val currentQuery = currentFilter.operator match {
        case Contains(value)       => query.filter(d => currentTransformation.transform(d.data #> currentFilter.originalField) @> value)
        case In(value)             => query.filter(d => value <@: currentTransformation.transform(d.data #> currentFilter.originalField))
        case Between(lower, upper) => query.filter(d => currentTransformation.transform(d.data #> currentFilter.originalField) between (lower, upper))
        case Find(searchTerm)      => query.filter(d => FieldTransformation.Searchable().transformCustom(d.data #> currentFilter.originalField) @@ plainToTsQuery(searchTerm.asColumnOf[String]))
        case _                     => query
      }
      generateDataQueryFiltered(filters.tail, currentQuery)
    }
  }

  private def propertyDataQuery(endpointQueries: Seq[EndpointQuery], orderBy: Option[String], limit: Int): Query[((DataJson, ConstColumn[String]), Rep[Option[(DataJson, ConstColumn[String])]]), ((DataJsonRow, String), Option[(DataJsonRow, String)]), Seq] = {
    val queriesWithMappers = //: Seq[Query[(DataJson, Rep[Option[JsValue]], ConstColumn[String]), (DataJsonRow, Option[JsValue], String), Seq]] =
      endpointQueries.zipWithIndex map {
        case (endpointQuery, endpointQueryIndex) =>
          orderBy map { orderBy =>
            for {
              data <- generatedDataQuery(endpointQuery, DataJson)
            } yield (data, data.data #> endpointQuery.originalField(orderBy), endpointQueryIndex.toString)
          } getOrElse {
            //          for {
            //            data <- generatedDataQuery(endpointQuery, DataJson)
            //          } yield (data, data.data #> endpointQuery.originalField(""), endpointQueryIndex.toString)
            for {
              data <- generatedDataQuery(endpointQuery, DataJson)
            } yield (data, toJson(data.date.asColumnOf[String]).asColumnOf[Option[JsValue]], endpointQueryIndex.toString)
          }
      }

    val endpointDataQuery = queriesWithMappers
      .reduce((aggregate, query) => aggregate.unionAll(query)) // merge all the queries together
      .sortBy(_._2) // order all the results by the chosen column
      .take(limit) // take the desired number of records

    val linkedRecordQueries = endpointQueries.zipWithIndex map {
      case (endpointQuery, endpointQueryIndex) =>
        endpointQuery.links map { links =>
          links.zipWithIndex map {
            case (link, linkIndex) =>
              for {
                endpointQueryRecordGroup <- DataJsonGroupRecords
                (linkedGroupId, linkedRecordId) <- DataJsonGroupRecords.map(v => (v.groupId, v.recordId)) if endpointQueryRecordGroup.groupId === linkedGroupId && endpointQueryRecordGroup.recordId =!= linkedRecordId
                linkedRecord <- generatedDataQuery(link, DataJson.filter(_.recordId === linkedRecordId))
              } yield (endpointQueryIndex.toString, s"$endpointQueryIndex-$linkIndex", endpointQueryRecordGroup.recordId, linkedRecord)
          }
        } getOrElse {
          Seq(for {
            noGroup <- DataJsonGroupRecords.take(0)
            noLinkedRecord <- DataJson.take(0)
          } yield (endpointQueryIndex.toString, s"$endpointQueryIndex-", noGroup.recordId, noLinkedRecord))
        }
    }

    val groupRecords = linkedRecordQueries.flatten
      .reduce((aggregate, query) => aggregate.unionAll(query))

    val resultQuery = endpointDataQuery
      .joinLeft(groupRecords)
      .on((l, r) => l._1.recordId === r._3 && l._3.asColumnOf[String] === r._1.asColumnOf[String])
      .sortBy(_._1._2)
      .map(v => ((v._1._1, v._1._3), v._2.map(lr => (lr._4, lr._2))))

    resultQuery
  }

  implicit def equalDataJsonRowIdentity(a: (DataJsonRow, String), b: (DataJsonRow, String)): Boolean = {
    a._1.recordId == b._1.recordId
  }

  @tailrec
  private def groupRecords[T, U](list: Seq[(T, Option[U])], groups: Seq[(T, Seq[U])] = Seq())(implicit equalIdentity: ((T, T) => Boolean)): Seq[(T, Seq[U])] = {
    if (list.isEmpty) {
      groups
    }
    else {
      groupRecords(
        list.dropWhile(v => equalIdentity(v._1, list.head._1)),
        groups :+ ((list.head._1, list.takeWhile(v => equalIdentity(v._1, list.head._1)).unzip._2.flatten)))
    }
  }

  def propertyData(endpointQueries: List[EndpointQuery], orderBy: Option[String], limit: Int)(implicit db: Database): Future[Seq[EndpointData]] = {
    val query = propertyDataQuery(endpointQueries, orderBy, limit)
    val mappers = queryMappers(endpointQueries)
    db.run(query.result).map { results =>
      groupRecords[(DataJsonRow, String), (DataJsonRow, String)](results).map {
        case ((record, queryId), linkedResults) =>
          val linked = linkedResults.map {
            case (linkedRecord, linkedQueryId) =>
              endpointDataWithMappers(linkedRecord, linkedQueryId, mappers)
          }
          val endpointData = endpointDataWithMappers(record, queryId, mappers)
          if (linked.nonEmpty) {
            endpointData.copy(links = Some(linked))
          }
          else {
            endpointData
          }
      }
    }
  }

  private def endpointDataWithMappers(record: DataJsonRow, queryId: String, mappers: HashMap[String, Reads[JsObject]]): EndpointData = {
    EndpointData(
      record.source,
      Some(record.recordId),
      mappers.get(queryId)
        .map(record.data.transform) // apply JSON transformation if it is present
        .map(_.getOrElse(Json.obj())) // quietly empty object of transformation fails
        .getOrElse(record.data), // if no mapper, return data as-is
      None)
  }

  def bundleData(bundle: Map[String, PropertyQuery])(implicit db: Database): Future[Map[String, Seq[EndpointData]]] = {
    val results = bundle map {
      case (property, propertyQuery) =>
        propertyData(propertyQuery.endpoints, propertyQuery.orderBy, propertyQuery.limit)
          .map(property -> _)
    }

    Future.fold(results)(Map[String, Seq[EndpointData]]()) { (propertyMap, response) =>
      propertyMap + response
    }
  }
}
