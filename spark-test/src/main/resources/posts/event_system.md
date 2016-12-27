# James event system

## General introduction to the event system

James mailbox uses an event system on major changes.

An event may concern :

- Mailbox addition, deletion and rename operations
- Messages addition, deletion and flags update operations

This event system is used in different part of James, such as :

- in indexing solutions. IMAP allows the user to search for messages in a given mailbox that matches some criteria. James default implementation relies on a full mailbox scan that can be very expensive. Other solutions were contributed. They rely on the event system for indexing mailboxes messages, allowing fast message retrieval upon queries. Their are today two implementations : one based on Lucene, and an other we contributed at Linagora : ElasticSearch. I also contributed a mailbox re-indexer, allowing you to re-index mailboxes. Event are there use to keep the indexed snapshot up to date.
- in quota management. RFC-**** introduce quota management. Event were listened to update quotas current values.
- in IMAP IDLE. IMAP IDLE allows a client to register to a specific mailbox updates. It allows to :
- Avoid latencies seen by the client induced by polling
- Decrease load as useless polling are avoided
- in guava cache updates. A not yet exposed by the configuration mailbox project implements an in memory cache for the mailbox. Cache is used to asynchronously update the cache, while relying on mailbox level locks for concurrency control
- and finally to compute Message Sequence Numbers. We will discuss Message Sequence Numbers maybe [in a future post]().

The use cases of the event system can easily be extended. They can be used :

- as a response to an event
- as a way to extend current Message and mailbox manager capabilities in a non invasive way.

## The diversity of use cases

Ok, it is time to do some classification !

We can distinguish :

- Mailbox level registration : the functionality only requires mailbox level registration
- Global registration : the functionality needs to be aware of all events

Finally, in a cluster environment :

- Some functionality interacts with an external system (example : Cassandra quotas, ElasticSearch quotas)
- while others changes local state and need events to be propagated to all James servers (Lucene indexing, in memory quota cache implementation)

Given that, we now need a little vocabulary :

- The object that receive events is called a mailbox listener
- We call a mailbox level mailbox listener a mailbox listener that is registered at the mailbox level
- We call a global mailbox listener a mailbox listener that need to be aware of all events
- We call a once global mailbox listener a global mailbox listener that only need to be aware of events generated on its servers (based on the assumption the same mailbox listener will be present on other nodes)
- We call a each node global mailbox listener a mailbox listener that need to be aware of all events appending on the cluster

## What were my expectations about the event system ?

I started working on the event system as it was an in memory implementation, not modular and not relying on guava.

In memory implementation means it would not work in a clustered environment.

The lack of modularity means it is hard to make it match your infrastructure.

The fact guava was not used to implement a locked multimap means it is harder to read and to understand the underlying logic, while increasing the risk of bugs.

That being said :

- We need previous implementation for a standalone James server
- We need an implementation for large James clusters, scaling easily
- I finally think an implementation allowing you to scale up while allowing each node mailbox listeners would be appreciated. More on that later.

The end of this post will detail these implementations.

## Implementations details

Today implementation works the following way :

- 1/ The user interacts with either the mailbox manager or the message manager
- 2/ The given manager will trigger an Event Dispatcher with the appropriate details
- 3/ The Event dispatcher will generate an event using an Event Factory (code factorization)
- 4/ The Event is then passed to the mailbox listener registered by the event dispatcher. It is a Mailbox Distributing Listener. Its role is to send the events to the appropriated mailbox listeners, both mailbox and global. The way events are send other the network is an implementation detail. A mailbox listener registry is used to match the appropriate listeners located on the current node. More will follow on event delivery.

Note the Mailbox Distributing Listener is also responsible of handling new mailbox listeners registrations. A mailbox listener registry object is used to keep track of local registrations. Retrieving distant mailbox registrations is implementation specific.

### Mono node event system

The simplest event system you might think of :

- Event are never sent to other servers
- External systems are never updated upon mailbox listener registry

All go on in memory, and it is fast. This is of course the default behavior.

### Multi node, scale efficient event system

Each node mailbox listener are disable for obvious reasons. Once Mailbox Listener are triggered before further treatment.

We shall not send events related to mailbox A to a server that had not registered any mailbox listener on mailbox A (for load reasons).

That being said, sending by mistake/inconsistencies events to the wrong servers will only generate unneeded load (de-serialization of received event + local delivery to no mailbox listener).

To sum up :

- We need to keep track of our registration in an external system. We should register externally a server to a mailbox only once, but we should keep track of the registration locally : A Distant Mailbox Path Register. Mapper is used for underlying implementation. We also need a way to have registration expiring upon server faults. A Distant Mailbox Path register handles this by default.
- We need to send events across servers. We need serialization / de-serialization and a transport mechanism. Jackson is used either in MessagePack mode or JSON mode. JSON is faster to compute (2 times) but is two time larger and more readable. Kafka queues are used as a transport mechanism : each James server listen on a queue. Of course other mechanism can be used here.

![Distributed event system](/images)

So what is your implementation of Distant Mailbox Path Register Mapper ?

It is based on Cassandra and uses entry timeout to handle server faults. It is eventually consistent, thus there is no strong warranties (message can get duplicated / not received by James servers). You can of course implement your own Mailbox Path Register Mapper to match your infrastructure (ZooKeeper? SQL + entry expiration?)

### Broadcast event system

It is here very simple : it is a simple in memory distributing event listener but events get broadcasted to all servers. It reuses mechanisms already introduced for serialization and transport.

Why would I want to use it ?

As a temporary solution if I use mono node global listeners (in memory quota/ in memory caching/ lucene indexing) but need to scale up fast.

Note that you are risking inconsistencies on these listeners in the face of network partitions. It can hardly be healed ( message re-indexing, dropping quotas and cache, etc...) but need a sys admin intervention.

### Event delivery

One last implementation detail : event delivery. This operation means iterating over mailbox listeners and delivering events to them. Here the purpose is latency implementation. Some quite expensive computation can occurs while delivering events, like indexing in Lucene, which, if done synchronously might increase significantly latencies.

That's why I proposed three different models for event delivery :

- **synchronous** : This means events are processed one after another before the Distributed Listener returns. It may be slow but no inconsistencies can arise.
- **asynchronous** : Event is delivered by a Thread pool to the mailbox listeners. Mailbox Listeners event delivery is then done concurrently. Note that it is fast but inconsistencies may arise (for example using Message Sequence Number), so this mode is not advised.
- **mixed** : Each listener declares if it can be executed asynchronously safely. Some listeners will get executed synchronously while non critic listeners will be executed asynchronously.

### Error handling on event delivery

Exception are caught upon event delivery and not propagated further (before, a single failing mailbox listener might cause the entire event delivery process to stop and generate negative user response. I saw it while debugging ElasticSearch indexing).

Logger from the Mailbox Session is used to register the unexpected event.

## Conclusion

I hope this post helped you understand my work on James event system. Do not hesitate to criticize it, and ask or (better) propose new mappers / transport implementations... Thanks for reading!
