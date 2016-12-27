/**
  * Created by benwa on 15/01/16.
  *
  * Project under the Apache v 2 license
  */

import org.apache.spark.SparkContext
import org.apache.spark.SparkConf
import org.apache.spark.rdd.RDD
import scala.math.max;

object Warmup {
  def main(args: Array[String]) {
    val logFile = "file:///openup/text.txt" // Should be some file on your system
    val conf = new SparkConf().setAppName("Simple Application")
    val sc = new SparkContext(conf)
    val fileContent = sc.textFile(logFile, 2).cache()

    countLines(fileContent)
    maxLine(fileContent)
    maxWordsPerLine(fileContent)
    countDistinctWords(fileContent)
    averageLineLength(fileContent)
  }

  def countLines(fileContent: RDD[String]): Unit = {
     /* Question 1

     This method prints the number of lines of text
     */

    println("File number of lines " + fileContent.count())
  }

  def maxLine(fileContent: RDD[String]): Unit = {
    /* Question 2

    This method prints the maximum length of the lines of the text
     */

    println("Maximum file length " + fileContent.map(line => line.length)
      .reduce((a, b) => max(a, b)))
  }

  def maxWordsPerLine(fileContent: RDD[String]): Unit = {
    /* Question 3

    This method prints the maximum number of words a line of the text contains
     */

    println("Max words per line " + fileContent.map(line => line.split(" ").length)
      .reduce((a, b) => max(a, b)))
  }

  def countDistinctWords(fileContent: RDD[String]): Unit = {
    /* Question 4

    This method prints the count of distinct words
     */

    println("Distinct words : " + fileContent.flatMap(line => line.split(" "))
      .distinct()
      .count())
  }

  def averageLineLength(fileContent: RDD[String]): Unit = {
    /* Question 5

    This method prints the average length of lines
     */

    val totalLength = fileContent.map(line => line.length).reduce((a, b) => a + b)
    val lineCount = fileContent.count()

    println("Average line length : " + (totalLength / lineCount))
  }
}
