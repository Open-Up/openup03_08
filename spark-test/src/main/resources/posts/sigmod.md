# Bolt-on Causal Consistency

## Conciliate Causal consistency with highly available systems

I have recently read a Peter Bailis paper. I want to sum it up and explain what I learnt from it.

Tags : Distributed algorithms, Eventual consistency, Causal consistency

### Causal consistencies implementations

The first mini revolution I encountered with this paper is how to achive Causal consistencies.

Here are the few methods I knew :

 - When you transmit a write, you transmit all its dependencies. This way, you can't miss a dependency. Ok, but this is inneficient.
 - You use vector clock to track causality, and you block until you receive the message causaly before. This is nice, but this is not available.

And the mini revolution is :

 - You can have local representations that diverge but keep causal consistency. No inter node communication is compulsary, and you trivialy keep causality.

Of course, latest point is too simple. But it is really nice as a back up behaviour in the face of partitions, as process can still read and writes.

In fact, using the latest point, we can update local versions of causality in an eventual way, and achieve something eventual consistency friendly.

### Separation of concerns

The authors make a distinction between liveness properties and safety properties.

 - Liveness : It represent the eventual consistancy concerns. Our replica will eventually agree. This task can be done by a data base, let's say Cassandra.
 - Safety : Read operation only show causal consistent data to our application. This can be achieved client side with what he calls a **shim layer**. This layer is responsible to track causality.

I find this seperation interesting, as client implementation can easily have these guaranties using existing eventual consistent database systems.

### Causal cut

The authors took time define the notion of causality, and added a definition : a **causal cut**. A causal cut means that you are at one of the possible causal chain for each writes. Combined with what was said before it means you local, causality consistent version.

### Overwritten histories

One of the main issue the user encountered is **Overwritten hostories**. This means, on concurrent updates, the underlying data store will merge these updates, and for instance in the case of last write wins, will drop all concurrent branches but one. We need to take this into acount.

The article solves this by having an asynchronous update of the keys contained in the shim layer.

### Other interesting notions

They highlight the fact that causality can either be :

 - **implied** : This causal dependency was present and might have influence the decision but we can't be sure of that.
 - **explicit** : our application have a mechanism for specifying dependencies. This can be miss used (or not used), but most of the time it will track successfully dependencies, but will be way lighter. One example is the *reply* button.

### Conclusion

I really found this paper awesome, and it really improve my understanding of causality.

I think I will continue to write articles on what I learnt from paper I read.
