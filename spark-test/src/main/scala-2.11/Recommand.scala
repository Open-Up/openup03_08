import org.apache.spark.graphx.{Edge, Graph, VertexId}
import org.apache.spark.rdd.RDD
import org.apache.spark.{SparkConf, SparkContext}

object Recommand {

  def main(args: Array[String]): Unit = {
    val conf = new SparkConf().setAppName("Simple Application")
    val sc = new SparkContext(conf)
    val usersFile = sc.textFile("user.csv", 2).cache()
    val friendshipFile = sc.textFile("firendship.csv", 2).cache()

    /*
    Question 7 : Complete this method
     */

    val graph = importGraph(sc, usersFile, friendshipFile)

    /* Question 8 :
    Build a RDD with : key : user id
                       value : its friends
     */
    val friendshipPerUser = ???

    /* Question 9 :
    Compute friendship recommendation :

    (nb of common friends) / (nb of total friends)

    Sort it from highest to lowest.

    Strength of this recommendation algorithm ?
     */

    /* Question 10 :
    Use the pageRank algorithm of the above graph, and print top users

    What is the strength of this recommendation algorithm ?
     */
  }

  def importGraph(sc: SparkContext, usersFile: RDD[String], friendshipFile: RDD[String]): Graph[String, String] = {
    // val users: RDD[(VertexId, String)] = sc.textFile("use", 2).map(row => /* TODO Question 7 */)
    // val friendship = friendshipFile.map(row => /* TODO Question 7 */)
    // Graph(users, friendship)
    ???
  }



}
