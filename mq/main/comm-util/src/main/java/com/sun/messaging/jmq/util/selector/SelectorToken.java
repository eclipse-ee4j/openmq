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
 * @(#)SelectorToken.java	1.7 07/06/07
 */ 

package com.sun.messaging.jmq.util.selector;

/**
 * Immutable class that represents a token. A token consists of two
 * parts. And integer that defines the token, and an optional value
 * that defines an associated value. For example a LONG token has
 * an associated value that is the value of the long it represents.
 */
class SelectorToken {

    // Pre-allocate TRUE, FALSE and UNKNOWN tokens since these are used
    // constantly during evaluation.
    static final SelectorToken trueToken  =
                            new SelectorToken(Selector.TRUE, "true");
    static final SelectorToken falseToken =
                            new SelectorToken(Selector.FALSE, "false");
    static final SelectorToken unknownToken =
                            new SelectorToken(Selector.UNKNOWN, "unknown");
    
    // Pre-allocate a couple other tokens that commonly appear in expressions.
    // Note that LTE and GTE are used to evaluate BETWEEN so it's important
    // to have them in here.
    static final SelectorToken equalsToken  =
                            new SelectorToken(Selector.EQUALS, "=");
    static final SelectorToken notEqualsToken =
                            new SelectorToken(Selector.NOT_EQUALS, "<>");
    static final SelectorToken gtToken =
                            new SelectorToken(Selector.GT, ">");
    static final SelectorToken gteToken =
                            new SelectorToken(Selector.GTE, ">=");
    static final SelectorToken ltToken =
                            new SelectorToken(Selector.LT, "<");
    static final SelectorToken lteToken =
                            new SelectorToken(Selector.LTE, "<=");

    // Pre-allocate marker tokens
    static final SelectorToken andMarker =
                            new SelectorToken(Selector.AND_MARKER, "&");
    static final SelectorToken orMarker =
                            new SelectorToken(Selector.OR_MARKER, "|");

    // What this token is.
    int token = Selector.UNKNOWN;

    // Some tokens have an associated value. For example:
    // ESCAPE has an escape character.
    // IDENTIFIER has the identifier String
    // STRING     has the String value
    // DOUBLE      has the Float value
    Object value = null;

    public static SelectorToken getInstance(int token, Object value) {

        switch (token) {

        case Selector.TRUE:
            return trueToken;
        case Selector.FALSE:
            return falseToken;
        case Selector.UNKNOWN:
            return unknownToken;
        case Selector.EQUALS:
            return equalsToken;
        case Selector.GTE:
            return gteToken;
        case Selector.LTE:
            return lteToken;
        case Selector.GT:
            return gtToken;
        case Selector.LT:
            return ltToken;
        case Selector.NOT_EQUALS:
            return notEqualsToken;
        case Selector.AND_MARKER:
            return andMarker;
        case Selector.OR_MARKER:
            return orMarker;
        default:
            return new SelectorToken(token, value);
        }
    }

    public static SelectorToken getInstance(int token) {
        return getInstance(token, null);
    }

    private SelectorToken(int token) {
        this.token = token;
    }

    private SelectorToken(int token, Object value) {
        this.token = token;
        this.value = value;
    }

    public int getToken() {
        return token;
    }

    public Object getValue() {
        return value;
    }

    public boolean equals(Object o) {

        if (this == o) return true;

        if (!(o instanceof SelectorToken)) {
            return false;
        }

        SelectorToken obj = (SelectorToken)o;

        if (obj.token != token) {
            return false;
        }

        return (value == null ? obj.value == null : value.equals(obj.value));
    }

    public int hashCode() {

        if (value == null) {
            return token;
        } else {
            return value.hashCode() * token;
        }
    }

    public String toString() {
        return ("[" + token + "," +
                (value == null ? "null" : value.toString()) + "]");
    }
}
