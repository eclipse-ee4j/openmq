/*
 * Copyright (c) 1997, 2017 Oracle and/or its affiliates. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v. 2.0, which is available at
 * http://www.eclipse.org/legal/epl-2.0.
 *
 * This Source Code may also be made available under the following Secondary
 * Licenses when the conditions for such availability set forth in the
 * Eclipse Public License v. 2.0 are satisfied: GNU General Public License,
 * version 2 with the GNU Classpath Exception, which is available at
 * https://www.gnu.org/software/classpath/license.html.
 *
 * SPDX-License-Identifier: EPL-2.0 OR GPL-2.0 WITH Classpath-exception-2.0
 */

package javax.jms;

/** The {@code TopicRequestor} helper class simplifies
  * making service requests.
  *
  * <P>The {@code TopicRequestor} constructor is given a non-transacted 
  * {@code TopicSession} and a destination {@code Topic}. It creates a 
  * {@code TemporaryTopic} for the responses and provides a 
  * {@code request} method that sends the request message and waits 
  * for its reply.
  * <p>
  * This is a very basic request/reply abstraction which assumes the session 
  * is non-transacted with a delivery mode of either AUTO_ACKNOWLEDGE or 
  * DUPS_OK_ACKNOWLEDGE. It is expected that most applications will create 
  * less basic implementations.
  * 
  * @see javax.jms.QueueRequestor
  * 
  * @version JMS 2.0
  * @since JMS 1.0
  * 
  */

public class TopicRequestor {

    TopicSession    session;    // The topic session the topic belongs to.
    TemporaryTopic  tempTopic;
    TopicPublisher  publisher;
    TopicSubscriber subscriber;


    /** Constructor for the {@code TopicRequestor} class.
      * 
      * <P>This implementation assumes the session parameter to be non-transacted,
      * with a delivery mode of either {@code AUTO_ACKNOWLEDGE} or 
      * {@code DUPS_OK_ACKNOWLEDGE}.
      *
      * @param session the {@code TopicSession} the topic belongs to
      * @param topic the topic to perform the request/reply call on
      *
      * @exception JMSException if the JMS provider fails to create the
      *                         {@code TopicRequestor} due to some internal
      *                         error.
      * @exception InvalidDestinationException if an invalid topic is specified.
      */ 

    public 
    TopicRequestor(TopicSession session, Topic topic) throws JMSException {
    	
    	if (topic==null) throw new InvalidDestinationException("topic==null");

	    this.session = session;
        tempTopic    = session.createTemporaryTopic();
        publisher    = session.createPublisher(topic);
        subscriber   = session.createSubscriber(tempTopic);
    }


    /** Sends a request and waits for a reply. The temporary topic is used for
      * the {@code JMSReplyTo} destination; the first reply is returned, 
      * and any following replies are discarded.
      *
      * @param message the message to send
      *  
      * @return the reply message
      *  
      * @exception JMSException if the JMS provider fails to complete the
      *                         request due to some internal error.
      */

    public Message
    request(Message message) throws JMSException {
	message.setJMSReplyTo(tempTopic);
        publisher.publish(message);
	return(subscriber.receive());
    }


    /** Closes the {@code TopicRequestor} and its session.
      *
      * <P>Since a provider may allocate some resources on behalf of a 
      * {@code TopicRequestor} outside the Java virtual machine, clients 
      * should close them when they 
      * are not needed. Relying on garbage collection to eventually reclaim 
      * these resources may not be timely enough.
      *
      * <P>Note that this method closes the {@code TopicSession} object 
      * passed to the {@code TopicRequestor} constructor.
      *  
      * @exception JMSException if the JMS provider fails to close the
      *                         {@code TopicRequestor} due to some internal
      *                         error.
      */

    public void
    close() throws JMSException {

	// publisher and consumer created by constructor are implicitly closed.
	session.close();
	tempTopic.delete();
    }
}
