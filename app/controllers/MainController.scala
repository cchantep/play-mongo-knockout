package controllers

import javax.inject.Inject

import play.api.mvc.{ Action, Controller }
import play.api.Routes
import services.EventDao
import play.api.libs.EventSource

import play.modules.reactivemongo.{
  MongoController, ReactiveMongoApi, ReactiveMongoComponents
}

class MainController @Inject() (
  val reactiveMongoApi: ReactiveMongoApi)
    extends Controller with MongoController with ReactiveMongoComponents {

  /**
   * The index page.  This is the main entry point, seeing as this is a single page app.
   */
  def index(path: String) = Action { Ok(views.html.index()) }

  /** The javascript router. */
  def router = Action { implicit req =>
    Ok(
      Routes.javascriptRouter("routes")(
        routes.javascript.MainController.events,
        routes.javascript.MessageController.getMessages,
        routes.javascript.MessageController.saveMessage
      )
    ).as("text/javascript")
  }

  /** Server Sent Events endpoint. */
  def events = Action {
    implicit val plugin = new services.EventPlugin(reactiveMongoApi)
    Ok.feed(EventDao.stream &> EventSource()).as(EVENT_STREAM)
  }
}
