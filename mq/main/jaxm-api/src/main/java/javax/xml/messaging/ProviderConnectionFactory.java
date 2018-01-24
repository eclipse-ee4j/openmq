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
 * A factory for creating connections to a particular messaging provider.
 * A <code>ProviderConnectionFactory</code> object can be obtained in two
 * different ways.
 * <ul>
 * <li>Call the <code>ProviderConnectionFactory.newInstance</code>
 * method to get an instance of the default <code>ProviderConnectionFactory</code>
 * object.<br>
 *  This instance can be used to create a <code>ProviderConnection</code>
 * object that connects to the default provider implementation.
 * <PRE>
 *      ProviderConnectionFactory pcf = ProviderConnectionFactory.newInstance();
 *      ProviderConnection con = pcf.createConnection();
 * </PRE>
 * <P>
 * <li>Retrieve a <code>ProviderConnectionFactory</code> object
 * that has been registered with a naming service based on Java Naming and 
 * Directory Interface<sup><font size=-2>TM</font></sup> (JNDI) technology.<br>
 * In this case, the <code>ProviderConnectionFactory</code> object is an 
 * administered object that was created by a container (a servlet or Enterprise
 * JavaBeans<sup><font size=-2>TM</font></sup> container). The
 * <code>ProviderConnectionFactory</code> object was configured in an implementation-
 * specific way, and the connections it creates will be to the specified
 * messaging provider. <br>
 * <P>
 * Registering a <code>ProviderConnectionFactory</code> object with a JNDI naming service
 * associates it with a logical name. When an application wants to establish a
 * connection with the provider associated with that
 * <code>ProviderConnectionFactory</code> object, it does a lookup, providing the
 * logical name.  The application can then use the 
 * <code>ProviderConnectionFactory</code>
 * object that is returned to create a connection to the messaging provider.
 * The first two lines of the  following code fragment use JNDI methods to 
 * retrieve a <code>ProviderConnectionFactory</code> object. The third line uses the
 * returned object to create a connection to the JAXM provider that was 
 * registered with "ProviderXYZ" as its logical name.
 * <PRE>
 *      Context ctx = new InitialContext();
 *      ProviderConnectionFactory pcf = (ProviderConnectionFactory)ctx.lookup(
 *                                                                 "ProviderXYZ");
 *      ProviderConnection con = pcf.createConnection();
 * </PRE>
 * </ul>
 */
public abstract class ProviderConnectionFactory {
    /**
     * Creates a <code>ProviderConnection</code> object to the messaging provider that
     * is associated with this <code>ProviderConnectionFactory</code>
     * object. 
     *
     * @return a <code>ProviderConnection</code> object that represents 
     *         a connection to the provider associated with this 
     *         <code>ProviderConnectionFactory</code> object
     * @exception JAXMException if there is an error in creating the
     *            connection
     */
    public abstract ProviderConnection createConnection() 
        throws JAXMException;

    static private final String PCF_PROPERTY
        = "javax.xml.messaging.ProviderConnectionFactory";

    static private final String DEFAULT_PCF 
        = "com.sun.xml.messaging.jaxm.client.remote.ProviderConnectionFactoryImpl";

    /**
     * Creates a default <code>ProviderConnectionFactory</code> object. 
     *
     * @return a new instance of a <code>ProviderConnectionFactory</code>
     *
     * @exception JAXMException if there was an error creating the
     *            default <code>ProviderConnectionFactory</code>
     */
    public static ProviderConnectionFactory newInstance() 
        throws JAXMException
    {
        //try {
	    return (ProviderConnectionFactory)
                FactoryFinder.find(PCF_PROPERTY,
                                   DEFAULT_PCF);
        //} catch (Exception ex) {
            //throw new JAXMException("Unable to create "+
                                    //"ProviderConnectionFactory: "
                                    //+ex.getMessage());
        //}
    }
}
