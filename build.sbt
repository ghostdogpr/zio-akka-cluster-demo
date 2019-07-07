val mainScala = "2.12.8"
val allScala  = Seq("2.11.12", mainScala)

name := "zio-akka-cluster-demo"
scalaVersion := mainScala

libraryDependencies ++= Seq(
  "dev.zio" %% "zio-akka-cluster" % "0.1.0"
)
