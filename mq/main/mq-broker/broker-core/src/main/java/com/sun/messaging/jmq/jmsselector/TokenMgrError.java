/*
 * Copyright (c) 2000, 2017 Oracle and/or its affiliates. All rights reserved.
 * Copyright (c) 2020, 2022 Contributors to the Eclipse Foundation
 * Copyright (c) 2020 Payara Services Ltd.
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

package com.sun.messaging.jmq.jmsselector;

public class TokenMgrError extends Error {
    /*
     * Ordinals for various reasons why an Error of this type can be thrown.
     */

    private static final long serialVersionUID = -4902585116025942432L;

    /**
     * Lexical error occurred.
     */
    static final int LEXICAL_ERROR = 0;

    /**
     * An attempt wass made to create a second instance of a static token manager.
     */
    static final int STATIC_LEXER_ERROR = 1;

    /**
     * Tried to change to an invalid lexical state.
     */
    static final int INVALID_LEXICAL_STATE = 2;

    /**
     * Detected (and bailed out of) an infinite loop in the token manager.
     */
    static final int LOOP_DETECTED = 3;

    /**
     * Indicates the reason why the exception is thrown. It will have one of the above 4 values.
     */
    int errorCode;

    /**
     * Replaces unprintable characters by their espaced (or unicode escaped) equivalents in the given string
     */
    protected static String addEscapes(String str) {
        StringBuilder retval = new StringBuilder();
        char ch;
        for (int i = 0; i < str.length(); i++) {
            switch (str.charAt(i)) {
            case 0:
                continue;
            case '\b':
                retval.append("\\b");
                continue;
            case '\t':
                retval.append("\\t");
                continue;
            case '\n':
                retval.append("\\n");
                continue;
            case '\f':
                retval.append("\\f");
                continue;
            case '\r':
                retval.append("\\r");
                continue;
            case '\"':
                retval.append("\\\"");
                continue;
            case '\'':
                retval.append("\\\'");
                continue;
            case '\\':
                retval.append("\\\\");
                continue;
            default:
                if ((ch = str.charAt(i)) < 0x20 || ch > 0x7e) {
                    String s = "0000" + Integer.toString(ch, 16);
                    retval.append("\\u").append(s.substring(s.length() - 4, s.length()));
                } else {
                    retval.append(ch);
                }
                continue;
            }
        }
        return retval.toString();
    }

    /**
     * Returns a detailed message for the Error when it is thrown by the token manager to indicate a lexical error.
     * Parameters : EOFSeen : indicates if EOF caused the lexicl error curLexState : lexical state in which this error
     * occurred errorLine : line number when the error occurred errorColumn : column number when the error occurred
     * errorAfter : prefix that was seen before this error occurred curchar : the offending character Note: You can
     * customize the lexical error message by modifying this method.
     */
    private static String lexicalError(boolean EOFSeen, int errorLine, int errorColumn, String errorAfter, char curChar) {
        return ("Lexical error at line " + errorLine + ", column " + errorColumn + ".  Encountered: "
                + (EOFSeen ? "<EOF> " : ("\"" + addEscapes(String.valueOf(curChar)) + "\"") + " (" + (int) curChar + "), ") + "after : \""
                + addEscapes(errorAfter) + "\"");
    }

    /*
     * Constructors of various flavors follow.
     */

    public TokenMgrError() {
    }

    public TokenMgrError(String message, int reason) {
        super(message);
        errorCode = reason;
    }

    public TokenMgrError(boolean EOFSeen, int errorLine, int errorColumn, String errorAfter, char curChar, int reason) {
        this(lexicalError(EOFSeen, errorLine, errorColumn, errorAfter, curChar), reason);
    }
}
