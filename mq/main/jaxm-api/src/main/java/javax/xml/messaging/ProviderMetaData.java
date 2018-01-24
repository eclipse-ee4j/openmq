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

/** 
 * Information about the messaging provider to which a client has
 * a connection.
 * <P>
 * After obtaining a connection to its messaging provider, a client
 * can get information about that provider.  The following code fragment
 * demonstrates how the <code>ProviderConnection</code> object <code>con</code>
 * can be used to retrieve its <code>ProviderMetaData</code> object 
 * and then to get the name and version number of the messaging provider.
 * <PRE>
 *   ProviderMetaData pmd = con.getMetaData();
 *   String name = pmd.getName();
 *   int majorVersion = pmd.getMajorVersion();
 *   int minorVersion = pmd.getMinorVersion();
 * </PRE>
 *
 * The <code>ProviderMetaData</code> interface also makes it possible
 * to find out which profiles a JAXM provider supports.
 * The following line of code uses the method 
 * <code>getSupportedProfiles</code> to 
 * retrieve an array of <code>String</code> objects naming the profile(s)
 * that the JAXM provider supports.
 * <PRE>
 *   String [] profiles = pmd.getSupportedProfiles();
 * </PRE>
 * 
 * When a JAXM implementation supports a profile, it supports the functionality 
 * supplied by a particular messaging specification. A profile is built on top 
 * of the SOAP 1.1 and SOAP with Attachments specifications and adds more
 * capabilities.  For example, a JAXM provider may support
 * an ebXML profile, which means that it supports headers that specify
 * functionality defined in the ebXML specification "Message Service Specification:
 * ebXML Routing, Transport, & Packaging, Version 1.0".
 * <P>
 * Support for  profiles, which typically add enhanced security
 * and quality of service features, is required for the implementation of
 * end-to-end asynchronous messaging.
 */
public interface ProviderMetaData {

    /**
     * Retrieves a <code>String</code> containing the name of the
     * messaging provider to which the <code>ProviderConnection</code> object 
     * described by this <code>ProviderMetaData</code> object is
     * connected. This string is provider implementation-dependent. It
     * can either describe a particular instance of the provider or
     * just give the name of the provider.
     *
     * @return the messaging provider's name as a <code>String</code>
     */
    public String getName();

    /**
     * Retrieves an <code>int</code> indicating the major version number 
     * of the messaging provider to which the <code>ProviderConnection</code> object 
     * described by this <code>ProviderMetaData</code> object is
     * connected.
     *
     * @return the messaging provider's major version number as an 
     *         <code>int</code>
     */
    public int getMajorVersion();
 
    /**
     * Retrieves an <code>int</code> indicating the minor version number 
     * of the messaging provider to which the <code>ProviderConnection</code> object 
     * described by this <code>ProviderMetaData</code> object is
     * connected.
     *
     * @return the messaging provider's minor version number as an 
     *         <code>int</code>
     */
    public int getMinorVersion();

    /**
     * Retrieves a list of the messaging profiles that are supported
     * by the messaging provider to which the <code>ProviderConnection</code> object
     * described by this <code>ProviderMetaData</code> object is
     * connected.
     *
     * @return a <code>String</code> array in which each element is a
     *         messaging profile supported by the messaging provider
     */
    public String[] getSupportedProfiles();
}
