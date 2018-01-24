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
 * An opaque representation of an application endpoint. Typically, an
 * <code>Endpoint</code> object represents a business entity, but it
 * may represent a party of any sort. Conceptually, an 
 * <code>Endpoint</code> object is the mapping of a logical name
 * (example, a URI) to a physical location, such as a URL.
 * <P>
 * For messaging using a provider that supports profiles, an application
 * does not need to specify an endpoint when it sends a message because 
 * destination information will be contained in the profile-specific header.
 * However, for point-to-point plain SOAP messaging, an application must supply
 * an <code>Endpoint</code> object to
 * the <code>SOAPConnection</code> method <code>call</code>
 * to indicate the intended destination for the message. 
 * The subclass {@link URLEndpoint} can be used when an application
 * wants to send a message directly to a remote party without using a
 * messaging provider.
 * <P>
 * The default identification for an <code>Endpoint</code> object
 * is a URI. This defines what JAXM messaging
 * providers need to support at minimum for identification of
 * destinations. A messaging provider
 * needs to be configured using a deployment-specific mechanism with
 * mappings from an endpoint to the physical details of that endpoint. 
 * <P>
 * <code>Endpoint</code> objects can be created using the constructor, or
 * they can be looked up in a naming
 * service. The latter is more flexible because logical identifiers
 * or even other naming schemes (such as DUNS numbers)
 * can be bound and rebound to specific URIs. 
 */
public class Endpoint {
   /**
    * A string that identifies the party that this <code>Endpoint</code>
    * object represents; a URI is the default.
    */
    protected String id;
    
    /**
     * Constructs an <code>Endpoint</code> object using the given
     * string identifier.
     * 
     * @param uri a string that identifies the party that this
     *        <code>Endpoint</code> object represents; the default
     *        is a URI
     */
    public Endpoint(String uri) {
	this.id = uri;
    }
    
    /**
     * Retrieves a string representation of this <code>Endpoint</code>
     * object.  This string is likely to be provider-specific, and
     * programmers are discouraged from parsing and programmatically
     * interpreting the contents of this string.
     *
     * @return a <code>String</code> with a provider-specific representation
     *         of this <code>Endpoint</code> object
     */
    public String toString() {
        return id;
    }
}
