organization := "ru.rknrl"

name := "rpc"

version := "1.0"

scalaVersion := "2.11.11"

crossScalaVersions := Seq("2.11.11", "2.12.2")

libraryDependencies ++= Seq(
  "com.typesafe.akka" %% "akka-actor" % "2.5.1",
  "com.trueaccord.scalapb" %% "scalapb-runtime" % "0.5.47"
)
