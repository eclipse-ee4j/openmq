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

/*
 * @(#)OutOfLimitsException.java	1.3 06/29/07
 */ 

package com.sun.messaging.jmq.util.lists;

import java.util.*;
import java.io.*;

/**
 * this class is the exception which is thrown when a limit
 * on a limitable set/map is exceeded. It contains specifics
 * about the event that occurred (which can be used to localize
 * at higher levels)
 */


public class OutOfLimitsException extends IndexOutOfBoundsException
{
    public static final int CAPACITY_EXCEEDED = 0;
    public static final int BYTE_CAPACITY_EXCEEDED = 1;
    public static final int ITEM_SIZE_EXCEEDED = 2;
    public static final int PRIORITY_EXCEEDED = 3;

    Object limit = null;
    Object value = null;
    int type = -1;

    public OutOfLimitsException(int type, Object actual,
         Object limit) 
    {
        super(composeString(type,actual, limit));
        this.type = type;
        this.limit = limit;
        this.value = actual;
    }

    public Object getValue() {  
        return value;
    }

    public Object getLimit() {
        return limit;
    }

    public int getType() {
        return type;
    }

    public static final String toString(int type) {
        switch (type) {
              case CAPACITY_EXCEEDED:
                  return "Capacity Exceeded";
              case BYTE_CAPACITY_EXCEEDED:
                  return "Byte Capacity Exceeded";
              case ITEM_SIZE_EXCEEDED:
                  return "Item Size Exceeded";
              case PRIORITY_EXCEEDED:
                  return "Priority Exceeded";
              default:
                  return "Unknown Error";
       }
    }

    public static final String composeString(int type,
           Object actual, Object limit) {
         return toString(type) + " was " + actual
             + " expected " + limit;
    }

}
