/*
 * Copyright (c) 2000, 2017 Oracle and/or its affiliates. All rights reserved.
 * Copyright (c) 2022 Contributors to Eclipse Foundation. All rights reserved.
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

/**
 * Describes the input token stream.
 */

public class Token {

    /**
     * An integer that describes the kind of this token. This numbering system is determined by JavaCCParser, and a table of
     * these numbers is stored in the file ...Constants.java.
     */
    public int kind;

    /**
     * beginLine and beginColumn describe the position of the first character of this token; endLine and endColumn describe
     * the position of the last character of this token.
     */
    public int beginLine, beginColumn/* , endLine, endColumn */;

    /**
     * The string image of the token.
     */
    public String image;

    /**
     * A reference to the next regular (non-special) token from the input stream. If this is the last token from the input
     * stream, or if the token manager has not read tokens beyond this one, this field is set to null. This is true only if
     * this token is also a regular token. Otherwise, see below for a description of the contents of this field.
     */
    public Token next;

    /**
     * This field is used to access special tokens that occur prior to this token, but after the immediately preceding
     * regular (non-special) token. If there are no such special tokens, this field is set to null. When there are more than
     * one such special token, this field refers to the last of these special tokens, which in turn refers to the next
     * previous special token through its specialToken field, and so on until the first special token (whose specialToken
     * field is null). The next fields of special tokens refer to other special tokens that immediately follow it (without
     * an intervening regular token). If there is no such token, this field is null.
     */
    // public Token specialToken;

    /**
     * Returns the image.
     */
    @Override
    public final String toString() {
        return image;
    }

    /**
     * Returns a new Token object, by default. However, if you want, you can create and return subclass objects based on the
     * value of ofKind. Simply add the cases to the switch for all those special cases. For example, if you have a subclass
     * of Token called IDToken that you want to create if ofKind is ID, simlpy add something like :
     *
     * case MyParserConstants.ID : return new IDToken();
     *
     * to the following switch statement. Then you can cast matchedToken variable to the appropriate type and use it in your
     * lexical actions.
     */
    public static Token newToken(int ofKind) {
        switch (ofKind) {
        default:
            return new Token();
        }
    }

}
