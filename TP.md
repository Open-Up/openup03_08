# Spark

Prerequisite :

This practical work is thought to be easy to set up. Please before the class, or during Spark presentation : 
 - Start downloading the spark docker image : docker pull sequenceiq/spark:1.6.0
 - Install SBT 0.13.13
 - You of course need docker

## Getting Spark

Issue the following actions :

 - Retrieve the sources of the practical work from GitHub
 - Go in the SparkTest directory
 - Issue the following commands

```
% sbt package
[info] Loading project definition from /home/benwa/Documents/linagora/open-up/s03/effective/e08_Spark/spark-test/project
[info] Set current project to spark-test (in build file:/home/benwa/Documents/linagora/open-up/s03/effective/e08_Spark/spark-test/)
[info] Updating {file:/home/benwa/Documents/linagora/open-up/s03/effective/e08_Spark/spark-test/}spark-test...
[info] Resolving jline#jline;2.12.1 ...
[info] Done updating.
[info] Compiling 4 Scala sources to /home/benwa/Documents/linagora/open-up/s03/effective/e08_Spark/spark-test/target/scala-2.11/classes...
[warn] Multiple main classes detected.  Run 'show discoveredMainClasses' to see the list
[info] Packaging /home/benwa/Documents/linagora/open-up/s03/effective/e08_Spark/spark-test/target/scala-2.11/spark-test_2.11-1.0.jar ...
[info] Done packaging.

% docker run -v $PWD:/openup --name=spark -d sequenceiq/spark:1.6.0 -d
f1740abff5ad1b55b888af8ea559965d060a31f1db7aa61349640bbcb159f370

% docker exec -t -i f1740abff5ad1b55b888af8ea559965d060a31f1db7aa61349640bbcb159f370 /bin/bash
-% cd /openup

-% ls
build.sbt  checkpoint  commands.txt  data  project  results.txt  src  target

-% spark-submit --class HelloWorld target/scala-2.11/spark-test_2.11-1.0.jar 
Question 0 was run successfully!

```

## WarmUp ! Classic operations on files...

Question 1 to 5 : Fill the file Warmup.scala

## Example : Prime number

Question 6 : Fill the file Prime.scala

## Example : Recommandations

Question 7 to 10 : Fill Reccommand.scala
