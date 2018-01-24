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
 * @(#)PropertyValueComparator.java	1.5 06/28/07
 */ 

package com.sun.messaging.jmq.jmsselector;

import java.util.*;

/*
 * compares 2 objects determines relative order.
 * 
 * Used by JMSSelector class.
 * 
 * Uses Singleton
 * 
 */

/**
 * This class can be used when migrating to 1.2 and comparators
 */
public class PropertyValueComparator { //XX:JAVA2 implements Comparator {
    static volatile PropertyValueComparator  instance = null;
    public final static int UNKNOWN = -100;

    /**
     *
     */
    public static PropertyValueComparator getInstance() {
        if (instance == null) {
            synchronized(PropertyValueComparator.class) {
                instance = new PropertyValueComparator();
            }
        }
        return instance;
    }

    /**
     * 
     */
    public int compare(Object o1, Object o2) throws ClassCastException {
        int result = 0;

        if (o1 instanceof NumericValue) {
            o1 = ((NumericValue)o1).getValue();
        }
        if (o2 instanceof NumericValue) {
            o2 = ((NumericValue)o2).getValue();
        }
        try {
            if ((o1 == null) || (o2 == null)) {
                result = UNKNOWN;
            }
            else if (o1 instanceof Boolean) {
                if (o2 instanceof Boolean) {
                    if (!o1.equals(o2)) {
                        result = -1;
                    } else {
                        result = 0;
                    }
                } else {
                    throw new ClassCastException("PropertyValueComparator: Invalid types for comparison between " + o1 + " and " + o2);
                }
            } else if (o1 instanceof Byte) {
                if (o2 instanceof Byte) {
                    //XX:JAVA2
                    //result = ((Byte) o1).compareTo(o2);
                    byte bo1 = ((Byte)o1).byteValue();
                    byte bo2 = ((Byte)o2).byteValue();
                    result = (bo1 < bo2 ? -1 : (bo1 > bo2 ? 1 : 0));
                } else if (o2 instanceof Short) {
                    //XX:JAVA2
                    //result = (new Short(((Byte) o1).shortValue())).compareTo(o2);
                    short so1 = ((Byte)o1).shortValue();
                    short so2 = ((Short)o2).shortValue();
                    result = (so1 < so2 ? -1 : (so1 > so2 ? 1 : 0));
                } else if (o2 instanceof Integer) {
                    //XX:JAVA2
                    //result = (new Integer(((Byte) o1).intValue())).compareTo(o2);
                    int io1 = ((Byte)o1).intValue();
                    int io2 = ((Integer)o2).intValue();
                    result = (io1 < io2 ? -1 : (io1 > io2 ? 1 : 0));
                } else if (o2 instanceof Long) {
                    //XX:JAVA2
                    //result = (new Long(((Byte) o1).longValue())).compareTo(o2);
                    long lo1 = ((Byte)o1).longValue();
                    long lo2 = ((Long)o2).longValue();
                    result = (lo1 < lo2 ? -1 : (lo1 > lo2 ? 1 : 0));
                } else if (o2 instanceof Float) {
                    //XX:JAVA2
                    //result = (new Float(((Byte) o1).floatValue())).compareTo(o2);
                    float fo1 = ((Byte)o1).floatValue();
                    float fo2 = ((Float)o2).floatValue();
                    result = (fo1 < fo2 ? -1 : (fo1 > fo2 ? 1 : 0));
                } else if (o2 instanceof Double) {
                    //XX:JAVA2
                    //result = (new Double(((Byte) o1).doubleValue())).compareTo(o2);
                    double do1 = ((Byte)o1).doubleValue();
                    double do2 = ((Double)o2).doubleValue();
                    result = (do1 < do2 ? -1 : (do1 > do2 ? 1 : 0));
                } else {
                    throw new ClassCastException("PropertyValueComparator: Invalid types for comparison between " + o1 + " and " + o2);
                }
            } else if (o1 instanceof Short) {
                if (o2 instanceof Byte) {
                    //XX:JAVA2
                    //result = ((Short) o1).compareTo(new Short(((Byte) o2).shortValue()));
                    short so1 = ((Short)o1).shortValue();
                    short so2 = ((Byte)o2).shortValue();
                    result = (so1 < so2 ? -1 : (so1 > so2 ? 1 : 0));
                } else if (o2 instanceof Short) {
                    //XX:JAVA2
                    //result = ((Short) o1).compareTo(o2);
                    short so1 = ((Short)o1).shortValue();
                    short so2 = ((Short)o2).shortValue();
                    result = (so1 < so2 ? -1 : (so1 > so2 ? 1 : 0));
                } else if (o2 instanceof Integer) {
                    //XX:JAVA2
                    //result = (new Integer(((Short) o1).intValue())).compareTo(o2);
                    int io1 = ((Short)o1).intValue();
                    int io2 = ((Integer)o2).intValue();
                    result = (io1 < io2 ? -1 : (io1 > io2 ? 1 : 0));
                } else if (o2 instanceof Long) {
                    //XX:JAVA2
                    //result = (new Long(((Short) o1).longValue())).compareTo(o2);
                    long lo1 = ((Short)o1).longValue();
                    long lo2 = ((Long)o2).longValue();
                    result = (lo1 < lo2 ? -1 : (lo1 > lo2 ? 1 : 0));
                } else if (o2 instanceof Float) {
                    //XX:JAVA2
                    //result = (new Float(((Short) o1).floatValue())).compareTo(o2);
                    float fo1 = ((Short)o1).floatValue();
                    float fo2 = ((Float)o2).floatValue();
                    result = (fo1 < fo2 ? -1 : (fo1 > fo2 ? 1 : 0));
                } else if (o2 instanceof Double) {
                    //XX:JAVA2
                    //result = (new Double(((Short) o1).doubleValue())).compareTo(o2);
                    double do1 = ((Short)o1).doubleValue();
                    double do2 = ((Double)o2).doubleValue();
                    result = (do1 < do2 ? -1 : (do1 > do2 ? 1 : 0));
                } else {
                    throw new ClassCastException("PropertyValueComparator: Invalid types for comparison between " + o1 + " and " + o2);
                }
            } else if (o1 instanceof Integer) {
                if (o2 instanceof Byte) {
                    //XX:JAVA2
                    //result = ((Integer) o1).compareTo(new Integer(((Byte) o2).intValue()));
                    int so1 = ((Integer)o1).intValue();
                    int so2 = ((Byte)o2).intValue();
                    result = (so1 < so2 ? -1 : (so1 > so2 ? 1 : 0));
                } else if (o2 instanceof Short) {
                    //XX:JAVA2
                    //result = ((Integer) o1).compareTo(new Integer(((Short) o2).intValue()));
                    int io1 = ((Integer)o1).intValue();
                    int io2 = ((Short)o2).intValue();
                    result = (io1 < io2 ? -1 : (io1 > io2 ? 1 : 0));
                } else if (o2 instanceof Integer) {
                    //XX:JAVA2
                    //result = ((Integer) o1).compareTo(o2);
                    int io1 = ((Integer)o1).intValue();
                    int io2 = ((Integer)o2).intValue();
                    result = (io1 < io2 ? -1 : (io1 > io2 ? 1 : 0));
                } else if (o2 instanceof Long) {
                    //XX:JAVA2
                    //result = (new Long(((Integer) o1).longValue())).compareTo(o2);
                    long lo1 = ((Integer)o1).longValue();
                    long lo2 = ((Long)o2).longValue();
                    result = (lo1 < lo2 ? -1 : (lo1 > lo2 ? 1 : 0));
                } else if (o2 instanceof Float) {
                    //XX:JAVA2
                    //result = (new Float(((Integer) o1).floatValue())).compareTo(o2);
                    float fo1 = ((Integer)o1).floatValue();
                    float fo2 = ((Float)o2).floatValue();
                    result = (fo1 < fo2 ? -1 : (fo1 > fo2 ? 1 : 0));
                } else if (o2 instanceof Double) {
                    //XX:JAVA2
                    //result = (new Double(((Integer) o1).doubleValue())).compareTo(o2);
                    double do1 = ((Integer)o1).doubleValue();
                    double do2 = ((Double)o2).doubleValue();
                    result = (do1 < do2 ? -1 : (do1 > do2 ? 1 : 0));
                } else {
                    throw new ClassCastException("PropertyValueComparator: Invalid types for comparison between " + o1 + " and " + o2);
                }
            } else if (o1 instanceof Long) {
                if (o2 instanceof Byte) {
                    //XX:JAVA2
                    //result = ((Long) o1).compareTo(new Long(((Byte) o2).longValue()));
                    long lo1 = ((Long)o1).longValue();
                    long lo2 = ((Byte)o2).longValue();
                    result = (lo1 < lo2 ? -1 : (lo1 > lo2 ? 1 : 0));
                } else if (o2 instanceof Short) {
                    //XX:JAVA2
                    //result = ((Long) o1).compareTo(new Long(((Short) o2).longValue()));
                    long lo1 = ((Long)o1).longValue();
                    long lo2 = ((Short)o2).longValue();
                    result = (lo1 < lo2 ? -1 : (lo1 > lo2 ? 1 : 0));
                } else if (o2 instanceof Integer) {
                    //XX:JAVA2
                    //result = ((Long) o1).compareTo(new Long(((Integer) o2).longValue()));
                    long lo1 = ((Long)o1).longValue();
                    long lo2 = ((Integer)o2).longValue();
                    result = (lo1 < lo2 ? -1 : (lo1 > lo2 ? 1 : 0));
                } else if (o2 instanceof Long) {
                    //XX:JAVA2
                    //result = ((Long) o1).compareTo(o2);
                    long lo1 = ((Long)o1).longValue();
                    long lo2 = ((Long)o2).longValue();
                    result = (lo1 < lo2 ? -1 : (lo1 > lo2 ? 1 : 0));
                } else if (o2 instanceof Float) {
                    //XX:JAVA2
                    //result = (new Float(((Long) o1).floatValue())).compareTo(o2);
                    float fo1 = ((Long)o1).floatValue();
                    float fo2 = ((Float)o2).floatValue();
                    result = (fo1 < fo2 ? -1 : (fo1 > fo2 ? 1 : 0));
                } else if (o2 instanceof Double) {
                    //XX:JAVA2
                    //result = (new Double(((Long) o1).doubleValue())).compareTo(o2);
                    double do1 = ((Long)o1).doubleValue();
                    double do2 = ((Double)o2).doubleValue();
                    result = (do1 < do2 ? -1 : (do1 > do2 ? 1 : 0));
                } else {
                    throw new ClassCastException("PropertyValueComparator: Invalid types for comparison between " + o1 + " and " + o2);
                }
            } else if (o1 instanceof Float) {
                if (o2 instanceof Byte) {
                    //XX:JAVA2
                    //result = ((Float) o1).compareTo(new Float(((Byte) o2).floatValue()));
                    float fo1 = ((Float)o1).floatValue();
                    float fo2 = ((Byte)o2).floatValue();
                    result = (fo1 < fo2 ? -1 : (fo1 > fo2 ? 1 : 0));
                } else if (o2 instanceof Short) {
                    //XX:JAVA2
                    //result = ((Float) o1).compareTo(new Float(((Short) o2).floatValue()));
                    float fo1 = ((Float)o1).floatValue();
                    float fo2 = ((Short)o2).floatValue();
                    result = (fo1 < fo2 ? -1 : (fo1 > fo2 ? 1 : 0));
                } else if (o2 instanceof Integer) {
                    //XX:JAVA2
                    //result = ((Float) o1).compareTo(new Float(((Integer) o2).floatValue()));
                    float fo1 = ((Float)o1).floatValue();
                    float fo2 = ((Integer)o2).floatValue();
                    result = (fo1 < fo2 ? -1 : (fo1 > fo2 ? 1 : 0));
                } else if (o2 instanceof Long) {
                    //XX:JAVA2
                    //result = ((Float) o1).compareTo(new Float(((Long) o2).floatValue()));
                    float fo1 = ((Float)o1).floatValue();
                    float fo2 = ((Long)o2).floatValue();
                    result = (fo1 < fo2 ? -1 : (fo1 > fo2 ? 1 : 0));
                } else if (o2 instanceof Float) {
                    //XX:JAVA2
                    //result = ((Float) o1).compareTo(o2);
                    float fo1 = ((Float)o1).floatValue();
                    float fo2 = ((Float)o2).floatValue();
                    result = (fo1 < fo2 ? -1 : (fo1 > fo2 ? 1 : 0));
                } else if (o2 instanceof Double) {
                    //XX:JAVA2
                    //result = (new Double(((Float) o1).doubleValue())).compareTo(o2);
                    double do1 = ((Float)o1).doubleValue();
                    double do2 = ((Double)o2).doubleValue();
                    result = (do1 < do2 ? -1 : (do1 > do2 ? 1 : 0));
                } else {
                    throw new ClassCastException("PropertyValueComparator: Invalid types for comparison between " + o1 + " and " + o2);
                }
            } else if (o1 instanceof Double) {
                if (o2 instanceof Byte) {
                    //XX:JAVA2
                    //result = ((Double) o1).compareTo(new Double(((Byte) o2).doubleValue()));
                    double do1 = ((Double)o1).doubleValue();
                    double do2 = ((Byte)o2).doubleValue();
                    result = (do1 < do2 ? -1 : (do1 > do2 ? 1 : 0));
                } else if (o2 instanceof Short) {
                    //XX:JAVA2
                    //result = ((Double) o1).compareTo(new Double(((Short) o2).doubleValue()));
                    double do1 = ((Double)o1).doubleValue();
                    double do2 = ((Short)o2).doubleValue();
                    result = (do1 < do2 ? -1 : (do1 > do2 ? 1 : 0));
                } else if (o2 instanceof Integer) {
                    //XX:JAVA2
                    //result = ((Double) o1).compareTo(new Double(((Integer) o2).doubleValue()));
                    double do1 = ((Double)o1).doubleValue();
                    double do2 = ((Integer)o2).doubleValue();
                    result = (do1 < do2 ? -1 : (do1 > do2 ? 1 : 0));
                } else if (o2 instanceof Long) {
                    //XX:JAVA2
                    //result = ((Double) o1).compareTo(new Double(((Long) o2).doubleValue()));
                    double do1 = ((Double)o1).doubleValue();
                    double do2 = ((Long)o2).doubleValue();
                    result = (do1 < do2 ? -1 : (do1 > do2 ? 1 : 0));
                } else if (o2 instanceof Float) {
                    //XX:JAVA2
                    //result = ((Double) o1).compareTo(new Double(((Float) o2).doubleValue()));
                    double do1 = ((Double)o1).doubleValue();
                    double do2 = ((Float)o2).doubleValue();
                    result = (do1 < do2 ? -1 : (do1 > do2 ? 1 : 0));
                } else if (o2 instanceof Double) {
                    //XX:JAVA2
                    //result = ((Double) o1).compareTo(o2);
                    double do1 = ((Double)o1).doubleValue();
                    double do2 = ((Double)o2).doubleValue();
                    result = (do1 < do2 ? -1 : (do1 > do2 ? 1 : 0));
                } else {
                    throw new ClassCastException("PropertyValueComparator: Invalid types for comparison between " + o1 + " and " + o2);
                }
            } else if (o1 instanceof String) {
                result = ((String) o1).compareTo(o2.toString());
            } else {
                throw new ClassCastException("PropertyValueComparator: Invalid types for comparison between " + o1 + " and " + o2);
            }
        } catch ( /* NumberFormat */Exception e) {
            throw new ClassCastException("PropertyValueComparator: Invalid comparison between " + o1 + " and " + o2);
        }
        return result;
    }

    /**
     *
     */
    public boolean equals(Object obj) {
        if (obj instanceof com.sun.messaging.jmq.jmsselector.PropertyValueComparator) {
            return true;
        } else {
            return false;
        }
    }

    public int hashCode() {
        return super.hashCode();
    }

}
