organization := "ru.rknrl"

name := "rpc"

version := "1.0"

scalaVersion := "2.11.11"

libraryDependencies ++= Seq(
  "com.typesafe.akka" %% "akka-actor" % "2.5.1",
  "com.trueaccord.scalapb" %% "scalapb-runtime" % "0.4.8"
)
