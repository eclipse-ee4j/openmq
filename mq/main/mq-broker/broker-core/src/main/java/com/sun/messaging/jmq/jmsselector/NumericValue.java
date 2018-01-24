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
 * @(#)NumericValue.java	1.5 06/28/07
 */ 

package com.sun.messaging.jmq.jmsselector;

/**
 * Wrapper class for a NumericValue that provides math operations on numbers
 * 
 * This class is a necessary because Java does not support arithmetic operations
 * in java.lang.Number
 */
public class NumericValue {
    Number value = null;
    String image = null;
    int imageType = 0;
    
    private final static int ByteValue = 0;
    private final static int ShortValue = 1;
    private final static int IntValue = 2;
    private final static int FloatValue = 3;
    final static int LongValue = 4;
    final static int DoubleValue = 5;

    private final static int[][] returnTypes =  {
        {ByteValue, ShortValue, IntValue, FloatValue, LongValue, DoubleValue},
        {ShortValue, ShortValue, IntValue, FloatValue, LongValue, DoubleValue},
        {IntValue, IntValue, IntValue, FloatValue, LongValue, DoubleValue},
        {FloatValue, FloatValue, FloatValue, FloatValue, FloatValue, DoubleValue},
        {LongValue, LongValue, LongValue, FloatValue, LongValue, DoubleValue},
        {DoubleValue, DoubleValue, DoubleValue, DoubleValue, DoubleValue, DoubleValue}            
    };

    public NumericValue(Object obj) throws ClassCastException {
        //It is OK for a NumericValue to be null. If not null it must be a Number sub-class
        
        if (value != null && !(obj instanceof java.lang.Number)) {
            throw new ClassCastException("Cannot make a NumericValue from a " + obj.getClass().getName());
        }
        if (obj instanceof NumericValue) {
            NumericValue n = (NumericValue) obj;
            this.value = n.value;
            this.image = n.image;
            this.imageType = n.imageType;
        } else {
            this.value = (Number)obj;        
        }
    }
    
    public NumericValue(String image, int imageType) {
        this.image = image;
        this.imageType = imageType;
        this.value = null;
    }

    public Number getValue() {
        if (value == null && image != null) {
            switch (imageType) {
            case DoubleValue:
                value = new Double(image);
                break;
            case LongValue:
                //XX:JAVA2
                value = Long.decode(image);
                //XX:JAVA1.1
                //value = new Long(image);
                break;
            default:
            }
            image = null;
            imageType = -1;
        }

        return value;
    }
    
    private int getIndexForType(Number obj) {

        int index = -1;
        Class objClass = obj.getClass();
        if (objClass == Byte.class) {
            index = ByteValue;
        } else if (objClass == Short.class) {
            index = ShortValue;
        } else if (objClass == Integer.class) {
            index = IntValue;
        } else if (objClass == Float.class) {
            index = FloatValue;
        } else if (objClass == Long.class) {
            index = LongValue;
        } else if (objClass == Double.class) {
            index = DoubleValue;
        } else {
                System.err.println("Unexpected failure in getIndexForType objClass=" + 
                         obj.getClass().getName());
        }
    
        return index;
    }
    
    int getUnifiedTypeIndex(Number val1, Number val2) {
        int index = -1;
                    
        if ((val1 != null) && (val2 != null)) {
            int index1 = getIndexForType(val1);
            int index2 = getIndexForType(val2);
            index = returnTypes[index1][index2];
        }
        return index;
    }
    
    private Number convertNumber(Number val, int typeIndex) {
        switch (typeIndex) {
        case ByteValue:
            return Byte.valueOf(val.byteValue());
        case ShortValue:
            return Short.valueOf(val.shortValue());
        case IntValue:
            return Integer.valueOf(val.intValue());
        case LongValue:
            return Long.valueOf(val.longValue());
        case DoubleValue:
            return Double.valueOf(val.doubleValue());
        default:
            return null;
        }
    }


    //XX:JAVA2 can be removed in favor of Comparator.compare(obj1, obj2)
    public boolean between(NumericValue num1, NumericValue num2) {
        boolean result = false;

        if (getValue() != null && num1 != null && num2 != null) {
            Number value1 = num1.getValue();
            Number value2 = num2.getValue();
            int typeIndex1 = getUnifiedTypeIndex(value, value1);
            int typeIndex2 = getUnifiedTypeIndex(value, value2);
            /*
            if (typeIndex1 != -1) {
                System.err.println("between:num1 is of type" + indexToTypeMap[typeIndex1]);
            } else {
                System.err.println("between:num1 is undefined");
            }
            if (typeIndex1 != -1) {
                System.err.println("between:num2 is of type" + indexToTypeMap[typeIndex2]);
            } else {
                System.err.println("between:num2 is undefined");
            }
            */
            int typeIndex = (typeIndex1 >= typeIndex2 ? typeIndex1 : typeIndex2);

            //System.err.println("between:GCType is " + indexToTypeMap[typeIndex]);

            Number val = convertNumber(value, typeIndex);
            Number val1 = convertNumber(value1, typeIndex);
            Number val2 = convertNumber(value2, typeIndex);

            //System.err.println("value = " + value + " val1 = " + val1);
            //System.err.println("value2 = " + value2 + " val2 = " + val2);

            switch (typeIndex) {
                case ByteValue:
                    result = ((val.byteValue() >= val1.byteValue()) && (val.byteValue() <= val2.byteValue()) ||
                              (val.byteValue() <= val1.byteValue()) && (val.byteValue() >= val2.byteValue()));
                    break;
                case ShortValue:
                    result = ((val.shortValue() >= val1.shortValue()) && (val.shortValue() <= val2.shortValue()) ||
                              (val.shortValue() <= val1.shortValue()) && (val.shortValue() >= val2.shortValue()));
                    break;
                case IntValue:
                    result = ((val.intValue() >= val1.intValue()) && (val.intValue() <= val2.intValue()) ||
                              (val.intValue() <= val1.intValue()) && (val.intValue() >= val2.intValue()));
                    break;
                case FloatValue:
                    result = ((val.floatValue() >= val1.floatValue()) && (val.floatValue() <= val2.floatValue()) ||
                              (val.floatValue() <= val1.floatValue()) && (val.floatValue() >= val2.floatValue()));
                    break;
                case LongValue:
                    result = ((val.longValue() >= val1.longValue()) && (val.longValue() <= val2.longValue()) ||
                              (val.longValue() <= val1.longValue()) && (val.longValue() >= val2.longValue()));
                    break;
                case DoubleValue:
                    result = ((val.doubleValue() >= val1.doubleValue()) && (val.doubleValue() <= val2.doubleValue()) ||
                              (val.doubleValue() <= val1.doubleValue()) && (val.doubleValue() >= val2.doubleValue()));
                    break;
                default:
                    //Undefined value
                    break;
            }
        }
        
        return result;
    }

    public Number add(NumericValue num) {
        Number result = null;
        
        if (getValue() != null && num != null) {
            Number value2 = num.getValue();
            int typeIndex = getUnifiedTypeIndex(value, value2);
        
            Number val1 = convertNumber(value, typeIndex);
            Number val2 = convertNumber(value2, typeIndex);
        
            switch (typeIndex) {
                case ByteValue:
                    result = Byte.valueOf((byte)(val1.byteValue() + val2.byteValue()));
                    break;
                case ShortValue:
                    result = Short.valueOf((short)(val1.shortValue() + val2.shortValue()));
                    break;
                case IntValue:
                    result = Integer.valueOf(val1.intValue() + val2.intValue());
                    break;
                case FloatValue:
                    result = Float.valueOf(val1.floatValue() + val2.floatValue());
                    break;
                case LongValue:
                    result = Long.valueOf(val1.longValue() + val2.longValue());
                    break;
                case DoubleValue:
                    result = Double.valueOf(val1.doubleValue() + val2.doubleValue());
                    break;
                default:
                    //Undefined value
                    break;
            }
        }
        
        return result;
    }

    public Number subtract(NumericValue num) {
        Number result = null;

        if (getValue() != null && num != null) {
            Number value2 = num.getValue();
            int typeIndex = getUnifiedTypeIndex(value, value2);
        
            Number val1 = convertNumber(value, typeIndex);
            Number val2 = convertNumber(value2, typeIndex);
        
            switch (typeIndex) {
                case ByteValue:
                    result = Byte.valueOf((byte)(val1.byteValue() - val2.byteValue()));
                    break;
                case ShortValue:
                    result = Short.valueOf((short)(val1.shortValue() - val2.shortValue()));
                    break;
                case IntValue:
                    result = Integer.valueOf(val1.intValue() - val2.intValue());
                    break;
                case FloatValue:
                    result = Float.valueOf(val1.floatValue() - val2.floatValue());
                    break;
                case LongValue:
                    result = Long.valueOf(val1.longValue() - val2.longValue());
                    break;
                case DoubleValue:
                    result = Double.valueOf(val1.doubleValue() - val2.doubleValue());
                    break;
                default:
                    //Undefined value
                    break;
            }
        }
        return result;
    }

    public Number multiply(NumericValue num) {
        Number result = null;
                
        if (getValue() != null && num != null) {
            Number value2 = num.getValue();
            int typeIndex = getUnifiedTypeIndex(value, value2);
        
            Number val1 = convertNumber(value, typeIndex);
            Number val2 = convertNumber(value2, typeIndex);
        
            switch (typeIndex) {
                case ByteValue:
                    result = Byte.valueOf((byte)(val1.byteValue() * val2.byteValue()));
                    break;
                case ShortValue:
                    result = Short.valueOf((short)(val1.shortValue() * val2.shortValue()));
                    break;
                case IntValue:
                    result = Integer.valueOf(val1.intValue() * val2.intValue());
                    break;
                case FloatValue:
                    result = Float.valueOf(val1.floatValue() * val2.floatValue());
                    break;
                case LongValue:
                    result = Long.valueOf(val1.longValue() * val2.longValue());
                    break;
                case DoubleValue:
                    result = Double.valueOf(val1.doubleValue() * val2.doubleValue());
                    break;
                default:
                    //Undefined value
                    break;
            }
        }
        
        return result;
    }

    public Number divide(NumericValue num) {
        Number result = null;
                
        if (getValue() != null && num != null) {
            Number value2 = num.getValue();
            int typeIndex = getUnifiedTypeIndex(value, value2);
        
            Number val1 = convertNumber(value, typeIndex);
            Number val2 = convertNumber(value2, typeIndex);
        
            switch (typeIndex) {
                case ByteValue:
                    result = Byte.valueOf((byte)(val1.byteValue() / val2.byteValue()));
                    break;
                case ShortValue:
                    result = Short.valueOf((short)(val1.shortValue() / val2.shortValue()));
                    break;
                case IntValue:
                    result = Integer.valueOf(val1.intValue() / val2.intValue());
                    break;
                case FloatValue:
                    result = Float.valueOf(val1.floatValue() / val2.floatValue());
                    break;
                case LongValue:
                    result = Long.valueOf(val1.longValue() / val2.longValue());
                    break;
                case DoubleValue:
                    result = Double.valueOf(val1.doubleValue() / val2.doubleValue());
                    break;
                default:
                    //Undefined value
                    break;
            }
        }
        return result;
    }

    public Number negate() {
        Number result = null;
                    
        if (image != null) {
            image = "-" + image;
            return getValue();
        }

        if (getValue() != null) {
            int typeIndex = getIndexForType(value);
            
            switch (typeIndex) {
                case ByteValue:
                    result = Byte.valueOf((byte)(-value.byteValue()));
                    break;
                case ShortValue:
                    result = Short.valueOf((short)(-value.shortValue()));
                    break;
                case IntValue:
                    result = Integer.valueOf(-value.intValue());
                    break;
                case FloatValue:
                    result = Float.valueOf(-value.floatValue());
                    break;
                case LongValue:
                    result = Long.valueOf(-value.longValue());
                    break;
                case DoubleValue:
                    result = Double.valueOf(-value.doubleValue());
                    break;
                default:
                    //Undefined value
                    break;
            }
        }
        return result;
    }
    
    public String toString() {
        String str = "null";
        
        if (getValue() != null) {
            str = value.toString();
        }
        return str;
    }
    
/* public static void main(String[] args) {
.       NumericValue num1 = new NumericValue(new Float(3.65));
.       NumericValue num2 = new NumericValue(new Long(100L));
.       System.err.println(num1 + "+" + num2 + " = " + num1.add(num2));
.       System.err.println(num2 + "+" + num1 + " = " + num2.add(num1));
.
.       System.err.println(num1 + "-" + num2 + " = " + num1.subtract(num2));
.       System.err.println(num2 + "-" + num1 + " = " + num2.subtract(num1));
.       
.       System.err.println(num1 + "*" + num2 + " = " + num1.multiply(num2));
.       System.err.println(num2 + "*" + num1 + " = " + num2.multiply(num1));
.       
.       System.err.println(num1 + "/" + num2 + " = " + num1.divide(num2));
.       System.err.println(num2 + "/" + num1 + " = " + num2.divide(num1));
.       
.       System.err.println("-" + num1 + " = " + num1.negate());
.       System.err.println("-" + num2 + " = " + num2.negate());
.       
.
.       System.exit(0);
.   }
*/    

}
