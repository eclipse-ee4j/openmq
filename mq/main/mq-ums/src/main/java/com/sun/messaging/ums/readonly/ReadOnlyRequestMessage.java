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

package com.sun.messaging.ums.readonly;

import com.sun.messaging.ums.simple.*;
import java.util.Map;

/**
 * The request message for ReadOnly request service.
 * 
 * @author chiaming
 */
public class ReadOnlyRequestMessage {
   
     /**
     * plain text message body content type.
     */
    public static final String CONTENT_TYPE = "text/plain;charset=UTF-8";
      
    private String requestMessage = null;
    
    //private String destinationName = null;
    
    private Map properties = null;
    
    public ReadOnlyRequestMessage(Map map, String text) {
        this.properties = map;
        this.requestMessage = text;
    }
    
    public Map getMessageProperties() {
        return this.properties;
    }
    
    public String getMessageProperty (String name) {
        
        String[] values =  (String[]) this.properties.get (name);
        
        if (values != null) {
            return values [0];
        } else {
            return null;
        }
    }
        
    public String getRequestMessage() {
        return this.requestMessage;
    }
    
}
