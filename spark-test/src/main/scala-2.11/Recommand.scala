import org.apache.spark.graphx._
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
    val friendshipPerUser =  new GraphOps(graph).collectNeighborIds(EdgeDirection.Out)
      .cache()

    friendshipPerUser.cartesian(friendshipPerUser)
      .flatMap(x => {
        val X_1 = x._1
        val X_2 = x._2
        computeScore((X_1._1.toLong, X_1._2.toList), (X_2._1.toLong, X_2._2.toList))
      })
      .sortBy(_._2)
      .take(5)
      .foreach(recommandation => println("Recommandation " + recommandation._1 + " to " + recommandation._3 + " : " + recommandation._2 ))

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
    val user: RDD[(VertexId, String)] = usersFile
      .filter(line => line.equals(""))
      .map(line => {
        val split = line.split(",").toIterator
        (split.next().toLong, split.next())
      })
    val friendship = friendshipFile
      .filter(line => line.equals(""))
      .map( line => {
        val split = line.split(",").toIterator
        Edge(split.next().toLong, split.next().toLong, "")
      })
    Graph(user, friendship)
  }

  def computeScore(x: (Long, List[Long]), y: (Long, List[Long])): Option[(Long, Double, Long)] = {
    if (x._1 == y._1)
      None
    else if (x._2.contains(y._1))
      None
    else {
      val intersection = x._2.filter(y._2.contains(_)).distinct
      val union = x._2.++(y._2).distinct
      Some((x._1, intersection.length.toDouble / union.length.toDouble, y._1))
    }
  }


}
