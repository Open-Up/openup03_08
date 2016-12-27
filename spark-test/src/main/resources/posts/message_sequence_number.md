# Message sequence numbers

## The IMAP plague : context management at scale

Introduced in RFC-****, Message Sequence Number is a way to address messages in mailboxes. It is a mutable number attached to your message, representing its order in the mailbox.

For instance, in a mailbox with n mails, the first mail will have message sequence number 1, the second message sequence number 2, and the third message sequence number 3. Now if we delete the second message, the first message will have message sequence number one, the (previous) third message will have message sequence number 2.

Here, I want to share my thoughts about Message Sequence Numbers.

## Why they are dangerous...

**Message Sequence Number is an unsafe way to address messages**. It looks like a natural way for human to easily address messages, but because of identifier mutability, you might get unexpected results.

![Race condition on valid Message Sequence Use leading to arbitrary message loss]()

So, this means that using message sequence number is :

- incompatible with shared mailboxes introduced in RFC-****
- and even incompatible with multiple devices belonging to the same user

**Deletions is a costly, linear operation**. Deleting a mail will demand to decrease the following Message Sequence Number, which can be an arbitrary expensive operation. Even using a database, you might not want to write something like *UPDATE MESSAGE WITH sequence = sequence - 1 WHERE sequence > any*.

That is basically what I don't like in general about Message Sequence Number.

## Message Sequence Numbers in James

In James, the Message Sequence Number relies on [the mailbox event system](). On a mono node set up, it works fine. A lock taken on the mailbox upon request processing will protect against data race in the face of concurrent requests. An in memory mapping between Unique Identifier and Message Sequence Number is kept up to date. Note that as this is the same process, a failure in the event system means a global failure (same node).

A quick look at this solution shows that computation of Message Sequence Numbers and updates are always done even if they are not used.

### Multi-node set up

Things gets harder when we move to multi-node...

To keep the Unique Identifier <=> Message Sequence Number up to date, you need hard concurrency control to avoid updating Message Sequence Number concurrently across servers. Which comes with great costs :

- You need CP middlewares in a critical part of your application (I am thinking to you, ZooKeeper)
- Or somethings that looks like ACID distributed transactions, scarifying either Fairness (which seems a valid option) or Throughput. See the [FIT theorem]() paper for more insight on this point. Transaction are needed as multiple values gets updated at the same time.

Todays implementation lack this distributed concurrency control.

To be quick, a fully consistent implementation of Message Sequence Number on top of Cassandra (no support for strong isolation transaction, that are needed here) and on top of HazelCast (gives up consistency upon network partitions) is impossible.

To add to our troubles, todays distributed [event system]() comes with no warranties. It can easily be turned into *at least one delivery* easily by increasing **Cassandra** consistency level to quorum. Note that these are **Kafka** warranties. Note that, in order for today event system to not violate the Message Search Number consistency, we need to have remote event delivery correlated with mailbox locking (not implemented today). **Using the event system for such purposes in a distributed setting comes at great costs**. We don't want our event system to be equivalent to consensus.

What option are left when not relying on the event system ? We are compelled to move the responsibility of Message Sequence Numbers to the core of our code, and manage it while adding and deleting messages. With such an implementation, if using strong distributed locks or distributed transactions would do the trick.

Finally, loosing consistency on Message Sequence Number would make their use even more risky. Maybe we just don't care.

## Implementing Message Sequence Numbers in James

To sum up, the Unique Identifier <=> Message Sequence number mapping **is not eventually consistent** across servers.

Viable options are, in my opinion :

- A perfect distributed event system coupled with distributed mailbox locking
- Moving Message Sequence Number responsibility to the MessageManager and relying on distributed mailbox locking.
- Moving Message Sequence Number responsibility to the MessageManager and relying on distributed transactions.
- Not supporting message sequence number in distributed set ups.

The last option is viable as :

- We can't implement safely the feature without relying on a CP system.
- It is dangerous even without a distributed set up.

I want to add a quick not on how IMAP might be extended to make the use of Message Sequence Number safe : **add a version**. The version is incremented upon message deletions (that invalidates previous contexts). If you are issuing a request using the wrong context version, then this request is rejected. Note that this is, to my knowledge, not yet present in IMAP.
