# Consistency between Cassandra and ElasticSearch

## Consistency between two data stores is often not well handled and inconsistencies might appear. In this article, I try to solve this problem for Cassandra and ElasticSearch. We will try to describe the reason of this problem and explore the solutions.

### What is the problem ?

We have an application that saves data in Cassandra but relies on ElasticSearch for the search feature. So on every write we need to update both ElasticSearch and Cassandra. The property we desire is having ElasticSearch Data eventually consistent with Cassandra data (ensuring they don't diverge over time). This problem is quite complicated as computers might fail between the writes in both systems. We will also see that the inherent ways concurrent updates are managed by eventually consistent systems forbids us to maintain eventually consitency accross stores.

What is eventual consistency ? It means that at any given moments we might have inconsistent data, but if you stops updating the system, have no network troubles, and wait long enough, then your data will become consistent again.

### Solution 1 : Build the search index on top of your database

It is maybe the better solution. But it might be complicated to get it right in a *free* way...

A few commercial solutions might exist for this problem. The first one that comes to my mind is [Datastax enterprise](http://www.datastax.com/products/datastax-enterprise), combining SolR and Cassandra. SolR indexes are build on top of local Cassandra tables. Then any changes to the Cassandra content is reflected on the search index. And it works ! No inconsistency can arise here, by design !

Drawback : You have to buy it...

Ok, and what if I want to rely on free software ? I saw an interresting project on Github recently. It is called [Elassandra](https://github.com/vroyer/elassandra). It is ElasticSearch indexes built on top of local Cassandra tables (so very similar to what Datastax does). Again, no inconsistencies between your database and your search index, and by design !

Other advantages includete as :

 - No more manually set sharding ! It just follows Cassandra's consistent hashing. So you will not have to re-index data when scaling!
 - Less network use : you write to only one system...
 - You have ElasticSearch coming in an Eventually Consistent patern. And I like this!

This list of advantage also apply to Datastax solution... Here the drawback is that Elassandra is still experimental. But definitly I wish them good luck.

You have over solutions for Lucene index on top of Cassandra like [stratio-cassandra](https://github.com/Stratio/stratio-cassandra). Some other implementations might lurk around...

### Solution 2 : Avoid the search index

You might not really need a search index for your feature. So why bother with it?

Here I am thinking to Apple implementing SQL like searchs on top of Cassandra. This might solve quite a bunch of use cases. The only good point of having a search index might be intelligent full text search, and maybe performances.

Good news : Apple is already doing it, and I heard it might come soon into future Cassandra releases.

### False solution 3 : Use a queue

Here the idea is that your application writes to a queue (say Kafka). Two services consumes your queue :

 - One writing in Cassandra
 - One writing in ElasticSearch

![]()

Organize your partition key on Kafka so that a write on some entry always fall on the same partition. It will allow you to have a total order per key.

Warning : Kafka have at least one delivery. Which means your writes might get executed several times. This might not be a problem for indepotent updates (like SET this entry to this value) but you will have troubles with types like CQL counters, which are not indepotent.

Also, you might avoid this patter : 

![]()

As your server issuing the write might fail between writing to Cassandra and writing to the queue. 

Basicly we solved the stopping problem here. But race conditions might arise between consummers (slow consummer) leading to inconsistency. Example : You issue two updates on the same key (u1 and u2). At t1, worker 1 start consume message for u1, and get a stop the world garbage collection. Worker 2 then get u2 and apply it. Worker 1 wake up and apply u1. The delivering order is still the good one, but update order is changed.

Conclusion : Queues will not solve this problem in a truely and perfectly consistent way !

### False solution 4 : Keep track of what you indexed

In this section, I want here to better highlight the fact that we can not achive consistencies accross stores because of Cassandra Eventual consistency.

Here our server execute a special algorithm to solve the stopping problem explained above.

It uses a guard entry to detect entries that were not indexed in ElasticSearch after some time. It can be summurized it as follow :

*Writing thread* :

 - Write a guard entry to a Cassandra table. This entry says "I am modifying this entry, which is indexed on ElasticSearch". Include a timestamp.
 - Write your data to Cassandra.
 - Write your data to ElasticSearch.
 - Remove your guard entry.

*Safety checking thread* :

 - Select * on guards. And select those older than your *Guard Grace period*. *Guard Grace period* is the time after which you consider that, if the guard entry is not removed, the write went wrong. As guard entry are removed once the write indexed this table is supposed to be slow. (note : we are using select * as we can not say "find me the timestamp older than...")
 - On entries older than *Guard Grace period*, read data in Cassandra
 - If their is no entries, it means the server failed between writing the guard entry and writing data to Cassandra.
 - If their is an entry in Cassandra, then re-index the ElasticSearch document with what you found. Use ElasticSearch version and a lightweight condition to ensure consistency.
 - Finaly remove the guard entry.

Drawbacks : 3 writes on Cassandra, a complicated *Safety checking thread* to implement.

Moreover, we are not truely consistent here. In fact, take two updates on the same entry : u1 and u2. U1 and U2 are applied on Cassandra. Conflict is won by U2 as it is more recent. but u1 server get a gc pause before writing to ElasticSearch. U2 writes to ELasticSearch. Then U1. And we get the wrong data into ElasticSearch.

Ok, we need to avoid reorders between Cassandra and ElasticSearch updates. To do this, we need to version our Cassandra entries, and ensure we write the last version of our entry to ElasticSearch. But versionning my Cassandra entries will result on abandonning eventual consistency to stronger consistency models...

This problem can be summarized as follow : our writes on the same key are ordered in ElasticSearch. But with CRDT conflict resolution with last write win policy in Cassandra we can get any order, different from the one in ElasticSearch (in fact you don't even have this notion of order, just solving conflicts).

### False solution 5 : Triggers ?

Let's try something else !

I am not really familiar with triggers. So I googled this. I found a mail in the Cassandra mailing list saying :

```
> Be very very careful not to perform blocking calls to ElasticSearch in
> your trigger otherwise you will kill C* performance. The biggest danger of
> the triggers in their current state is that they are on the write path.
>
> In your trigger, you can try to push the mutation asynchronously to ES but
> in this case it will mean managing a thread pool and all related issues.
>
> Not even mentioning atomicity issues like: what happen if the update to ES
> fails or the connection times out ? etc ...
```

Ouch, still the same problems, but pushed to the database... We might still have some write reordering between Cassandra and ElasticSearch (as writing to both systems is still not an atomic operation).

### Solution 6 : Scrapping ?

The idea is simple. On every table execute a select *. And index each entry to ElasticSearch. It takes time, it is not efficient, it consumes resources but it allow you to achieve eventual consistency accross stores.

Definitly I would not like to use it in production.

### My conclusion 

I would say that if you rely on some eventually consistent database like Cassandra, then to achieve eventual consistency between your search index and Cassandra, you will need your search index to be built on top of Cassandra. Any other solution will not be eventually consistent accorss datastores.


