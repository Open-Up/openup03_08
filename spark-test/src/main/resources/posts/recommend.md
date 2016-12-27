# Building a recommender system : Step 1

## Using a batch on Spark, we will recommend people that are likely to be your friends, seeing your current relationships.


### Instalation

I run this demonstration on a Spark 1.5.0 cluster. Installation is like the one [on this post](), except that the version changes.

To be compatible with the Datastax plugin for Spark, we need to use a Cassandra 2.2.4. I run it using docker :

  docker run -d --name cassandra2 -p 9042:9042 cassandra:2.2

To connect to cqlsh :

  docker exec -i -t cassandra2 cqlsh

### Algorithm

The basic idea we will follow here is that you are very likely to become friends with the ones sharing the most common friends with you.

We will build a recommender out of this statement.

By recommending, we mean sorting potential candidates for friendship, in way that match the user intent. This problem is equivalent to giving a score for each candidate.

So this is what we are going to do : given two users, we are going to compute a score, and save the result in a Cassandra table for later queries.

Our formula is the following :

With x and y two users, we write friends(x) peoples friend with x, and friendshipScore(x, y) the friendship score of x and y.

I decided that friendshipScore(x, y) = friends(x) intersect friends(y) / friends(x) union friends(y)

![Visual representation of the recommendation process]()

Why ?

In my opinion :

 - This commutative, opening to bath optimizations (as unions and intersections are commutative)
 - Intersection is friends that we have in common.
 - I chose to divide this result by the number of friends of these two peoples two reduce slow start effect.

Ok, what is slow start ?

Slow starts affect graph based recommender. It means in our case that we can't use our approach with someone without friends, and we might get unexpected / irrelevant results for people with small friendship degrees.

Dividing by the union means that if bob as 6 friends and 3 in common with Alice that has 500 friends, friendship score will be 0.5. And will be higher that someone having 4 friends in common with Alice and another 500 friends. This division makes us recommend new users more frequently, hence fighting slow start.

### Datastructure and populating Cassandra tables

Using cqlsh we will create a keyspace and use it :

    CREATE keyspace test WITH replication = {'class': 'SimpleStrategy', 'replication_factor': 1};
    USE test ;

So let's pretend we have users, indexed by a unique identifier :

  CREATE TABLE user ( id bigint PRIMARY KEY , name text);

These users are linked together by  relationships between these ids :

  CREATE TABLE friendship ( id_origin bigint , id_dest bigint , primary KEY (id_origin, id_dest));

We want to fill the recommendation table using the above algorithm :

  CREATE TABLE friendship ( id_origin bigint , id_dest bigint , primary KEY (id_origin, id_dest));

Let's comment on this table a little.

We use id_origin, representing the user to which the recommendation is made, as a primary key.

We want to read the recommendation using a sort on the score. We want to use directly the **ORDER BY** clause so we need the score to be the first clustering column.

Finaly the second and last clustering column should be the id of the people recommended as a friend.

### Filling Cassandra table

I wanted sample random data in order to start working. So I used Spark batches to populate the above tables.

  def createRelationship(sc: SparkContext): Unit = {
    sc.cassandraTable("test", "user").select("id")
      .map(_.getLong("id"))
      .map(id => List.range(1L, 20 + Math.abs(random.nextInt()) % 40)
        .map(long => (id, 1 + Math.abs(random.nextInt()) % 1000)))
      .flatMap(list => list)
      .saveToCassandra("test", "friendship", SomeColumns("id_origin" as "_1", "id_dest" as "_2"))
  }
 
  def createUsers(sc: SparkContext): Unit = {
    sc.range(1L, 1000L)
      .map(long => (long, UUID.randomUUID().toString))
      .saveToCassandra("test", "user", SomeColumns("id" as "_1", "name" as "_2"))
  }

The above function details will be trivial to understand by reading the next section...

### Spark implementation

We will use the Cassandra connector from DataStax to have a smooth integration of Cassandra in Spark.

We need this dependency :

  libraryDependencies += "com.datastax.spark" % "spark-cassandra-connector_2.11" % "1.5.0-M3"

And this import :

  import com.datastax.spark.connector._

We can then initialize the Spark context :

  val conf = new SparkConf().setAppName("Simple Application")
                            .set("spark.cassandra.connection.host", "127.0.0.1")
  val sc = new SparkContext(conf)

You note that we pass the Cassandra host to our context.

Then, we can compute our recommendations from our friendship table :

  val friendshipByKey = sc.cassandraTable("test", "friendship")
      .map(row => (row.getLong("id_origin"), row.getLong("id_dest")))
      .groupBy(_._1)
      .map(x => (x._1, x._2.map(y => y._2)))
      .cache()

  friendshipByKey.cartesian(friendshipByKey)
      .flatMap(x => computeScore(x._1, x._2))
      .saveToCassandra("test",
        "recommendation",
        SomeColumns("id_origin" as "_1", "score" as "_2", "id_dest" as "_3"))

With

  def computeScore(x: (Long, Iterable[Long]), y: (Long, Iterable[Long])): Option[(Long, Double, Long)] = {
    if (x._1 == y._1)
      None
    else if (x._2.contains(y._1))
      None
    else {
      val intersection = x._2.filter(y._2.contains(_)).toList.distinct
      val union = x._2.toList.++(y._2).distinct
      Some((x._1, intersection.length.toDouble / union.length.toDouble, y._1))
    }
  }

So, what is happening here ?

First, we need to read and stream the friendship table :

  val friendshipByKey = sc.cassandraTable("test", "friendship")

Then we transform it in a pair so as to work easily with it :

  .map(row => (row.getLong("id_origin"), row.getLong("id_dest")))

We will group friendship links so as to have for each user its friends : (id_user, list_of_friends)

  .groupBy(_._1)
  .map(x => (x._1, x._2.map(y => y._2)))

We then cache the RDD as we will reuse it a lot :

  .cache()

Now, we want to calculate intersections and friends union for each pair of users. What we need is a cartesian product :

  friendshipByKey.cartesian(friendshipByKey)

Then we can compute scores (more on that later) and ignore empty values :

  .flatMap(x => computeScore(x._1, x._2))

Once score are calculated, we then can save it to the cassadra table. Note that we indicated which tuple field corresponds to which table column :

  .saveToCassandra("test", "recommendation", SomeColumns("id_origin" as "_1", "score" as "_2", "id_dest" as "_3"))

Now, the last question peoples are asking : **How do we calculate scores ?**

  def computeScore(x: (Long, Iterable[Long]), y: (Long, Iterable[Long])): Option[(Long, Double, Long)] = {
    if (x._1 == y._1)
      None
    else if (x._2.contains(y._1))
      None
    else {
      val intersection = x._2.filter(y._2.contains(_)).toList.distinct
      val union = x._2.toList.++(y._2).distinct
      Some((x._1, intersection.length.toDouble / union.length.toDouble, y._1))
    }
  }

Recommending a user to have a friendship with himself is a non sens so we do not return results :

    if (x._1 == y._1)
      None

Same thing about recommending a user that is already a friend :

    else if (x._2.contains(y._1))
      None

When none of these conditions is met, we can calculate score :

    else {
      val intersection = x._2.filter(y._2.contains(_)).toList.distinct
      val union = x._2.toList.++(y._2).distinct
      Some((x._1, intersection.length.toDouble / union.length.toDouble, y._1))
    }

So basically what we did here is the friend intersection cardinal divided by the frien union cardinal.

### And using GraphX ?

As you might have noted, we directly used RDDs to make graphs computations. You might wonder what is the equivalent code when using Sparks Graph calculation library, GraphX.

It is that simple :

    val friendshipGraph: Graph[String, String] = importGraph(sc)

    val friendshipByUsers = new GraphOps(friendshipGraph).collectNeighborIds(EdgeDirection.Out)
      .cache()

    friendshipByUsers.cartesian(friendshipByUsers)
      .flatMap(x => computeScore((x._1._1.toLong, x._1._2.toList), (x._2._1.toLong, x._2._2.toList)))
      .saveToCassandra("test", "recommandation", SomeColumns("id_origin" as "_1", "score" as "_2", "id_dest" as "_3"))

with

  def importGraph(sc: SparkContext): Graph[String, String] = {
    val users: RDD[(VertexId, String)] = sc.cassandraTable("test", "user").map(row => (row.getLong("id"), row.getString("name")))
    val friendship = sc.cassandraTable("test", "friendship").map(row => Edge(row.getLong("id_origin"), row.getLong("id_dest"), "Information about friendship"))
    Graph(users, friendship)
  }

To create a graph with GraphX, we need a Edges and a Vertices collection. Here we retrieve them from Cassandra and map them to the appropriated collection.

We then have basic operations on graph implemented. Obtaining Edges grouped by vertex becomes then as simple as :

  val friendshipByUsers = new GraphOps(friendshipGraph).collectNeighborIds(EdgeDirection.Out)

We then cache the result as we will reuse it later :

  .cache()

Then we can make the intersection / union logic as above and previously explained :

    friendshipByUsers.cartesian(friendshipByUsers)
      .flatMap(x => computeScore((x._1._1.toLong, x._1._2.toList), (x._2._1.toLong, x._2._2.toList)))
      .saveToCassandra("test", "recommandation", SomeColumns("id_origin" as "_1", "score" as "_2", "id_dest" as "_3"))

Using GraphX makes the graph operation easier to read in this case.

### Using classic Graph algorithms

Now imagine we want to be able to recommend most popular users to new users. We introduce a trendiness table, along with a trendiness score. We can then run the pageRank algorithm on this graph.

      val friendshipGraph: Graph[String, String] = importGraph(sc)
   
      friendshipGraph.pageRank(0.1)
        .vertices
        .saveToCassandra("test", "user_trendiness", SomeColumns("id" as "_1", "score" as "_2")

Running such an algorithm here is trivial, and we can recommand friendship for new comers to top users to fight against the slow start problem they faces.

![Page rank feature]()

### Following this article

We wrote here 3 batches job to help us recommend friendship for our users. These batches are expensive to run. If the data they were run on are updated, then we will have to run a new batch in order to get our recommendations. Running new batches means potentially computing things that did not change since last batch, hence useless work.

![Image illustrating streaming computing](/images/static_streaming.png)

I am interested into writing a similar article, but using streaming computation and maintaining a nearly consistent view of the recommendation, avoid cyclic and expensive batches. And computing only information that changes. This is maybe for the next article.
