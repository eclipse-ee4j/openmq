/*
 * Copyright (c) 2000, 2017 Oracle and/or its affiliates. All rights reserved.
 * Copyright (c) 2021 Contributors to the Eclipse Foundation
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

    void setAddressList(String addressList);

    String getAddressList();

    void setUserName(String userName);

    String getUserName();

    void setPassword(String password);

    String getPassword();

    void setClientId(String clientId);

    String getClientId();

    void setAddressListBehavior(String addressListBehavior);

    String getAddressListBehavior();

    void setAddressListIterations(int addressListIterations);

    int getAddressListIterations();

    void setReconnectEnabled(boolean flag);

    boolean getReconnectEnabled();

    void setReconnectAttempts(int reconnectAttempts);

    int getReconnectAttempts();

    void setReconnectInterval(int reconnectInterval);

    int getReconnectInterval();

    void setOptions(String stringProps);

    String getOptions();

}
