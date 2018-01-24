/*
 * Copyright (c) 1997, 2017 Oracle and/or its affiliates. All rights reserved.
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

package javax.jms;

import java.util.Enumeration;

/** The {@code Message} interface is the root interface of all JMS 
  * messages. It defines the message header and the {@code acknowledge} 
  * method used for all messages.
  *
  * <P>Most message-oriented middleware (MOM) products treat messages as 
  * lightweight entities that consist
  * of a header and a body. The header contains fields used for message
  * routing and identification; the body contains the application data
  * being sent.
  *
  * <P>Within this general form, the definition of a message varies
  * significantly across products. It would be quite difficult for the JMS API
  * to support all of these message models.
  *
  * <P>With this in mind, the JMS message model has the following goals:
  * <UL>
  *   <LI>Provide a single, unified message API
  *   <LI>Provide an API suitable for creating messages that match the
  *       format used by provider-native messaging applications
  *   <LI>Support the development of heterogeneous applications that span
  *       operating systems, machine architectures, and computer languages
  *   <LI>Support messages containing objects in the Java programming language
  *       ("Java objects")
  *   <LI>Support messages containing Extensible Markup Language (XML) pages
  * </UL>
  *
  * <P>JMS messages are composed of the following parts:
  * <UL>
  *   <LI>Header - All messages support the same set of header fields. 
  *       Header fields contain values used by both clients and providers to 
  *       identify and route messages.
  *   <LI>Properties - Each message contains a built-in facility for supporting
  *       application-defined property values. Properties provide an efficient 
  *       mechanism for supporting application-defined message filtering.
  *   <LI>Body - The JMS API defines several types of message body, which cover
  *       the majority of messaging styles currently in use.
  * </UL>
  *
  * <H4>Message Bodies</H4>
  *
  * <P>The JMS API defines five types of message body:
  * <UL>
  *   <LI>Stream - A {@code StreamMessage} object's message body contains 
  *       a stream of primitive values in the Java programming 
  *       language ("Java primitives"). It is filled and read sequentially.
  *   <LI>Map - A {@code MapMessage} object's message body contains a set 
  *       of name-value pairs, where names are {@code String} 
  *       objects, and values are Java primitives. The entries can be accessed 
  *       sequentially or randomly by name. The order of the entries is 
  *       undefined.
  *   <LI>Text - A {@code TextMessage} object's message body contains a 
  *       {@code java.lang.String} object. This message type can be used
  *       to transport plain-text messages, and XML messages.
  *   <LI>Object - An {@code ObjectMessage} object's message body contains 
  *       a {@code Serializable} Java object.
  *   <LI>Bytes - A {@code BytesMessage} object's message body contains a 
  *       stream of uninterpreted bytes. This message type is for 
  *       literally encoding a body to match an existing message format. In 
  *       many cases, it is possible to use one of the other body types, 
  *       which are easier to use. Although the JMS API allows the use of  
  *       message properties with byte messages, they are typically not used,
  *       since the inclusion of properties may affect the format.
  * </UL>
  *
  * <H4>Message Headers</H4>
  *
  * <P>The {@code JMSCorrelationID} header field is used for linking one 
  * message with
  * another. It typically links a reply message with its requesting message.
  *
  * <P>{@code JMSCorrelationID} can hold a provider-specific message ID,
  * an application-specific {@code String} object, or a provider-native 
  * {@code byte[]} value.
  *
  * <H4>Message Properties</H4>
  *
  * <P>A {@code Message} object contains a built-in facility for supporting
  * application-defined property values. In effect, this provides a mechanism 
  * for adding application-specific header fields to a message.
  *
  * <P>Properties allow an application, via message selectors, to have a JMS 
  * provider select, or filter, messages on its behalf using 
  * application-specific criteria.
  *
  * <P>Property names must obey the rules for a message selector identifier. 
  * Property names must not be null, and must not be empty strings. If a property
  * name is set and it is either null or an empty string, an 
  * {@code IllegalArgumentException} must be thrown.
  *
  * <P>Property values can be {@code boolean}, {@code byte}, 
  * {@code short}, {@code int}, {@code long}, {@code float},
  * {@code double}, and {@code String}.
  *
  * <P>Property values are set prior to sending a message. When a client 
  * receives a message, its properties are in read-only mode. If a 
  * client attempts to set properties at this point, a 
  * {@code MessageNotWriteableException} is thrown. If 
  * {@code clearProperties} is called, the properties can now be both
  * read from and written to. Note that header fields are distinct from 
  * properties. Header fields are never in read-only mode. 
  *
  * <P>A property value may duplicate a value in a message's body, or it may 
  * not. Although JMS does not define a policy for what should or should not 
  * be made a property, application developers should note that JMS providers 
  * will likely handle data in a message's body more efficiently than data in 
  * a message's properties. For best performance, applications should use
  * message properties only when they need to customize a message's header. 
  * The primary reason for doing this is to support customized message 
  * selection.
  *
  * <P>Message properties support the following conversion table. The marked 
  * cases must be supported. The unmarked cases must throw a 
  * {@code JMSException}. The {@code String}-to-primitive conversions 
  * may throw a runtime exception if the
  * primitive's {@code valueOf} method does not accept the 
  * {@code String} as a valid representation of the primitive.
  *
  * <P>A value written as the row type can be read as the column type.
  *
  * <PRE>
  * |        | boolean byte short int long float double String 
  * |----------------------------------------------------------
  * |boolean |    X                                       X
  * |byte    |          X     X    X   X                  X 
  * |short   |                X    X   X                  X 
  * |int     |                     X   X                  X 
  * |long    |                         X                  X 
  * |float   |                               X     X      X 
  * |double  |                                     X      X 
  * |String  |    X     X     X    X   X     X     X      X 
  * |----------------------------------------------------------
  * </PRE>
  *
  * <P>In addition to the type-specific set/get methods for properties, JMS 
  * provides the {@code setObjectProperty} and 
  * {@code getObjectProperty} methods. These support the same set of 
  * property types using the objectified primitive values. Their purpose is 
  * to allow the decision of property type to made at execution time rather 
  * than at compile time. They support the same property value conversions.
  *
  * <P>The {@code setObjectProperty} method accepts values of class 
  * {@code Boolean}, {@code Byte}, {@code Short}, 
  * {@code Integer}, {@code Long}, {@code Float}, 
  * {@code Double}, and {@code String}. An attempt 
  * to use any other class must throw a {@code JMSException}.
  *
  * <P>The {@code getObjectProperty} method only returns values of class 
  * {@code Boolean}, {@code Byte}, {@code Short}, 
  * {@code Integer}, {@code Long}, {@code Float}, 
  * {@code Double}, and {@code String}.
  *
  * <P>The order of property values is not defined. To iterate through a 
  * message's property values, use {@code getPropertyNames} to retrieve 
  * a property name enumeration and then use the various property get methods 
  * to retrieve their values.
  *
  * <P>A message's properties are deleted by the {@code clearProperties}
  * method. This leaves the message with an empty set of properties.
  *
  * <P>Getting a property value for a name which has not been set returns a 
  * null value. Only the {@code getStringProperty} and 
  * {@code getObjectProperty} methods can return a null value. 
  * Attempting to read a null value as a primitive type must be treated as 
  * calling the primitive's corresponding {@code valueOf(String)} 
  * conversion method with a null value.
  *
  * <P>The JMS API reserves the {@code JMSX} property name prefix for JMS 
  * defined properties.
  * The full set of these properties is defined in the Java Message Service
  * specification. The specification also defines whether support for each
  * property is mandatory or optional.  
  * New JMS defined properties may be added in later versions 
  * of the JMS API.  The 
  * {@code String[] ConnectionMetaData.getJMSXPropertyNames} method 
  * returns the names of the JMSX properties supported by a connection.
  *
  * <P>JMSX properties may be referenced in message selectors whether or not
  * they are supported by a connection. If they are not present in a
  * message, they are treated like any other absent property.
  * The effect of setting a message selector on a property 
  * which is set by the provider on receive is undefined.
  *
  * <P>JMSX properties defined in the specification as "set by provider on 
  * send" are available to both the producer and the consumers of the message. 
  * JMSX properties defined in the specification as "set by provider on 
  * receive" are available only to the consumers.
  *
  * <P>{@code JMSXGroupID} and {@code JMSXGroupSeq} are standard 
  * properties that clients 
  * should use if they want to group messages. All providers must support them.
  * Unless specifically noted, the values and semantics of the JMSX properties 
  * are undefined.
  *
  * <P>The JMS API reserves the <CODE>JMS_<I>vendor_name</I></CODE> property 
  * name prefix for provider-specific properties. Each provider defines its own 
  * value for <CODE><I>vendor_name</I></CODE>. This is the mechanism a JMS 
  * provider uses to make its special per-message services available to a JMS 
  * client.
  *
  * <P>The purpose of provider-specific properties is to provide special 
  * features needed to integrate JMS clients with provider-native clients in a 
  * single JMS application. They should not be used for messaging between JMS 
  * clients.
  *
  * <H4>Provider Implementations of JMS Message Interfaces</H4>
  *
  * <P>The JMS API provides a set of message interfaces that define the JMS 
  * message 
  * model. It does not provide implementations of these interfaces.
  *
  * <P>Each JMS provider supplies a set of message factories with its 
  * {@code Session} object for creating instances of messages. This allows 
  * a provider to use message implementations tailored to its specific needs.
  *
  * <P>A provider must be prepared to accept message implementations that are 
  * not its own. They may not be handled as efficiently as its own 
  * implementation; however, they must be handled.
  *
  * <P>Note the following exception case when a provider is handling a foreign 
  * message implementation. If the foreign message implementation contains a 
  * {@code JMSReplyTo} header field that is set to a foreign destination 
  * implementation, the provider is not required to handle or preserve the 
  * value of this header field. 
  *
  * <H4>Message Selectors</H4>
  *
  * <P>A JMS message selector allows a client to specify, by
  * header field references and property references, the
  * messages it is interested in. Only messages whose header 
  * and property values
  * match the 
  * selector are delivered. What it means for a message not to be delivered
  * depends on the {@code MessageConsumer} being used (see 
  * {@link javax.jms.QueueReceiver QueueReceiver} and 
  * {@link javax.jms.TopicSubscriber TopicSubscriber}).
  *
  * <P>Message selectors cannot reference message body values.
  *
  * <P>A message selector matches a message if the selector evaluates to 
  * true when the message's header field values and property values are 
  * substituted for their corresponding identifiers in the selector.
  *
  * <P>A message selector is a {@code String} whose syntax is based on a 
  * subset of 
  * the SQL92 conditional expression syntax. If the value of a message selector 
  * is an empty string, the value is treated as a null and indicates that there 
  * is no message selector for the message consumer. 
  *
  * <P>The order of evaluation of a message selector is from left to right 
  * within precedence level. Parentheses can be used to change this order.
  *
  * <P>Predefined selector literals and operator names are shown here in 
  * uppercase; however, they are case insensitive.
  *
  * <P>A selector can contain:
  *
  * <UL>
  *   <LI>Literals:
  *   <UL>
  *     <LI>A string literal is enclosed in single quotes, with a single quote 
  *         represented by doubled single quote; for example, 
  *         {@code 'literal'} and {@code 'literal''s'}. Like 
  *         string literals in the Java programming language, these use the 
  *         Unicode character encoding.
  *     <LI>An exact numeric literal is a numeric value without a decimal 
  *         point, such as {@code 57}, {@code -957}, and  
  *         {@code +62}; numbers in the range of {@code long} are 
  *         supported. Exact numeric literals use the integer literal 
  *         syntax of the Java programming language.
  *     <LI>An approximate numeric literal is a numeric value in scientific 
  *         notation, such as {@code 7E3} and {@code -57.9E2}, or a 
  *         numeric value with a decimal, such as {@code 7.}, 
  *         {@code -95.7}, and {@code +6.2}; numbers in the range of 
  *         {@code double} are supported. Approximate literals use the 
  *         floating-point literal syntax of the Java programming language.
  *     <LI>The boolean literals {@code TRUE} and {@code FALSE}.
  *   </UL>
  *   <LI>Identifiers:
  *   <UL>
  *     <LI>An identifier is an unlimited-length sequence of letters 
  *         and digits, the first of which must be a letter. A letter is any 
  *         character for which the method {@code Character.isJavaLetter}
  *         returns true. This includes {@code '_'} and {@code '$'}.
  *         A letter or digit is any character for which the method 
  *         {@code Character.isJavaLetterOrDigit} returns true.
  *     <LI>Identifiers cannot be the names {@code NULL}, 
  *         {@code TRUE}, and {@code FALSE}.
  *     <LI>Identifiers cannot be {@code NOT}, {@code AND}, 
  *         {@code OR}, {@code BETWEEN}, {@code LIKE}, 
  *         {@code IN}, {@code IS}, or {@code ESCAPE}.
  *     <LI>Identifiers are either header field references or property 
  *         references.  The type of a property value in a message selector 
  *         corresponds to the type used to set the property. If a property 
  *         that does not exist in a message is referenced, its value is 
  *         {@code NULL}.
  *     <LI>The conversions that apply to the get methods for properties do not
  *         apply when a property is used in a message selector expression.
  *         For example, suppose you set a property as a string value, as in the
  *         following:
  *         <PRE>myMessage.setStringProperty("NumberOfOrders", "2");</PRE>
  *         The following expression in a message selector would evaluate to 
  *         false, because a string cannot be used in an arithmetic expression:
  *         <PRE>"NumberOfOrders > 1"</PRE>
  *     <LI>Identifiers are case-sensitive.
  *     <LI>Message header field references are restricted to 
  *         {@code JMSDeliveryMode}, {@code JMSPriority}, 
  *         {@code JMSMessageID}, {@code JMSTimestamp}, 
  *         {@code JMSCorrelationID}, and {@code JMSType}. 
  *         {@code JMSMessageID}, {@code JMSCorrelationID}, and 
  *         {@code JMSType} values may be null and if so are treated as a 
  *         {@code NULL} value.
  *     <LI>Any name beginning with {@code 'JMSX'} is a JMS defined  
  *         property name.
  *     <LI>Any name beginning with {@code 'JMS_'} is a provider-specific 
  *         property name.
  *     <LI>Any name that does not begin with {@code 'JMS'} is an 
  *         application-specific property name.
  *   </UL>
  *   <LI>White space is the same as that defined for the Java programming
  *       language: space, horizontal tab, form feed, and line terminator.
  *   <LI>Expressions: 
  *   <UL>
  *     <LI>A selector is a conditional expression; a selector that evaluates 
  *         to {@code true} matches; a selector that evaluates to 
  *         {@code false} or unknown does not match.
  *     <LI>Arithmetic expressions are composed of themselves, arithmetic 
  *         operations, identifiers (whose value is treated as a numeric 
  *         literal), and numeric literals.
  *     <LI>Conditional expressions are composed of themselves, comparison 
  *         operations, and logical operations.
  *   </UL>
  *   <LI>Standard bracketing {@code ()} for ordering expression evaluation
  *      is supported.
  *   <LI>Logical operators in precedence order: {@code NOT}, 
  *       {@code AND}, {@code OR}
  *   <LI>Comparison operators: {@code =}, {@code >}, {@code >=},
  *       {@code <}, {@code <=}, {@code <>} (not equal)
  *   <UL>
  *     <LI>Only like type values can be compared. One exception is that it 
  *         is valid to compare exact numeric values and approximate numeric 
  *         values; the type conversion required is defined by the rules of 
  *         numeric promotion in the Java programming language. If the 
  *         comparison of non-like type values is attempted, the value of the 
  *         operation is false. If either of the type values evaluates to 
  *         {@code NULL}, the value of the expression is unknown.   
  *     <LI>String and boolean comparison is restricted to {@code =} and 
  *         {@code <>}. Two strings are equal 
  *         if and only if they contain the same sequence of characters.
  *   </UL>
  *   <LI>Arithmetic operators in precedence order:
  *   <UL>
  *     <LI>{@code +}, {@code -} (unary)
  *     <LI>{@code *}, {@code /} (multiplication and division)
  *     <LI>{@code +}, {@code -} (addition and subtraction)
  *     <LI>Arithmetic operations must use numeric promotion in the Java 
  *         programming language.
  *   </UL>
  *   <LI><CODE><I>arithmetic-expr1</I> [NOT] BETWEEN <I>arithmetic-expr2</I> 
  *       AND <I>arithmetic-expr3</I></CODE> (comparison operator)
  *   <UL>
  *     <LI><CODE>"age&nbsp;BETWEEN&nbsp;15&nbsp;AND&nbsp;19"</CODE> is 
  *         equivalent to 
  *         <CODE>"age&nbsp;>=&nbsp;15&nbsp;AND&nbsp;age&nbsp;<=&nbsp;19"</CODE>
  *     <LI><CODE>"age&nbsp;NOT&nbsp;BETWEEN&nbsp;15&nbsp;AND&nbsp;19"</CODE> 
  *         is equivalent to 
  *         <CODE>"age&nbsp;<&nbsp;15&nbsp;OR&nbsp;age&nbsp;>&nbsp;19"</CODE>
  *   </UL>
  *   <LI><CODE><I>identifier</I> [NOT] IN (<I>string-literal1</I>, 
  *       <I>string-literal2</I>,...)</CODE> (comparison operator where 
  *       <CODE><I>identifier</I></CODE> has a <CODE>String</CODE> or 
  *       <CODE>NULL</CODE> value)
  *   <UL>
  *     <LI><CODE>"Country&nbsp;IN&nbsp;('&nbsp;UK',&nbsp;'US',&nbsp;'France')"</CODE>
  *         is true for 
  *         <CODE>'UK'</CODE> and false for <CODE>'Peru'</CODE>; it is 
  *         equivalent to the expression 
  *         <CODE>"(Country&nbsp;=&nbsp;'&nbsp;UK')&nbsp;OR&nbsp;(Country&nbsp;=&nbsp;'&nbsp;US')&nbsp;OR&nbsp;(Country&nbsp;=&nbsp;'&nbsp;France')"</CODE>
  *     <LI><CODE>"Country&nbsp;NOT&nbsp;IN&nbsp;('&nbsp;UK',&nbsp;'US',&nbsp;'France')"</CODE> 
  *         is false for <CODE>'UK'</CODE> and true for <CODE>'Peru'</CODE>; it 
  *         is equivalent to the expression 
  *         <CODE>"NOT&nbsp;((Country&nbsp;=&nbsp;'&nbsp;UK')&nbsp;OR&nbsp;(Country&nbsp;=&nbsp;'&nbsp;US')&nbsp;OR&nbsp;(Country&nbsp;=&nbsp;'&nbsp;France'))"</CODE>
  *     <LI>If identifier of an <CODE>IN</CODE> or <CODE>NOT IN</CODE> 
  *         operation is <CODE>NULL</CODE>, the value of the operation is 
  *         unknown.
  *   </UL>
  *   <LI><CODE><I>identifier</I> [NOT] LIKE <I>pattern-value</I> [ESCAPE 
  *       <I>escape-character</I>]</CODE> (comparison operator, where 
  *       <CODE><I>identifier</I></CODE> has a <CODE>String</CODE> value; 
  *       <CODE><I>pattern-value</I></CODE> is a string literal where 
  *       <CODE>'_'</CODE> stands for any single character; <CODE>'%'</CODE> 
  *       stands for any sequence of characters, including the empty sequence; 
  *       and all other characters stand for themselves. The optional 
  *       <CODE><I>escape-character</I></CODE> is a single-character string 
  *       literal whose character is used to escape the special meaning of the 
  *       <CODE>'_'</CODE> and <CODE>'%'</CODE> in 
  *       <CODE><I>pattern-value</I></CODE>.)
  *   <UL>
  *     <LI><CODE>"phone&nbsp;LIKE&nbsp;'12%3'"</CODE> is true for 
  *         <CODE>'123'</CODE> or <CODE>'12993'</CODE> and false for 
  *         <CODE>'1234'</CODE>
  *     <LI><CODE>"word&nbsp;LIKE&nbsp;'l_se'"</CODE> is true for 
  *         <CODE>'lose'</CODE> and false for <CODE>'loose'</CODE>
  *     <LI><CODE>"underscored&nbsp;LIKE&nbsp;'\_%'&nbsp;ESCAPE&nbsp;'\'"</CODE>
  *          is true for <CODE>'_foo'</CODE> and false for <CODE>'bar'</CODE>
  *     <LI><CODE>"phone&nbsp;NOT&nbsp;LIKE&nbsp;'12%3'"</CODE> is false for 
  *         <CODE>'123'</CODE> or <CODE>'12993'</CODE> and true for 
  *         <CODE>'1234'</CODE>
  *     <LI>If <CODE><I>identifier</I></CODE> of a <CODE>LIKE</CODE> or 
  *         <CODE>NOT LIKE</CODE> operation is <CODE>NULL</CODE>, the value 
  *         of the operation is unknown.
  *   </UL>
  *   <LI><CODE><I>identifier</I> IS NULL</CODE> (comparison operator that tests
  *       for a null header field value or a missing property value)
  *   <UL>
  *     <LI><CODE>"prop_name&nbsp;IS&nbsp;NULL"</CODE>
  *   </UL>
  *   <LI><CODE><I>identifier</I> IS NOT NULL</CODE> (comparison operator that
  *       tests for the existence of a non-null header field value or a property
  *       value)
  *   <UL>
  *     <LI><CODE>"prop_name&nbsp;IS&nbsp;NOT&nbsp;NULL"</CODE>
  *   </UL>
  *
  * <P>JMS providers are required to verify the syntactic correctness of a 
  *    message selector at the time it is presented. A method that provides a 
  *  syntactically incorrect selector must result in a {@code JMSException}.
  * JMS providers may also optionally provide some semantic checking at the time
  * the selector is presented. Not all semantic checking can be performed at
  * the time a message selector is presented, because property types are not known.
  * 
  * <P>The following message selector selects messages with a message type 
  * of car and color of blue and weight greater than 2500 pounds:
  *
  * <PRE>"JMSType&nbsp;=&nbsp;'car'&nbsp;AND&nbsp;color&nbsp;=&nbsp;'blue'&nbsp;AND&nbsp;weight&nbsp;>&nbsp;2500"</PRE>
  *
  * <H4>Null Values</H4>
  *
  * <P>As noted above, property values may be {@code NULL}. The evaluation 
  * of selector expressions containing {@code NULL} values is defined by 
  * SQL92 {@code NULL} semantics. A brief description of these semantics 
  * is provided here.
  *
  * <P>SQL treats a {@code NULL} value as unknown. Comparison or arithmetic
  * with an unknown value always yields an unknown value.
  *
  * <P>The {@code IS NULL} and {@code IS NOT NULL} operators convert 
  * an unknown value into the respective {@code TRUE} and 
  * {@code FALSE} values.
  *
  * <P>The boolean operators use three-valued logic as defined by the 
  * following tables:
  *
  * <P><B>The definition of the {@code AND} operator</B>
  *
  * <PRE>
  * | AND  |   T   |   F   |   U
  * +------+-------+-------+-------
  * |  T   |   T   |   F   |   U
  * |  F   |   F   |   F   |   F
  * |  U   |   U   |   F   |   U
  * +------+-------+-------+-------
  * </PRE>
  *
  * <P><B>The definition of the {@code OR} operator</B>
  *
  * <PRE>
  * | OR   |   T   |   F   |   U
  * +------+-------+-------+--------
  * |  T   |   T   |   T   |   T
  * |  F   |   T   |   F   |   U
  * |  U   |   T   |   U   |   U
  * +------+-------+-------+------- 
  * </PRE> 
  *
  * <P><B>The definition of the {@code NOT} operator</B>
  *
  * <PRE>
  * | NOT
  * +------+------
  * |  T   |   F
  * |  F   |   T
  * |  U   |   U
  * +------+-------
  * </PRE>
  *
  * <H4>Special Notes</H4>
  *
  * <P>When used in a message selector, the {@code JMSDeliveryMode} header 
  *    field is treated as having the values {@code 'PERSISTENT'} and 
  *    {@code 'NON_PERSISTENT'}.
  *
  * <P>Date and time values should use the standard {@code long} 
  *    millisecond value. When a date or time literal is included in a message 
  *    selector, it should be an integer literal for a millisecond value. The 
  *    standard way to produce millisecond values is to use 
  *    {@code java.util.Calendar}.
  *
  * <P>Although SQL supports fixed decimal comparison and arithmetic, JMS 
  *    message selectors do not. This is the reason for restricting exact 
  *    numeric literals to those without a decimal (and the addition of 
  *    numerics with a decimal as an alternate representation for 
  *    approximate numeric values).
  *
  * <P>SQL comments are not supported.
  * 
  * @version JMS 2.0
  * @since JMS 1.0
  * 
  * @see         javax.jms.MessageConsumer#receive()
  * @see         javax.jms.MessageConsumer#receive(long)
  * @see         javax.jms.MessageConsumer#receiveNoWait()
  * @see         javax.jms.MessageListener#onMessage(Message)
  * @see         javax.jms.BytesMessage
  * @see         javax.jms.MapMessage
  * @see         javax.jms.ObjectMessage
  * @see         javax.jms.StreamMessage
  * @see         javax.jms.TextMessage
  */

public interface Message {

    /** The message producer's default delivery mode is {@code PERSISTENT}.
     *
     *  @see DeliveryMode#PERSISTENT
     */
    static final int DEFAULT_DELIVERY_MODE = DeliveryMode.PERSISTENT;

    /** The message producer's default priority is 4. 
     */
    static final int DEFAULT_PRIORITY = 4;

    /** The message producer's default time to live is unlimited; the message 
     *  never expires. 
     */
    static final long DEFAULT_TIME_TO_LIVE = 0;
    
    /** The message producer's default delivery delay is zero.
     * @since JMS 2.0
     */
    static final long DEFAULT_DELIVERY_DELAY = 0;    


    /** Gets the message ID.
      *
      * <P>The {@code JMSMessageID} header field contains a value that 
      * uniquely identifies each message sent by a provider.
      *  
      * <P>When a message is sent, {@code JMSMessageID} can be ignored. 
      * When the {@code send} or {@code publish} method returns, it 
      * contains a provider-assigned value.
      *
      * <P>A {@code JMSMessageID} is a {@code String} value that 
      * should function as a 
      * unique key for identifying messages in a historical repository. 
      * The exact scope of uniqueness is provider-defined. It should at 
      * least cover all messages for a specific installation of a 
      * provider, where an installation is some connected set of message 
      * routers.
      *
      * <P>All {@code JMSMessageID} values must start with the prefix 
      * {@code 'ID:'}. 
      * Uniqueness of message ID values across different providers is 
      * not required.
      *
      * <P>Since message IDs take some effort to create and increase a
      * message's size, some JMS providers may be able to optimize message
      * overhead if they are given a hint that the message ID is not used by
      * an application. By calling the 
      * {@code MessageProducer.setDisableMessageID} method, a JMS client 
      * enables this potential optimization for all messages sent by that 
      * message producer. If the JMS provider accepts this
      * hint, these messages must have the message ID set to null; if the 
      * provider ignores the hint, the message ID must be set to its normal 
      * unique value.
      *
      * @return the message ID
      *
      * @exception JMSException if the JMS provider fails to get the message ID 
      *                         due to some internal error.
      * @see javax.jms.Message#setJMSMessageID(String)
      * @see javax.jms.MessageProducer#setDisableMessageID(boolean)
      */ 
 
    String
    getJMSMessageID() throws JMSException;


    /** Sets the message ID.
      *  
     * <P>This method is for use by JMS providers only to set this field 
     * when a message is sent. This message cannot be used by clients 
     * to configure the message ID. This method is public
     * to allow a JMS provider to set this field when sending a message
     * whose implementation is not its own.
      *
      * @param id the ID of the message
      *
      * @exception JMSException if the JMS provider fails to set the message ID 
      *                         due to some internal error.
      *
      * @see javax.jms.Message#getJMSMessageID()
      */ 

    void
    setJMSMessageID(String id) throws JMSException;


    /** Gets the message timestamp.
      *  
      * <P>The {@code JMSTimestamp} header field contains the time a 
      * message was 
      * handed off to a provider to be sent. It is not the time the 
      * message was actually transmitted, because the actual send may occur 
      * later due to transactions or other client-side queueing of messages.
      *
      * <P>When a message is sent, {@code JMSTimestamp} is ignored. When 
      * the {@code send} or {@code publish}
      * method returns, it contains a time value somewhere in the interval 
      * between the call and the return. The value is in the format of a normal 
      * millis time value in the Java programming language.
      *
      * <P>Since timestamps take some effort to create and increase a 
      * message's size, some JMS providers may be able to optimize message 
      * overhead if they are given a hint that the timestamp is not used by an 
      * application. By calling the
      * {@code MessageProducer.setDisableMessageTimestamp} method, a JMS 
      * client enables this potential optimization for all messages sent by 
      * that message producer. If the JMS provider accepts this
      * hint, these messages must have the timestamp set to zero; if the 
      * provider ignores the hint, the timestamp must be set to its normal 
      * value.
      *
      * @return the message timestamp
      *
      * @exception JMSException if the JMS provider fails to get the timestamp
      *                         due to some internal error.
      *
      * @see javax.jms.Message#setJMSTimestamp(long)
      * @see javax.jms.MessageProducer#setDisableMessageTimestamp(boolean)
      */

    long
    getJMSTimestamp() throws JMSException;


    /** Sets the message timestamp.
      *  
     * <P>This method is for use by JMS providers only to set this field 
     * when a message is sent. This message cannot be used by clients 
     * to configure the message timestamp. This method is public
     * to allow a JMS provider to set this field when sending a message
     * whose implementation is not its own.
      *
      * @param timestamp the timestamp for this message
      *  
      * @exception JMSException if the JMS provider fails to set the timestamp
      *                         due to some internal error.
      *
      * @see javax.jms.Message#getJMSTimestamp()
      */

    void
    setJMSTimestamp(long timestamp) throws JMSException;


    /** Gets the correlation ID as an array of bytes for the message.
      *  
      * <P>The use of a {@code byte[]} value for 
      * {@code JMSCorrelationID} is non-portable.
      *
      * @return the correlation ID of a message as an array of bytes
      *
      * @exception JMSException if the JMS provider fails to get the correlation
      *                         ID due to some internal error.
      *  
      * @see javax.jms.Message#setJMSCorrelationID(String)
      * @see javax.jms.Message#getJMSCorrelationID()
      * @see javax.jms.Message#setJMSCorrelationIDAsBytes(byte[])
      */

    byte []
    getJMSCorrelationIDAsBytes() throws JMSException;


    /** Sets the correlation ID as an array of bytes for the message.
      * 
      * <P>The array is copied before the method returns, so
      * future modifications to the array will not alter this message header.
      *  
      * <P>If a provider supports the native concept of correlation ID, a 
      * JMS client may need to assign specific {@code JMSCorrelationID} 
      * values to match those expected by native messaging clients. 
      * JMS providers without native correlation ID values are not required to 
      * support this method and its corresponding get method; their 
      * implementation may throw a
      * {@code java.lang.UnsupportedOperationException}. 
      *
      * <P>The use of a {@code byte[]} value for 
      * {@code JMSCorrelationID} is non-portable.
      *
      * @param correlationID the correlation ID value as an array of bytes
      *  
      * @exception JMSException if the JMS provider fails to set the correlation
      *                         ID due to some internal error.
      *  
      * @see javax.jms.Message#setJMSCorrelationID(String)
      * @see javax.jms.Message#getJMSCorrelationID()
      * @see javax.jms.Message#getJMSCorrelationIDAsBytes()
      */

    void
    setJMSCorrelationIDAsBytes(byte[] correlationID) throws JMSException;


    /** Sets the correlation ID for the message.
      *  
      * <P>A client can use the {@code JMSCorrelationID} header field to 
      * link one message with another. A typical use is to link a response 
      * message with its request message.
      *  
      * <P>{@code JMSCorrelationID} can hold one of the following:
      *    <UL>
      *      <LI>A provider-specific message ID
      *      <LI>An application-specific {@code String}
      *      <LI>A provider-native {@code byte[]} value
      *    </UL>
      *  
      * <P>Since each message sent by a JMS provider is assigned a message ID
      * value, it is convenient to link messages via message ID. All message ID
      * values must start with the {@code 'ID:'} prefix.
      *  
      * <P>In some cases, an application (made up of several clients) needs to
      * use an application-specific value for linking messages. For instance,
      * an application may use {@code JMSCorrelationID} to hold a value 
      * referencing some external information. Application-specified values 
      * must not start with the {@code 'ID:'} prefix; this is reserved for 
      * provider-generated message ID values.
      *  
      * <P>If a provider supports the native concept of correlation ID, a JMS
      * client may need to assign specific {@code JMSCorrelationID} values 
      * to match those expected by clients that do not use the JMS API. A 
      * {@code byte[]} value is used for this
      * purpose. JMS providers without native correlation ID values are not
      * required to support {@code byte[]} values. The use of a 
      * {@code byte[]} value for {@code JMSCorrelationID} is 
      * non-portable.
      *  
      * @param correlationID the message ID of a message being referred to
      *  
      * @exception JMSException if the JMS provider fails to set the correlation
      *                         ID due to some internal error.
      *  
      * @see javax.jms.Message#getJMSCorrelationID()
      * @see javax.jms.Message#getJMSCorrelationIDAsBytes()
      * @see javax.jms.Message#setJMSCorrelationIDAsBytes(byte[])
      */ 

    void
    setJMSCorrelationID(String correlationID) throws JMSException;


    /** Gets the correlation ID for the message.
      *  
      * <P>This method is used to return correlation ID values that are 
      * either provider-specific message IDs or application-specific 
      * {@code String} values.
      *
      * @return the correlation ID of a message as a {@code String}
      *
      * @exception JMSException if the JMS provider fails to get the correlation
      *                         ID due to some internal error.
      *
      * @see javax.jms.Message#setJMSCorrelationID(String)
      * @see javax.jms.Message#getJMSCorrelationIDAsBytes()
      * @see javax.jms.Message#setJMSCorrelationIDAsBytes(byte[])
      */ 
 
    String
    getJMSCorrelationID() throws JMSException;


    /** Gets the {@code Destination} object to which a reply to this 
      * message should be sent.
      *  
      * @return {@code Destination} to which to send a response to this 
      *         message
      *
      * @exception JMSException if the JMS provider fails to get the  
      *                         {@code JMSReplyTo} destination due to some 
      *                         internal error.
      *
      * @see javax.jms.Message#setJMSReplyTo(Destination)
      */ 
 
    Destination
    getJMSReplyTo() throws JMSException;


    /** Sets the {@code Destination} object to which a reply to this 
      * message should be sent.
      *  
      * <P>The {@code JMSReplyTo} header field contains the destination 
      * where a reply 
      * to the current message should be sent. If it is null, no reply is 
      * expected. The destination may be either a {@code Queue} object or
      * a {@code Topic} object.
      *
      * <P>Messages sent with a null {@code JMSReplyTo} value may be a 
      * notification of some event, or they may just be some data the sender 
      * thinks is of interest.
      *
      * <P>Messages with a {@code JMSReplyTo} value typically expect a 
      * response. A response is optional; it is up to the client to decide.  
      * These messages are called requests. A message sent in response to a 
      * request is called a reply.
      *
      * <P>In some cases a client may wish to match a request it sent earlier 
      * with a reply it has just received. The client can use the 
      * {@code JMSCorrelationID} header field for this purpose.
      *
      * @param replyTo {@code Destination} to which to send a response to 
      *                this message
      *
      * @exception JMSException if the JMS provider fails to set the  
      *                         {@code JMSReplyTo} destination due to some 
      *                         internal error.
      *
      * @see javax.jms.Message#getJMSReplyTo()
      */ 

    void
    setJMSReplyTo(Destination replyTo) throws JMSException;


    /** Gets the {@code Destination} object for this message.
      *  
      * <P>The {@code JMSDestination} header field contains the 
      * destination to which the message is being sent.
      *  
      * <P>When a message is sent, this field is ignored. After completion
      * of the {@code send} or {@code publish} method, the field 
      * holds the destination specified by the method.
      *  
      * <P>When a message is received, its {@code JMSDestination} value 
      * must be equivalent to the value assigned when it was sent.
      *
      * @return the destination of this message
      *  
      * @exception JMSException if the JMS provider fails to get the destination
      *                         due to some internal error.
      *  
      * @see javax.jms.Message#setJMSDestination(Destination)
      */ 

    Destination
    getJMSDestination() throws JMSException;


    /** Sets the {@code Destination} object for this message.
      *  
     * <P>This method is for use by JMS providers only to set this field 
     * when a message is sent. This message cannot be used by clients 
     * to configure the destination of the message. This method is public
     * to allow a JMS provider to set this field when sending a message
     * whose implementation is not its own.
      *
      * @param destination the destination for this message
      *  
      * @exception JMSException if the JMS provider fails to set the destination
      *                         due to some internal error.
      *  
      * @see javax.jms.Message#getJMSDestination()
      */ 

    void
    setJMSDestination(Destination destination) throws JMSException;


    /** Gets the {@code DeliveryMode} value specified for this message.
      *  
      * @return the delivery mode for this message
      *  
      * @exception JMSException if the JMS provider fails to get the 
      *                         delivery mode due to some internal error.
      *  
      * @see javax.jms.Message#setJMSDeliveryMode(int)
      * @see javax.jms.DeliveryMode
      */ 
 
    int
    getJMSDeliveryMode() throws JMSException;
 
 
    /** Sets the {@code DeliveryMode} value for this message.
      *  
     * <P>This method is for use by JMS providers only to set this field 
     * when a message is sent. This message cannot be used by clients 
     * to configure the delivery mode of the message. This method is public
     * to allow a JMS provider to set this field when sending a message
     * whose implementation is not its own.
      *
      * @param deliveryMode the delivery mode for this message
      *  
      * @exception JMSException if the JMS provider fails to set the 
      *                         delivery mode due to some internal error.
      *  
      * @see javax.jms.Message#getJMSDeliveryMode()
      * @see javax.jms.DeliveryMode
      */ 
 
    void 
    setJMSDeliveryMode(int deliveryMode) throws JMSException;


    /** Gets an indication of whether this message is being redelivered.
      *
      * <P>If a client receives a message with the {@code JMSRedelivered} 
      * field set,
      * it is likely, but not guaranteed, that this message was delivered
      * earlier but that its receipt was not acknowledged
      * at that time.
      *
      * @return true if this message is being redelivered
      *  
      * @exception JMSException if the JMS provider fails to get the redelivered
      *                         state due to some internal error.
      *
      * @see javax.jms.Message#setJMSRedelivered(boolean)
      */ 
 
    boolean
    getJMSRedelivered() throws JMSException;
 
 
    /** Specifies whether this message is being redelivered.
      *  
     * <P>This method is for use by JMS providers only to set this field 
     * when a message is delivered. This message cannot be used by clients 
     * to configure the redelivered status of the message. This method is public
     * to allow a JMS provider to set this field when sending a message
     * whose implementation is not its own.
      *
      * @param redelivered an indication of whether this message is being
      * redelivered
      *  
      * @exception JMSException if the JMS provider fails to set the redelivered
      *                         state due to some internal error.
      *
      * @see javax.jms.Message#getJMSRedelivered()
      */ 
 
    void
    setJMSRedelivered(boolean redelivered) throws JMSException;


    /** Gets the message type identifier supplied by the client when the
      * message was sent.
      *
      * @return the message type
      *  
      * @exception JMSException if the JMS provider fails to get the message 
      *                         type due to some internal error.
      *
      * @see javax.jms.Message#setJMSType(String)
      */
       
    String
    getJMSType() throws JMSException;


    /** Sets the message type.
      *
      * <P>Some JMS providers use a message repository that contains the 
      * definitions of messages sent by applications. The {@code JMSType} 
      * header field may reference a message's definition in the provider's
      * repository.
      *
      * <P>The JMS API does not define a standard message definition repository,
      * nor does it define a naming policy for the definitions it contains. 
      *
      * <P>Some messaging systems require that a message type definition for 
      * each application message be created and that each message specify its 
      * type. In order to work with such JMS providers, JMS clients should 
      * assign a value to {@code JMSType}, whether the application makes 
      * use of it or not. This ensures that the field is properly set for those 
      * providers that require it.
      *
      * <P>To ensure portability, JMS clients should use symbolic values for 
      * {@code JMSType} that can be configured at installation time to the 
      * values defined in the current provider's message repository. If string 
      * literals are used, they may not be valid type names for some JMS 
      * providers.
      *
      * @param type the message type
      *  
      * @exception JMSException if the JMS provider fails to set the message 
      *                         type due to some internal error.
      *
      * @see javax.jms.Message#getJMSType()
      */

    void 
    setJMSType(String type) throws JMSException;


    /** Gets the message's expiration time.
      *  
      * <P>When a message is sent, the {@code JMSExpiration} header field 
      * is left unassigned. After completion of the {@code send} or 
      * {@code publish} method, it holds the expiration time of the
      * message. This is the the difference, measured in milliseconds, 
      * between the expiration time and midnight, January 1, 1970 UTC.
      *
      * <P>If the time-to-live is specified as zero, {@code JMSExpiration} 
      * is set to zero to indicate that the message does not expire.
      *
      * <P>When a message's expiration time is reached, a provider should
      * discard it. The JMS API does not define any form of notification of 
      * message expiration.
      *
      * <P>Clients should not receive messages that have expired; however,
      * the JMS API does not guarantee that this will not happen.
      *
      * @return the message's expiration time value
      *  
      * @exception JMSException if the JMS provider fails to get the message 
      *                         expiration due to some internal error.
      *
      * @see javax.jms.Message#setJMSExpiration(long)
      */ 
 
    long
    getJMSExpiration() throws JMSException;
 
 
    /** Sets the message's expiration value.
      *
     * <P>This method is for use by JMS providers only to set this field 
     * when a message is sent. This message cannot be used by clients 
     * to configure the expiration time of the message. This method is public
     * to allow a JMS provider to set this field when sending a message
     * whose implementation is not its own.
      *  
      * @param expiration the message's expiration time
      *  
      * @exception JMSException if the JMS provider fails to set the message 
      *                         expiration due to some internal error.
      *
      * @see javax.jms.Message#getJMSExpiration() 
      */
 
    void
    setJMSExpiration(long expiration) throws JMSException;
    
    /**
	 * Gets the message's delivery time value.
	 * 
	 * <P>
	 * When a message is sent, the {@code JMSDeliveryTime} header field is
	 * left unassigned. After completion of the {@code send} or
	 * {@code publish} method, it holds the delivery time of the message.
	 * This is the the difference, measured in milliseconds, 
     * between the delivery time and midnight, January 1, 1970 UTC.
	 * <p>
	 * A message's delivery time is the earliest time when a JMS provider may
	 * deliver the message to a consumer. The provider must not deliver messages
	 * before the delivery time has been reached.
	 * 
	 * @return the message's delivery time value
	 * 
	 * @exception JMSException
	 *                if the JMS provider fails to get the delivery time due to
	 *                some internal error.
	 * 
	 * @see javax.jms.Message#setJMSDeliveryTime(long)
	 * 
	 * @since JMS 2.0
	 */ 
   long getJMSDeliveryTime() throws JMSException;

   	/**
	 * Sets the message's delivery time value.
	 * <P>
	 * This method is for use by JMS providers only to set this field when a
	 * message is sent. This message cannot be used by clients to configure the
	 * delivery time of the message. This method is public to allow a JMS
	 * provider to set this field when sending a message whose implementation is
	 * not its own.
	 * 
	 * @param deliveryTime
	 *            the message's delivery time value
	 * 
	 * @exception JMSException
	 *                if the JMS provider fails to set the delivery time due to
	 *                some internal error.
	 * 
	 * @see javax.jms.Message#getJMSDeliveryTime()
	 * 
	 * @since JMS 2.0
	 */ 
   void setJMSDeliveryTime(long deliveryTime) throws JMSException;    


    /** Gets the message priority level.
      *  
      * <P>The JMS API defines ten levels of priority value, with 0 as the 
      * lowest
      * priority and 9 as the highest. In addition, clients should consider
      * priorities 0-4 as gradations of normal priority and priorities 5-9
      * as gradations of expedited priority.
      *  
      * <P>The JMS API does not require that a provider strictly implement 
      * priority 
      * ordering of messages; however, it should do its best to deliver 
      * expedited messages ahead of normal messages.
      *  
      * @return the default message priority
      *  
      * @exception JMSException if the JMS provider fails to get the message 
      *                         priority due to some internal error.
      *
      * @see javax.jms.Message#setJMSPriority(int) 
      */ 

    int
    getJMSPriority() throws JMSException;


    /** Sets the priority level for this message.
      *  
     * <P>This method is for use by JMS providers only to set this field 
     * when a message is sent. This message cannot be used by clients 
     * to configure the priority level of the message. This method is public
     * to allow a JMS provider to set this field when sending a message
     * whose implementation is not its own.
      *
      * @param priority the priority of this message
      *  
      * @exception JMSException if the JMS provider fails to set the message 
      *                         priority due to some internal error.
      *
      * @see javax.jms.Message#getJMSPriority() 
      */ 

    void
    setJMSPriority(int priority) throws JMSException;


    /** Clears a message's properties.
      *
      * <P>The message's header fields and body are not cleared.
      *
      * @exception JMSException if the JMS provider fails to clear the message 
      *                         properties due to some internal error.
      */ 

    void
    clearProperties() throws JMSException;


    /** Indicates whether a property value exists.
      *
      * @param name the name of the property to test
      *
      * @return true if the property exists
      *  
      * @exception JMSException if the JMS provider fails to determine if the 
      *                         property exists due to some internal error.
      */

    boolean
    propertyExists(String name) throws JMSException;


    /** Returns the value of the {@code boolean} property with the  
      * specified name.
      *  
      * @param name the name of the {@code boolean} property
      *  
      * @return the {@code boolean} property value for the specified name
      *  
      * @exception JMSException if the JMS provider fails to get the property
      *                         value due to some internal error.
      * @exception MessageFormatException if this type conversion is invalid. 
      */ 

    boolean
    getBooleanProperty(String name) throws JMSException;


    /** Returns the value of the {@code byte} property with the specified 
      * name.
      *  
      * @param name the name of the {@code byte} property
      *  
      * @return the {@code byte} property value for the specified name
      *  
      * @exception JMSException if the JMS provider fails to get the property
      *                         value due to some internal error.
      * @exception MessageFormatException if this type conversion is invalid. 
      */ 

    byte
    getByteProperty(String name) throws JMSException;


    /** Returns the value of the {@code short} property with the specified 
      * name.
      *
      * @param name the name of the {@code short} property
      *
      * @return the {@code short} property value for the specified name
      *
      * @exception JMSException if the JMS provider fails to get the property
      *                         value due to some internal error.
      * @exception MessageFormatException if this type conversion is invalid.
      */
   
    short
    getShortProperty(String name) throws JMSException;
 
 
    /** Returns the value of the {@code int} property with the specified 
      * name.
      *  
      * @param name the name of the {@code int} property
      *  
      * @return the {@code int} property value for the specified name
      *  
      * @exception JMSException if the JMS provider fails to get the property
      *                         value due to some internal error.
      * @exception MessageFormatException if this type conversion is invalid.
      */ 

    int
    getIntProperty(String name) throws JMSException;


    /** Returns the value of the {@code long} property with the specified 
      * name.
      *  
      * @param name the name of the {@code long} property
      *  
      * @return the {@code long} property value for the specified name
      *  
      * @exception JMSException if the JMS provider fails to get the property
      *                         value due to some internal error.
      * @exception MessageFormatException if this type conversion is invalid.
      */ 

    long
    getLongProperty(String name) throws JMSException;


    /** Returns the value of the {@code float} property with the specified 
      * name.
      *  
      * @param name the name of the {@code float} property
      *  
      * @return the {@code float} property value for the specified name
      *  
      * @exception JMSException if the JMS provider fails to get the property
      *                         value due to some internal error.
      * @exception MessageFormatException if this type conversion is invalid.
      */ 

    float
    getFloatProperty(String name) throws JMSException;


    /** Returns the value of the {@code double} property with the specified
      * name.
      *  
      * @param name the name of the {@code double} property
      *  
      * @return the {@code double} property value for the specified name
      *  
      * @exception JMSException if the JMS provider fails to get the property
      *                         value due to some internal error.
      * @exception MessageFormatException if this type conversion is invalid.
      */ 

    double
    getDoubleProperty(String name) throws JMSException;


    /** Returns the value of the {@code String} property with the specified
      * name.
      *  
      * @param name the name of the {@code String} property
      *  
      * @return the {@code String} property value for the specified name;
      * if there is no property by this name, a null value is returned
      *  
      * @exception JMSException if the JMS provider fails to get the property
      *                         value due to some internal error.
      * @exception MessageFormatException if this type conversion is invalid.
      */ 

    String
    getStringProperty(String name) throws JMSException;


    /** Returns the value of the Java object property with the specified name.
      *  
      * <P>This method can be used to return, in objectified format,
      * an object that has been stored as a property in the message with the 
      * equivalent <CODE>setObjectProperty</CODE> method call, or its equivalent
      * primitive <CODE>set<I>type</I>Property</CODE> method.
      *  
      * @param name the name of the Java object property
      *  
      * @return the Java object property value with the specified name, in 
      * objectified format (for example, if the property was set as an 
      * {@code int}, an {@code Integer} is 
      * returned); if there is no property by this name, a null value 
      * is returned
      *  
      * @exception JMSException if the JMS provider fails to get the property
      *                         value due to some internal error.
      */ 

    Object
    getObjectProperty(String name) throws JMSException;


    /** Returns an {@code Enumeration} of all the property names.
      *
      * <P>Note that JMS standard header fields are not considered
      * properties and are not returned in this enumeration.
      *  
      * @return an enumeration of all the names of property values
      *  
      * @exception JMSException if the JMS provider fails to get the property
      *                          names due to some internal error.
      */ 
     
    Enumeration
    getPropertyNames() throws JMSException;


    /** Sets a {@code boolean} property value with the specified name into 
      * the message.
      *
      * @param name the name of the {@code boolean} property
      * @param value the {@code boolean} property value to set
      *  
      * @exception JMSException if the JMS provider fails to set the property
      *                          due to some internal error.
      * @exception IllegalArgumentException if the name is null or if the name is
      *                          an empty string.
      * @exception MessageNotWriteableException if properties are read-only
      */ 

    void
    setBooleanProperty(String name, boolean value)
                        throws JMSException;


    /** Sets a {@code byte} property value with the specified name into 
      * the message.
      *  
      * @param name the name of the {@code byte} property
      * @param value the {@code byte} property value to set
      *  
      * @exception JMSException if the JMS provider fails to set the property
      *                          due to some internal error.
      * @exception IllegalArgumentException if the name is null or if the name is
      *                          an empty string.
      * @exception MessageNotWriteableException if properties are read-only
      */ 

    void
    setByteProperty(String name, byte value)
                        throws JMSException;


    /** Sets a {@code short} property value with the specified name into
      * the message.
      *  
      * @param name the name of the {@code short} property
      * @param value the {@code short} property value to set
      *  
      * @exception JMSException if the JMS provider fails to set the property
      *                          due to some internal error.
      * @exception IllegalArgumentException if the name is null or if the name is
      *                          an empty string.
      * @exception MessageNotWriteableException if properties are read-only
      */ 

    void
    setShortProperty(String name, short value)
                        throws JMSException;


    /** Sets an {@code int} property value with the specified name into
      * the message.
      *  
      * @param name the name of the {@code int} property
      * @param value the {@code int} property value to set
      *  
      * @exception JMSException if the JMS provider fails to set the property
      *                          due to some internal error.
      * @exception IllegalArgumentException if the name is null or if the name is
      *                          an empty string.
      * @exception MessageNotWriteableException if properties are read-only
      */ 

    void
    setIntProperty(String name, int value)
                        throws JMSException;


    /** Sets a {@code long} property value with the specified name into 
      * the message.
      *  
      * @param name the name of the {@code long} property
      * @param value the {@code long} property value to set
      *  
      * @exception JMSException if the JMS provider fails to set the property
      *                          due to some internal error.
      * @exception IllegalArgumentException if the name is null or if the name is
      *                          an empty string.
      * @exception MessageNotWriteableException if properties are read-only
      */ 

    void
    setLongProperty(String name, long value)
                        throws JMSException;


    /** Sets a {@code float} property value with the specified name into 
      * the message.
      *  
      * @param name the name of the {@code float} property
      * @param value the {@code float} property value to set
      *  
      * @exception JMSException if the JMS provider fails to set the property
      *                          due to some internal error.
      * @exception IllegalArgumentException if the name is null or if the name is
      *                          an empty string.
      * @exception MessageNotWriteableException if properties are read-only
      */ 

    void
    setFloatProperty(String name, float value)
                        throws JMSException;


    /** Sets a {@code double} property value with the specified name into 
      * the message.
      *  
      * @param name the name of the {@code double} property
      * @param value the {@code double} property value to set
      *  
      * @exception JMSException if the JMS provider fails to set the property
      *                          due to some internal error.
      * @exception IllegalArgumentException if the name is null or if the name is
      *                          an empty string.
      * @exception MessageNotWriteableException if properties are read-only
      */ 

    void
    setDoubleProperty(String name, double value)
                        throws JMSException;


    /** Sets a {@code String} property value with the specified name into 
      * the message.
      *
      * @param name the name of the {@code String} property
      * @param value the {@code String} property value to set
      *  
      * @exception JMSException if the JMS provider fails to set the property
      *                          due to some internal error.
      * @exception IllegalArgumentException if the name is null or if the name is
      *                          an empty string.
      * @exception MessageNotWriteableException if properties are read-only
      */ 

    void
    setStringProperty(String name, String value)
                        throws JMSException;


    /** Sets a Java object property value with the specified name into the 
      * message.
      *  
      * <P>Note that this method works only for the objectified primitive
      * object types ({@code Integer}, {@code Double}, 
      * {@code Long} ...) and {@code String} objects.
      *  
      * @param name the name of the Java object property
      * @param value the Java object property value to set
      *  
      * @exception JMSException if the JMS provider fails to set the property
      *                          due to some internal error.
      * @exception IllegalArgumentException if the name is null or if the name is
      *                          an empty string.
      * @exception MessageFormatException if the object is invalid
      * @exception MessageNotWriteableException if properties are read-only
      */ 

    void
    setObjectProperty(String name, Object value)
                        throws JMSException;


    /** Acknowledges all consumed messages of the session of this consumed 
      * message.
      *  
      * <P>All consumed JMS messages support the {@code acknowledge} 
      * method for use when a client has specified that its JMS session's 
      * consumed messages are to be explicitly acknowledged.  By invoking 
      * {@code acknowledge} on a consumed message, a client acknowledges 
      * all messages consumed by the session that the message was delivered to.
      * 
      * <P>Calls to {@code acknowledge} are ignored for both transacted 
      * sessions and sessions specified to use implicit acknowledgement modes.
      *
      * <P>A client may individually acknowledge each message as it is consumed,
      * or it may choose to acknowledge messages as an application-defined group 
      * (which is done by calling acknowledge on the last received message of the group,
      *  thereby acknowledging all messages consumed by the session.)
      *
      * <P>Messages that have been received but not acknowledged may be 
      * redelivered.
      *
      * @exception JMSException if the JMS provider fails to acknowledge the
      *                         messages due to some internal error.
      * @exception IllegalStateException if this method is called on a closed
      *                         session.
      *
      * @see javax.jms.Session#CLIENT_ACKNOWLEDGE
      */ 

    void
    acknowledge() throws JMSException;


    /** Clears out the message body. Clearing a message's body does not clear 
      * its header values or property entries.
      *
      * <P>If this message body was read-only, calling this method leaves
      * the message body in the same state as an empty body in a newly
      * created message.
      *
      * @exception JMSException if the JMS provider fails to clear the message
      *                         body due to some internal error.
      */

    void
    clearBody() throws JMSException;
    
	/**
	 * Returns the message body as an object of the specified type. 
	 * This method may be called on any type of message except for
	 * <tt>StreamMessage</tt>. The message
	 * body must be capable of being assigned to the specified type. This means
	 * that the specified class or interface must be either the same as, or a
	 * superclass or superinterface of, the class of the message body. 
	 * If the message has no body then any type may be specified and null is returned.
	 * <p>
	 * 
	 * @param c
	 *            The type to which the message body will be assigned. <br>
	 *            If the message is a {@code TextMessage} then this parameter must 
	 *            be set to {@code String.class} or another type to which
	 *            a {@code String} is assignable. <br>
	 *            If the message is a {@code ObjectMessage} then parameter must 
	 *            must be set to {@code java.io.Serializable.class} or
	 *            another type to which the body is assignable. <br>
	 *            If the message is a {@code MapMessage} then this parameter must 
	 *            be set to {@code java.util.Map.class} (or {@code java.lang.Object.class}). <br>
	 *            If the message is a {@code BytesMessage} then this parameter must 
	 *            be set to {@code byte[].class} (or {@code java.lang.Object.class}). This method
	 *            will reset the {@code BytesMessage} before and after use.<br>
	 *            If the message is a 
	 *            {@code TextMessage}, {@code ObjectMessage}, {@code MapMessage} 
	 *            or {@code BytesMessage} and the message has no body, 
	 *            then the above does not apply and this parameter may be set to any type;
	 *            the returned value will always be null.<br>
	 *            If the message is a {@code Message} (but not one of its subtypes)
	 *            then this parameter may be set to any type;
	 *            the returned value will always be null.
	 * 
	 * @return the message body
	 * 
	 * @exception MessageFormatException
	 * 				  <ul>
	 *                <li>if the message is a {@code StreamMessage}
	 *                <li> if the message body cannot be assigned to 
	 *                the specified type 
	 *                <li> if the message is an {@code ObjectMessage} and object
	 *                deserialization fails.
	 *                </ul>
	 *                
	 * @exception JMSException
	 *                if the JMS provider fails to get the message body due to
	 *                some internal error.
	 *                
	 * @since JMS 2.0                
	 */
	<T> T getBody(Class<T> c) throws JMSException;

	/**
	 * Returns whether the message body is capable of being assigned to the
	 * specified type. If this method returns true then a subsequent call to the
	 * method {@code getBody} on the same message with the same type argument would not throw a
	 * MessageFormatException.
	 * <p>
	 * If the message is a {@code StreamMessage} then false is always returned. 
	 * If the message is a {@code ObjectMessage} and object deserialization
	 * fails then false is returned. If the message has no body then any type may be specified and true is
	 * returned.
	 * 
	 * @param c
	 *            The specified type <br>
	 *            If the message is a {@code TextMessage} then this method will
	 *            only return true if this parameter is set to
	 *            {@code String.class} or another type to which a {@code String}
	 *            is assignable. <br>
	 *            If the message is a {@code ObjectMessage} then this
	 *            method will only return true if this parameter is set to
	 *            {@code java.io.Serializable.class} or another class to
	 *            which the body is assignable. <br>
	 *            If the message is a {@code MapMessage} then this method
	 *            will only return true if this parameter is set to
	 *            {@code java.util.Map.class} (or {@code java.lang.Object.class}). <br>
	 *            If the message is a {@code BytesMessage} then this this
	 *            method will only return true if this parameter is set to
	 *            {@code byte[].class} (or {@code java.lang.Object.class}). <br>
	 *            If the message is a 
	 *            {@code TextMessage}, {@code ObjectMessage}, {@code MapMessage} 
	 *            or {@code BytesMessage} and the message has no body, 
	 *            then the above does not apply and this method will return true
	 *            irrespective of the value of this parameter.<br>
	 *            If the message is a 
	 *            {@code Message} (but not one of its subtypes)
	 *            then this method will return true
	 *            irrespective of the value of this parameter.          
	 * 
	 * @return whether the message body is capable of being assigned to the
	 *         specified type
	 * 
	 * @exception JMSException
	 *                if the JMS provider fails to return a value due to some
	 *                internal error.
	 */
	boolean isBodyAssignableTo(Class c) throws JMSException;    
}
