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
 * A special case of the <code>Endpoint</code> class used for simple
 * applications that want to communicate directly with another
 * SOAP-based application in a point-to-point fashion instead of 
 * going through a messaging provider.
 * <P>
 * A <code>URLEndpoint</code> object contains a URL, which is used to make
 * connections to the remote party.
 * A standalone client can pass a <code>URLEndpoint</code> object 
 * to the <code>SOAPConnection</code> method <code>call</code>
 * to send a message synchronously. 
 *
 */
public class URLEndpoint extends Endpoint {
    /**
     * Constructs a new <code>URLEndpoint</code> object using the given URL. 
     *
     * @param url a <code>String</code> giving the URL to use in constructing
     *         the new <code>URLEndpoint</code> object
     */
    public URLEndpoint(String url) {
        super(url);
    }
    
    /**
     * Gets the URL associated with this <code>URLEndpoint</code> object.
     *
     * @return a <code>String</code> giving the URL associated with this 
     *         <code>URLEndpoint</code> object
     */
    public String getURL() {
        return id;
    }
}

