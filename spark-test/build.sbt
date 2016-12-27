name := "spark-test"

version := "1.0"

scalaVersion := "2.11.7"

libraryDependencies += "org.apache.spark" %% "spark-core" % "1.6.0"
libraryDependencies += "org.apache.spark" % "spark-sql_2.11" % "1.6.0"
libraryDependencies += "com.databricks" % "spark-csv_2.11" % "1.5.0"
libraryDependencies += "org.apache.spark" % "spark-streaming_2.11" % "1.6.0"
libraryDependencies += "org.apache.spark" % "spark-graphx_2.11" % "1.6.0"
libraryDependencies += "org.apache.spark" % "spark-streaming-kafka_2.11" % "1.6.0"
libraryDependencies += "org.apache.spark" % "spark-mllib_2.11" % "1.6.0"
libraryDependencies += "com.datastax.spark" % "spark-cassandra-connector_2.11" % "1.6.0"

javacOptions ++= Seq("-encoding", "UTF-8")
