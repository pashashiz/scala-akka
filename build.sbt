name := "scala-akka"

version := "1.0"

scalaVersion := "2.12.2"

libraryDependencies ++= Seq(
  "com.typesafe.akka" %% "akka-actor" % "2.5.1",
  "com.typesafe.akka" %% "akka-cluster" % "2.5.1",
  "com.typesafe.akka" %% "akka-cluster-metrics" % "2.5.1",
  "com.typesafe.akka" %% "akka-testkit" % "2.5.1" % "test",
  "org.scalatest" %% "scalatest" % "3.0.1" % "test"
)

