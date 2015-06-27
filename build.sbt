import play.PlayImport.PlayKeys._

name := "play-mongo-knockout"

version := "1.0-SNAPSHOT"

scalaVersion := "2.11.7"

libraryDependencies ++= Seq(
  // Reactive Mongo dependencies
  "org.reactivemongo" %% "play2-reactivemongo" % "0.11.11-play24",
  "org.reactivemongo" %% "reactivemongo-iteratees" % "0.11.11",
  // WebJars pull in client-side web libraries
  "org.webjars" %% "webjars-play" % "2.4.0",
  "org.webjars" % "bootstrap" % "3.3.5",
  "org.webjars" % "knockout" % "3.3.0",
  "org.webjars" % "requirejs" % "2.1.18",
  "org.specs2" %% "specs2-core" % "2.4.9" % "test",
  "org.specs2" %% "specs2-junit" % "2.4.9" % "test"
  // Add your own project dependencies in the form:
  // "group" % "artifact" % "version"
)

javaOptions in Test ++= Seq("-Dconfig.resource=test.conf")

lazy val root = (project in file(".")).enablePlugins(PlayScala)

routesGenerator := InjectedRoutesGenerator

pipelineStages := Seq(rjs)
