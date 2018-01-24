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
 * @(#)SQLParserConstants.java	1.4 06/28/07
 */ 

package com.sun.messaging.jmq.jmsselector;

public interface SQLParserConstants {

  int EOF = 0;
  int AND = 6;
  int BETWEEN = 7;
  int IN = 8;
  int IS = 9;
  int LIKE = 10;
  int NOT = 11;
  int NULL = 12;
  int OR = 13;
  int ESCAPE = 14;
  int INTEGER_LITERAL = 15;
  int FLOATING_POINT_LITERAL = 16;
  int EXPONENT = 17;
  int STRING_LITERAL = 18;
  int BOOLEAN_LITERAL = 19;
  int ID = 20;
  int LETTER = 21;
  int DIGIT = 22;
  int LESS = 23;
  int LESSEQUAL = 24;
  int GREATER = 25;
  int GREATEREQUAL = 26;
  int EQUAL = 27;
  int NOTEQUAL = 28;
  int OPENPAREN = 29;
  int CLOSEPAREN = 30;
  int ASTERISK = 31;
  int SLASH = 32;
  int PLUS = 33;
  int MINUS = 34;
  int QUESTIONMARK = 35;

  int DEFAULT = 0;

  String[] tokenImage = {
    "<EOF>",
    "\" \"",
    "\"\\n\"",
    "\"\\r\"",
    "\"\\t\"",
    "\"\\f\"",
    "\"and\"",
    "\"between\"",
    "\"in\"",
    "\"is\"",
    "\"like\"",
    "\"not\"",
    "\"null\"",
    "\"or\"",
    "\"escape\"",
    "<INTEGER_LITERAL>",
    "<FLOATING_POINT_LITERAL>",
    "<EXPONENT>",
    "<STRING_LITERAL>",
    "<BOOLEAN_LITERAL>",
    "<ID>",
    "<LETTER>",
    "<DIGIT>",
    "\"<\"",
    "\"<=\"",
    "\">\"",
    "\">=\"",
    "\"=\"",
    "\"<>\"",
    "\"(\"",
    "\")\"",
    "\"*\"",
    "\"/\"",
    "\"+\"",
    "\"-\"",
    "\"?\"",
    "\",\"",
  };

}
