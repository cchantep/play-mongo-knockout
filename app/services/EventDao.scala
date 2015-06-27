package services

import play.api._
import play.api.libs.concurrent.Execution.Implicits._
import play.api.libs.iteratee._
import play.api.libs.json.{ Json, Format, JsValue }

import reactivemongo.api.QueryOpts
import reactivemongo.bson.BSONObjectID
import reactivemongo.api.indexes.{ IndexType, Index }

import reactivemongo.play.json.collection.JSONCollection
import play.modules.reactivemongo.ReactiveMongoApi

import scala.concurrent.Future
import models.Event

/**
 * DAO for stateless event management.
 *
 * Uses a MongoDB capped collection to send events between nodes.
 */
object EventDao {
  /**
   * Publish an event.
   *
   * @param name The name of the event to publish
   * @param data The event data
   * @return The event, once it's been published.  Not that redeeming this future doesn't necessarily mean all subscribers have received the event, it just means it's been saved to MongoDB.
   */
  def publish[T: Format](name: String, data: T)(implicit plugin: EventPlugin): Future[Event] = publish(name, Json.toJson(data))

  /**
   * Publish an event.
   *
   * @param name The name of the event to publish
   * @param data The event data
   * @return The event, once it's been published.  Not that redeeming this future doesn't necessarily mean all subscribers have received the event, it just means it's been saved to MongoDB.
   */
  private def publish(name: String, data: JsValue)(implicit plugin: EventPlugin): Future[Event] = {
    val event = Event(BSONObjectID.generate, name, data)

    for {
      coll <- plugin.collection
      result <- coll.insert(event)
    } yield {
      result match {
        case ok if result.ok => event
        case error => throw new scala.RuntimeException(error.message)
      }
    }
  }

  /**
   * Get the stream of events.
   */
  def stream(implicit plugin: EventPlugin): Enumerator[Event] = plugin.stream
}

/**
 * The event plugin.
 *
 * This is implemented as a plugin so we can easily hook into the Play lifecycle, to ensure that we only setup the
 * capped collection once, and to ensure that we only create the event stream once.
 */
class EventPlugin(api: ReactiveMongoApi) {
  import reactivemongo.play.json._
  import reactivemongo.play.iteratees.cursorProducer

  lazy val collection: Future[JSONCollection] =
    api.database.map(_[JSONCollection]("events")).flatMap { coll =>
      coll.stats().flatMap {
        case stats if !stats.capped =>
          // The collection is not capped, so we convert it
          coll.convertToCapped(102400, Some(1000))
        case _ => Future.successful(true)
      }.recover {
        // The collection mustn't exist, create it
        case _ =>
          coll.createCapped(102400, Some(1000))
      }.map { _ =>
        coll.indexesManager.ensure(Index(
          key = Seq("_id" -> IndexType.Ascending),
          unique = true
        ))
        coll
      }
    }

  /**
   * Get the stream of events.
   */
  lazy val stream: Enumerator[Event] = {
    // ObjectIDs are linear with time, we only want events created after now.
    val since = BSONObjectID.generate
    val enumerator = Enumerator.flatten(for {
      coll <- collection
    } yield {
      val selector = Json.obj("_id" -> Json.obj("$gt" -> since))

      coll.find(selector).options(QueryOpts().tailable.awaitData).
        cursor[Event]().enumerator()
    })

    Concurrent.broadcast(enumerator)._1
  }
}
