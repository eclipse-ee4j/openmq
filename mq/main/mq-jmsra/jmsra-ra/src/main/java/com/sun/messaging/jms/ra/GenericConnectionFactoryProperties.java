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

package com.sun.messaging.jms.ra;

/**
 * This interface defines JavaBean properties that are common to a ManagedConnectionFactory and an ActivationSpec
 */
public interface GenericConnectionFactoryProperties {
	
    public void setAddressList(String addressList);
    public String getAddressList();
    
    public void setUserName(String userName);
    public String getUserName();
    
    public void setPassword(String password);
    public String getPassword();
    
    public void setClientId(String clientId);
    public String getClientId();
    
    public void setAddressListBehavior(String addressListBehavior);
    public String getAddressListBehavior();
    
    public void setAddressListIterations(int addressListIterations);
    public int getAddressListIterations();
    
    public void setReconnectEnabled(boolean flag);
    public boolean getReconnectEnabled();
    
    public void setReconnectAttempts(int reconnectAttempts);
    public int getReconnectAttempts();
    
    public void setReconnectInterval(int reconnectInterval);
    public int getReconnectInterval();

    public void setOptions(String stringProps);
    public String getOptions();  
    
}
