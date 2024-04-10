/*
 * Copyright (c) 2000, 2018 Oracle and/or its affiliates. All rights reserved.
 * Copyright (c) 2020 Payara Services Ltd.
 * Copyright (c) 2020, 2024 Contributors to Eclipse Foundation
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

package com.sun.messaging.jmq.util.selector;

import java.util.*;

import com.sun.messaging.jmq.util.lists.WeakValueHashMap;

/**
 * A class that implements JMS selectors. See section 3.8 of the JMS 1.1 spec.
 *
 */
public class Selector {

    private static boolean DEBUG = false;
    private static boolean VERBOSE_DEBUG = false;

    // Tokens that can appear in a selector string. Note that some
    // tokens are considered "compound". I.e. they are built from
    // one or more primitive tokens.
    static final int INVALID = 500; // Illegal token
    static final int STARTING = 0;

    // Operators
    static final int OR = 1; // OR, or
    static final int AND = 2; // AND, and
    static final int NOT = 3; // NOT, not

    static final int NOT_EQUALS = 4; // <>
    static final int LTE = 5; // <=
    static final int LT = 6; // <
    static final int GTE = 7; // >=
    static final int GT = 8; // >
    static final int EQUALS = 9; // =

    static final int UNARY_PLUS = 10; // +
    static final int UNARY_MINUS = 11; // -
    static final int MULTIPLY = 12; // *
    static final int DIVIDE = 13; // /
    static final int PLUS = 14; // +
    static final int MINUS = 15; // -

    static final int BETWEEN = 16; // BETWEEN, between
    static final int NOT_BETWEEN = 17; // Compound: "NOT BETWEEN"
    static final int IN = 18; // IN
    static final int NOT_IN = 19; // Compound: "NOT IN"
    static final int LIKE = 20; // LIKE
    static final int ESCAPE = 21; // ESCAPE
    static final int NOT_LIKE = 22; // Compound: "NOT LIKE"
    static final int IS_NULL = 23; // Compound: "IS NULL"
    static final int IS_NOT_NULL = 24; // Compound: "IS NOT NULL"
    static final int IS = 25; // IS
    static final int IS_NOT = 26; // Compound "IS NOT"
    static final int LEFT_PAREN = 27; // (
    static final int RIGHT_PAREN = 28; // )

    static final int COMMA = 29; // ,
    // Operands
    static final int IDENTIFIER = 101; // Java identifier
    static final int STRING = 102; // '...'
    static final int DOUBLE = 103; // -57.92
    static final int LONG = 104; // +347
    static final int TRUE = 105; // TRUE, true
    static final int FALSE = 106; // FALSE, false
    static final int JMS_FIELD = 107; // JMS*
    static final int RANGE = 108; // Compound: "15 AND 19"
    static final int LIST = 109; // Compound: "('US', 'UK', 'Peru')"
    static final int WHITESPACE = 110; // Whitespace
    static final int NULL = 111; // NULL token
    static final int UNKNOWN = 112; // Unknown result
    static final int RE = 113; // LIKE regular expression

    // Markers
    static final int AND_MARKER = 200;
    static final int OR_MARKER = 201;

    /*
     * The JMS specification specifically states that type conversions between Strings and numeric values should not be
     * performed. See JMS 1.1 section 3.8.1.1. For example if you set a string property: msg.setStringProperty("count", "2")
     * then the following should evaluate to false because a string cannot be used in an arithmetic expression: "count = 2"
     * The above expression should be "count = '2'" The older selector implementation supported this type conversion for
     * some expressions. This introduces the possibility that some applications may be relying on this bug, and will break
     * now that it is fixed. This boolean let's use switch to the old behavior.
     *
     * If convertTypes is true, then the selector evaluator will convert string values to numeric values. Currently for only
     * = and <>
     */
    private static boolean convertTypes = false;

    /*
     * True to short circuit boolean evaluation. For example if you have "e1 AND e2", you do not need to evaluate e2 if e1
     * is false. This boolean is just a safetyvalve in case there is a flaw in the shortCircuit algorithm
     */
    private static boolean shortCircuit = true;
    private static boolean shortCircuitCompileTimeTest = true;

    private boolean usesProperties = false;
    private boolean usesFields = false;

    private static HashMap<String, Integer> keywords = null;

    private static HashSet<String> headers = null;

    // Original selector string
    private String selector = null;

    // Compiled selector string. An array of SelectorTokens in RPN
    private SelectorToken[] compiledSelector = null;

    // Stack used for evaluation
    private Stack<SelectorToken> stack = new Stack<>();

    // The selector cache is used to cache selectors. This way we can
    // return the same Selector instance for identical selector strings.
    // The selectors are cached in a WeakValueHashMap. This means once
    // the Selector is no longer referenced it is garbage collected and
    // removed from the HashMap.
    private static WeakValueHashMap<String, Selector> selectorCache = null;

    static {
        keywords = new HashMap<>();

        keywords.put("NOT", Integer.valueOf(NOT));
        keywords.put("AND", Integer.valueOf(AND));
        keywords.put("OR", Integer.valueOf(OR));
        keywords.put("BETWEEN", Integer.valueOf(BETWEEN));
        keywords.put("LIKE", Integer.valueOf(LIKE));
        keywords.put("IN", Integer.valueOf(IN));
        keywords.put("IS", Integer.valueOf(IS));
        keywords.put("ESCAPE", Integer.valueOf(ESCAPE));
        keywords.put("NULL", Integer.valueOf(NULL));
        keywords.put("TRUE", Integer.valueOf(TRUE));
        keywords.put("FALSE", Integer.valueOf(FALSE));

        headers = new HashSet<>(6);

        headers.add("JMSDeliveryMode");
        headers.add("JMSPriority");
        headers.add("JMSMessageID");
        headers.add("JMSTimestamp");
        headers.add("JMSCorrelationID");
        headers.add("JMSType");

        selectorCache = new WeakValueHashMap<>("SelectorCache");
    }

    public static void setConvertTypes(boolean b) {
        convertTypes = b;
    }

    public static boolean getConvertTypes() {
        return convertTypes;
    }

    public static void setShortCircuit(boolean b) {
        shortCircuit = b;
    }

    public static boolean getShortCircuit() {
        return shortCircuit;
    }

    public static void setShortCircuitCompileTimeTest(boolean b) {
        shortCircuitCompileTimeTest = b;
    }

    /**
     * Compiles a selector string into a Selector object. This also checks to ensure that the passed selector string is a
     * valid expression.
     *
     * @param selector Selector string as specified in JMS 1.1
     */
    public static Selector compile(String selector) throws SelectorFormatException {

        if (selector == null || selector.length() == 0) {
            return null;
        }

        Selector o = null;
        synchronized (selectorCache) {
            // First check if selector is already in cache.
            o = selectorCache.get(selector);

            if (o == null) {
                // Selector not in cache. Create a new one and stick it in the
                // cache.
                o = new Selector(selector);
                o.compile();
                selectorCache.put(selector, o);
            }
        }

        return o;
    }

    /**
     * Create a Selector.
     *
     * @param selector Selector string as specified in JMS 1.1
     */
    private Selector(String selector) {
        this.selector = selector;
    }

    /**
     * Compile the Selector
     * <p>
     * Compiles the selector into its binary form. This must be called before match(). A call to compile also performs an
     * evaluation to make sure the selector is a valid expression.
     */
    public synchronized void compile() throws SelectorFormatException {

        /*
         * This isn't the most efficient implementation possible, but compilation doesn't need to be as fast as evaluation. Note
         * that we do some extra work (mainly by tracking more token values than we need to) to make debugging easier. Also, we
         * do this in multiple passes simply because it is easier to understand, and optimizing compile speed is not a priority.
         *
         * A compiled selector consists of a token stream. This steam is held in a LinkedList initially, but is an Object[] in
         * its final compiled form. Each token is ecapsulated by a SelectorToken object. This object contains the token constant
         * (OR, STRING, etc) as well as an optional associated value (like the actual string value for a STRING token).
         *
         * By the time the compilation is done we want everything boiled down to operators and operands.
         *
         * The final compiled form is in Reverse Polish Notation (RPN).
         */

        /*
         * First pass: tokenize into primitives. Add a trailing space to the selector cleanly terminate parsing
         */
        LinkedList<SelectorToken> l = tokenize(selector + " ");

        if (VERBOSE_DEBUG) {
            dumpTokens(l);
        }

        /*
         * Second pass: aggregate primatives into compound tokens (if any) For example this converts the 3 primative tokens: IS
         * NOT NULL to the single token IS_NOT_NULL
         */
        l = aggregate(l);
        if (VERBOSE_DEBUG) {
            dumpTokens(l);
            System.out.println();
        }

        /*
         * Third pass: prepare This pass prepares some of the more funky operations (LIKE, BETWEEN, IN, etc) for evaluation. For
         * example it takes all the strings in the IN list and puts them in a hash table and making that a single operand.
         */
        l = prepare(l);
        if (VERBOSE_DEBUG) {
            dumpTokens(l);
            System.out.println();
        }

        /*
         * Fourth pass: Perform any additional validation
         */
        validate(l);

        /*
         * Fith pass: convert to RPN. This removes parens and prepares the stream for simple stack based execution. The output
         * from this is an Object[] of SelectorTokens
         */
        compiledSelector = convertToRPN(l);

        if (DEBUG) {
            System.out.println(toDebugString());
        }

        // At this point compiledSelector has a token stream that is all
        // ready for evaluation! We perform one evaluation to catch any
        // errors that may occur at runtime. We do this with empty
        // property hashtables

        this.match(new HashMap<>(0), new HashMap<>(0));

        if (shortCircuitCompileTimeTest) {
            this.match(new HashMap<>(0), new HashMap<>(0), true);
        }
    }

    /**
     * Parse selector string into token primitives. This uses a state machine to track state. Each state has a number.
     */
    private LinkedList<SelectorToken> tokenize(String selector) throws SelectorFormatException {

        LinkedList<SelectorToken> buf = new LinkedList<>();
        int len = selector.length();
        int state = 0;

        // A buffer to hold the token string.
        StringBuffer tokenBuf = new StringBuffer(80);

        int token = STARTING;
        int lastToken = STARTING;
        int radix = 10;
        int i = 0;

        for (i = 0; i < len; i++) {
            char c = selector.charAt(i);
            Object value = null;

            switch (state) {

            case 0:
                tokenBuf.delete(0, tokenBuf.length());
                switch (c) {
                case ',':
                    token = Selector.COMMA;
                    tokenBuf.append(c);
                    value = tokenBuf.toString();
                    break;
                case '=':
                    if (lastToken == Selector.EQUALS) {
                        // We do an explicit check for == since this may be
                        // a common error.
                        throw new SelectorFormatException("Invalid operator ==, use =", selector, i);
                    }
                    token = Selector.EQUALS;
                    tokenBuf.append(c);
                    value = tokenBuf.toString();
                    break;
                case '/':
                    token = Selector.DIVIDE;
                    tokenBuf.append(c);
                    value = tokenBuf.toString();
                    break;
                case '*':
                    token = Selector.MULTIPLY;
                    tokenBuf.append(c);
                    value = tokenBuf.toString();
                    break;
                case '(':
                    token = Selector.LEFT_PAREN;
                    tokenBuf.append(c);
                    value = tokenBuf.toString();
                    break;
                case ')':
                    token = Selector.RIGHT_PAREN;
                    tokenBuf.append(c);
                    value = tokenBuf.toString();
                    break;
                case '-':
                    // If last token was an operator then this is unary
                    if (lastToken == STARTING || (isOperator(lastToken) && lastToken != RIGHT_PAREN)) {
                        token = Selector.UNARY_MINUS;
                        tokenBuf.append(c);
                        value = tokenBuf.toString();
                    } else {
                        token = Selector.MINUS;
                        tokenBuf.append(c);
                        value = tokenBuf.toString();
                    }
                    break;
                case '+':
                    // If last token was an operator then this is unary
                    if (lastToken == STARTING || (isOperator(lastToken) && lastToken != RIGHT_PAREN)) {
                        token = Selector.UNARY_PLUS;
                        tokenBuf.append(c);
                        value = tokenBuf.toString();
                    } else {
                        token = Selector.PLUS;
                        tokenBuf.append(c);
                        value = tokenBuf.toString();
                    }
                    break;
                case '>':
                    // GT or GTE.
                    tokenBuf.append(c);
                    state = 1;
                    break;
                case '<':
                    // LT, LTE, or NOT_EQUALS
                    tokenBuf.append(c);
                    state = 2;
                    break;
                case '\'':
                    // Start of a string literal
                    state = 9;
                    break;
                case '.':
                    // Start of a float
                    tokenBuf.append(c);
                    state = 6;
                    break;
                case '0':
                    // Start of octal or hex numeric constant
                    tokenBuf.append(c);
                    state = 3;
                    break;
                default:
                    if (Character.isJavaIdentifierStart(c)) {
                        // Start of an identifier
                        tokenBuf.append(c);
                        state = 11;
                    } else if (Character.isDigit(c)) {
                        // Start of a number
                        tokenBuf.append(c);
                        state = 5;
                    } else if (Character.isWhitespace(c)) {
                        // Whitespace. Ignore.
                        token = Selector.WHITESPACE;
                    } else {
                        // Invalid character
                        throw new SelectorFormatException("Invalid character " + c, selector, i);
                    }
                }
                break;

            // Saw a >
            case 1:
                switch (c) {
                case '=':
                    tokenBuf.append(c);
                    token = Selector.GTE;
                    value = tokenBuf.toString();
                    state = 0;
                    break;
                default:
                    token = Selector.GT;
                    value = tokenBuf.toString();
                    state = 0;
                    i--; // pushback delimiter
                    break;
                }
                break;

            // Saw a <
            case 2:
                switch (c) {
                case '=':
                    tokenBuf.append(c);
                    token = Selector.LTE;
                    value = tokenBuf.toString();
                    state = 0;
                    break;
                case '>':
                    tokenBuf.append(c);
                    token = Selector.NOT_EQUALS;
                    value = tokenBuf.toString();
                    state = 0;
                    break;
                default:
                    token = Selector.LT;
                    value = tokenBuf.toString();
                    state = 0;
                    i--; // pushback delimiter
                    break;
                }
                break;

            // Either an octal or hex numeric constant
            case 3:
                // We go to state 5 whether it's a hex or an octal constant.
                // This means we may get something like 049h which is invalid.
                // But when we go to construct the java.lang number object
                // we'll catch this.
                if (c == 'x' || c == 'X') {
                    // Hex. Don't remember X, just that we're in base 16
                    radix = 16;
                    state = 5;
                } else if (Character.isDigit(c)) {
                    // Octal
                    radix = 8;
                    tokenBuf.append(c);
                    state = 5;
                } else {
                    // Hit a delimeter. Back up and make state 5 handle this 0
                    i--;
                    state = 5;
                }
                break;

            // Working on a number!
            case 5:
                if ((radix == 16 && isHexDigit(c)) || Character.isDigit(c)) {
                    tokenBuf.append(c);
                } else if (c == '.') {
                    // It's a float. Go get decimal portion
                    tokenBuf.append(c);
                    state = 6;
                } else if (c == 'E' || c == 'e') {
                    // It's a float. Go get exponential
                    tokenBuf.append(c);
                    state = 7;
                } else {
                    // Hit delimeter. It's just an integer
                    token = Selector.LONG;

                    // Handle this here, cause if the value is MIN_LONG
                    // we can't create the absoute value of it!
                    if (lastToken == UNARY_MINUS) {
                        tokenBuf.insert(0, '-');
                        // Remove UNARY_MINUS from token stream
                        buf.removeLast();
                    }

                    try {
                        value = Long.valueOf(tokenBuf.toString(), radix);
                        radix = 10;
                    } catch (NumberFormatException e) {
                        throw new SelectorFormatException("Invalid numeric constant: " + e.getMessage(), selector, i);
                    }
                    state = 0;

                    if (c == 'l' || c == 'L') {//NOPMD
                        // If it is a trailing L then we skip it.
                        // We always use longs
                    } else {
                        i--; // pushback delimiter
                    }
                }
                break;

            // Working on decimal portion of a float
            case 6:
                if (Character.isDigit(c)) {
                    tokenBuf.append(c);
                } else if (c == 'E' || c == 'e') {
                    // Go get exponential
                    tokenBuf.append(c);
                    state = 7;
                } else {
                    // Hit delimeter.
                    token = Selector.DOUBLE;
                    try {
                        value = Double.valueOf(tokenBuf.toString());
                    } catch (NumberFormatException e) {
                        throw new SelectorFormatException("Invalid numeric constant: " + e.getMessage(), selector, i);
                    }
                    state = 0;
                    if (c == 'd' || c == 'D' || c == 'f' || c == 'F') {//NOPMD
                        // Trailing qualifier. Just skip it. Everything is a D
                    } else {
                        i--; // pushback delimiter
                    }
                }
                break;

            // Starting to work on exponential portion of a float
            case 7:
                if (Character.isDigit(c)) {
                    tokenBuf.append(c);
                    state = 8;
                } else if (c == '-') {
                    tokenBuf.append(c);
                    state = 8;
                } else {
                    // Hit delimeter. Nothing after the E
                    token = Selector.DOUBLE;
                    try {
                        value = Double.valueOf(tokenBuf.toString());
                    } catch (NumberFormatException e) {
                        throw new SelectorFormatException("Invalid numeric constant: " + e.getMessage(), selector, i);
                    }
                    state = 0;
                    if (c == 'd' || c == 'D' || c == 'f' || c == 'F') {//NOPMD
                        // Trailing qualifier. Just skip it. Everything is a D
                    } else {
                        i--; // pushback delimiter
                    }
                }
                break;

            // Finishing work on exponential portion of a float
            case 8:
                if (Character.isDigit(c)) {
                    tokenBuf.append(c);
                } else {
                    // Hit delimeter.
                    token = Selector.DOUBLE;
                    try {
                        value = Double.valueOf(tokenBuf.toString());
                    } catch (NumberFormatException e) {
                        throw new SelectorFormatException("Invalid numeric constant: " + e.getMessage(), selector, i);
                    }
                    state = 0;
                    if (c == 'd' || c == 'D' || c == 'f' || c == 'F') {//NOPMD
                        // Trailing qualifier. Just skip it. Everything is a D
                    } else {
                        i--; // pushback delimiter
                    }
                }
                break;

            // Working on a string literal
            case 9:
                if (c == '\'') {
                    state = 10;
                } else {
                    tokenBuf.append(c);
                }
                break;

            // Is this the end of a string? Or an escaped single quote
            case 10:
                if (c == '\'') {
                    // Escaped single quote. Put it in token and continue
                    state = 9;
                    tokenBuf.append(c);
                } else {
                    // Hit delimeter.
                    token = Selector.STRING;
                    value = tokenBuf.toString();
                    state = 0;
                    i--; // pushback delimiter
                }
                break;

            // Working on an identifier
            case 11:
                if (Character.isJavaIdentifierPart(c)) {
                    tokenBuf.append(c);
                } else {
                    value = tokenBuf.toString();
                    // OK, we either have an identifier, or a keyword.
                    // this method handles figuring that out.
                    token = identifierToKeyWord((String) value);
                    state = 0;
                    i--; // pushback delimiter
                }
                break;
            default:
                // This should never happen.
                throw new SelectorFormatException("Selector tokenizer in bad state: " + state + " tokenBuf=" + tokenBuf + " char=" + c, selector, i);
            }

            // We detect if the Selector uses message properties
            // (as opposed to just JMS fields).
            if (token == Selector.IDENTIFIER) {
                usesProperties = true;
            } else if (token == Selector.JMS_FIELD) {
                usesFields = true;
            }

            if (state == 0 && token == Selector.INVALID) {
                // This should never happen.
                throw new SelectorFormatException("Unknown token: " + token + " tokenBuf=" + tokenBuf, selector, i);
            }

            if (state == 0 && token != Selector.WHITESPACE) {
                buf.add(SelectorToken.getInstance(token, value));
                lastToken = token;
                radix = 10;
            }
        }

        if (state == 9) {
            // Missing closing quote
            throw new SelectorFormatException("Missing closing quote", selector, i);
        } else if (state != 0) {
            throw new SelectorFormatException("Invalid Expression", selector, i);
        }

        return buf;
    }

    // Check if s is a keyword, JMS field, or generic identifier
    private int identifierToKeyWord(String s) {
        Integer n = keywords.get(s.toUpperCase());
        if (n != null) {
            return n;
        } else if (s.startsWith("JMS")) {
            if (headers.contains(s)) {
                return JMS_FIELD;
            } else {
                return IDENTIFIER;
            }
        } else {
            return IDENTIFIER;
        }
    }

    private boolean isHexDigit(char c) {
        return (Character.isDigit(c) || c == 'a' || c == 'A' || c == 'b' || c == 'B' || c == 'c' || c == 'C' || c == 'd' || c == 'D' || c == 'e' || c == 'E'
                || c == 'f' || c == 'F');
    }

    /**
     * Aggregate primitives into compound tokens (if any). This performs the following conversions: NOT BETWEEN =>
     * NOT_BETWEEN NOT IN => NOT_IN NOT LIKE => NOT_LIKE IS NULL => IS_NULL IS NOT NULL => IS_NOT_NULL
     */
    private LinkedList<SelectorToken> aggregate(LinkedList<SelectorToken> in) throws SelectorFormatException {
        LinkedList<SelectorToken> out = new LinkedList<>();

        SelectorToken token0;
        SelectorToken token1;
        SelectorToken token2;
        int len = in.size();

        for (int i = 0; i < len; i++) {
            token0 = in.get(i);
            token1 = null;
            token2 = null;

            if (i + 1 < len) {
                token1 = in.get(i + 1);
            }

            if (i + 2 < len) {
                token2 = in.get(i + 2);
            }

            switch (token0.getToken()) {
            case Selector.NOT:
                if (token1 == null) {
                    // NOT
                    out.add(token0);
                } else if (token1.getToken() == Selector.BETWEEN) {
                    // NOT BETWEEN
                    out.add(SelectorToken.getInstance(Selector.NOT_BETWEEN, (String) token0.getValue() + " " + (String) token1.getValue()));
                    // Skip BETWEEN
                    i++;
                } else if (token1.getToken() == Selector.IN) {
                    // NOT IN
                    out.add(SelectorToken.getInstance(Selector.NOT_IN, (String) token0.getValue() + " " + (String) token1.getValue()));
                    // Skip IN
                    i++;
                } else if (token1.getToken() == Selector.LIKE) {
                    // NOT LIKE
                    out.add(SelectorToken.getInstance(Selector.NOT_LIKE, (String) token0.getValue() + " " + (String) token1.getValue()));
                    // Skip LIKE
                    i++;
                } else {
                    // NOT
                    out.add(token0);
                }
                break;

            case Selector.IS:
                if (token1 == null) {
                    // just IS
                    out.add(token0);
                } else if (token1.getToken() == Selector.NULL) {
                    // IS NULL
                    out.add(SelectorToken.getInstance(Selector.IS_NULL, (String) token0.getValue() + " " + (String) token1.getValue()));
                    // Skip NULL
                    i++;
                } else if (token1.getToken() == Selector.NOT) {
                    // IS NOT
                    if (token2 == null) {
                        // just IS NOT
                        out.add(SelectorToken.getInstance(Selector.IS_NOT, (String) token0.getValue() + " " + (String) token1.getValue()));
                        // Skip NOT
                        i++;
                    } else if (token2.getToken() == Selector.NULL) {
                        // IS NOT NULL
                        out.add(SelectorToken.getInstance(Selector.IS_NOT_NULL,
                                (String) token0.getValue() + " " + (String) token1.getValue() + " " + (String) token2.getValue()));
                        // Skip NOT NULL
                        i++;
                        i++;
                    } else {
                        // just IS NOT
                        out.add(SelectorToken.getInstance(Selector.IS_NOT, (String) token0.getValue() + " " + (String) token1.getValue()));
                        // Skip NOT
                        i++;
                    }
                } else {
                    // Just IS
                    out.add(token0);
                }
                break;
            default:
                // Simple token
                out.add(token0);
                break;
            }
        }

        return out;
    }

    /**
     * Prepare list for conversion to RPN. This step prepares some of the more funky operations into a format that can be
     * more easily evaluated using a simple RPN expression evaluator. It performs the following:
     *
     * Replaces the AND in the BETWEEN and NOT_BETWEEN constructs with a comma. The comma ensures we correctly convert the
     * arithmetic expressions in the BETWEEN ranges to RPN. This is especially true when you take into account unary minus
     * (ie BETWEEN - 1 and 5). Then BETWEEN is just treated as an operator that requires 3 operands and the COMMA is
     * ignmored.
     *
     * Converts the list construct in the IN and NOT_IN operations into a single token (operand) that has a HashMap for it's
     * value.
     *
     * Detects the ESCAPE keyword and converts the LIKE regular expression string into a simple object that continas the
     * string and the escape character, so when we go to evaluate it we can do the right thing based on the RE package we
     * use.
     */
    private LinkedList<SelectorToken> prepare(LinkedList<SelectorToken> in) throws SelectorFormatException {
        LinkedList<SelectorToken> out = new LinkedList<>();

        SelectorToken token0;
        int len = in.size();

        for (int i = 0; i < len; i++) {
            token0 = in.get(i);

            switch (token0.getToken()) {

            case Selector.BETWEEN:
            case Selector.NOT_BETWEEN:
                out.add(token0);
                i++;
                // OKAY we saw a BETWEEN. Scan forward until we hit an AND
                // and convert it to a COMMA
                while (i < len) {
                    token0 = in.get(i);
                    if (token0.getToken() == Selector.AND) {
                        out.add(SelectorToken.getInstance(Selector.COMMA, ","));
                        break;
                    }
                    out.add(token0);
                    i++;
                }
                break;

            case Selector.IN:
            case Selector.NOT_IN:
                out.add(token0);
                i++;
                token0 = in.get(i);
                if (token0.getToken() != Selector.LEFT_PAREN) {
                    throw new SelectorFormatException("Missing ( in IN statement", selector);
                }
                // Skip open paren
                i++;
                // OK convert list of strings into a HashSet
                HashSet<Object> set = new HashSet<>();
                while (i < len) {
                    token0 = in.get(i);

                    if (token0.getToken() == Selector.RIGHT_PAREN) {
                        // skip close paren and terminate
                        break;
                    }

                    if (token0.getToken() == Selector.COMMA) {
                        // skip commas
                        i++;
                        continue;
                    }

                    if (token0.getToken() != Selector.STRING) {
                        throw new SelectorFormatException("IN requires string literal: " + token0.getValue(), selector);
                    }

                    // Put string in HashMap
                    set.add(token0.getValue());
                    i++;
                }

                // Put list token with HashSet as value. This now becomes
                // the right operand for IN and NOT_IN
                out.add(SelectorToken.getInstance(Selector.LIST, set));
                break;

            case Selector.LIKE:
            case Selector.NOT_LIKE:
                out.add(token0);
                i++;
                // String literal should be next token
                token0 = in.get(i);
                if (token0.getToken() != Selector.STRING) {
                    throw new SelectorFormatException("LIKE requires string literal: " + token0.getValue(), selector);
                }

                // String literal is the regular expression
                String re = (String) token0.getValue();
                String escape = null;
                i++;
                if (i < len) {
                    token0 = in.get(i);
                    if (token0.getToken() == Selector.ESCAPE) {
                        // Get escape string
                        i++;
                        token0 = in.get(i);
                        if (token0.getToken() != Selector.STRING) {
                            throw new SelectorFormatException("ESCAPE requires string literal: " + token0.getValue(), selector);
                        } else {
                            escape = (String) token0.getValue();
                        }
                    } else {
                        i--; // push back token since it wasn't ESCAPE
                    }
                }
                out.add(SelectorToken.getInstance(Selector.RE, new RegularExpression(re, escape)));
                break;

            default:
                // Simple token
                out.add(token0);
                break;
            }
        }

        return out;
    }

    /**
     * Validate expression This does a simple, final syntax check before conversion to RPN. It detects invalid expressions
     * such as "= red 'color'" and "color = red AND AND shape = round"
     */
    private void validate(LinkedList<SelectorToken> in) throws SelectorFormatException {

        SelectorToken token;
        int len = in.size();
        int prevToken = STARTING;

        for (int i = 0; i < len; i++) {
            token = in.get(i);

            // If the current token is an operand, then the previous
            // token must be an operator (or STARTING)
            if (!isOperator(token)) {
                if (prevToken != STARTING && !isOperator(prevToken)) {
                    throw new SelectorFormatException("Missing operator", selector);
                }
            } else {
                if (prevToken == token.getToken() && prevToken != LEFT_PAREN && prevToken != RIGHT_PAREN) {
                    throw new SelectorFormatException("Missing operand", selector);
                }
            }

            prevToken = token.getToken();
        }

    }

    /**
     * Convert the token stream into Reverse Polish Notation (aka RPN or postfix notation). This helps detect syntax errors
     * and prepares the expression for evaluation. Here is the procedure for converting infix to postfix:
     *
     * Scan infix expression from left to right. A. When an operand is encountered move it immediately to the RPN
     * expression. B. When an operator is encountered: 1. First pop operators from the stack and place them into the RPN
     * expression until either the stack is empty or the precedence level of the top operator in the stack is LESS than the
     * precedence of the operator encountered in the scan. 2. Then push the operator encountered onto the stack C. When a
     * left paren is encountered push it onto the stack (it creates a "sub-stack"). D. When unstacking operators stop when a
     * left paren comes to the top of the stack. E. When a right paren is encountered when scanning the expression unstack
     * operators until a matching left paren is found in the stack. Pop left paren and disgard. Disgard right paren. F. When
     * the entire expression has been scanned pop any remaining operators from the stack and place into the RPN expression.
     *
     * The following is done to support evaluation short circuit:
     *
     * After you have pushed an AND (OR) operator onto the stack, insert the AND_MARKER (OR_MARKER) into the RPN expression.
     */
    private SelectorToken[] convertToRPN(LinkedList<SelectorToken> in) throws SelectorFormatException {
        Stack<SelectorToken> stack = new Stack<>();

        // For this final pass we convert to a fixed size array to
        // make final evaluation faster. We make the array larger to
        // handle markers if we have any.
        SelectorToken[] out = new SelectorToken[(int) (in.size() * 1.5)];
        int i = 0;

        for (SelectorToken token : in) {
            if (!isOperator(token)) {
                // Operand. Move directly to RPN
                out[i++] = token;
                continue;
            }

            if (token.getToken() == LEFT_PAREN) {
                // Push ( immediately on stack
                stack.push(token);
                continue;
            }

            SelectorToken t = null;
            if (token.getToken() == RIGHT_PAREN) {
                // Pop operators until we encounter a left paren
                do {
                    if (stack.empty()) {
                        throw new SelectorFormatException("Missing (", selector);
                    }

                    t = stack.pop();
                    if (t.getToken() != LEFT_PAREN) {
                        out[i++] = t;
                    }
                } while (t.getToken() != LEFT_PAREN);
                continue;
            }

            // Operator is not a paren. Copy operators off of stack
            // until we hit one with a lower priority than the one
            // from the scanned expression.
            while (!stack.empty()) {
                t = stack.peek();
                if (t.getToken() == LEFT_PAREN) {
                    break;
                }
                if (getPrecedence(t) < getPrecedence(token)) {
                    // Stop if precedence of top operator is less than
                    // operator from expression scan.
                    break;
                }
                // Copy higher precedence operators to RPN expression
                out[i++] = stack.pop();
            }

            // Push operator from scanned expression onto stack
            stack.push(token);

            if (shortCircuit) {
                // Markers are used to short circuit expression evaluation
                // If we just pushed an AND or OR onto the stack, put the
                // corresponding marker into the expression
                if (token.getToken() == Selector.AND) {
                    out[i++] = SelectorToken.getInstance(Selector.AND_MARKER);
                } else if (token.getToken() == Selector.OR) {
                    out[i++] = SelectorToken.getInstance(Selector.OR_MARKER);
                }
            }
        }

        // Expression has been scanned. Pop all remaining operators
        // off of stack and put in expression.
        while (!stack.empty()) {
            try {
                out[i] = stack.pop();
            } catch (IndexOutOfBoundsException e) {
                SelectorFormatException ex = new SelectorFormatException("Bad selector ", selector);
                ex.initCause(e);
                throw ex;
            }
            if (out[i].getToken() == LEFT_PAREN) {
                throw new SelectorFormatException("Missing )", selector);
            }
            i++;
        }

        return out;
    }

    /**
     * Evaluate the selector using the passed properties and message fields. compile() must have been called before calling
     * match().
     *
     * @param properties HashMap containing message properties. These should be String/Object pairs. If usesProperties()
     * returns 'false' then message properties are not needed to evaluate the expression and this parameter may be null.
     * @param fields HashMap containing JMS Message fields. These should be String/Object pairs. If usesFields() returns
     * 'false' then JMS fields are not needed to evaluate the expression and this parameter may be null.
     *
     * @return true if expression evaluates to true, else false.
     *
     * @throws SelectorFormatException if the selector syntax is invalid
     */
    public synchronized boolean match(Map<Object, Object> properties, Map<Object, Object> fields) throws SelectorFormatException {
        return match(properties, fields, false);
    }

    private synchronized boolean match(Map<Object, Object> properties, Map<Object, Object> fields, boolean compileTestShortCircuit) throws SelectorFormatException {

        /*
         * This method is synchronized primarily because of the runtime stack. If the stack was local then we wouldn't need to
         * synchronize, but we'd be creating a new stack on every match() call.
         */

        /*
         * You evaluate an RPN using a stack. It goes like this: A. Scan RPN expression from left to right B. When you encounter
         * an operand push it onto the stack. C. When you encounter an operator pop off as many operands as you need (in our
         * case 1, 2 or 3), and apply operator to the operands. D. Push result onto the stack E. When scan is complete the final
         * result is on top of the stack
         *
         * The following is performed when supporting evaluation short circuit:
         *
         * If an AND_MARKER is encountered during scanning: If the top of the evaluation stack is FALSE then scan the RPN
         * expression until the AND operator is encountered. Skip the AND operator. If during this scan additional AND_MARKERS
         * are encountered then continue scanning expression until you have skipped as man AND operators as AND_MARKERS
         * encountered. Then continue evaluation. Else skip the AND_MARKER and continue evaluation.
         *
         * If an OR_MARKER is encountered during scanning: If the top of the evaluation stack is TRUE then scan the RPN
         * expression until the OR operator is encountered. Skip the OR operator. If during this scan additional OR_MARKERS are
         * encountered then continue scanning expression until you have skipped as man OR operators as OR_MARKERS encountered.
         * Then continue evaluation. Else skip the OR_MARKER and continue evaluation.
         */

        stack.clear();
        SelectorToken token, operand1, operand2;

        int markers = 0;

        try {

            for (int i = 0; i < compiledSelector.length; i++) {
                token = compiledSelector[i];

                if (token == null) {
                    // RPN may be shorter than the original since we
                    // remove parens.
                    break;
                }

                if (shortCircuit) {

                    // Short circuit boolean expressions
                    if (token.getToken() == Selector.AND_MARKER) {
                        // We hit an AND_MARKER.
                        int t = stack.peek().getToken();
                        if (compileTestShortCircuit) {
                            t = Selector.TRUE;
                        }
                        if (t == Selector.FALSE) {
                            // Top of eval stack is FALSE. Short circuit by scanning
                            // until we hit an AND operator. If we see other AND_MARKERS
                            // we must skip as many operators as markers.
                            markers = 1;
                            while (markers > 0) {
                                token = compiledSelector[++i];
                                if (token.getToken() == Selector.AND_MARKER) {
                                    markers++;
                                } else if (token.getToken() == Selector.AND) {
                                    markers--;
                                }
                            }
                            // Completed short circuit. Continue evaluation
                            continue;
                        } else {
                            // Not a short circuit. Skip marker and continue
                            continue;
                        }
                    } else if (token.getToken() == Selector.OR_MARKER) {
                        // We hit an OR_MARKER.
                        int t = stack.peek().getToken();
                        if (compileTestShortCircuit) {
                            t = Selector.TRUE;
                        }
                        if (t == Selector.TRUE) {
                            // Top of eval stack is TRUE. Short circuit by scanning
                            // until we hit an OR operator. If we see other OR_MARKERS
                            // we must skip as many operators as markers.
                            markers = 1;
                            while (markers > 0) {
                                token = compiledSelector[++i];
                                if (token.getToken() == Selector.OR_MARKER) {
                                    markers++;
                                } else if (token.getToken() == Selector.OR) {
                                    markers--;
                                }
                            }
                            // Completed short circuit. Continue evaluation
                            continue;
                        } else {
                            // Not a short circuit. Skip marker and continue
                            continue;
                        }
                    }

                } // if shortCircuit

                // Push operands onto stack
                if (!isOperator(token)) {
                    if (token.getToken() == IDENTIFIER) {
                        // Expand identifier
                        Object value;
                        if (properties == null) {
                            value = null;
                        } else {
                            value = properties.get(token.getValue());
                        }
                        if (value == null) {
                            stack.push(SelectorToken.getInstance(UNKNOWN, null));
                        } else {
                            stack.push(propertyToToken(value));
                        }
                    } else if (token.getToken() == JMS_FIELD) {
                        // Expand identifier
                        Object value;
                        if (fields == null) {
                            value = null;
                        } else {
                            value = fields.get(token.getValue());
                        }
                        if (value == null) {
                            stack.push(SelectorToken.getInstance(UNKNOWN, null));
                        } else {
                            stack.push(propertyToToken(value));
                        }
                    } else {
                        // A literal operand
                        stack.push(token);
                    }
                    continue;
                }

                if (token.getToken() == COMMA) {
                    // Comma's are no-ops. They were there to ensure conversion
                    // from infix to RPN went correctly for BETWEEN ranges.
                    continue;
                }

                // Handle operator. We know we'll need at least one operand
                // so get it now.
                operand1 = stack.pop();

                // Process operator
                switch (token.getToken()) {

                // For OR, AND, and NOT we have to handle UNKNOWN.
                // See Section 3.8.1.2 of the JMS 1.1 spec
                case OR:
                    operand2 = stack.pop();
                    if (operand1.getToken() == TRUE || operand2.getToken() == TRUE) {
                        stack.push(SelectorToken.getInstance(TRUE));
                    } else if (operand1.getToken() == FALSE && operand2.getToken() == FALSE) {
                        stack.push(SelectorToken.getInstance(FALSE));
                    } else {
                        stack.push(SelectorToken.getInstance(UNKNOWN));
                    }
                    break;
                case AND:
                    operand2 = stack.pop();
                    if (operand1.getToken() == TRUE && operand2.getToken() == TRUE) {
                        stack.push(SelectorToken.getInstance(TRUE));
                    } else if (operand1.getToken() == FALSE || operand2.getToken() == FALSE) {
                        stack.push(SelectorToken.getInstance(FALSE));
                    } else {
                        stack.push(SelectorToken.getInstance(UNKNOWN));
                    }
                    break;
                case NOT:
                    if (operand1.getToken() == TRUE) {
                        stack.push(SelectorToken.getInstance(FALSE));
                    } else if (operand1.getToken() == FALSE) {
                        stack.push(SelectorToken.getInstance(TRUE));
                    } else {
                        stack.push(SelectorToken.getInstance(UNKNOWN));
                    }
                    break;
                case EQUALS:
                    operand2 = stack.pop();

                    if (isNumeric(operand1) || isNumeric(operand2)) {
                        stack.push(doNumericOperation(token, operand2, operand1));
                    } else if (operand1.equals(operand2)) {
                        stack.push(SelectorToken.getInstance(TRUE));
                    } else {
                        stack.push(SelectorToken.getInstance(FALSE));
                    }
                    break;
                case NOT_EQUALS:
                    operand2 = stack.pop();

                    if (isNumeric(operand1) || isNumeric(operand2)) {
                        stack.push(doNumericOperation(token, operand2, operand1));
                    } else if (operand1.equals(operand2)) {
                        stack.push(SelectorToken.getInstance(FALSE));
                    } else {
                        stack.push(SelectorToken.getInstance(TRUE));
                    }
                    break;

                case LT:
                case LTE:
                case GT:
                case GTE:
                    operand2 = stack.pop();

                    // operand2 is first. It is actually the first
                    // operation. They are reversed on the stack
                    stack.push(doNumericOperation(token, operand2, operand1));
                    break;

                case PLUS:
                case MINUS:
                case MULTIPLY:
                case DIVIDE:
                    operand2 = stack.pop();
                    stack.push(doNumericOperation(token, operand2, operand1));
                    break;

                case UNARY_MINUS:
                    stack.push(doNumericOperation(token, operand1, null));
                    break;

                case UNARY_PLUS:
                    stack.push(doNumericOperation(token, operand1, null));
                    break;

                case BETWEEN:
                case NOT_BETWEEN:
                    // Operand 1 is the second range value
                    SelectorToken max = operand1;

                    // Operand 2 is the first range value
                    SelectorToken min = stack.pop();

                    // Operand 3 is the operand on the left side of BETWEEN
                    SelectorToken operand = stack.pop();

                    boolean between = false;

                    // The operands may be floats or longs. We use
                    // doNumericOperation to handle this for us
                    if (doNumericOperation(SelectorToken.getInstance(GTE), operand, min).getToken() == TRUE
                            && doNumericOperation(SelectorToken.getInstance(LTE), operand, max).getToken() == TRUE) {
                        between = true;
                    }

                    if (token.getToken() == BETWEEN) {
                        if (between) {
                            stack.push(SelectorToken.getInstance(TRUE));
                        } else {
                            stack.push(SelectorToken.getInstance(FALSE));
                        }
                    } else {
                        if (between) {
                            stack.push(SelectorToken.getInstance(FALSE));
                        } else {
                            stack.push(SelectorToken.getInstance(TRUE));
                        }
                    }
                    break;

                case IN:
                case NOT_IN:

                    // operand2 is the identifier
                    operand2 = stack.pop();

                    if (!(operand2.getValue() instanceof String)) {
                        throw new SelectorFormatException("IN requires string operand: " + operand2.getValue(), selector);
                    }

                    // operand1 is the string list
                    HashSet set = (HashSet) operand1.getValue();

                    if (operand2.getToken() == UNKNOWN) {
                        // If operand is unknow, result is unknown.
                        stack.push(SelectorToken.getInstance(FALSE));
                    } else if (set.contains(operand2.getValue())) {
                        if (token.getToken() == IN) {
                            stack.push(SelectorToken.getInstance(TRUE));
                        } else {
                            stack.push(SelectorToken.getInstance(FALSE));
                        }
                    } else {
                        if (token.getToken() == IN) {
                            stack.push(SelectorToken.getInstance(FALSE));
                        } else {
                            stack.push(SelectorToken.getInstance(TRUE));
                        }
                    }
                    break;

                case LIKE:
                case NOT_LIKE:
                    // operand2 is the identifier
                    operand2 = stack.pop();

                    if (!(operand2.getValue() instanceof String)) {
                        throw new SelectorFormatException("LIKE requires string operand: " + operand2.getValue(), selector);
                    }

                    // operand1 is the RE
                    RegularExpression re = (RegularExpression) operand1.getValue();

                    if (operand2.getToken() == UNKNOWN) {
                        // If operand is unknow, result is unknown.
                        stack.push(SelectorToken.getInstance(FALSE));
                    } else if (re.match((String) operand2.getValue())) {
                        if (token.getToken() == LIKE) {
                            stack.push(SelectorToken.getInstance(TRUE));
                        } else {
                            stack.push(SelectorToken.getInstance(FALSE));
                        }
                    } else {
                        if (token.getToken() == LIKE) {
                            stack.push(SelectorToken.getInstance(FALSE));
                        } else {
                            stack.push(SelectorToken.getInstance(TRUE));
                        }
                    }
                    break;

                case IS_NULL:
                    if (operand1.getToken() == UNKNOWN) {
                        stack.push(SelectorToken.getInstance(TRUE));
                    } else {
                        stack.push(SelectorToken.getInstance(FALSE));
                    }
                    break;
                case IS_NOT_NULL:
                    if (operand1.getToken() != UNKNOWN) {
                        stack.push(SelectorToken.getInstance(TRUE));
                    } else {
                        stack.push(SelectorToken.getInstance(FALSE));
                    }
                    break;
                default:
                    throw new SelectorFormatException("Unknown operator: " + token, selector);
                }
            }

            // All done!
            // The top of the stack better hold a boolean!
            token = stack.pop();

        } catch (java.util.EmptyStackException e) {
            SelectorFormatException ex = new SelectorFormatException("Missing operand", selector);
            ex.initCause(e);
            throw ex;
        } catch (java.lang.ArithmeticException e) {
            SelectorFormatException ex = new SelectorFormatException(e.toString(), selector);
            ex.initCause(e);
            throw ex;
        }

        if (!stack.empty()) {
            throw new SelectorFormatException("Missing operator", selector);
        } else if (token.getToken() == TRUE) {
            return true;
        } else if (token.getToken() == FALSE) {
            return false;
        } else if (token.getToken() == UNKNOWN) {
            return false;
        } else {
            throw new SelectorFormatException("Non-boolean expression", selector);
        }
    }

    private SelectorToken propertyToToken(Object value) {
        if (value instanceof String) {
            return SelectorToken.getInstance(STRING, value);
        } else if (value instanceof Boolean) {
            boolean b = ((Boolean) value).booleanValue();
            if (b) {
                return SelectorToken.getInstance(TRUE);
            } else {
                return SelectorToken.getInstance(FALSE);
            }
        } else if (value instanceof Double) {
            return SelectorToken.getInstance(DOUBLE, value);
        } else if (value instanceof Float) {
            double d = ((Float) value).floatValue();
            return SelectorToken.getInstance(DOUBLE, Double.valueOf(d));
        } else if (value instanceof Long) {
            return SelectorToken.getInstance(LONG, value);
        } else if (value instanceof Integer) {
            long l = ((Integer) value).intValue();
            return SelectorToken.getInstance(LONG, Long.valueOf(l));
        } else if (value instanceof Short) {
            long l = ((Short) value).shortValue();
            return SelectorToken.getInstance(LONG, Long.valueOf(l));
        } else if (value instanceof Byte) {
            long l = ((Byte) value).byteValue();
            return SelectorToken.getInstance(LONG, Long.valueOf(l));
        }

        return null;
    }

    private SelectorToken convertStringToNumber(String s) throws SelectorFormatException {

        try {
            Long l = Long.valueOf(s);
            return SelectorToken.getInstance(LONG, l);
        } catch (NumberFormatException e) {
            try {
                // Hmmm...maybe it's a double
                Double d = Double.valueOf(s);
                return SelectorToken.getInstance(DOUBLE, d);
            } catch (NumberFormatException e2) {
                throw new SelectorFormatException("Cannot convert string to number '" + s + "'", selector);
            }
        }
    }

    /**
     * Perform a numeric operation.
     *
     * The operands are either Long or Double.
     */
    private SelectorToken doNumericOperation(SelectorToken t, SelectorToken op1, SelectorToken op2) throws SelectorFormatException {

        boolean b = false;
        boolean is1L = false;
        boolean is2L = false;
        long val1L = 0, val2L = 0;
        double val1D = 0, val2D = 0;

        if ((!isNumeric(op1) && op1.getToken() != UNKNOWN)) {
            if (convertTypes && op1.getToken() == STRING) {
                op1 = convertStringToNumber((String) op1.getValue());
            } else {
                throw new SelectorFormatException("Non-numeric argument '" + op1.getValue() + "'", selector);
            }
        }

        if (op2 != null && (!isNumeric(op2) && op2.getToken() != UNKNOWN)) {
            if (convertTypes && op2.getToken() == STRING) {
                op2 = convertStringToNumber((String) op2.getValue());
            } else {
                throw new SelectorFormatException("Non-numeric argument '" + op2.getValue() + "'", selector);
            }
        }

        if (op1.getToken() == UNKNOWN || (op2 != null && op2.getToken() == UNKNOWN)) {
            // Operation with a UNKNOWN argument is always UNKNOWN
            return SelectorToken.getInstance(UNKNOWN);
        }

        if (op1.getValue() instanceof Long) {
            is1L = true;
            val1L = ((Long) op1.getValue()).longValue();
            val1D = ((Long) op1.getValue()).doubleValue();
        } else {
            is1L = false;
            val1L = ((Double) op1.getValue()).longValue();
            val1D = ((Double) op1.getValue()).doubleValue();
        }

        if (op2 != null) {
            if (op2.getValue() instanceof Long) {
                is2L = true;
                val2L = ((Long) op2.getValue()).longValue();
                val2D = ((Long) op2.getValue()).doubleValue();
            } else {
                is2L = false;
                val2L = ((Double) op2.getValue()).longValue();
                val2D = ((Double) op2.getValue()).doubleValue();
            }
        }

        switch (t.getToken()) {
        case EQUALS:
        case NOT_EQUALS:
            if (is1L && is2L) {
                b = val1L == val2L;
            } else if (is1L) {
                b = val1L == val2D;
            } else if (is2L) {
                b = val1D == val2L;
            } else {
                b = val1D == val2D;
            }
            if (t.getToken() == EQUALS) {
                return SelectorToken.getInstance(b ? TRUE : FALSE);
            } else {
                return SelectorToken.getInstance(b ? FALSE : TRUE);
            }

        case LT:
            if (is1L && is2L) {
                b = val1L < val2L;
            } else if (is1L) {
                b = val1L < val2D;
            } else if (is2L) {
                b = val1D < val2L;
            } else {
                b = val1D < val2D;
            }
            return SelectorToken.getInstance(b ? TRUE : FALSE);
        case LTE:
            if (is1L && is2L) {
                b = val1L <= val2L;
            } else if (is1L) {
                b = val1L <= val2D;
            } else if (is2L) {
                b = val1D <= val2L;
            } else {
                b = val1D <= val2D;
            }
            return SelectorToken.getInstance(b ? TRUE : FALSE);
        case GT:
            if (is1L && is2L) {
                b = val1L > val2L;
            } else if (is1L) {
                b = val1L > val2D;
            } else if (is2L) {
                b = val1D > val2L;
            } else {
                b = val1D > val2D;
            }
            return SelectorToken.getInstance(b ? TRUE : FALSE);
        case GTE:
            if (is1L && is2L) {
                b = val1L >= val2L;
            } else if (is1L) {
                b = val1L >= val2D;
            } else if (is2L) {
                b = val1D >= val2L;
            } else {
                b = val1D >= val2D;
            }
            return SelectorToken.getInstance(b ? TRUE : FALSE);
        case PLUS:
            if (is1L && is2L) {
                long v = val1L + val2L;
                return SelectorToken.getInstance(LONG, Long.valueOf(v));
            } else {
                double d = val1D + val2D;
                return SelectorToken.getInstance(DOUBLE, Double.valueOf(d));
            }
        case UNARY_PLUS:
            // Unary plus is a no-op
            return (op1);

        case MINUS:
            if (is1L && is2L) {
                long v = val1L - val2L;
                return SelectorToken.getInstance(LONG, Long.valueOf(v));
            } else {
                double d = val1D - val2D;
                return SelectorToken.getInstance(DOUBLE, Double.valueOf(d));
            }
        case UNARY_MINUS:
            if (is1L) {
                long v = -val1L;
                return SelectorToken.getInstance(LONG, Long.valueOf(v));
            } else {
                double d = -val1D;
                return SelectorToken.getInstance(DOUBLE, Double.valueOf(d));
            }
        case MULTIPLY:
            if (is1L && is2L) {
                long v = val1L * val2L;
                return SelectorToken.getInstance(LONG, Long.valueOf(v));
            } else {
                double d = val1D * val2D;
                return SelectorToken.getInstance(DOUBLE, Double.valueOf(d));
            }
        case DIVIDE:
            if (is1L && is2L) {
                long v = val1L / val2L;
                return SelectorToken.getInstance(LONG, Long.valueOf(v));
            } else {
                double d = val1D / val2D;
                return SelectorToken.getInstance(DOUBLE, Double.valueOf(d));
            }
        default:
            throw new SelectorFormatException("Unknown numeric operation: " + t, selector);
        }

    }

    private static boolean isNumeric(SelectorToken t) {
        int tok = t.getToken();
        return ((tok == DOUBLE) || (tok == LONG));
    }

    private static boolean isOperator(SelectorToken t) {
        return (t.getToken() < 100);
    }

    private static boolean isOperator(int t) {
        return (t < 100);
    }

    // Return a number representing the precedene of an operator:
    //
    private static int getPrecedence(SelectorToken t) {

        switch (t.getToken()) {

        case OR:
            return 10;

        case AND:
            return 11;

        case NOT:
            return 12;

        case EQUALS:
        case NOT_EQUALS:
            return 20;

        case LT:
        case LTE:
        case GT:
        case GTE:
            return 21;

        case IN:
        case NOT_IN:
        case LIKE:
        case NOT_LIKE:
        case IS_NULL:
        case IS_NOT_NULL:
        case BETWEEN:
        case NOT_BETWEEN:
            return 30;

        case PLUS:
        case MINUS:
            return 40;

        case MULTIPLY:
        case DIVIDE:
            return 41;

        case COMMA:
            return 42;

        case UNARY_PLUS:
        case UNARY_MINUS:
            return 43;

        case LEFT_PAREN:
        case RIGHT_PAREN:
            return 50;

        default:
            return 1;
        }
    }

    /**
     * Two Selector instances are equal if they are the same instance or if the selector strings match.
     */
    @Override
    public boolean equals(Object o) {

        // Since we cache Selectors it should be the case that Selectors
        // with the same selector string will be the same instance.
        if (this == o) {
            return true;
        }

        if (!(o instanceof Selector)) {
            return false;
        }

        Selector obj = (Selector) o;
        return (this.selector.equals(obj.selector));
    }

    @Override
    public int hashCode() {
        /* Return the hashCode for the Selector string */
        return selector.hashCode();
    }

    @Override
    public String toString() {
        return selector;
    }

    /**
     * Check if the Selector uses properties.
     *
     * @return true if the selector expression uses properties false if the selector does not use properties (ie it only
     * uses JMS header fields).
     */
    public boolean usesProperties() {
        return usesProperties;
    }

    /**
     * Check if the Selector uses JMS Header Fiedls.
     *
     * @return true if the selector expression uses JMS fields. false if the selector does not use JMS fields (ie it only
     * uses JMS properties).
     */
    public boolean usesFields() {
        return usesFields;
    }

    private static void dumpTokens(LinkedList<SelectorToken> tokenList) {

        Iterator<SelectorToken> iter = tokenList.iterator();
        while (iter.hasNext()) {
            SelectorToken token = iter.next();
            System.out.print(token.toString());
        }
        System.out.println();
    }

    public String toDebugString() {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < compiledSelector.length; i++) {
            if (compiledSelector[i] != null) {
                sb.append(compiledSelector[i]);
            }
        }
        return sb.toString() + " cachesize=" + selectorCache.size();
    }
}
