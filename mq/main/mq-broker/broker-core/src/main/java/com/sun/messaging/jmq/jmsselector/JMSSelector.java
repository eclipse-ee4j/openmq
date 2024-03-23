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

import java.io.StringReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Hashtable;
import java.util.StringTokenizer;

/**
 * Provides JMS selector capability
 *
 * Uses SQL parser in SQLParser.jj
 */
public class JMSSelector implements java.io.Serializable {
    private static final long serialVersionUID = 987679311016714425L;
    transient SQLParser parser;
    String selectorPattern;
    transient Hashtable msgHeader;
    transient int jmsDeliveryMode;
    transient int jmsPriority;
    transient String jmsMessageID;
    transient long jmsTimestamp;
    transient String jmsCorrelationID;
    transient String jmsType;

    public JMSSelector() {
        this(null, null);
    }

    private void readObject(java.io.ObjectInputStream ois) throws IOException, ClassNotFoundException {
        ois.defaultReadObject();
        msgHeader = new Hashtable();
        jmsDeliveryMode = 2;
        jmsPriority = 4;
        jmsMessageID = "";
        jmsTimestamp = 0L;
        jmsCorrelationID = "";
        jmsType = "";
    }

    /**
     * @param pattern The selector pattern.
     * @param header The message header.
     */
    public JMSSelector(String pattern, Hashtable header) {
        if (pattern == null) {
            pattern = "";
        }

        if (header == null) {
            header = new Hashtable();
        }

        msgHeader = header;
        selectorPattern = pattern;

        // Initialze Header fields to their defaults
        jmsDeliveryMode = 2;
        jmsPriority = 4;
        jmsMessageID = "";
        jmsTimestamp = 0L;
        jmsCorrelationID = "";
        jmsType = "";

        // InputStream stream = new ByteArrayInputStream(selectorPattern.getBytes());
        StringReader stream = new StringReader(selectorPattern);

        parser = new SQLParser(this, stream);
    }

    /**
     * Returns the JMS Header property JMSDeliveryMode used by this JMSSelector
     *
     * @return The JMS Header property JMSDeliveryMode used by this JMSSelector
     */
    public int getJMSDeliveryMode() {
        return jmsDeliveryMode;
    }

    /**
     * Sets the JMS Header property JMSDeliveryMode used by this JMSSelector
     *
     * @param deliveryMode The JMS Header property JMSDeliveryMode used by this JMSSelector
     */
    public void setJMSDeliveryMode(int deliveryMode) {
        jmsDeliveryMode = deliveryMode;
    }

    /**
     * Returns the JMS Header property JMSPriority used by this JMSSelector
     *
     * @return The JMS Header property JMSPriority used by this JMSSelector
     */
    public int getJMSPriority() {
        return jmsPriority;
    }

    /**
     * Sets the JMS Header property JMSPriority used by this JMSSelector
     *
     * @param priority The JMS Header property JMSPriority used by this JMSSelector
     */
    public void setJMSPriority(int priority) {
        jmsPriority = priority;
    }

    /**
     * Returns the JMS Header property JMSMessageID used by this JMSSelector
     *
     * @return The JMS Header property JMSMessageID used by this JMSSelector
     */
    public String getJMSMessageID() {
        return jmsMessageID;
    }

    /**
     * Sets the JMS Header property JMSMessageID used by this JMSSelector
     *
     * @param messageID The JMS Header property JMSMessageID used by this JMSSelector
     */
    public void setJMSMessageID(String messageID) {
        jmsMessageID = messageID;
    }

    /**
     * Returns the JMS Header property JMSTimestamp used by this JMSSelector
     *
     * @return The JMS Header property JMSTimestamp used by this JMSSelector
     */
    public long getJMSTimestamp() {
        return jmsTimestamp;
    }

    /**
     * Sets the JMS Header property JMSTimestamp used by this JMSSelector
     *
     * @param timestamp The JMS Header property JMSTimestamp used by this JMSSelector
     */
    public void setJMSTimestamp(long timestamp) {
        jmsTimestamp = timestamp;
    }

    /**
     * Returns the JMS Header property JMSCorrelationID used by this JMSSelector
     *
     * @return The JMS Header property JMSCorrelationID used by this JMSSelector
     */
    public String getJMSCorrelationID() {
        return jmsCorrelationID;
    }

    /**
     * Sets the JMS Header property JMSCorrelationID used by this JMSSelector
     *
     * @param correlationID The JMS Header property JMSCorrelationID used by this JMSSelector
     */
    public void setJMSCorrelationID(String correlationID) {
        jmsCorrelationID = correlationID;
    }

    /**
     * Returns the JMS Header property JMSType used by this JMSSelector
     *
     * @return The JMS Header property JMSType used by this JMSSelector
     */
    public String getJMSType() {
        return jmsType;
    }

    /**
     * Sets the JMS Header property JMSType used by this JMSSelector
     *
     * @param type The JMS Header property JMSType used by this JMSSelector
     */
    public void setJMSType(String type) {
        jmsType = type;
    }

    /**
     * Sets all the JMS Header property fields used by this JMSSelector
     *
     * @param deliveryMode The JMS Header property JMSDeliveryMode used by this JMSSelector
     * @param priority The JMS Header property JMSPriority used by this JMSSelector
     * @param messageID The JMS Header property JMSMessageID used by this JMSSelector
     * @param timestamp The JMS Header property JMSTimestamp used by this JMSSelector
     * @param correlationID The JMS Header property JMSCorrelationID used by this JMSSelector
     * @param type The JMS Header property JMSType used by this JMSSelector
     */
    public void setJMSHeaderFields(int deliveryMode, int priority, String messageID, long timestamp, String correlationID, String type) {
        jmsDeliveryMode = deliveryMode;
        jmsPriority = priority;
        jmsMessageID = messageID;
        jmsTimestamp = timestamp;
        jmsCorrelationID = correlationID;
        jmsType = type;
    }

    /**
     * Returns the selectorPattern used by this JMSSelector.
     *
     * @return The selectorPattern used by this JMSSelector.
     */
    public String getSelectorPattern() {
        return selectorPattern;
    }

    /**
     * Validates the selector pattern that will be used by this JMSSelector to perform matches. This selector pattern must
     * conform to the SQL-92 specification for an SQL pattern.
     *
     * pattern.
     *
     */
    public void setSelectorPattern(String pattern) {
        if (pattern == null) {
            selectorPattern = "";
        } else {
            selectorPattern = pattern;
        }
    }

    /**
     * Validates the selector pattern that will be used by this JMSSelector to perform matches. This selector pattern must
     * conform to the SQL-92 specification for an SQL pattern.
     *
     * @exception InvalidJMSSelectorException If the selectorPattern does not conform to the SQL-92 specification for an SQL
     * pattern.
     *
     */
    public void validateSelectorPattern(String pattern) throws InvalidJMSSelectorException, NullMessageHeaderException {
        if (pattern == null) {
            pattern = "";
        }
        selectorPattern = pattern;
        if ("".equals(selectorPattern)) {
            return;
        }
        try {
            match(msgHeader);
        } catch (NullMessageHeaderException e) {
            throw e;
        } catch (Throwable t) {
            throw new InvalidJMSSelectorException(selectorPattern);
        }
    }

    /**
     * Matches the message header passed in with the selector pattern set in this JMSSelector.
     *
     * @param header The message header (a java.util.Hashtable object)
     *
     * @return <code>true</code> if a match was made; <code>false</code> otherwise.
     *
     * @exception InvalidJMSSelectorException If the selectorPattern does not conform to the SQL-92 specification for an SQL
     * pattern.
     */
    public boolean match(Hashtable header) throws InvalidJMSSelectorException, NullMessageHeaderException {
        boolean matched = false;
        if (selectorPattern.equals("")) {
            matched = true; // No selector
        } else {
            try {
                StringReader stream = new StringReader(selectorPattern);
                parser.reInit(stream);
                // If null properties passed, then use the default (empty, non-null) properties
                matched = parser.match(header == null ? msgHeader : header);
            } catch (NullMessageHeaderException e) {
                throw e;
            } catch (Throwable t) {
                // note that the message of this exception is simply the invalid pattern
                // and should not be translated.
                throw new InvalidJMSSelectorException(selectorPattern);
            }
        }
        return matched;
    }

    /**
     * Used to determine selector match in a SQL LIKE experssion as in <\p> str LIKE patternStr [ESCAPE escapeChar] <\p>
     *
     * @param patternStr The pattern used in SQL LIKE statement
     * @param str The string being compared with patternStr in SQL LIKE statement
     * @param escapeChar The escape character used to treat wildcards '_' and '%' as normal
     */
    boolean matchPattern(String patternStr, String str, char escapeChar) {
        boolean matched = false;
        String escapeCharStr = String.valueOf(escapeChar);
        String wildCards = "_%";
        String delims = wildCards + escapeCharStr;
        boolean escaped = false;
        int index = 0;
        String tok = null;

        try {
            if (str != null) {
                StringTokenizer st = new StringTokenizer(patternStr, delims, true);

                // Parse string into a Collection of tokens since we will need to peek forward as we
                // scan tokens
                ArrayList tokens = new ArrayList();
                while (st.hasMoreTokens()) {
                    tok = st.nextToken();
                    tokens.add(tok);
                }

                matched = true;

                // Iterate over tokens list and match each token with str
                int numTokens = tokens.size();
                for (int i = 0; i < numTokens; i++) {
                    tok = (String) tokens.get(i);

                    // Token can be a delimeter or actual token
                    if (tok.equals(escapeCharStr) && (!escaped)) {
                        // Remember that the next character in patterStr must be treated literally
                        escaped = true;
                    } else if (tok.equals("%") && (!escaped)) {
                        if (i == (numTokens - 1)) {

                            // wildcard is last character in pattern,
                            // match entire string.
                            index = str.length();
                        } else if (i != numTokens - 1) { // There are more tokens. If not then we have a match

                            /*
                             * //Peek forward and get the next non delimter token if any String nextNonDelimToken = null; for (int j=i+1;
                             * j<numTokens; j++) { String newTok = (String)tokens.get(j); if ((!newTok.equals(escapeCharStr)) &&
                             * (!newTok.equals("_")) && (!newTok.equals("%"))) { nextNonDelimToken = newTok; break; } }
                             */

                            // Now scan forward
                            int _cnt = 0; // count of '_' delimeters encountered
                            ++i;
                            for (; i < numTokens; i++) {
                                tok = (String) tokens.get(i);

                                if (tok.equals(escapeCharStr) && (!escaped)) {
                                    // Remember that the next character in patterStr must be treated literally
                                    escaped = true;
                                } else if (tok.equals("%") && (!escaped)) {//NOPMD
                                    // % followed by % is same as %
                                } else if (tok.equals("_") && (!escaped)) {
                                    ++_cnt;
                                } else {
                                    // This is the nextNonDelimTok
                                    int oldIndex = index;

                                    if (i == (numTokens - 1)) {

                                        // Not a general purpose fix for
                                        // wildcard matching bug.
                                        // At least handle case when
                                        // only one wildcard in pattern
                                        // that has a group of characters
                                        // trailing it.
                                        if (str.endsWith(tok)) {
                                            index = str.length() - tok.length();
                                        } else {
                                            matched = false;
                                        }
                                    } else {
                                        index = str.indexOf(tok, index);
                                    }

                                    if (index < 0) {
                                        matched = false;
                                    } else {
                                        // Make sure that we have _cnt charecters between old index and new index
                                        if (index - oldIndex >= _cnt) {
                                            index += tok.length();
                                        } else {
                                            matched = false;
                                        }
                                    }

                                    escaped = false;
                                    break;
                                }
                            }
                        }
                    } else if (tok.equals("_") && (!escaped)) {
                        index++;
                    } else {
                        // Compare token read with corresponding string
                        int tokLen = tok.length();

                        if (index + tokLen <= str.length()) {
                            String subStr = null;

                            try {
                                subStr = str.substring(index, index + tokLen);
                            } catch (StringIndexOutOfBoundsException e) {
                                matched = false;
                                break;
                            }

                            if (!subStr.equalsIgnoreCase(tok)) {
                                matched = false;

                                break;
                            } else {
                                index = index + tok.length();
                            }
                        } else {
                            matched = false;
                            break;
                        }
                        escaped = false;
                    }
                }
            }
            if (matched && index != str.length()) {
                matched = false;
            }
        } catch (StringIndexOutOfBoundsException e) {
            matched = false;
        } finally {
            return matched;
        }
    }

    /**
     * Strip leading and trailing quotes from a String Literal. Also, the nested quote character is represented as 2
     * consecutive quotes, so replace all occurrances of double quotes with single quotes.
     */
    String processStringLiteral(String strLiteral) {

        // Strip leading and trailing quotes
        strLiteral = strLiteral.substring(1, strLiteral.length() - 1);

        // Replace all occurances of consecutive quotes as single quote.
        int index = strLiteral.indexOf("''");
        if (index > -1) {
            StringBuffer sb = new StringBuffer(strLiteral);
            while (index != -1) {
                sb.deleteCharAt(index);
                index = sb.indexOf("''");
            }
            strLiteral = sb.toString();
        }
        return strLiteral;
    }

    @Override
    public String toString() {

        return ("JMSSelector:\tPattern=\t`" + selectorPattern + "'" + "\n    Headers:\tDeliveryMode\t" + jmsDeliveryMode + "\n\t\tPriority\t" + jmsPriority
                + "\n\t\tMessageID\t`" + jmsMessageID + "'" + "\n\t\tTimestamp\t" + jmsTimestamp + "\n\t\tCorrelationID\t`" + jmsCorrelationID + "'"
                + "\n\t\tType\t\t`" + jmsType + "'" + "\n");
    }
}
