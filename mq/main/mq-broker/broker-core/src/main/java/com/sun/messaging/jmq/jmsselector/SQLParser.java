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
 * @(#)SQLParser.java	1.9 06/28/07
 */ 

package com.sun.messaging.jmq.jmsselector;

import java.io.*;
import java.util.*;

/**
 * Subset of SQL-92 used by JMS message selectors. 
 *
 * <p>
 * SQL grammar contributed by kevinh@empower.com.au to the JAVACC web site
 */
public class SQLParser implements SQLParserConstants {

    Hashtable msg = null;
    JMSSelector selector;
    PropertyValueComparator comparator = PropertyValueComparator.getInstance();

    public SQLParser(JMSSelector selector, java.io.Reader stream) {
           this(stream);
           this.selector = selector;
    }


    public void setMessageHeader(Hashtable msg) {
           this.msg = msg;
    }

    /**
     *
     */
    public String getJMSDeliveryModeAsString(Hashtable msg) {
        //int deliveryMode = Message.DEFAULT_DELIVERY_MODE;
        int deliveryMode = 2; //static in Message
        //try {
            //deliveryMode = ((Integer)msg.get("JMSDeliveryMode")).intValue();
            deliveryMode = selector.getJMSDeliveryMode();
        //} catch (Exception e) {
            //deliveryMode will be Message.DEFAULT_DELIVERY_MODE
        //}

        String  strDeliveryMode = "";

        switch (deliveryMode) {
            //case javax.jms.DeliveryMode.PERSISTENT: 
            case 2:
                strDeliveryMode = "PERSISTENT";
                break;
            //case javax.jms.DeliveryMode.NON_PERSISTENT: 
            case 1:
                strDeliveryMode = "NON_PERSISTENT";
                break;
            default:
                //Set PERSISTENT DEFAULT
                strDeliveryMode = "PERSISTENT";
                //throw new JMSException("Invalid message delivery mode " + deliveryMode);
        }
        return strDeliveryMode;
    }


    /**
     * Return true iff 'name' might be a JMS Message Header.
     */
    boolean mightBeMessageHeader(String name) {
        try {
            return (name.startsWith("JMS") && name.charAt(3) != 'X');
            // Handle boundary case that colName is just "JMS".
        } catch (IndexOutOfBoundsException ex) {
            // Handle boundary case that colName is just "JMS".
        }
        return false;
    }

    boolean messageHeaderNotValidInSelector(String name) {
        return mightBeMessageHeader(name) &&
            (name.equals("JMSDestination") || name.equals("JMSExpiration") ||
             name.equals("JMSRedelivered") || name.equals("JMSReplyTo"));
    }

/*******************************************************************
 * The SQL-92 grammar starts here
 *******************************************************************/
  final public boolean match(Hashtable msg) throws ParseException, NullMessageHeaderException {
boolean matchResult = false;
Object res = null;
      if (msg == null)
         {if (true) throw new NullMessageHeaderException();}
      else
         this.msg = msg;
    res = SQLOrExpr();
        if (res != null) {
            if (! (res instanceof java.lang.Boolean)) {
                {if (true) throw new ParseException("Selector must evaluate to a java.lang.Boolean. Instead evaluated to a " + res.getClass().getName());}
            }

            matchResult = ((Boolean)res).booleanValue();
        }
        {if (true) return matchResult;}
    throw new Error("Missing return statement in function");
  }

  final public Object SQLOrExpr() throws ParseException {
    Object res1=null;
    Object res2=null;
    res1 = SQLAndExpr();
    label_1:
    while (true) {
      switch ((jj_ntk==-1)?jj_ntk():jj_ntk) {
      case OR:
        ;
        break;
      default:
        jj_la1[0] = jj_gen;
        break label_1;
      }
      jj_consume_token(OR);
      res2 = SQLAndExpr();
            if ((res1 != null && !(res1 instanceof java.lang.Boolean)) ||
                (res2 != null && !(res2 instanceof java.lang.Boolean))) {
                {if (true) throw new ParseLogicalOperandException("OR");}
            }

            if (res1 != null && res2 != null) {
                res1 = Boolean.valueOf(((Boolean)res1).booleanValue() || ((Boolean)res2).booleanValue());
            } else if (res1 == null && res2 == null) {
                // Unknown || Unknown = Unknown
                res1 = null;
            } else {
                //One of the OR operands is unknown
                Boolean oneKnownValue = (Boolean)(res1 == null ? res2 : res1);
                if (oneKnownValue.booleanValue()) {
                    //True || Unknown = True
                    res1 = oneKnownValue;
                } else {
                    //False || Unknown = Unknown
                    res1 = null;
                }
            }
    }
        {if (true) return (res1);}
    throw new Error("Missing return statement in function");
  }

  final public Object SQLAndExpr() throws ParseException {
    Object res1=null;
    Object res2=null;
    res1 = SQLNotExpr();
    label_2:
    while (true) {
      switch ((jj_ntk==-1)?jj_ntk():jj_ntk) {
      case AND:
        ;
        break;
      default:
        jj_la1[1] = jj_gen;
        break label_2;
      }
      jj_consume_token(AND);
      res2 = SQLNotExpr();
            if ((res1 != null && !(res1 instanceof java.lang.Boolean)) ||
                (res2 != null && !(res2 instanceof java.lang.Boolean))) {
                //AND operator requires java.lang.Boolean for opearnds
                {if (true) throw new ParseLogicalOperandException("AND");}
            }

            if (res1 != null && res2 != null) {
                res1 = Boolean.valueOf(((Boolean)res1).booleanValue() && ((Boolean)res2).booleanValue());
            } else if (res1 == null && res2 == null) {
                //Unknown && Unknown = Unknown
                res1 = null;
            } else {
                //One of the OR operands is unknown
                Boolean oneKnownValue = (Boolean)(res1 == null ? res2 : res1);
                if (oneKnownValue.booleanValue()) {
                    //True && Unknown = Unknown
                    res1 = null;
                } else {
                    //False && Unknown = False
                    res1 = oneKnownValue;
                }
            }
    }
            {if (true) return (res1);}
    throw new Error("Missing return statement in function");
  }

  final public Object SQLNotExpr() throws ParseException {
    boolean isNot=false;
    Object res = null;
    Object obj = null;
    if (jj_2_1(2)) {
      jj_consume_token(NOT);
        isNot = true;
    } else {
      ;
    }
    res = SQLCompareExpr();
        if (isNot) {
            if (res == null) {
                //NOT unknown is unknown
                {if (true) return res;}
            } else {
                if (!(res instanceof java.lang.Boolean)) {
                    //The NOT operator requires a java.lang.Boolean for its operand
                    {if (true) throw new ParseLogicalOperandException("NOT");}
                }
            }

            res = Boolean.valueOf(! (((Boolean)res).booleanValue()));
        }
        {if (true) return res;}
    throw new Error("Missing return statement in function");
  }

  final public Object SQLCompareExpr() throws ParseException {
    Object res = null;
    if (jj_2_2(2)) {
      res = SQLIsClause();
    } else {
      switch ((jj_ntk==-1)?jj_ntk():jj_ntk) {
      case INTEGER_LITERAL:
      case FLOATING_POINT_LITERAL:
      case STRING_LITERAL:
      case BOOLEAN_LITERAL:
      case ID:
      case OPENPAREN:
      case PLUS:
      case MINUS:
        res = SQLSumExpr();
        switch ((jj_ntk==-1)?jj_ntk():jj_ntk) {
        case BETWEEN:
        case IN:
        case LIKE:
        case NOT:
        case LESS:
        case LESSEQUAL:
        case GREATER:
        case GREATEREQUAL:
        case EQUAL:
        case NOTEQUAL:
          res = SQLCompareExprRight(res);
          break;
        default:
          jj_la1[2] = jj_gen;
          ;
        }
        break;
      default:
        jj_la1[3] = jj_gen;
        jj_consume_token(-1);
        throw new ParseException();
      }
    }
        {if (true) return res;}
    throw new Error("Missing return statement in function");
  }

  final public Boolean SQLCompareExprRight(Object obj1) throws ParseException {
    Boolean res = null;
    Object obj2 = null;
        if (obj1 == null) {
//            return null; //Comparisons with unknowns yield unknown
        }
    if (jj_2_3(2)) {
      res = SQLLikeClause(obj1);
    } else if (jj_2_4(4)) {
      res = SQLInClause(obj1);
    } else if (jj_2_5(2)) {
      res = SQLBetweenClause(obj1);
    } else if (jj_2_6(2)) {
      jj_consume_token(EQUAL);
      obj2 = SQLSumExpr();
            if (obj1 == null) {
                {if (true) return null;}
            }
            res = Boolean.valueOf((comparator.compare(obj1, obj2) == 0));
    } else if (jj_2_7(2)) {
      jj_consume_token(NOTEQUAL);
      obj2 = SQLSumExpr();
            if (obj1 == null) {
                {if (true) return null;}
            }
            res = Boolean.valueOf((comparator.compare(obj1, obj2) != 0));
    } else if (jj_2_8(2)) {
      jj_consume_token(GREATER);
      obj2 = SQLSumExpr();
            if ((obj1 instanceof String) || (obj2 instanceof String) ||
                (obj1 instanceof Boolean) || (obj2 instanceof Boolean) ) {
                // > Operator cannot be used with String or Boolean operands
                {if (true) throw new ParseComparisonOperandException(">");}
            }
            if (obj1 == null) {
                {if (true) return null;}
            }
            res = Boolean.valueOf((comparator.compare(obj1, obj2) > 0));
    } else if (jj_2_9(2)) {
      jj_consume_token(GREATEREQUAL);
      obj2 = SQLSumExpr();
            if ((obj1 instanceof String) || (obj2 instanceof String) ||
                (obj1 instanceof Boolean) || (obj2 instanceof Boolean) ) {
                // >= Operator cannot be used with String or Boolean operands
                {if (true) throw new ParseComparisonOperandException(">=");}
            }
            if (obj1 == null) {
                {if (true) return null;}
            }
            res = Boolean.valueOf((comparator.compare(obj1, obj2) >= 0));
    } else if (jj_2_10(2)) {
      jj_consume_token(LESS);
      obj2 = SQLSumExpr();
            if ((obj1 instanceof String) || (obj2 instanceof String) ||
                (obj1 instanceof Boolean) || (obj2 instanceof Boolean) ) {
                // < Operator cannot be used with String or Boolean operands
                {if (true) throw new ParseComparisonOperandException("<");}
            }
            if (obj1 == null) {
                {if (true) return null;}
            }
            //Need to handle the case where comparator may return unknown as a negative value
            int i = comparator.compare(obj1, obj2);
            if (i != PropertyValueComparator.UNKNOWN) {
                res = Boolean.valueOf(i < 0);
            }
    } else if (jj_2_11(2)) {
      jj_consume_token(LESSEQUAL);
      obj2 = SQLSumExpr();
            if ((obj1 instanceof String) || (obj2 instanceof String) ||
                (obj1 instanceof Boolean) || (obj2 instanceof Boolean) ) {
                // <= Operator cannot be used with String or Boolean operands
                {if (true) throw new ParseComparisonOperandException("<=");}
            }
            if (obj1 == null) {
                {if (true) return null;}
            }
            //Need to handle the case where comparator may return unknown as a negative value
            int i = comparator.compare(obj1, obj2);
            if (i != PropertyValueComparator.UNKNOWN) {
                res = Boolean.valueOf(i <= 0);
            }
    } else {
      jj_consume_token(-1);
      throw new ParseException();
    }
        {if (true) return res;}
    throw new Error("Missing return statement in function");
  }

  final public Object SQLSumExpr() throws ParseException {
    Object res1 = null;
    Object res2 = null;
    NumericValue num1 = null;
    NumericValue num2 = null;
    boolean doAdd = true;
    res1 = SQLProductExpr();
    label_3:
    while (true) {
      switch ((jj_ntk==-1)?jj_ntk():jj_ntk) {
      case PLUS:
      case MINUS:
        ;
        break;
      default:
        jj_la1[4] = jj_gen;
        break label_3;
      }
      switch ((jj_ntk==-1)?jj_ntk():jj_ntk) {
      case PLUS:
        jj_consume_token(PLUS);
                     doAdd = true;
        break;
      case MINUS:
        jj_consume_token(MINUS);
                       doAdd = false;
        break;
      default:
        jj_la1[5] = jj_gen;
        jj_consume_token(-1);
        throw new ParseException();
      }
      res2 = SQLProductExpr();
                num1 = new NumericValue(res1);
                num2 = new NumericValue(res2);

                if (doAdd) {
                    res1 = num1.add(num2);
                   //System.err.println(num1 + "+" + num2 + " = " + res1);
                }
                else {
                    res1 = num1.subtract(num2);
                    //System.err.println(num1 + "-" + num2 + " = " + res1);
                }
    }
        {if (true) return res1;}
    throw new Error("Missing return statement in function");
  }

  final public Object SQLProductExpr() throws ParseException {
    Object res1 = null;
    Object res2 = null;
    NumericValue num1 = null;
    NumericValue num2 = null;
    boolean doMultiply = true;
    res1 = SQLUnaryExpr();
    label_4:
    while (true) {
      switch ((jj_ntk==-1)?jj_ntk():jj_ntk) {
      case ASTERISK:
      case SLASH:
        ;
        break;
      default:
        jj_la1[6] = jj_gen;
        break label_4;
      }
      switch ((jj_ntk==-1)?jj_ntk():jj_ntk) {
      case ASTERISK:
        jj_consume_token(ASTERISK);
                     doMultiply = true;
        break;
      case SLASH:
        jj_consume_token(SLASH);
                       doMultiply = false;
        break;
      default:
        jj_la1[7] = jj_gen;
        jj_consume_token(-1);
        throw new ParseException();
      }
      res2 = SQLUnaryExpr();
                num1 = new NumericValue(res1);
                num2 = new NumericValue(res2);

                if (doMultiply) {
                    res1 = num1.multiply(num2);
                   //System.err.println(num1 + "*" + num2 + " = " + res1);
                }
                else {
                    res1 = num1.divide(num2);
                    //System.err.println(num1 + "/" + num2 + " = " + res1);
                }
    }
        {if (true) return res1;}
    throw new Error("Missing return statement in function");
  }

  final public Object SQLUnaryExpr() throws ParseException {
    Object res1 = null;
    NumericValue num1 = null;
    boolean negate = false;
    switch ((jj_ntk==-1)?jj_ntk():jj_ntk) {
    case PLUS:
    case MINUS:
      switch ((jj_ntk==-1)?jj_ntk():jj_ntk) {
      case PLUS:
        jj_consume_token(PLUS);
        break;
      case MINUS:
        jj_consume_token(MINUS);
                       negate = true;
        break;
      default:
        jj_la1[8] = jj_gen;
        jj_consume_token(-1);
        throw new ParseException();
      }
      break;
    default:
      jj_la1[9] = jj_gen;
      ;
    }
    res1 = SQLTerm();
        if (negate) {
            num1 = new NumericValue(res1);
            res1 = num1.negate();
        }
        {if (true) return res1;}
    throw new Error("Missing return statement in function");
  }

  final public String SQLColRef() throws ParseException {
  Token x;
  String colName = "";
    x = jj_consume_token(ID);
        colName = x.image;
        //Prevent JMS Message headers that are not allowed to be used
        if (messageHeaderNotValidInSelector(colName))
        {
             //Cannot use JMS property 'colName' in a selector
             {if (true) throw new ParseInvalidJMSPropertyInSelectorException(colName);}
        }

         {if (true) return colName;}
    throw new Error("Missing return statement in function");
  }

  final public Object SQLTerm() throws ParseException {
    String colName;
    Object res=null;
    switch ((jj_ntk==-1)?jj_ntk():jj_ntk) {
    case OPENPAREN:
      jj_consume_token(OPENPAREN);
      res = SQLOrExpr();
      jj_consume_token(CLOSEPAREN);
        {if (true) return res;}
      break;
    case INTEGER_LITERAL:
    case FLOATING_POINT_LITERAL:
    case STRING_LITERAL:
    case BOOLEAN_LITERAL:
      res = SQLLiteral();
        {if (true) return res;}
      break;
    case ID:
      colName = SQLColRef();
        try {
            // Specialized processing needed for JMS Message Headers.
            boolean mightBeMsgHeader = mightBeMessageHeader(colName);
            if (mightBeMsgHeader && colName.equals("JMSDeliveryMode")) {
                res = getJMSDeliveryModeAsString(msg);
            } else if (mightBeMsgHeader && colName.equals("JMSType")) {
                //res = (String)msg.get("JMSType");
                res = selector.getJMSType();
                if (res == null) {
                    res = "";
                }
            } else if (mightBeMsgHeader && colName.equals("JMSMessageID")) {
                //res = (String)msg.get("JMSMessageID");
                res = selector.getJMSMessageID();
                if (res == null) {
                    res = "";
                }
            } else if (mightBeMsgHeader && colName.equals("JMSPriority")) {
                //res = (Integer)(msg.get("JMSPriority")); //will be promoted to Long below
                res = Integer.valueOf(selector.getJMSPriority()); //will be promoted to Long below
            } else if (mightBeMsgHeader && colName.equals("JMSTimestamp")) {
                //res = (Long)(msg.get("JMSTimestamp"));
                res = Long.valueOf(selector.getJMSTimestamp());
            } else if (mightBeMsgHeader && colName.equals("JMSCorrelationID")) {
                //res = (String)msg.get("JMSCorrelationID");
                res = selector.getJMSCorrelationID();
                if (res == null) {
                    res = "";
                }
            } else {
              //Process colName as a plain property.
              res = msg.get(colName);

              //For Numbers, only deal with Long and Double. Promote as needed
              if ((res instanceof java.lang.Byte) ||
                   (res instanceof java.lang.Short) ||
                   (res instanceof java.lang.Integer)) {

                  res = Long.valueOf(((Number)res).longValue());
              }
              else if (res instanceof java.lang.Float) {
                  res = Double.valueOf(((Number)res).doubleValue());
              }
            }
        }
        catch (Exception e) {
            e.printStackTrace();
        }
        {if (true) return res;}
      break;
    default:
      jj_la1[10] = jj_gen;
      jj_consume_token(-1);
      throw new ParseException();
    }
    throw new Error("Missing return statement in function");
  }

  final public Object SQLLiteral() throws ParseException {
    Token x=null;
    Object obj=null;
    try {
      switch ((jj_ntk==-1)?jj_ntk():jj_ntk) {
      case STRING_LITERAL:
        x = jj_consume_token(STRING_LITERAL);
            //Strip leading and trailing quotes
            //obj = x.image.substring(1, x.image.length()-1);        
            obj = selector.processStringLiteral(x.image);
        break;
      case INTEGER_LITERAL:
        x = jj_consume_token(INTEGER_LITERAL);
                              //{obj = new Long(x.image);}
                                obj = new NumericValue(x.image,
                                                       NumericValue.LongValue);
        break;
      case FLOATING_POINT_LITERAL:
        x = jj_consume_token(FLOATING_POINT_LITERAL);
                                     //{obj = new Double(x.image);}
                 obj = new NumericValue(x.image, NumericValue.DoubleValue);
        break;
      case BOOLEAN_LITERAL:
        x = jj_consume_token(BOOLEAN_LITERAL);
                             obj = Boolean.valueOf(x.image.toLowerCase());
        break;
      default:
        jj_la1[11] = jj_gen;
        jj_consume_token(-1);
        throw new ParseException();
      }
      {if (true) return obj;}
    } catch (Exception e) {
    //Cant happen
    e.printStackTrace();
    {if (true) throw generateParseException();}
    }
    throw new Error("Missing return statement in function");
  }

  final public Boolean SQLLikeClause(Object obj1) throws ParseException {
    Boolean res = null;
    boolean isLike = false;
    boolean isNot=false;
    String colName, propVal=null, pattern;
    char escapeChar=0;
    switch ((jj_ntk==-1)?jj_ntk():jj_ntk) {
    case NOT:
      jj_consume_token(NOT);
              isNot = true;
      break;
    default:
      jj_la1[12] = jj_gen;
      ;
    }
    jj_consume_token(LIKE);
    pattern = SQLPattern();
    switch ((jj_ntk==-1)?jj_ntk():jj_ntk) {
    case ESCAPE:
      jj_consume_token(ESCAPE);
      escapeChar = EscapeChar();
      break;
    default:
      jj_la1[13] = jj_gen;
      ;
    }
        if (pattern != null && ! (pattern instanceof java.lang.String)) {
            {if (true) throw new ParseException("The LIKE target must be a string." +
                           " Found " + pattern.getClass());}
        }

        if (obj1 instanceof java.lang.String) {
            isLike = selector.matchPattern(pattern, (String)obj1, escapeChar);

            if (isNot) {
                isLike = !isLike;
            }
            res = Boolean.valueOf(isLike);
      }
      {if (true) return res;}
    throw new Error("Missing return statement in function");
  }

  final public String SQLPattern() throws ParseException {
    Token x;
    String res;
    x = jj_consume_token(STRING_LITERAL);
                           res = x.image;
        //Strip leading and trailing quotes
        //res = res.substring(1, res.length()-1);
        {if (true) return selector.processStringLiteral(res);}  //res; 

    throw new Error("Missing return statement in function");
  }

  final public char EscapeChar() throws ParseException {
    Token x;
    String escapeCharStr = null;
    char escapeChar;
    x = jj_consume_token(STRING_LITERAL);
                             escapeCharStr = x.image;
        //Must be a single char String
        if (escapeCharStr.length() != 3) {
            //Escape is a single character
            {if (true) throw new ParseESCAPENotASingleCharacterException(escapeCharStr);}
        }

        escapeChar = escapeCharStr.charAt(1);
        {if (true) return escapeChar;}
    throw new Error("Missing return statement in function");
  }

  final public Boolean SQLIsClause() throws ParseException {
    String colName;
    boolean isNull=false;
    boolean notNull = false;
    Boolean res=null;
    colName = SQLColRef();
    jj_consume_token(IS);
    switch ((jj_ntk==-1)?jj_ntk():jj_ntk) {
    case NOT:
      jj_consume_token(NOT);
            notNull = true;
      break;
    default:
      jj_la1[14] = jj_gen;
      ;
    }
    jj_consume_token(NULL);
      try {
          boolean mightBeMsgHeader = mightBeMessageHeader(colName);
          if (mightBeMsgHeader && colName.equals("JMSDeliveryMode")) {
              isNull = false; //JMSDeliveryMode always has a default value so never considreed null
          } else if (mightBeMsgHeader && colName.equals("JMSMessageID")) {
              //isNull = (msg.get("JMSMessageID") == null);
              //isNull = (selector.getJMSMessageID() == null);
              isNull = false; //MessageID always generated by pkt so never considered null.
          } else if (mightBeMsgHeader && colName.equals("JMSPriority")) {
              isNull = false; //JMSPriority always has a default value so never considered null.
          } else if (mightBeMsgHeader && colName.equals("JMSTimestamp")) {
              isNull = false; //JMSTimestamp always generated by pkt so never considered null.
          } else if (mightBeMsgHeader && colName.equals("JMSCorrelationID")) {
              //isNull = (msg.get("JMSCorrelationID") == null);
              isNull = (selector.getJMSCorrelationID() == null);
          } else if (mightBeMsgHeader && colName.equals("JMSType")) {
              //isNull = (msg.get("JMSType") == null);
              isNull = (selector.getJMSType() == null);
          } else {
              isNull = (!msg.containsKey(colName));
          }
      }
      catch (Exception e) {
          e.printStackTrace();
          {if (true) throw generateParseException();}
      }
      if (notNull) {
        isNull = !isNull;
      }
      res = Boolean.valueOf(isNull);
      {if (true) return res;}
    throw new Error("Missing return statement in function");
  }

  final public Boolean SQLInClause(Object obj1) throws ParseException {
    boolean found=false;
    boolean negate = false;
    Boolean res=null;
    Vector list = null;
    Object element = null;
    switch ((jj_ntk==-1)?jj_ntk():jj_ntk) {
    case NOT:
      jj_consume_token(NOT);
              negate = true;
      break;
    default:
      jj_la1[15] = jj_gen;
      ;
    }
    jj_consume_token(IN);
    jj_consume_token(OPENPAREN);
    list = SQLLValueList();
    jj_consume_token(CLOSEPAREN);
        if (list != null && !list.isEmpty()) {
            Enumeration e = list.elements();
            try {
                while (e.hasMoreElements()) {
                    element = e.nextElement();
                    //String str = (String)element;
                }
            } catch (ClassCastException cce) {
                {if (true) throw new ParseException("All TARGETS of a IN clause "  +
                     "must be a String. Found a " + element.getClass());}
            }

            if (obj1 == null) {
                {if (true) return null;}
            }
            if (! (obj1 instanceof java.lang.String)) {
                {if (true) throw new ParseException("Source of IN clause must be " +
                           "a String. Found a " + obj1.getClass().getName());}
            }
            found = list.contains(obj1);
        }

        if (negate) {
            found = !found;
        }

        res = Boolean.valueOf(found);
        {if (true) return res;}
    throw new Error("Missing return statement in function");
  }

  final public Vector SQLLValueList() throws ParseException {
    Object elem = null;
    Vector list = new Vector();
    elem = SQLLValueElement();
                                list.addElement(elem);
    label_5:
    while (true) {
      switch ((jj_ntk==-1)?jj_ntk():jj_ntk) {
      case 36:
        ;
        break;
      default:
        jj_la1[16] = jj_gen;
        break label_5;
      }
      jj_consume_token(36);
      elem = SQLLValueElement();
                                      list.addElement(elem);
    }
        {if (true) return list;}
    throw new Error("Missing return statement in function");
  }

  final public Object SQLLValueElement() throws ParseException {
    Object res = null;
    switch ((jj_ntk==-1)?jj_ntk():jj_ntk) {
    case NULL:
      jj_consume_token(NULL);
      break;
    default:
      jj_la1[17] = jj_gen;
      if (jj_2_12(3)) {
        res = SQLSumExpr();
      } else if (jj_2_13(3)) {
        res = SQLOrExpr();
      } else {
        jj_consume_token(-1);
        throw new ParseException();
      }
    }
        if (res instanceof NumericValue) {
            res = ((NumericValue)res).getValue();
        }
        {if (true) return res;}
    throw new Error("Missing return statement in function");
  }

  final public Boolean SQLBetweenClause(Object obj1) throws ParseException {
    boolean between=false;
    boolean negate = false;
    NumericValue res = null;
    Object res1 = null;
    Object res2 = null;
    Vector list = null;
    switch ((jj_ntk==-1)?jj_ntk():jj_ntk) {
    case NOT:
      jj_consume_token(NOT);
               negate = true;
      break;
    default:
      jj_la1[18] = jj_gen;
      ;
    }
    jj_consume_token(BETWEEN);
    res1 = SQLSumExpr();
    jj_consume_token(AND);
    res2 = SQLSumExpr();
        //Use java.lang.Comparable when min JDK = 1.2
        if (obj1 != null && !(obj1 instanceof java.lang.Number)) {
            {if (true) throw new ParseException("The LValue for BETWEEN must be a java.lang.Number. Found " + obj1);}
        }

        if (res1 != null &&
            (res1 instanceof String || res1 instanceof Boolean)) {
            {if (true) throw new ParseException("The START target for BETWEEN must "
                           + "be a numeric value. Found " + res1.getClass());}
        }

        if (res2 != null &&
            (res2 instanceof String || res2 instanceof Boolean)) {
            {if (true) throw new ParseException("The END target for BETWEEN must "
                           + "be a numeric value. Found " + res2.getClass());}
        }

        if (obj1 == null || res1 == null || res2 == null) {

            // if any operand is unknown, result of expression is unknown.
            {if (true) return null;}
        }

        //XXX GT: can be used when min JDK = 1.2
        //if (!(obj1 instanceof java.lang.Comparable)) {
        //    throw new ParseException("The LValue for BETWEEN must be a  java.lang.Comparable. Found " + obj1);
        //}
        // 
        //if ( (((Comparable)obj1).compareTo(res1) >= 0) && (((Comparable)obj1).compareTo(res2) <= 0) ) {
        //    between = true;
        //}
        ////Using the following five lines for now.
        if (!(obj1 instanceof java.lang.Number)) {
            //LValue for BETWEEN must be a java.lang.Number
            {if (true) throw new ParseBetweenLValueException(obj1.toString());}
        }
        res = new NumericValue(obj1);
        between = res.between(new NumericValue(res1), new NumericValue(res2));
        ////between set above from NumericValue.between() instead of Comparable.compareTo()

        if (negate) {
            between = !between;
        }

        res1= Boolean.valueOf(between);
        {if (true) return (Boolean)res1;}
    throw new Error("Missing return statement in function");
  }

  final private boolean jj_2_1(int xla) {
    jj_la = xla; jj_lastpos = jj_scanpos = token;
    boolean retval = !jj_3_1();
    jj_save(0, xla);
    return retval;
  }

  final private boolean jj_2_2(int xla) {
    jj_la = xla; jj_lastpos = jj_scanpos = token;
    boolean retval = !jj_3_2();
    jj_save(1, xla);
    return retval;
  }

  final private boolean jj_2_3(int xla) {
    jj_la = xla; jj_lastpos = jj_scanpos = token;
    boolean retval = !jj_3_3();
    jj_save(2, xla);
    return retval;
  }

  final private boolean jj_2_4(int xla) {
    jj_la = xla; jj_lastpos = jj_scanpos = token;
    boolean retval = !jj_3_4();
    jj_save(3, xla);
    return retval;
  }

  final private boolean jj_2_5(int xla) {
    jj_la = xla; jj_lastpos = jj_scanpos = token;
    boolean retval = !jj_3_5();
    jj_save(4, xla);
    return retval;
  }

  final private boolean jj_2_6(int xla) {
    jj_la = xla; jj_lastpos = jj_scanpos = token;
    boolean retval = !jj_3_6();
    jj_save(5, xla);
    return retval;
  }

  final private boolean jj_2_7(int xla) {
    jj_la = xla; jj_lastpos = jj_scanpos = token;
    boolean retval = !jj_3_7();
    jj_save(6, xla);
    return retval;
  }

  final private boolean jj_2_8(int xla) {
    jj_la = xla; jj_lastpos = jj_scanpos = token;
    boolean retval = !jj_3_8();
    jj_save(7, xla);
    return retval;
  }

  final private boolean jj_2_9(int xla) {
    jj_la = xla; jj_lastpos = jj_scanpos = token;
    boolean retval = !jj_3_9();
    jj_save(8, xla);
    return retval;
  }

  final private boolean jj_2_10(int xla) {
    jj_la = xla; jj_lastpos = jj_scanpos = token;
    boolean retval = !jj_3_10();
    jj_save(9, xla);
    return retval;
  }

  final private boolean jj_2_11(int xla) {
    jj_la = xla; jj_lastpos = jj_scanpos = token;
    boolean retval = !jj_3_11();
    jj_save(10, xla);
    return retval;
  }

  final private boolean jj_2_12(int xla) {
    jj_la = xla; jj_lastpos = jj_scanpos = token;
    boolean retval = !jj_3_12();
    jj_save(11, xla);
    return retval;
  }

  final private boolean jj_2_13(int xla) {
    jj_la = xla; jj_lastpos = jj_scanpos = token;
    boolean retval = !jj_3_13();
    jj_save(12, xla);
    return retval;
  }

  final private boolean jj_3R_8() {
    Token xsp;
    xsp = jj_scanpos;
    if (jj_3R_15()) jj_scanpos = xsp;
    else if (jj_la == 0 && jj_scanpos == jj_lastpos) return false;
    if (jj_scan_token(IN)) return true;
    if (jj_la == 0 && jj_scanpos == jj_lastpos) return false;
    if (jj_scan_token(OPENPAREN)) return true;
    if (jj_la == 0 && jj_scanpos == jj_lastpos) return false;
    if (jj_3R_16()) return true;
    if (jj_la == 0 && jj_scanpos == jj_lastpos) return false;
    if (jj_scan_token(CLOSEPAREN)) return true;
    if (jj_la == 0 && jj_scanpos == jj_lastpos) return false;
    return false;
  }

  final private boolean jj_3_9() {
    if (jj_scan_token(GREATEREQUAL)) return true;
    if (jj_la == 0 && jj_scanpos == jj_lastpos) return false;
    if (jj_3R_10()) return true;
    if (jj_la == 0 && jj_scanpos == jj_lastpos) return false;
    return false;
  }

  final private boolean jj_3R_18() {
    if (jj_3R_24()) return true;
    if (jj_la == 0 && jj_scanpos == jj_lastpos) return false;
    Token xsp;
    while (true) {
      xsp = jj_scanpos;
      if (jj_3R_25()) { jj_scanpos = xsp; break; }
      if (jj_la == 0 && jj_scanpos == jj_lastpos) return false;
    }
    return false;
  }

  final private boolean jj_3_8() {
    if (jj_scan_token(GREATER)) return true;
    if (jj_la == 0 && jj_scanpos == jj_lastpos) return false;
    if (jj_3R_10()) return true;
    if (jj_la == 0 && jj_scanpos == jj_lastpos) return false;
    return false;
  }

  final private boolean jj_3R_40() {
    if (jj_3R_12()) return true;
    if (jj_la == 0 && jj_scanpos == jj_lastpos) return false;
    return false;
  }

  final private boolean jj_3R_13() {
    if (jj_scan_token(NOT)) return true;
    if (jj_la == 0 && jj_scanpos == jj_lastpos) return false;
    return false;
  }

  final private boolean jj_3R_7() {
    Token xsp;
    xsp = jj_scanpos;
    if (jj_3R_13()) jj_scanpos = xsp;
    else if (jj_la == 0 && jj_scanpos == jj_lastpos) return false;
    if (jj_scan_token(LIKE)) return true;
    if (jj_la == 0 && jj_scanpos == jj_lastpos) return false;
    if (jj_3R_14()) return true;
    if (jj_la == 0 && jj_scanpos == jj_lastpos) return false;
    return false;
  }

  final private boolean jj_3R_17() {
    if (jj_scan_token(NOT)) return true;
    if (jj_la == 0 && jj_scanpos == jj_lastpos) return false;
    return false;
  }

  final private boolean jj_3R_39() {
    if (jj_3R_42()) return true;
    if (jj_la == 0 && jj_scanpos == jj_lastpos) return false;
    return false;
  }

  final private boolean jj_3_7() {
    if (jj_scan_token(NOTEQUAL)) return true;
    if (jj_la == 0 && jj_scanpos == jj_lastpos) return false;
    if (jj_3R_10()) return true;
    if (jj_la == 0 && jj_scanpos == jj_lastpos) return false;
    return false;
  }

  final private boolean jj_3R_9() {
    Token xsp;
    xsp = jj_scanpos;
    if (jj_3R_17()) jj_scanpos = xsp;
    else if (jj_la == 0 && jj_scanpos == jj_lastpos) return false;
    if (jj_scan_token(BETWEEN)) return true;
    if (jj_la == 0 && jj_scanpos == jj_lastpos) return false;
    if (jj_3R_10()) return true;
    if (jj_la == 0 && jj_scanpos == jj_lastpos) return false;
    return false;
  }

  final private boolean jj_3R_27() {
    if (jj_scan_token(MINUS)) return true;
    if (jj_la == 0 && jj_scanpos == jj_lastpos) return false;
    return false;
  }

  final private boolean jj_3R_38() {
    if (jj_scan_token(OPENPAREN)) return true;
    if (jj_la == 0 && jj_scanpos == jj_lastpos) return false;
    if (jj_3R_11()) return true;
    if (jj_la == 0 && jj_scanpos == jj_lastpos) return false;
    if (jj_scan_token(CLOSEPAREN)) return true;
    if (jj_la == 0 && jj_scanpos == jj_lastpos) return false;
    return false;
  }

  final private boolean jj_3R_32() {
    Token xsp;
    xsp = jj_scanpos;
    if (jj_3R_38()) {
    jj_scanpos = xsp;
    if (jj_3R_39()) {
    jj_scanpos = xsp;
    if (jj_3R_40()) return true;
    if (jj_la == 0 && jj_scanpos == jj_lastpos) return false;
    } else if (jj_la == 0 && jj_scanpos == jj_lastpos) return false;
    } else if (jj_la == 0 && jj_scanpos == jj_lastpos) return false;
    return false;
  }

  final private boolean jj_3_6() {
    if (jj_scan_token(EQUAL)) return true;
    if (jj_la == 0 && jj_scanpos == jj_lastpos) return false;
    if (jj_3R_10()) return true;
    if (jj_la == 0 && jj_scanpos == jj_lastpos) return false;
    return false;
  }

  final private boolean jj_3R_26() {
    if (jj_scan_token(PLUS)) return true;
    if (jj_la == 0 && jj_scanpos == jj_lastpos) return false;
    return false;
  }

  final private boolean jj_3R_29() {
    if (jj_scan_token(AND)) return true;
    if (jj_la == 0 && jj_scanpos == jj_lastpos) return false;
    if (jj_3R_28()) return true;
    if (jj_la == 0 && jj_scanpos == jj_lastpos) return false;
    return false;
  }

  final private boolean jj_3_5() {
    if (jj_3R_9()) return true;
    if (jj_la == 0 && jj_scanpos == jj_lastpos) return false;
    return false;
  }

  final private boolean jj_3_4() {
    if (jj_3R_8()) return true;
    if (jj_la == 0 && jj_scanpos == jj_lastpos) return false;
    return false;
  }

  final private boolean jj_3R_43() {
    if (jj_3R_49()) return true;
    if (jj_la == 0 && jj_scanpos == jj_lastpos) return false;
    return false;
  }

  final private boolean jj_3R_20() {
    if (jj_3R_28()) return true;
    if (jj_la == 0 && jj_scanpos == jj_lastpos) return false;
    Token xsp;
    while (true) {
      xsp = jj_scanpos;
      if (jj_3R_29()) { jj_scanpos = xsp; break; }
      if (jj_la == 0 && jj_scanpos == jj_lastpos) return false;
    }
    return false;
  }

  final private boolean jj_3R_19() {
    Token xsp;
    xsp = jj_scanpos;
    if (jj_3R_26()) {
    jj_scanpos = xsp;
    if (jj_3R_27()) return true;
    if (jj_la == 0 && jj_scanpos == jj_lastpos) return false;
    } else if (jj_la == 0 && jj_scanpos == jj_lastpos) return false;
    if (jj_3R_18()) return true;
    if (jj_la == 0 && jj_scanpos == jj_lastpos) return false;
    return false;
  }

  final private boolean jj_3_3() {
    if (jj_3R_7()) return true;
    if (jj_la == 0 && jj_scanpos == jj_lastpos) return false;
    return false;
  }

  final private boolean jj_3R_47() {
    if (jj_scan_token(BOOLEAN_LITERAL)) return true;
    if (jj_la == 0 && jj_scanpos == jj_lastpos) return false;
    return false;
  }

  final private boolean jj_3_13() {
    if (jj_3R_11()) return true;
    if (jj_la == 0 && jj_scanpos == jj_lastpos) return false;
    return false;
  }

  final private boolean jj_3_12() {
    if (jj_3R_10()) return true;
    if (jj_la == 0 && jj_scanpos == jj_lastpos) return false;
    return false;
  }

  final private boolean jj_3R_46() {
    if (jj_scan_token(FLOATING_POINT_LITERAL)) return true;
    if (jj_la == 0 && jj_scanpos == jj_lastpos) return false;
    return false;
  }

  final private boolean jj_3R_30() {
    if (jj_scan_token(NULL)) return true;
    if (jj_la == 0 && jj_scanpos == jj_lastpos) return false;
    return false;
  }

  final private boolean jj_3R_45() {
    if (jj_scan_token(INTEGER_LITERAL)) return true;
    if (jj_la == 0 && jj_scanpos == jj_lastpos) return false;
    return false;
  }

  final private boolean jj_3R_10() {
    if (jj_3R_18()) return true;
    if (jj_la == 0 && jj_scanpos == jj_lastpos) return false;
    Token xsp;
    while (true) {
      xsp = jj_scanpos;
      if (jj_3R_19()) { jj_scanpos = xsp; break; }
      if (jj_la == 0 && jj_scanpos == jj_lastpos) return false;
    }
    return false;
  }

  final private boolean jj_3R_49() {
    Token xsp;
    xsp = jj_scanpos;
    if (jj_3_3()) {
    jj_scanpos = xsp;
    if (jj_3_4()) {
    jj_scanpos = xsp;
    if (jj_3_5()) {
    jj_scanpos = xsp;
    if (jj_3_6()) {
    jj_scanpos = xsp;
    if (jj_3_7()) {
    jj_scanpos = xsp;
    if (jj_3_8()) {
    jj_scanpos = xsp;
    if (jj_3_9()) {
    jj_scanpos = xsp;
    if (jj_3_10()) {
    jj_scanpos = xsp;
    if (jj_3_11()) return true;
    if (jj_la == 0 && jj_scanpos == jj_lastpos) return false;
    } else if (jj_la == 0 && jj_scanpos == jj_lastpos) return false;
    } else if (jj_la == 0 && jj_scanpos == jj_lastpos) return false;
    } else if (jj_la == 0 && jj_scanpos == jj_lastpos) return false;
    } else if (jj_la == 0 && jj_scanpos == jj_lastpos) return false;
    } else if (jj_la == 0 && jj_scanpos == jj_lastpos) return false;
    } else if (jj_la == 0 && jj_scanpos == jj_lastpos) return false;
    } else if (jj_la == 0 && jj_scanpos == jj_lastpos) return false;
    } else if (jj_la == 0 && jj_scanpos == jj_lastpos) return false;
    return false;
  }

  final private boolean jj_3R_22() {
    Token xsp;
    xsp = jj_scanpos;
    if (jj_3R_30()) {
    jj_scanpos = xsp;
    if (jj_3_12()) {
    jj_scanpos = xsp;
    if (jj_3_13()) return true;
    if (jj_la == 0 && jj_scanpos == jj_lastpos) return false;
    } else if (jj_la == 0 && jj_scanpos == jj_lastpos) return false;
    } else if (jj_la == 0 && jj_scanpos == jj_lastpos) return false;
    return false;
  }

  final private boolean jj_3R_12() {
    if (jj_scan_token(ID)) return true;
    if (jj_la == 0 && jj_scanpos == jj_lastpos) return false;
    return false;
  }

  final private boolean jj_3R_21() {
    if (jj_scan_token(OR)) return true;
    if (jj_la == 0 && jj_scanpos == jj_lastpos) return false;
    if (jj_3R_20()) return true;
    if (jj_la == 0 && jj_scanpos == jj_lastpos) return false;
    return false;
  }

  final private boolean jj_3R_48() {
    if (jj_scan_token(NOT)) return true;
    if (jj_la == 0 && jj_scanpos == jj_lastpos) return false;
    return false;
  }

  final private boolean jj_3R_44() {
    if (jj_scan_token(STRING_LITERAL)) return true;
    if (jj_la == 0 && jj_scanpos == jj_lastpos) return false;
    return false;
  }

  final private boolean jj_3R_37() {
    if (jj_scan_token(MINUS)) return true;
    if (jj_la == 0 && jj_scanpos == jj_lastpos) return false;
    return false;
  }

  final private boolean jj_3R_6() {
    if (jj_3R_12()) return true;
    if (jj_la == 0 && jj_scanpos == jj_lastpos) return false;
    if (jj_scan_token(IS)) return true;
    if (jj_la == 0 && jj_scanpos == jj_lastpos) return false;
    Token xsp;
    xsp = jj_scanpos;
    if (jj_3R_48()) jj_scanpos = xsp;
    else if (jj_la == 0 && jj_scanpos == jj_lastpos) return false;
    if (jj_scan_token(NULL)) return true;
    if (jj_la == 0 && jj_scanpos == jj_lastpos) return false;
    return false;
  }

  final private boolean jj_3R_41() {
    if (jj_3R_10()) return true;
    if (jj_la == 0 && jj_scanpos == jj_lastpos) return false;
    Token xsp;
    xsp = jj_scanpos;
    if (jj_3R_43()) jj_scanpos = xsp;
    else if (jj_la == 0 && jj_scanpos == jj_lastpos) return false;
    return false;
  }

  final private boolean jj_3R_36() {
    if (jj_scan_token(PLUS)) return true;
    if (jj_la == 0 && jj_scanpos == jj_lastpos) return false;
    return false;
  }

  final private boolean jj_3R_23() {
    if (jj_scan_token(36)) return true;
    if (jj_la == 0 && jj_scanpos == jj_lastpos) return false;
    return false;
  }

  final private boolean jj_3R_42() {
    Token xsp;
    xsp = jj_scanpos;
    if (jj_3R_44()) {
    jj_scanpos = xsp;
    if (jj_3R_45()) {
    jj_scanpos = xsp;
    if (jj_3R_46()) {
    jj_scanpos = xsp;
    if (jj_3R_47()) return true;
    if (jj_la == 0 && jj_scanpos == jj_lastpos) return false;
    } else if (jj_la == 0 && jj_scanpos == jj_lastpos) return false;
    } else if (jj_la == 0 && jj_scanpos == jj_lastpos) return false;
    } else if (jj_la == 0 && jj_scanpos == jj_lastpos) return false;
    return false;
  }

  final private boolean jj_3_2() {
    if (jj_3R_6()) return true;
    if (jj_la == 0 && jj_scanpos == jj_lastpos) return false;
    return false;
  }

  final private boolean jj_3R_16() {
    if (jj_3R_22()) return true;
    if (jj_la == 0 && jj_scanpos == jj_lastpos) return false;
    Token xsp;
    while (true) {
      xsp = jj_scanpos;
      if (jj_3R_23()) { jj_scanpos = xsp; break; }
      if (jj_la == 0 && jj_scanpos == jj_lastpos) return false;
    }
    return false;
  }

  final private boolean jj_3R_35() {
    Token xsp;
    xsp = jj_scanpos;
    if (jj_3_2()) {
    jj_scanpos = xsp;
    if (jj_3R_41()) return true;
    if (jj_la == 0 && jj_scanpos == jj_lastpos) return false;
    } else if (jj_la == 0 && jj_scanpos == jj_lastpos) return false;
    return false;
  }

  final private boolean jj_3R_31() {
    Token xsp;
    xsp = jj_scanpos;
    if (jj_3R_36()) {
    jj_scanpos = xsp;
    if (jj_3R_37()) return true;
    if (jj_la == 0 && jj_scanpos == jj_lastpos) return false;
    } else if (jj_la == 0 && jj_scanpos == jj_lastpos) return false;
    return false;
  }

  final private boolean jj_3R_11() {
    if (jj_3R_20()) return true;
    if (jj_la == 0 && jj_scanpos == jj_lastpos) return false;
    Token xsp;
    while (true) {
      xsp = jj_scanpos;
      if (jj_3R_21()) { jj_scanpos = xsp; break; }
      if (jj_la == 0 && jj_scanpos == jj_lastpos) return false;
    }
    return false;
  }

  final private boolean jj_3_11() {
    if (jj_scan_token(LESSEQUAL)) return true;
    if (jj_la == 0 && jj_scanpos == jj_lastpos) return false;
    if (jj_3R_10()) return true;
    if (jj_la == 0 && jj_scanpos == jj_lastpos) return false;
    return false;
  }

  final private boolean jj_3R_24() {
    Token xsp;
    xsp = jj_scanpos;
    if (jj_3R_31()) jj_scanpos = xsp;
    else if (jj_la == 0 && jj_scanpos == jj_lastpos) return false;
    if (jj_3R_32()) return true;
    if (jj_la == 0 && jj_scanpos == jj_lastpos) return false;
    return false;
  }

  final private boolean jj_3R_34() {
    if (jj_scan_token(SLASH)) return true;
    if (jj_la == 0 && jj_scanpos == jj_lastpos) return false;
    return false;
  }

  final private boolean jj_3_10() {
    if (jj_scan_token(LESS)) return true;
    if (jj_la == 0 && jj_scanpos == jj_lastpos) return false;
    if (jj_3R_10()) return true;
    if (jj_la == 0 && jj_scanpos == jj_lastpos) return false;
    return false;
  }

  final private boolean jj_3_1() {
    if (jj_scan_token(NOT)) return true;
    if (jj_la == 0 && jj_scanpos == jj_lastpos) return false;
    return false;
  }

  final private boolean jj_3R_33() {
    if (jj_scan_token(ASTERISK)) return true;
    if (jj_la == 0 && jj_scanpos == jj_lastpos) return false;
    return false;
  }

  final private boolean jj_3R_28() {
    Token xsp;
    xsp = jj_scanpos;
    if (jj_3_1()) jj_scanpos = xsp;
    else if (jj_la == 0 && jj_scanpos == jj_lastpos) return false;
    if (jj_3R_35()) return true;
    if (jj_la == 0 && jj_scanpos == jj_lastpos) return false;
    return false;
  }

  final private boolean jj_3R_14() {
    if (jj_scan_token(STRING_LITERAL)) return true;
    if (jj_la == 0 && jj_scanpos == jj_lastpos) return false;
    return false;
  }

  final private boolean jj_3R_25() {
    Token xsp;
    xsp = jj_scanpos;
    if (jj_3R_33()) {
    jj_scanpos = xsp;
    if (jj_3R_34()) return true;
    if (jj_la == 0 && jj_scanpos == jj_lastpos) return false;
    } else if (jj_la == 0 && jj_scanpos == jj_lastpos) return false;
    if (jj_3R_24()) return true;
    if (jj_la == 0 && jj_scanpos == jj_lastpos) return false;
    return false;
  }

  final private boolean jj_3R_15() {
    if (jj_scan_token(NOT)) return true;
    if (jj_la == 0 && jj_scanpos == jj_lastpos) return false;
    return false;
  }

  public SQLParserTokenManager token_source;
  JavaCharStream jj_input_stream;
  public Token token, jj_nt;
  private int jj_ntk;
  private Token jj_scanpos, jj_lastpos;
  private int jj_la;
  public boolean lookingAhead = false;
  //private boolean jj_semLA;
  private int jj_gen;
  final private int[] jj_la1 = new int[19];
  final private int[] jj_la1_0 = {0x2000,0x40,0x1f800d80,0x201d8000,0x0,0x0,0x80000000,0x80000000,0x0,0x0,0x201d8000,0xd8000,0x800,0x4000,0x800,0x800,0x0,0x1000,0x800,};
  final private int[] jj_la1_1 = {0x0,0x0,0x0,0x6,0x6,0x6,0x1,0x1,0x6,0x6,0x0,0x0,0x0,0x0,0x0,0x0,0x10,0x0,0x0,};
  final private JJCalls[] jj_2_rtns = new JJCalls[13];
  private boolean jj_rescan = false;
  private int jj_gc = 0;

  public SQLParser(java.io.InputStream stream) {
    jj_input_stream = new JavaCharStream(stream, 1, 1);
    token_source = new SQLParserTokenManager(jj_input_stream);
    token = new Token();
    jj_ntk = -1;
    jj_gen = 0;
    for (int i = 0; i < 19; i++) jj_la1[i] = -1;
    for (int i = 0; i < jj_2_rtns.length; i++) jj_2_rtns[i] = new JJCalls();
  }

  public void reInit(java.io.InputStream stream) {
    jj_input_stream.reInit(stream, 1, 1);
    token_source.reInit(jj_input_stream);
    token = new Token();
    jj_ntk = -1;
    jj_gen = 0;
    for (int i = 0; i < 19; i++) jj_la1[i] = -1;
    for (int i = 0; i < jj_2_rtns.length; i++) jj_2_rtns[i] = new JJCalls();
  }

  public SQLParser(java.io.Reader stream) {
    jj_input_stream = new JavaCharStream(stream, 1, 1);
    token_source = new SQLParserTokenManager(jj_input_stream);
    token = new Token();
    jj_ntk = -1;
    jj_gen = 0;
    for (int i = 0; i < 19; i++) jj_la1[i] = -1;
    for (int i = 0; i < jj_2_rtns.length; i++) jj_2_rtns[i] = new JJCalls();
  }

  public void reInit(java.io.Reader stream) {
    jj_input_stream.reInit(stream, 1, 1);
    token_source.reInit(jj_input_stream);
    token = new Token();
    jj_ntk = -1;
    jj_gen = 0;
    for (int i = 0; i < 19; i++) jj_la1[i] = -1;
    for (int i = 0; i < jj_2_rtns.length; i++) jj_2_rtns[i] = new JJCalls();
  }

  public SQLParser(SQLParserTokenManager tm) {
    token_source = tm;
    token = new Token();
    jj_ntk = -1;
    jj_gen = 0;
    for (int i = 0; i < 19; i++) jj_la1[i] = -1;
    for (int i = 0; i < jj_2_rtns.length; i++) jj_2_rtns[i] = new JJCalls();
  }

  public void reInit(SQLParserTokenManager tm) {
    token_source = tm;
    token = new Token();
    jj_ntk = -1;
    jj_gen = 0;
    for (int i = 0; i < 19; i++) jj_la1[i] = -1;
    for (int i = 0; i < jj_2_rtns.length; i++) jj_2_rtns[i] = new JJCalls();
  }

  final private Token jj_consume_token(int kind) throws ParseException {
    Token oldToken;
    if ((oldToken = token).next != null) token = token.next;
    else token = token.next = token_source.getNextToken();
    jj_ntk = -1;
    if (token.kind == kind) {
      jj_gen++;
      if (++jj_gc > 100) {
        jj_gc = 0;
        for (int i = 0; i < jj_2_rtns.length; i++) {
          JJCalls c = jj_2_rtns[i];
          while (c != null) {
            if (c.gen < jj_gen) c.first = null;
            c = c.next;
          }
        }
      }
      return token;
    }
    token = oldToken;
    jj_kind = kind;
    throw generateParseException();
  }

  final private boolean jj_scan_token(int kind) {
    if (jj_scanpos == jj_lastpos) {
      jj_la--;
      if (jj_scanpos.next == null) {
        jj_lastpos = jj_scanpos = jj_scanpos.next = token_source.getNextToken();
      } else {
        jj_lastpos = jj_scanpos = jj_scanpos.next;
      }
    } else {
      jj_scanpos = jj_scanpos.next;
    }
    if (jj_rescan) {
      int i = 0; Token tok = token;
      while (tok != null && tok != jj_scanpos) { i++; tok = tok.next; }
      if (tok != null) jj_add_error_token(kind, i);
    }
    return (jj_scanpos.kind != kind);
  }

  final public Token getNextToken() {
    if (token.next != null) token = token.next;
    else token = token.next = token_source.getNextToken();
    jj_ntk = -1;
    jj_gen++;
    return token;
  }

  final public Token getToken(int index) {
    Token t = lookingAhead ? jj_scanpos : token;
    for (int i = 0; i < index; i++) {
      if (t.next != null) t = t.next;
      else t = t.next = token_source.getNextToken();
    }
    return t;
  }

  final private int jj_ntk() {
    if ((jj_nt=token.next) == null)
      return (jj_ntk = (token.next=token_source.getNextToken()).kind);
    else
      return (jj_ntk = jj_nt.kind);
  }

  private java.util.Vector jj_expentries = new java.util.Vector();
  private int[] jj_expentry;
  private int jj_kind = -1;
  private int[] jj_lasttokens = new int[100];
  private int jj_endpos;

  private void jj_add_error_token(int kind, int pos) {
    if (pos >= 100) return;
    if (pos == jj_endpos + 1) {
      jj_lasttokens[jj_endpos++] = kind;
    } else if (jj_endpos != 0) {
      jj_expentry = new int[jj_endpos];
      for (int i = 0; i < jj_endpos; i++) {
        jj_expentry[i] = jj_lasttokens[i];
      }
      boolean exists = false;
      for (java.util.Enumeration enm = jj_expentries.elements(); enm.hasMoreElements();) {
        int[] oldentry = (int[])(enm.nextElement());
        if (oldentry.length == jj_expentry.length) {
          exists = true;
          for (int i = 0; i < jj_expentry.length; i++) {
            if (oldentry[i] != jj_expentry[i]) {
              exists = false;
              break;
            }
          }
          if (exists) break;
        }
      }
      if (!exists) jj_expentries.addElement(jj_expentry);
      if (pos != 0) jj_lasttokens[(jj_endpos = pos) - 1] = kind;
    }
  }

  final public ParseException generateParseException() {
    jj_expentries.removeAllElements();
    boolean[] la1tokens = new boolean[37];
    for (int i = 0; i < 37; i++) {
      la1tokens[i] = false;
    }
    if (jj_kind >= 0) {
      la1tokens[jj_kind] = true;
      jj_kind = -1;
    }
    for (int i = 0; i < 19; i++) {
      if (jj_la1[i] == jj_gen) {
        for (int j = 0; j < 32; j++) {
          if ((jj_la1_0[i] & (1<<j)) != 0) {
            la1tokens[j] = true;
          }
          if ((jj_la1_1[i] & (1<<j)) != 0) {
            la1tokens[32+j] = true;
          }
        }
      }
    }
    for (int i = 0; i < 37; i++) {
      if (la1tokens[i]) {
        jj_expentry = new int[1];
        jj_expentry[0] = i;
        jj_expentries.addElement(jj_expentry);
      }
    }
    jj_endpos = 0;
    jj_rescan_token();
    jj_add_error_token(0, 0);
    int[][] exptokseq = new int[jj_expentries.size()][];
    for (int i = 0; i < jj_expentries.size(); i++) {
      exptokseq[i] = (int[])jj_expentries.elementAt(i);
    }
    return new ParseException(token, exptokseq, tokenImage);
  }

  final public void enable_tracing() {
  }

  final public void disable_tracing() {
  }

  final private void jj_rescan_token() {
    jj_rescan = true;
    for (int i = 0; i < 13; i++) {
      JJCalls p = jj_2_rtns[i];
      do {
        if (p.gen > jj_gen) {
          jj_la = p.arg; jj_lastpos = jj_scanpos = p.first;
          switch (i) {
            case 0: jj_3_1(); break;
            case 1: jj_3_2(); break;
            case 2: jj_3_3(); break;
            case 3: jj_3_4(); break;
            case 4: jj_3_5(); break;
            case 5: jj_3_6(); break;
            case 6: jj_3_7(); break;
            case 7: jj_3_8(); break;
            case 8: jj_3_9(); break;
            case 9: jj_3_10(); break;
            case 10: jj_3_11(); break;
            case 11: jj_3_12(); break;
            case 12: jj_3_13(); break;
          }
        }
        p = p.next;
      } while (p != null);
    }
    jj_rescan = false;
  }

  final private void jj_save(int index, int xla) {
    JJCalls p = jj_2_rtns[index];
    while (p.gen > jj_gen) {
      if (p.next == null) { p = p.next = new JJCalls(); break; }
      p = p.next;
    }
    p.gen = jj_gen + xla - jj_la; p.first = token; p.arg = xla;
  }

  static final class JJCalls {
    int gen;
    Token first;
    int arg;
    JJCalls next;
  }

}
