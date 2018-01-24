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

package com.sun.messaging.visualvm.ui;

import java.util.EventObject;

/**
 *
 * @author isa
 */
public class MQUIEvent extends EventObject {
    public static int BROKER_CONNECT = 0;
    public static int UPDATE_DATA_LIST = 1;
    public static int ITEM_SELECTED = 2;
    
    private int type = -1;
    private String addr;
    private String[] attrList = null;
    private Object selObj = null;

    public MQUIEvent(Object source) {
        super(source);
    }

    public MQUIEvent(Object source, int type) {
        super(source);
        this.type = type;
    }

    public int getType() {
        return (type);
    }
    
    public void setBrokerAddress(String addr)  {
        this.addr = addr;
    }

    public String getAddr() {
        return addr;
    }

    public String[] getAttrList() {
        return attrList;
    }

    public void setAttrList(String[] attrList) {
        this.attrList = attrList;
    }

    public void setSelectedObject(Object obj) {
        selObj = obj;
    }
    public Object getSelectedObject() {
        return (selObj);
    }
    
   
}
