/**
  * Created by benwa on 15/01/16.
  *
  * Project under the Apache v 2 license
  */

import org.apache.spark.SparkContext
import org.apache.spark.SparkConf
import org.apache.spark.rdd.RDD

object Warmup {
  def main(args: Array[String]) {
    val logFile = "/openup/text.txt" // Should be some file on your system
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

    println("Question 1 is not implemented")
  }

  def maxLine(fileContent: RDD[String]): Unit = {
    /* Question 2

    This method prints the maximum length of the lines of the text
     */

    println("Question 2 is not implemented")
  }

  def maxWordsPerLine(fileContent: RDD[String]): Unit = {
    /* Question 3

    This method prints the maximum number of words a line of the text contains
     */

    println("Question 3 is not implemented")
  }

  def countDistinctWords(fileContent: RDD[String]): Unit = {
    /* Question 4

    This method prints the count of distinct words
     */

    println("Question 4 is not implemented")
  }

  def averageLineLength(fileContent: RDD[String]): Unit = {
    /* Question 5

    This method prints the average length of lines
     */

    println("Question 5 is not implemented")
  }
}
