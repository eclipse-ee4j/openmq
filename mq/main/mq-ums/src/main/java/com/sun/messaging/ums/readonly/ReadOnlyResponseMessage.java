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

import java.util.Map;
import javax.servlet.http.HttpServletResponse;

/**
 * The response message for ReadOnly request service.
 * 
 * @author chiaming
 */
public class ReadOnlyResponseMessage {
    
    private int respStatusCode = HttpServletResponse.SC_OK;
    
    private Map map = null;
    
    private String responseMessage = null;
    
    
    public ReadOnlyResponseMessage () {
    }
    
    public void setResponseMap (Map map) {
        this.map = map;
    }
    
    public Map getResponseMap () {
        return this.map;
    }
    
    public void setStatusCode (int respStatusCode) {
        this.respStatusCode = respStatusCode;
    }
            
    public int getStatusCode() {
        return this.respStatusCode;
    }
    
    public void setResponseMessage(String msg) {
        this.responseMessage = msg;
    }
    
    public String getResponseMessage () {
        return this.responseMessage;
    }

}
