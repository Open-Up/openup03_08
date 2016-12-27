# One hour introduction to Spark

## This is a ready to use guide you can use as a basis for introducing Apache Spark to students. This post will be completed soonwith sample code for Spark ML, and Spark GraphX.

I was quite frustrate we didn't even used Spark nor any other Big Data computing system. We got some introduction to Hadoop from external peoples, spoke a little about Spark. Some students have a project using it (managing a Stream of linked data), but they encountered difficulties using it. It is really sad, as Cloud computing systems like Spark should have been at the heart of our lessons : Distributed Systems Architecture.

As a reaction to this situation, I decided to play a little with these technologies. And now, as of 16th January 2016, I would like to share what I have done in order to :

 - prove Spark is not complicated to master
 - give materials ready for practice in order to help

Note : I consumed 8 GB of RAM running Spark, kafka, maven, sbt, my IDE and firefox tabs.... I consider a minimum of 8GB of ram is compulsory to do this on a single computer.

### Installing Spark

I choose to compile it from sources in Scala 2.11 .

Consider giving the student an already compiled version of Spark as it is quite long, resource intensive, and download stressing...

I downloaded Spark 1.6.0 sources and ran :

	./dev/change-scala-version.sh 2.11
	mvn -Dscala-2.11 -DskipTests clean package

My versions at the moment of the build :

	Apache Maven 3.3.9
	Java version: 1.8.0_66, vendor: Oracle Corporation (OpenJDK)
	OS name: "linux", version: "4.3.3-2-arch"
	Scala: 2.11.7

### Development environment

I wrote Spark applications in Scala. You might prefer java 8 to not introduce Scala to your students, but I find Spark more fluent in Scala, and this is a good occasion to play with Scala, hence my choice.

I use IntelliJ as an IDE with the scala plugin. I use **sbt** to manage dependencies.

Here are the dependencies I use :

	libraryDependencies += "org.apache.spark" %% "spark-core" % "1.6.0"
	libraryDependencies += "org.apache.spark" % "spark-sql_2.11" % "1.6.0"
	libraryDependencies += "com.databricks" % "spark-csv_2.11" % "1.3.0"
	libraryDependencies += "org.apache.spark" % "spark-streaming_2.11" % "1.6.0"
	libraryDependencies += "org.apache.spark" % "spark-streaming-kafka_2.11" % "1.6.0"

### Sample Spark Batch : find prime numbers

Here my goal is to introduce the notion of **RDD** : Replicated Distributed Data-set

We will write a pet application that will find and print prime numbers between a max and a min value. We will use Spark to paralelize this computation.

We will generate the list of numbers between min and max, the filter them using a prime function, and finally print them and their count.

Here is the code in file *Prime.scala* . I will explain it in the end of the section :

	import org.apache.spark.{SparkContext, SparkConf}
	
	object Prime {
	
		val start = 10000000000000L
		val stop = 10000000010000L
	
		def main(args: Array[String]): Unit = {
			val conf = new SparkConf().setAppName("Prime number searcher")
			val sparkContext = new SparkContext(conf)
			val rdd = sparkContext.range(start, stop)
			val primeList = rdd.filter(isPrime).cache()
			primeList.foreach(prime => println("Prime number found : " + prime))
			println("Found " + primeList.count() + " prime numbers in range [" + start + ", " + stop + "]")
		}
		
		def isPrime(number: Long): Boolean = {
			if (number == 1)
				return false
			if (number == 2)
				return true
			if (number == 3)
				return true
			val mod6 = number % 6
			if (mod6 == 0 || mod6 == 3 || mod6 == 2 || mod6 == 4)
				return false
			
			val range : Stream[Long] = Stream.range(5L, Math.sqrt(number).toLong, 2L)
			!range.exists(divide(number))
		}
		
		def divide(number: Long)(candidate: Long): Boolean = number % candidate == 0
		
	}

First, we have to create a Main class Spark can launch. Note that the min and max interval value are hard coded...

Then, we have to retrieve Spark context :

	val conf = new SparkConf().setAppName("Prime number searcher")
	val sparkContext = new SparkContext(conf)

We will create our first RDD. As we want to find prime numbers between start and stop, we will compute a RDD of all longs between start and stop.

	val rdd = sparkContext.range(start, stop)

RDDs are the basic unit Spark works with. It is an encapsulated stream. You can have basic functional operations on them.

Ok it is now the time to do some work, no ? We need to find prime number so lets write a prime detector in scala...

	def isPrime(number: Long): Boolean = {
		if (number == 1)
			return false
		if (number == 2)
			return true
		if (number == 3)
			return true
		val mod6 = number % 6
		if (mod6 == 0 || mod6 == 3 || mod6 == 2 || mod6 == 4)
			return false
		
		val range : Stream[Long] = Stream.range(5L, Math.sqrt(number).toLong, 2L)
		!range.exists(divide(number))
	}

	def divide(number: Long)(candidate: Long): Boolean = number % candidate == 0

The algorithm is basic... First we check for easy to detect value and then rely on a 6 modulo to limit the compute of the squared root. Then we iterate on odd numbers and tests whether they divide the tested value... The divide function uses function composition to have a nicer style. We then check if there is a value in the stream (computation will stop when one will be found : stream magic). If yes it means the tested number is not a prime.

We need to filter our RDDs using this function...

	val primeList = rdd.filter(isPrime).cache()

You can spot the .cache() operation : we tell Spark to keep the RDD in RAM in order to better reuse it for multiple operations we will do on it.

Then we print the list of prime numbers using a side effect (warning, as all your Spark nodes will print in the same console, this is not scalable :-p ) :

	primeList.foreach(prime => println("Prime number found : " + prime))

And we want to display the count :

	println("Found " + primeList.count() + " prime numbers in range [" + start + ", " + stop + "]")

Job done. Here the difficult part in my opinion is working with streamed collections. With the doc, working with Spark is easy.

We now need to deploy it. We should first start our Spark sample cluster. I ran a mono node instance, in standalone mode. Go to your Spark installation folder and :

	./sbin/start-master.sh
	./sbin/start-slave.sh spark://127.0.0.1:7077

And submit the application...

	sbt package
	/home/benwa/Documents/ZZ/spark-1.6.0/bin/spark-submit \\
		--class Prime \\
		--master local[4] \\
		target/scala-2.11/spark-test_2.11-1.0.jar

You can then scroll in the displayed logs, you will see the prime values and prime count.

### Querying your GMail contacts in a SQL like syntax

Why having a SQL syntax on top of Spark ?

The idea is that SQL operations like groupBys, joins, and aggregates are expensive, and should be done separately from storage. And maybe do it asynchronously at write time, in order to have shorter reads. You can check the [CQRS pattern](http://martinfowler.com/bliki/CQRS.html).

Here I will just demonstrate a working SQL command on a CSV (my google contacts).

I exported my GMail contacts as CSV in */home/benwa/google.csv*

I want to select contacts having a website specified.

Here is the code :

	import org.apache.spark.{SparkContext, SparkConf}
	import org.apache.spark.sql.SQLContext
	
	object Contacts {
	
		def main(args: Array[String]): Unit = {
			val conf = new SparkConf().setAppName("SQL contact searcher")
			val sparkContext = new SparkContext(conf)
			val sqlContext = new SQLContext(sparkContext)
			val dataFrame = sqlContext.read.format("com.databricks.spark.csv")
				.option("mode", "DROPMALFORMED")
				.option("delimiter", ",")
				.load("/home/benwa/Downloads/google.csv")
			dataFrame.registerTempTable("contacts")
			val withWebSite = sqlContext.sql("SELECT C0,C41 FROM contacts WHERE C41 IS NOT NULL")
			
			dataFrame.printSchema()
			
			println("Total : " + dataFrame.count())
			println("Total with website : " + withWebSite.count())
			
			withWebSite.foreach(row => println(row))
		}
	
	}

Again, we have to retrieve the Spark context :

	val conf = new SparkConf().setAppName("SQL contact searcher")
	val sparkContext = new SparkContext(conf)

On the Spark context, we will instanciate our SQL context :

	val sqlContext = new SQLContext(sparkContext)

We need to load data from the CSV. To do this, we need to use an external plugin **com.databricks.spark.csv**. We drop malformed data in order to avoid NullPointerExceptions, use coma as delimiter, and load from the CSV.

	val dataFrame = sqlContext.read.format("com.databricks.spark.csv")
		.option("mode", "DROPMALFORMED")
		.option("delimiter", ",")
		.load("/home/benwa/Downloads/google.csv")

The resulting dataFrame holds our data. It is a RDD you can query using SQL. To query it, we need to register it as a table.

	dataFrame.registerTempTable("contacts")

Ouch, side effect...

But we can now create new dataframes by querying the *contacts* table :

	val withWebSite = sqlContext.sql("SELECT C0,C41 FROM contacts WHERE C41 IS NOT NULL")

The odd names are those by default, C0 corresponds to contact name, C41 to the website. As I am not especially interested by this SQL plugin, I didn't investigate on the columns naming issue.

Then we can do whatever we want with the computed data frames :

	println("Total with website : " + withWebSite.count())
	withWebSite.foreach(row => println(row))

Finally we shall load our application to Spark :

	/home/benwa/Documents/ZZ/spark-1.6.0/bin/spark-submit --packages com.databricks:spark-csv_2.11:1.3.0 --class Contacts --master local[4] target/scala-2.11/spark-test_2.11-1.0.jar

Two things changes from previous submit :

- The class name : it the launched main class.
- We uses a package to allow us to read data frames from CSV

### Handling Kafka streams

The idea is to compute some basic statistics when receiving messages from Kafka.

#### Running Kafka

I followed this [official guide](http://kafka.apache.org/07/quickstart.html), and downloaded [version 0.9.0.0 for Scala 2.11](https://www.apache.org/dyn/closer.cgi?path=/kafka/0.9.0.0/kafka_2.11-0.9.0.0.tgz). Untar it. Then :

	bin/zookeeper-server-start.sh config/zookeeper.properties
	bin/kafka-server-start.sh config/server.properties

Congratulation, you have a working Kafka broker...

You can try it using the provided consumer and producer (they were an error on the readme : the --zookeeper do not exists anymore in Kafka 0.9.0.0):

	bin/kafka-console-producer.sh --topic test --broker-list localhost:9092
	bin/kafka-console-consumer.sh --zookeeper localhost:2181 --topic test --from-beginning

Type in the producer, you should see your messages in the consumer.

#### Streaming computation with Spark

We will send manually data to Kafka, and display in near real time, for the last minute :

- The total count of distinct words
- The word count per word (top 10)

Here is the code :

	import org.apache.spark.SparkConf
	import org.apache.spark.streaming.kafka.KafkaUtils
	import org.apache.spark.streaming.{Minutes, Seconds, StreamingContext}
	
	object Streaming {
		
		def main(args: Array[String]) {
			val conf = new SparkConf().setAppName("Tryal")
			val streamingContext = new StreamingContext(conf, Seconds(10))
			streamingContext.checkpoint("checkpoint")
			
			val kafkaStream = KafkaUtils.createStream(streamingContext, "127.0.0.1", "consumer_group", Map("test" -> 1))
			
			val lines = kafkaStream.map(_._2)
			val words = lines.flatMap(_.split(" "))
			val wordsWithCount = words.map(word => (word, 1))
			val wordCounts = wordsWithCount.reduceByKeyAndWindow(_ + _, _ - _, Minutes(1)).filter(_._2 > 0).cache()
			
			val discintWords = wordCounts.count()
			
			val totalWordCount = wordCounts.map(_._2).reduce(_ + _)
			
			val totalCharCount = words.map(_.length()).reduce(_ + _)
			
			wordCounts.count().print()
			wordCounts.print()
			
			streamingContext.start()
			streamingContext.awaitTermination()
		}
	
		def sum(pair1: (String, Int), pair2: (String, Int)) = ("total", pair1._2 + pair2._2)
		def myMap(key: String, count: Int): (String, Int) = (key, count * key.length)
	
	}

We first start with creating a streaming context :

	val conf = new SparkConf().setAppName("Tryal")
	val streamingContext = new StreamingContext(conf, Seconds(10))

The last line is very interesting : it is the batch interval. Spark Streaming does not behave as a pure streaming solution but as micro batching. This means it will launch a batch every 10 seconds that will consume the Kafka topic and handle my computation. This is not real time.

We should register a checkpoint for our stream. A checkpoint enable our streaming application to be fault tolerant : upon JVM failure it can catch up, and restart from where it stopped.

	streamingContext.checkpoint("checkpoint")

Here we uses a checkpoint directory called checkPoint. In a distributed context, we should rather use a distributed storage (HDFS is mentionned in the documentation).

Ok, it is time to connect to Kafka :

	val kafkaStream = KafkaUtils.createStream(streamingContext, "127.0.0.1", "consumer_group", Map("test" -> 1))

Here we consume every ten seconds the **test** topic. Only one member of the consumer group will get a message. By being the only one to have this consumer group, we will ensure we receive every message. And of course, we connect our self to localhost.

We now extract the text from the received Kafka messages :

	val lines = kafkaStream.map(_._2)

We now stream the words contained in the messages :

	val words = lines.flatMap(_.split(" "))

We initialize a map reduce by giving each word a weight : 1

	val wordsWithCount = words.map(word => (word, 1))	

Then we keep the words received during the last minute, and reduce them : ("toto" -> 1), ("erty" -> 1), ("toto" -> 1) will give ("toto" -> 2), ("erty" -> 1)

	val wordCounts = wordsWithCount.reduceByKeyAndWindow(_ + _, _ - _, Minutes(1)).filter(_._2 > 0).cache()

I will take some time to explain what happened on this line :

- We first some the weight of the same words. This is this **_ + _** operation. Note that after one minute we have to remove weight attributed to timed out words, hence the **_ - _** operation.
- We can garbage collect and remove words with 0 count : they were timed out in the last minute : don't bother ourselves with these values...
- We cache the DStream so as to display both the total distinct word count and the total word count.

We can now display our information :

	wordCounts.count().print()
	wordCounts.print()

Once our operations were defined, we can launch our streaming batches :

	streamingContext.start()
	streamingContext.awaitTermination()

(Side effect spotted)

To deploy it :

	/home/benwa/Documents/ZZ/spark-1.6.0/bin/spark-submit --packages org.apache.spark:spark-streaming-kafka_2.11:1.6.0 --class Streaming --master local[4] target/scala-2.11/spark-test_2.11-1.0.jar

### Quick introduction to Spark ML lib

I am not a machine learning at all. My knowledge is basicaly limited to [this blog post presenting a TOP 10 machine learning algorithms](http://rayli.net/blog/data/top-10-data-mining-algorithms-in-plain-english/).

I just picked an algorithm at random and played with it. I wanted it to be a classification algorithm, and my choice stopped on decision trees. So it really sticks to the examples the documentation provides. Nothing original.

I choosed to use [external datas](http://www.csie.ntu.edu.tw/~cjlin/libsvmtools/datasets/binary.html#leukemia) rather than those provided by Spark. I choosed a1a for its limited dataset size, and limited size of features (I wanted to be able to test it quickly).

The application I will present now trains a model, evaluate how efficient it is, and then saves it for future uses, in one batch.

	import org.apache.spark.mllib.regression.LabeledPoint
	import org.apache.spark.mllib.tree.DecisionTree
	import org.apache.spark.mllib.util.MLUtils
	import org.apache.spark.{SparkContext, SparkConf}
	
	object Classification {
	
	  def main(args: Array[String]): Unit = {
	    val conf = new SparkConf().setAppName("Simple Application")
	    val sc = new SparkContext(conf)
	
	    val trainingData = MLUtils.loadLibSVMFile(sc, "/home/benwa/Documents/ZZ/spark-1.6.0/data/mine/a1a").map(sanitize)
	    val testingData = MLUtils.loadLibSVMFile(sc, "/home/benwa/Documents/ZZ/spark-1.6.0/data/mine/a1a.t").map(sanitize)
	
	    val model = DecisionTree.trainClassifier(trainingData, 2, Map[Int, Int](), "gini", 5, 32)
	
	    val predictionVersusReality = testingData.map(labeledPoint => (labeledPoint.label, model.predict(labeledPoint.features)))
	    val defectFraction: (Int, Int) = predictionVersusReality.map(rap => rap._1 == rap._2)
	      .aggregate((0,0))(handleNewValue, combine)
	
	    println("Test error : " + (defectFraction._1.toDouble / defectFraction._2.toDouble))
	
	    model.save(sc, "target/tmp/testDecisionTree_1")
	  }
	
	  def handleNewValue(accumulator: (Int, Int), value: Boolean): (Int, Int) =
			    if (value)	
	      (accumulator._1, accumulator._2 + 1)
	    else
	      (accumulator._1 + 1, accumulator._2 + 1)
	
	  def combine(accumulator1: (Int, Int), accumulator2: (Int, Int)): (Int, Int) = (accumulator1._1 + accumulator2._1, accumulator1._2 + accumulator2._2)
	
	  def sanitize(labeledPoint: LabeledPoint) : LabeledPoint =
	    if (labeledPoint.label == 1.0)
	      labeledPoint
	    else
	      new LabeledPoint(0.0, labeledPoint.features)
	}

Ok, so as always, we instanciate our Spark context :

	    val conf = new SparkConf().setAppName("Simple Application")
	    val sc = new SparkContext(conf)

Then we load datas. They are sanitize due to different conventions for cass denomination ( [-1 || +1] on the site I founded datas instead of the [0:2[ Spark convention ).

	    val trainingData = MLUtils.loadLibSVMFile(sc, "/home/benwa/Documents/ZZ/spark-1.6.0/data/mine/a1a").map(sanitize)
	    val testingData = MLUtils.loadLibSVMFile(sc, "/home/benwa/Documents/ZZ/spark-1.6.0/data/mine/a1a.t").map(sanitize)

We now have two RDD of labeled data ( a labeled data consists of its class, and a vector for each feature ).

We will use the training data set to train our model :

	    val model = DecisionTree.trainClassifier(trainingData, 2, Map[Int, Int](), "gini", 5, 32)

I kept the default values. I played a little with the tree depth. Clearly, Spark documentation will explain it better than me.

So let's use our model against testing data. We want the pair of the real class and the predicted class :

	val predictionVersusReality = testingData.map(labeledPoint => (labeledPoint.label, model.predict(labeledPoint.features)))

Then I searched the defect rate on the test datas :

	    val defectFraction: (Int, Int) = predictionVersusReality.map(rap => rap._1 == rap._2)
	      .aggregate((0,0))(handleNewValue, combine)
	
	    println("Test error : " + (defectFraction._1.toDouble / defectFraction._2.toDouble))

The idea is to extract in one pass on the RDD both the defect count and the RDD size. It's done with the aggregate methods (a reduce to a different type).

We can then save the model for future use.

To deploy it :

	/home/benwa/Documents/ZZ/spark-1.6.0/bin/spark-submit --class Classification --master local[4] target/scala-2.11/spark-test_2.11-1.0.jar

Note one : I found a defect rate of 17%, so my algorithmic choice is quite bad. I should test other classification algorithms to try to improve these statistics.

Note two : you can imagine use your model to classify in (near) real time a DStream of features. Awesome !

### Conclusion

I found Spark extremely easy to play with for someone with a functional background. The notion of RDD is declined for every other framework I tested here, was it the DStream or the DataFrame. Someone with a basic knowledge of either Scala or Java 8 and an internet connection to Spark documentation can start to play with it.

I was surprised how it was easy to write and run a pet application. The only serious problem I encountered was the long compilation of Spark, and the need to specify explicitly (in my case) the Scala version to 2.11 .

I will continue to play with Spark :

- I will add GraphX to this post.
- I would be pleased to learn how to unit test my Spark applications
- I have a project of graph based recommendation for friends. I want to implement it with a CQRS pattern using Spark Streaming. It should be interesting.

Stay tuned !
