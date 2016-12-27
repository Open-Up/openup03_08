import org.apache.spark.{SparkContext, SparkConf}

object Prime {
  val start = 10000000000000L
  val stop =  10000000010000L

  def main(args: Array[String]): Unit = {
    val conf = new SparkConf().setAppName("Prime number searcher")
    val sparkContext = new SparkContext(conf)
    val rdd = sparkContext.range(start, stop).cache()
    val primeList = rdd.filter(isPrime).cache()
    primeList.foreach(prime => println("Prime number found : " + prime))
    println("Found " + primeList.count() + " prime numbers in range [" + start + ", " + stop + "]")
  }

  def isPrime(number: Long): Boolean = {
    if (number == 1)
      return false
    if (number == 2)
      return true
    val mod6 = number % 6
    if (mod6 == 0 || mod6 == 3 || mod6 == 2 || mod6 == 4)
      return false

    val range : Stream[Long] = Stream.range(5L, Math.sqrt(number).toLong, 2L)
    !range.exists(divide(number))
  }

  def divide(number: Long)(candidate: Long): Boolean = number % candidate == 0
}
