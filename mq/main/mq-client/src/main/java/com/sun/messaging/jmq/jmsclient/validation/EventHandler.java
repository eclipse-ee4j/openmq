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

package com.sun.messaging.jmq.jmsclient.validation;

import org.xml.sax.SAXException;
import org.xml.sax.SAXParseException;
import org.xml.sax.helpers.DefaultHandler;

/**
 * Used by DTD validation.
 * @author chiaming
 */
public class EventHandler extends DefaultHandler {
    
    public void error(SAXParseException e)
           throws SAXException {
        throw e;
    }
    
    public void fatalError(SAXParseException exception)
                throws SAXException {
        throw exception;
    }
    
    public void warning (SAXParseException exception)
               throws SAXException {
        //do nothing -- we allow warning conditions.
    }
}
