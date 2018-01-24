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

package com.sun.messaging.jms.ra.util;

import java.util.Hashtable;
import java.util.ListIterator;
import javax.resource.spi.InvalidPropertyException;

/**
 * This Custom Tokenizer class allows Custom command to break strings into tokens.
 * The tokenizer checks for the escape characters and the quotes to determine
 * the tokens.
 * Consider the following examples:
 * <li>
 *   string is <code>name1=value1,name2=value2</code> and the delimiter is ,
 *   Custom tokenizer will tokenized the string to:
 *   <blockquote><pre>
 *   name1=value1
 *   name2=value2
 *   </pre></blockquote>
 * </li>
 * <li>
 *   string is <code>name1=abc\,def,name2=value2</code> and the delimiter is ,
 *   Custom tokenizer will tokenized the string to:
 *   <blockquote><pre>
 *   name1=abc,def
 *   name2=value2
 *   </pre></blockquote>
 *   notice that abc\,def is not tokenized since it contains an escape character
 *   before the ,.
 * </li>
 * <li>
 *   string is <code>name1="abc,def",name2=value2</code> and the delimiter is ,
 *   Custom tokenizer will tokenized the string to:
 *   <blockquote><pre>
 *   name1=abc,def
 *   name2=value2
 *   </pre></blockquote>
 *   notice that "abc,def" is not not tokenized since it's in the quotes
 * </li>
 * <li>
 *   string is <code>name1="abc\,def",name2=value2</code> and the delimiter is ,
 *   Custom tokenizer will tokenized the string to:
 *   <blockquote><pre>
 *   name1=abc\,def
 *   name2=value2
 *   </pre></blockquote>
 * </li>
 * @author  Jane Young
 */
public class CustomTokenizer {
    private final static char    ESCAPE_CHAR  = '\\';
    private final static String  QUOTE_STRING = "\"";
    private int size = 0;
    private ListIterator tokenIterator = null;    
    
    /**
     *  constructor that calls popluateList to create the tokeIterator
     *  and size variables.
     *  @param stringToken - the string to tokenize.
     *  @param delimiter - the delimiter to tokenize.
     */
    public CustomTokenizer(String stringToken, String delimiter)
    throws InvalidPropertyException {
        if (!checkForMatchingQuotes(stringToken))
            throw new InvalidPropertyException("UnclosedString");
        
        if (stringToken != null && delimiter != null)
            tokenIterator = populateList(stringToken, delimiter);
        else
            throw new InvalidPropertyException("CouldNotCreateCliTokenizer");
    }
    
    /**
     *  returns the number of tokens in the string.
     *  @return number of tokens
     */
    public int countTokens() {
        return size;
    }
    
    /**
     *  returns true is there are more token in the list
     *  @return true if there are more tokens else false.
     */
    public boolean hasMoreTokens() {
        return tokenIterator.hasNext();
    }
    
    /**
     *  returns the token string without the  escape characters
     *  @return the next string token without the escape characters.
     */
    public String nextTokenWithoutEscapeAndQuoteChars() throws InvalidPropertyException {
        final String strWOEscape = removeEscapeChars((String)tokenIterator.next());
        final String strWOQuotes = removeQuoteChars(strWOEscape);
        return removeEscapeCharsFromQuotes(strWOQuotes);
    }
    
    
    /**
     *  returns the next token string
     *  @return the next string token
     */
    public String nextToken() throws InvalidPropertyException {
        return (String)tokenIterator.next();
    }
    
    
    /**
     *  This method will check for matching quotes.  If quotes do not match then
     *  return false else return true.
     *  @param str - string to check for matching quotes
     *  @return boolean - true if quotes match else false.
     */
    private boolean checkForMatchingQuotes(String str) throws InvalidPropertyException {
        //get index of the first quote in the string
        int beginQuote = getStringDelimiterIndex(str, QUOTE_STRING, 0);
        
        while (beginQuote != -1) {
            int endQuote = getStringDelimiterIndex(str, QUOTE_STRING, beginQuote+1);
            if (endQuote == -1) return false;
            beginQuote = getStringDelimiterIndex(str, QUOTE_STRING, endQuote+1);
        }
        return true;
    }
    
    
    /**
     *  this methos calls the getStringDelimiterIndex to determine the index
     *  of the delimiter and use that to populate the tokenIterator.
     *  @param strToken - string to tokenize
     *  @param delimiter - delimiter to tokenize the string
     *  @return ListIterator
     */
    private ListIterator populateList(String strToken, String delimiter)
    throws InvalidPropertyException {
        java.util.List tokenList = new java.util.Vector();
        int endIndex = getStringDelimiterIndex(strToken, delimiter, 0);
        if (endIndex == -1) tokenList.add(strToken);
        else {
            int beginIndex = 0;
            while (endIndex > -1) {
                //do not want to add to the list if the string is empty
                if (beginIndex != endIndex)
                    tokenList.add(strToken.substring(beginIndex, endIndex));
                beginIndex = endIndex + 1;
                endIndex = getStringDelimiterIndex(strToken, delimiter, beginIndex);
            }
            //do not want to add to the list if the begindIndex is the last index
            if (beginIndex != strToken.length())
                tokenList.add(strToken.substring(beginIndex));
        }
        size = tokenList.size();
        try {
            return tokenList.listIterator();
        } catch (java.lang.IndexOutOfBoundsException ioe) {
            throw new InvalidPropertyException(ioe);
        }
    }
    
    
    /**
     * Removes the escape characters from the property value
     * @param strValue - string value to remove the escape character
     * @return the string with escape character removed
     */
    private String removeEscapeChars(String strValue)
    throws InvalidPropertyException {
        int prefixIndex = 0;
        java.lang.StringBuffer strbuff = new java.lang.StringBuffer();
        
        while (prefixIndex < strValue.length()) {
            int delimeterIndex = getStringDelimiterIndex(strValue,
                    String.valueOf(ESCAPE_CHAR), prefixIndex);
            if (delimeterIndex == -1) {
                strbuff.append(strValue.substring(prefixIndex));
                break;
            }
            
            //if a quote is follow by an esacpe then keep the escape character
            if (delimeterIndex+1 < strValue.length() &&
                    String.valueOf(strValue.charAt(delimeterIndex+1)).equals(QUOTE_STRING))
                strbuff.append(strValue.substring(prefixIndex, delimeterIndex+1));
            else
                strbuff.append(strValue.substring(prefixIndex, delimeterIndex));
            
            prefixIndex = delimeterIndex+1;
        }
        return strbuff.toString();
    }
    
    /**
     * Removes escape characters that precedes quotes
     * @param strValue - the string value to remove the escape characters
     * @return string value with escape characters removed
     */
    private String removeEscapeCharsFromQuotes(String strValue) throws InvalidPropertyException {
        int prefixIndex = 0;
        java.lang.StringBuffer strbuff = new java.lang.StringBuffer();
        
        while (prefixIndex < strValue.length()) {
            int delimeterIndex = strValue.indexOf(String.valueOf(ESCAPE_CHAR), prefixIndex);
            if (delimeterIndex == -1) {
                strbuff.append(strValue.substring(prefixIndex));
                break;
            }
            //if a quote is follow by an esacpe then remove the escape character
            if (String.valueOf(strValue.charAt(delimeterIndex+1)).equals(QUOTE_STRING))
                strbuff.append(strValue.substring(prefixIndex, delimeterIndex));
            else
                strbuff.append(strValue.substring(prefixIndex, delimeterIndex+1));
            
            prefixIndex = delimeterIndex+1;
        }
        return strbuff.toString();
    }
    
    
    /**
     * Removes the quote characters from the property value
     * @return string value with quotes removed
     */
    private String removeQuoteChars(String strValue)
    throws InvalidPropertyException {
        int prefixIndex = 0;
        java.lang.StringBuffer strbuff = new java.lang.StringBuffer();
        
        while (prefixIndex < strValue.length()) {
            int delimeterIndex = getStringDelimiterIndex(strValue,
                    QUOTE_STRING, prefixIndex);
            if (delimeterIndex == -1) {
                strbuff.append(strValue.substring(prefixIndex));
                break;
            }
            strbuff.append(strValue.substring(prefixIndex, delimeterIndex));
            prefixIndex = delimeterIndex+1;
        }
        return strbuff.toString();
    }
    
    
    /**
     *  This method returns the index of the delimiter.  It will factor out the
     *  escape and quote characters.
     *  @param strToken - string to token
     *  @param delimiter - the delimiter to tokenize
     *  @param fromIndex - the index to start the tokenize
     *  @return index - index of the delimiter in the strToken
     *  @throw CommandException if the end quote do not match.
     */
    private int getStringDelimiterIndex(String strToken, String delimiter,
            int fromIndex)
            throws InvalidPropertyException {
        if (fromIndex > strToken.length()-1) return -1;
        
        //get index of the delimiter
        final int hasDelimiter = strToken.indexOf(delimiter, fromIndex);
        
        //get index of the first quote in the string token
        final int quoteBeginIndex = strToken.indexOf(QUOTE_STRING, fromIndex);
        
        // ex: set server.ias1.jdbcurl="jdbc://oracle"
        // if there's is a quote and a delimiter, then find the end quote
        if ((quoteBeginIndex != -1) && (hasDelimiter != -1) &&
                (quoteBeginIndex < hasDelimiter)) {
            //get index of the end quote in the string token
            final int quoteEndIndex = strToken.indexOf(QUOTE_STRING, quoteBeginIndex+1);
            
            if (quoteEndIndex == -1)
                throw new InvalidPropertyException("UnclosedString");
            if (quoteEndIndex != (strToken.length()-1)) {
                return getStringDelimiterIndex(strToken, delimiter, quoteEndIndex + 1);
            } else {
                return -1;
            }
        }
        if ((hasDelimiter > 0) && (strToken.charAt(hasDelimiter-1) == ESCAPE_CHAR)) {
            return getStringDelimiterIndex(strToken, delimiter, hasDelimiter+1);
        } else {
            return hasDelimiter;
        }
    }   
    
    public static void main(String[] args) throws Exception{
    	
    	//String str = "imqConsumerFlowThreshold=78,imqAddressList=\"tcp://localhost:7676,tcp://localhost:7677\"";
    	String str = "imqConsumerFlowThreshold=78,imqAddressList=tcp://localhost:7676\\,tcp://localhost:7677";
    	
    	System.out.println("Parsing "+str);
    	
        String separator = "=";
        String delimiter = ",";

        CustomTokenizer tokenList = new CustomTokenizer(str, delimiter);

        while (tokenList.hasMoreTokens()) {
            String propValuePair = (String) tokenList.nextTokenWithoutEscapeAndQuoteChars();
            
            int loc = propValuePair.indexOf(separator);
            String propName = propValuePair.substring(0, loc);
            String propValue = propValuePair.substring(loc + separator.length());
            System.out.println("name="+propName);
            System.out.println("value="+propValue);
        }

 
    }
    
    
    public static Hashtable<String, String> parseToProperties(String prop) throws InvalidPropertyException {
    	
        String separator = "=";
        String delimiter = ",";

        Hashtable<String, String> result = new Hashtable<String, String>();

        if ((prop == null) || prop.trim().equals("")) {
            return result;
        }

        CustomTokenizer tokenList = new CustomTokenizer(prop, delimiter);

        while (tokenList.hasMoreTokens()) {
            String propValuePair = (String) tokenList.nextTokenWithoutEscapeAndQuoteChars();
            
            int loc = propValuePair.indexOf(separator);
            String propName = propValuePair.substring(0, loc);
            String propValue = propValuePair.substring(loc + separator.length());
            result.put(propName, propValue);
        }

        return result;
    }
}
