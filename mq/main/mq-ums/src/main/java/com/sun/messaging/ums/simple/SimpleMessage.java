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

package com.sun.messaging.ums.simple;

import com.sun.messaging.ums.common.Constants;
import java.util.Map;

/**
 *
 * @author chiaming
 */
public class SimpleMessage {
    
    /**
     * Message type
     */
    public static final int LOGIN_SERVICE_TYPE = 100;
    
    public static final int SEND_SERVICE_TYPE = 101;
    
    public static final int RECEIVE_SERVICE_TYPE = 102;
    
    public static final int CLOSE_SERVICE_TYPE = 103;
    
    public static final int COMMIT_SERVICE_TYPE = 104;
    
    public static final int ADMIN_SERVICE_TYPE = 900;
    
    public static final int INVALID_SERVICE_TYPE = -1;
    
    //public static final String SID = "ums.sid";

    //public static final String DOMAIN = "ums.domain";
   
    //public static final String USER = "ums.user";
    
    //public static final String PASSWORD = "ums.password";
    
    //public static final String SERVICE = "ums.service";
   
  
    /**
     * plain text message body content type.
     */
    public static final String CONTENT_TYPE = "text/plain;charset=UTF-8";
      
    private String text = null;
    
    //private String destinationName = null;
    
    private Map properties = null;
    
    private int myServiceType = this.INVALID_SERVICE_TYPE;
    
    public SimpleMessage(Map map, String text) {
        this.properties = map;
        this.text = text;
        
        init();
    }
    
    private void init() {
        
        if (this.isLoginService()) {
            this.myServiceType = LOGIN_SERVICE_TYPE;
        } else if (this.isSendService()) {
            this.myServiceType = SEND_SERVICE_TYPE;
        } else if (this.isReceiveService()) {
            this.myServiceType = RECEIVE_SERVICE_TYPE;
        } else if (this.isCloseService()) {
            this.myServiceType = CLOSE_SERVICE_TYPE;
        } else if (this.isCommitService()) {
            this.myServiceType = COMMIT_SERVICE_TYPE;
        }
    
    }
    
    public int getServiceType() {
        return this.myServiceType;
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
        
    public String getText() {
        return this.text;
    }
    
    //public void setText (String text) {
    //    this.text = text;
    //}
    
    public boolean isTopicDomain() {
        boolean istopic = false;
        
        String domain = this.getMessageProperty(Constants.DOMAIN);
        
        if ("topic".equals(domain)) {
            istopic = true;
        } 
        
        return istopic;
        
    }
    
    public boolean isLoginService () {
        String service = this.getMessageProperty(Constants.SERVICE_NAME);
        
        return (Constants.SERVICE_VALUE_LOGIN.equals(service));
    }
    
    public boolean isSendService () {
        String service = this.getMessageProperty(Constants.SERVICE_NAME);
        
        return (Constants.SERVICE_VALUE_SEND_MESSAGE.equals(service));
    }
    
    public boolean isReceiveService () {
        String service = this.getMessageProperty(Constants.SERVICE_NAME);
        
        return (Constants.SERVICE_VALUE_RECEIVE_MESSAGE.equals(service));
    }
    
    public boolean isCommitService () {
        String service = this.getMessageProperty(Constants.SERVICE_NAME);
        
        return (Constants.SERVICE_VALUE_COMMIT.equals(service));
    }
    
    public boolean isRollbackService () {
        String service = this.getMessageProperty(Constants.SERVICE_NAME);
        
        return (Constants.SERVICE_VALUE_ROLLBACK.equals(service));
    }
    
    public boolean isCloseService () {
        String service = this.getMessageProperty(Constants.SERVICE_NAME);
        
        return (Constants.SERVICE_VALUE_CLOSE.equals(service));
    }
    
    public boolean isAdminService () {
        String service = this.getMessageProperty(Constants.SERVICE_NAME);
        
        return ("admin".equals(service));
    }
    
}
