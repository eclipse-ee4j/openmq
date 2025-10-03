
1.  Files

    BrokerAddress.java
        Defines abstract serializable BrokerAddress class.

    Cluster.java
    ClusterCallback.java
        Define the basic cluster interface.

    Interest.java
    InterestID.java
        Topology independant implementation of an interest.

    InterestManager.java
        Interest manager interface.
    
    RouteTable.java
    JMSDestination.java
        Topology independant route table / interest table manager.

    MessageBus.java
        Topology independant part of the cluster communications.

    BrokerInfo.java
        Encapsulates useful information about a broker. Used by
        MessageBus to maintain a (topology independant) list of
        all the brokers in the cluster.

    MessageBusCallback.java
        A callback interface implemented by the MessageBus users.

    CallbackDispatcher.java
        A separate thread for dispatching MessageBusCallback methods.

2.  Topologies

    *   standalone
    *   twobroker
    *   fullyconnected
    *   arbitrary


3.  TODO list :

    *   Check for any re-entrancy issues
    *   Dynamic topology updates - "cluster.properties" changes should
        take effect right away.

4.  Interest forwarding

    *   Broadcast interest updates whenever a local client creates,
        detaches from or removes an interest.
    *   Each broker maintains a topology independant list of all the
        known brokers. The cluster topology implementation is
        responsible for keeping this list uptodate. Whenever a new
        broker gets added to this list, a unicast interest update
        packet containing information about all the local interests is
        sent to the new broker.
    *   Conversely, when a broker gets removed from this list, the
        interests local to that broker are either removed or detached.

5.  Election mechanism


6.  Failover queues -

    The failover queue router needs to choose a primary queue
    receiver when -

    1.  A new local interest is created and currently there is no
        primary interest for the queue.
    2.  The current primary interest goes away.

    The primary queue receiver is selected as follows -

    1.  Select the first local interest using -
            InterestManager.getFirstInterest()
        as potential primary receiver.

    2.  Run the election protocol using -
            MessageBus.lockResource(String, long timestamp, ConnectionID)
        with the interest creation time as the timestamp argument.

    3.  If lockResource succeeds, call
            InterestManager.announcePrimaryInterest()
        to tell all the brokers in the cluster about the new
        primary interest.

    4.  Handle the primary interest change notification -
            MessageManager.primaryInterestChanged()
        on all brokers.

    Following changes have been made to support this -

    1.  Added Interest.setTimestamp(long) and Interest.getTimestamp()
        methods to remember the interest creation time.

    2.  The MessageBus.lockResource() now takes a timestamp argument.

    3.  The InterestManager.getFirstInterest() method only looks
        for local interests.

    4.  Added a new event notification callback -
            MessageBusCallback.primaryInterestChanged(InterestID);

    5.  Added a new method - InterestManager.announcePrimaryInterest().
        which results in a event notification callback on all the
        brokers in the cluster (including the announcing broker.)


7.  Centralized cluster configuration

    1.  Config server should ensure that its own persistent database
        is up to date.

    2.  Avoid 2 master brokers.

    3.  Use config server's time to remember lastSyncTime. Add
        (- 1 hour) adjustment to compensate for the clock skew.

    4.  Write interest objects using special serialization methods.

    5.  Config history compression - restore config server log using
        config server's persistent store.

    6.  Backup/Restore operations.

    7.  jmqcmd list cluster support.

    8.  Fully connected clusters - Each broker should initiate
        connections to all the brokers it is aware of and the
        duplicate connections should get rejected. When a new broker
        joins the cluster, other brokers should not need a restart.

    9.  Need more research on how to add brokers to a cluster.

