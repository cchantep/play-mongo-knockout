package services

import scala.concurrent.Future

import play.api.Play.current
import play.api.libs.concurrent.Execution.Implicits._
import play.api.libs.json.Json

import reactivemongo.api.QueryOpts
import reactivemongo.bson.BSONObjectID

import reactivemongo.play.json.collection.JSONCollection

import models._
import models.Message._

/** A data access object for messages backed by a MongoDB collection */
object MessageDao {
  import play.modules.reactivemongo.json._

  /** The messages collection */
  @inline private def coll(implicit plugin: EventPlugin) =
    plugin.collection.map(_.db.collection[JSONCollection]("messages"))

  /**
   * Save a message.
   *
   * @return The saved message, once saved.
   */
  def save(message: Message)(implicit plugin: EventPlugin): Future[Message] =
    coll.flatMap(_.insert(message).map {
      case ok if ok.ok =>
        EventDao.publish("message", message)
        message
      case error => throw new scala.RuntimeException(error.message)
    })

  /**
   * Find all the messages.
   *
   * @param page The page to retrieve, 0 based.
   * @param perPage The number of results per page.
   * @return All of the messages.
   */
  def findAll(page: Int, perPage: Int)(implicit plugin: EventPlugin): Future[Seq[Message]] = coll.flatMap(_.find(Json.obj())
      .options(QueryOpts(page * perPage))
      .sort(Json.obj("_id" -> -1))
      .cursor[Message]()
      .collect[Seq](perPage))

  /** The total number of messages */
  def count(implicit plugin: EventPlugin): Future[Int] = coll.flatMap(_.count())
}
