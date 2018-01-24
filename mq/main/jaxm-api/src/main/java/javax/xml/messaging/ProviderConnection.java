/*
 * Copyright (c) 2000, 2017 Oracle and/or its affiliates. All rights reserved.
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

package javax.xml.messaging;

import javax.xml.soap.*;

/**
 * A client's active connection to its messaging provider.
 * <P>
 * A <code>ProviderConnection</code> object is created using a
 * <code>ProviderConnectionFactory</code> object, which is configured so that
 * the connections it creates will be to a particular messaging provider.
 * To create a connection, a client first needs to obtain an instance of
 * the <code>ProviderConnectionFactory</code> class that creates connections to
 * the desired messaging provider. The client then calls the
 * <code>createConnection</code> method on it.
 * <P>
 * The information necessary to set up a <code>ProviderConnectionFactory</code>
 * object that creates connections to a particular messaging provider is 
 * supplied at deployment time.  Typically an instance of
 * <code>ProviderConnectionFactory</code> will be bound to a logical
 * name in a naming service. Later the client can do a lookup on the
 * logical name to retrieve an instance of the
 * <code>ProviderConnectionFactory</code> class that produces
 * connections to its messaging provider.
 * <P>
 * The following code fragment is an example of a client doing a lookup of
 * a <code>ProviderConnectionFactory</code> object and then using it to create
 * a connection.  The first two lines in this example use the 
 * Java<sup><font size =-2>TM</font></sup> Naming and Directory
 * Interface (JNDI) to create a context, which is then used to do
 * the lookup.  The argument provided to the <code>lookup</code> method
 * is the logical name that was previously associated with the
 * desired messaging provider. The <code>lookup</code> method returns
 * a Java <code>Object</code>, which needs to be cast to a
 * <code>ProviderConnectionFactory</code> object before it can be used to create
 * a connection.  In the following code fragment, the resulting 
 * <code>ProviderConnection</code> object is a connection to the messaging provider
 * that is associated with the logical name "ProviderXYZ".
 * <PRE>
 *    Context ctx = new InitialContext();
 *    ProviderConnectionFactory pcf = (ProviderConnectionFactory)ctx.lookup("ProviderXYZ");
 *    ProviderConnection con = pcf.createConnection();
 * </PRE>
 *
 * <P>
 * After the client has obtained a connection to its messaging provider,
 * it can use that connection to create one or more 
 * <code>MessageFactory</code> objects, which can then be used to create
 * <code>SOAPMessage</code> objects.
 * Messages are delivered to an endpoint using the <code>ProviderConnection</code>
 * method <code>send</code>. 
 *
 * <P>
 * The messaging provider maintains a list of <code>Endpoint</code> objects,
 * which is established at deployment time as part of configuring 
 * the messaging provider. When a client uses a messaging provider to send
 * messages, it can 
 * send messages only to those parties represented by <code>Endpoint</code> 
 * objects in its messaging provider's list. This is true because the
 * messaging provider maps the URI for each <code>Endpoint</code> object to
 * a URL.  
 * <P>
 * Note that it is possible for a client to send a message without
 * using a messaging provider.  In this case, the client uses a
 * <code>SOAPConnection</code> object 
 * to send point-to-point messages via the method <code>call</code>.
 * This method takes an <code>Endpoint</code> object (actually a
 * <code>URLEndpoint</code> object) that specifies the URL where the message
 * is to be sent.  See {@link SOAPConnection} and
 * {@link URLEndpoint} for more information.
 * <P>
 * Typically, because clients have one messaging provider, they will do all 
 * their messaging with a single <code>ProviderConnection</code> object. It is 
 * possible, however, for a sophisticated application to use multiple
 * connections. 
 *
 * <P>
 * Generally, a container is configured with a listener component at
 * deployment time using an implementation-specific mechanism. 
 * A client running in such a container uses a <code>OnewayListener</code>
 * object to receive messages asynchronously. In this scenario, messages are
 * sent via the <code>ProviderConnection</code> method <code>send</code>.
 * A client running in a container that wants to receive synchronous messages
 * uses a <code>ReqRespListener</code> object. A <code>ReqRespListener</code>
 * object receives messages sent via the <code>SOAPConnection</code> method
 * <code>call</code>.
 * <P>
 * Due to the authentication and communication setup done when a 
 * <code>ProviderConnection</code> object is created, it is a relatively heavy-weight 
 * object. Therefore, a client should close its connection as soon as it is
 * done using it.
 * <P>
 * JAXM objects created using one <code>ProviderConnection</code> object cannot be 
 * used with a different <code>ProviderConnection</code> object.
 */
public interface ProviderConnection {

    /**
     * Retrieves the <code>ProviderMetaData</code> object that contains
     * information about the messaging provider to which this
     * <code>ProviderConnection</code> object is connected.
     *
     * @return the <code>ProviderMetaData</code> object with information
     *         about the messaging provider
     * @exception JAXMException if there is a problem getting the 
     *            <code>ProviderMetaData</code> object
     *
     * @see javax.xml.messaging.ProviderMetaData
     *
     */
    public ProviderMetaData getMetaData() throws JAXMException;

    /**
     * Closes this <code>ProviderConnection</code> object, freeing its resources
     * and making it immediately available for garbage collection.
     * Since a provider typically allocates significant resources outside
     * the JVM on behalf of a connection, clients should close connections
     * when they are not needed. Relying on garbage collection to eventually
     * reclaim these resources may not be timely enough.
     *
     * @exception JAXMException if a JAXM error occurs while closing
     *                          the connection. 
     */
    public void close() throws JAXMException; 


    /**
     * Creates a <code>MessageFactory</code> object that will produce
     * <code>SOAPMessage</code> objects for the given profile. The
     * <code>MessageFactory</code> object that is returned can create
     * instances of <code>SOAPMessage</code> subclasses as appropriate for
     * the given profile.
     *
     * @param profile a string that represents a particular JAXM
     *                profile in use. An example of a JAXM profile is:
     *                "ebxml".
     *
     * @return a new <code>MessageFactory</code> object that will create
     *         <code>SOAPMessage</code> objects for the given profile
     * @exception JAXMException if the JAXM infrastructure encounters
     *                          an error, for example, if the endpoint
     *                          that is being used is not compatible
     *                          with the specified profile
     */
    public MessageFactory createMessageFactory(String profile)
        throws JAXMException;

    /**
     * Sends the given <code>SOAPMessage</code> object and returns immediately 
     * after handing the message over to the
     * messaging provider. No assumptions can be made regarding the ultimate
     * success or failure of message delivery at the time this method returns.
     * 
     * @param message the <code>SOAPMessage</code> object that is to be
     *        sent asynchronously over this <code>ProviderConnection</code> object
     * @exception JAXMException if a JAXM transmission error occurs
     *
     */ 
    public void send(SOAPMessage message) 
        throws JAXMException;
}

