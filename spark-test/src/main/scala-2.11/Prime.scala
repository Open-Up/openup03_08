import org.apache.spark.{SparkContext, SparkConf}

object Prime {
  val start = 10000000000000L
  val stop =  10000000010000L

  def main(args: Array[String]): Unit = {
    val conf = new SparkConf().setAppName("Prime number searcher")
    val sparkContext = new SparkContext(conf)
    val rdd = sparkContext.range(start, stop).cache()

    /*
    Question 6 :

    Find the prime numbers between start and stop
     */
    println("Question 6 is not implemented")
  }
}
