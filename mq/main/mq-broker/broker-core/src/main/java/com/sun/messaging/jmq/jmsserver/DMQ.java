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
 * @(#)DMQ.java	1.5 06/28/07
 */ 

package com.sun.messaging.jmq.jmsserver;

/**
 * This class contains Mesage Queue properties used in conjuneciton
 * with the dead message queue. This class defines the following properties:<P>
 *
 * <B>JMSQ specific Properties for Produced Messages</b><P>
 *     <BLOCKQUOTE>
 *         <TABLE border=1>
 *             <TR>
 *                <TH>Property</TH>
 *                <TH>Type</TH>
 *                <TH>Values</TH>
 *                <TH>Default</TH>
 *                <TH>Descritpion</TH>
 *            </TR>
 *            <TR>
 *                <TD>JMS_SUN_PRESERVE_UNDELIVERED</td>
 *                <TD>boolean</td>
 *                <TD>TRUE<BR>FALSE<BR>Unset</td>
 *                <TD>Unset</td>
 *                <TD>overrides whether the
 *                    message should be sent to
 *                    the DMQ</TD>
 *            </TR>
 *             <TR>
 *                <TD>JMS_SUN_LOG_DEAD_MESSAGES</td>
 *                <TD>boolean</td>
 *                <TD>TRUE<BR>FALSE</td>
 *                <TD>False</td>
 *                <TD> indicates that the broker
 *                    should log when a message
 *                    is destroyed or moved to the
 *                    DMQ</TD>
 *            </TR>
 *             <TR>
 *                <TD>JMS_SUN_TRUNCATE_MSG_BODY</td>
 *                <TD>boolean</td>
 *                <TD>TRUE<BR>FALSE</td>
 *                <TD>FALSE</td>
 *                <TD>if set, it indicates that message 
*                     body will not be stored in the DMQ</TD>
 *            </TR>
 *         </TABLE>
 *     </BLOCKQUOTE><P>
 * <B>JMSQ specific Properties for Dead Messages</b><P>
 *         <BLOCKQUOTE>
 *         <TABLE border=1>
 *             <TR>
 *                <TH>Property</TH>
 *                <TH>Type</TH>
 *                <TH>Value</TH>
 *                <TH>Description</TH>
 *            </TR>
 *            <TR>
 *                <TD>JMS_SUN_DMQ_DELIVERY_COUNT</td>
 *                <TD>Integer</td>
 *                <TD>number</td>
 *                <TD>largest number of times the message was delivered to a given consumer (only set for ERROR or UNDELIVERABLE messages)</TD>
 *            </TR>
 *            <TR>
 *                <TD>JMS_SUN_MQ_UNDELIVERED_TIMESTAMP</td>
 *                <TD>Long</td>
 *                <TD>time in milliseconds</td>
 *                <TD>time the message was placed on the DMQ</TD>
 *            </TR>
 *             <TR>
 *                <TD>JMS_SUN_MQ_UNDELIVERED_REASON</td>
 *                <TD>String</td>
 *                <TD>OLDEST<BR>LOW_PRIORITY<BR>EXPIRED
 *                    <BR>UNDELIVERABLE<BR>ERROR</td>
 *                <TD>Reason the message was placed on the DMQ</TD>
 *            </TR>
 *             <TR>
 *                <TD>JMS_SUN_MQ_PRODUCING_BROKER</td>
 *                <TD>String</td>
 *                <TD>broker name</td>
 *                <TD>Identification of producing broker </TD>
 *            </TR>
 *             <TR>
 *                <TD>JMS_SUN_MQ_DEAD_BROKER</td>
 *                <TD>String</td>
 *                <TD>broker name</td>
 *                <TD>Identification of broker marking message dead </TD>
 *            </TR>
 *             <TR>
 *                <TD>JMS_SUN_MQ_UNDELIVERED_EXCEPTION</td>
 *                <TD>String</td>
 *                <TD>Exception name</td>
 *                <TD>If the message was dead because of an exception on either the client or the broker, this is the string for that exception</TD>
 *            </TR>
 *             <TR>
 *                <TD>JMS_SUN_MQ_UNDELIVERED_COMMENT</td>
 *                <TD>String</td>
 *                <TD>Optional Comment</td>
 *                <TD>optional comment provided when the message is marked dead</TD>
 *            </TR>
 *             <TR>
 *                <TD>JMS_SUN_MQ_BODY_TRUNCATED</td>
 *                <TD>boolean</td>
 *                <TD>TRUE<BR>FALSE</td>
 *                <TD>If true, indicates that the message body was not stored because of configuration options</TD>
 *            </TR>
 *         </TABLE>
 *         </BLOCKQUOTE><P>
 *
 */

public class DMQ
{
    private DMQ() {
    }

    /**
     * Produced Message property which overrides the
     * destination configuration.
     * <br>
     * Possible Values:<P>
     *   <TABLE border=1>
     *     <TR><TD>Boolean.TRUE</TD><TD>Sends this message to DMQ if it
     *            becomes dead</TD></TR>
     *     <TR><TD>Boolean.FALSE</TD><TD>Throws out this message if
     *            becomes dead</TD></TR>
     *     <TR><TD><i>&lt;unset&gt;</i></TD><TD>uses setting for message to
     *            this destination</TD></TR>
     *   </TABLE><P>
     * Usage:<BR><BLOCKQUOTE><I>
     *    msg.setBooleanProperty(DMQ.PRESERVE_UNDELIVERED, false);
     * </I></BLOCKQUOTE>
     */
    public static final String PRESERVE_UNDELIVERED=
          "JMS_SUN_PRESERVE_UNDELIVERED";
    /**
     * Requests that the broker log additional information when a 
     * message is destroyed or moved to the DMQ.
     * <br>
     * Possible Values:<P>
     *   <TABLE border=1>
     *     <TR><TD>Boolean.TRUE</TD><TD>Information is sent to the log file
     *            when it is destroy and also when it is move to the DMQ</TD></TR>
     *     <TR><TD>Boolean.FALSE</TD><TD>No information is logged when the message
     *            is destroyed or is marked dead </TD></TR>
     *     <TR><TD><i>&lt;unset&gt;</i></TD><TD>uses default broker set for logging
     *            verbose message information</TD></TR>
     *   </TABLE><P>
     * Usage:<BR><BLOCKQUOTE><I>
     *    msg.setBooleanProperty(DMQ.VERBOSE, false);
     * </I></BLOCKQUOTE>
     */
    public static final String VERBOSE=
          "JMS_SUN_LOG_DEAD_MESSAGES";

    /**
     * Determines if the full message or just the headers and properties
     * should be maintained when the message is moved to the DMQ.
     * <br>
     * Possible Values:<P>
     *   <TABLE border=1>
     *     <TR><TD>Boolean.TRUE</TD><TD>Message body (as well as properties and
     *            headers) will be maintained if the message is stored in the DMQ
     *            </TD></TR>
     *     <TR><TD>Boolean.FALSE</TD><TD>Only properties and header information
     *            is maintained if the message is placed in the DMQ </TD></TR>
     *     <TR><TD><i>&lt;unset&gt;</i></TD><TD>uses default broker property
     *            setting <i>imq.destination.DMQ.storeBody</i></TD></TR>
     *   </TABLE><P>
     * Usage:<BR><BLOCKQUOTE><I>
     *    msg.setBooleanProperty(DMQ.STORE_BODY, false);
     * </I></BLOCKQUOTE>
     *
     */

    public static final String TRUNCATE_BODY=
          "JMS_SUN_TRUNCATE_MSG_BODY";


    /**
     * Timestamp that a message was placed on the Dead Message Queue.
     * Property value is a long.
     * <br>
     *
     * Usage:<BR><BLOCKQUOTE><I>
     *    long time = msg.getLongProperty(DMQ.UNDELIVERED_TIMESTAMP);
     * </I></BLOCKQUOTE>
     */
    public static final String UNDELIVERED_TIMESTAMP=
          "JMS_SUN_DMQ_UNDELIVERED_TIMESTAMP";


    /**
     * Reason that a message was placed on the DeadMessageQueue.
     * If a message was marked dead for multiple reasons, it is 
     * not defined what reason will be returned.
     * Property value is a String.
     * <br>
     *
     * Usage:<BR><BLOCKQUOTE><I>
     *    String reason = msg.getStringProperty(DMQ.UNDELIVERED_REASON);
     * </I></BLOCKQUOTE>
     * @see #REASON_OLDEST
     * @see #REASON_LOW_PRIORITY
     * @see #REASON_EXPIRED
     * @see #REASON_UNDELIVERABLE
     * @see #REASON_ERROR
     */
    public static final String UNDELIVERED_REASON=
          "JMS_SUN_DMQ_UNDELIVERED_REASON";

    /**
     * The largest number of times the message was delivered to a given
     * consumer that gives ERROR or UNDELIVERABLE before mark dead
     * <br>
     *
     * Usage:<BR><BLOCKQUOTE><I>
     *    int count  = msg.getIntProperty(DMQ.DELIVERY_COUNT);
     * </I></BLOCKQUOTE>
     */
    public static final String DELIVERY_COUNT=
          "JMS_SUN_DMQ_DELIVERY_COUNT";

    /**
     * Exception which caused the message to become dead (optional).
     * Property value is a String which is Exception.toString().
     * <br>
     *
     * Usage:<BR><BLOCKQUOTE><I>
     *    String ex_str = msg.getStringProperty(DMQ.UNDELIVERED_EXCEPTION);
     * </I></BLOCKQUOTE>
     */
    public static final String UNDELIVERED_EXCEPTION=
          "JMS_SUN_DMQ_UNDELIVERED_EXCEPTION";

    /**
     * Comment about why the message was defined as dead (optional).
     * Property value is a String. There is not a defined set of 
     * return values.
     * <br>
     *
     * Usage:<BR><BLOCKQUOTE><I>
     *    String comment = msg.getStringProperty(DMQ.UNDELIVERED_COMMENT);
     * </I></BLOCKQUOTE>
     */
    public static final String UNDELIVERED_COMMENT=
          "JMS_SUN_DMQ_UNDELIVERED_COMMENT";

    /**
     * Indicates if the message body was truncated (not stored) when
     * the message was moved into the DMQ.
     * Property value is a boolean. 
     * <P>
     * Return values:<BR>
     * <TABLE>
     *    <TR><TD>true</td><td>message was truncated and only properties
     *                         and headers are available on the message</td></tr>
     *    <TR><TD>false</td><td>the message was not truncated and the content
     *                         of the message body is available</td></tr>
     * </TABLE><P>
     *
     * Usage:<BR><BLOCKQUOTE><I>
     *    boolean truncated = msg.getBooleanProperty(DMQ.BODY_TRUNCATED);
     * </I></BLOCKQUOTE>
     */
    public static final String BODY_TRUNCATED=
          "JMS_SUN_DMQ_BODY_TRUNCATED";


    /**
     * Name of the sending broker . 
     */
    public static final String BROKER=
          "JMS_SUN_DMQ_PRODUCING_BROKER";

    /**
     * Name of the broker who marked the message dead. 
     */
    public static final String DEAD_BROKER=
          "JMS_SUN_DMQ_DEAD_BROKER";


    /**
     * Message was removed because the destination has a behavior of
     * REMOVE_OLDEST and this message fit that criteria.
     * This string is a possible value for the UNDELIVERED_REASON property.
     * @see #UNDELIVERED_REASON
     */
    public static final String REASON_OLDEST = "OLDEST";

    /**
     * Message was removed because the destination has a behavior of
     * REMOVE_LOW_PRIORITY and this message fit that criteria.
     * This string is a possible value for the UNDELIVERED_REASON property.
     * @see #UNDELIVERED_REASON
     */
    public static final String REASON_LOW_PRIORITY = "LOW_PRIORITY";

    /**
     * Message was expired (because of a message TTL).
     * This string is a possible value for the UNDELIVERED_REASON property.
     * @see #UNDELIVERED_REASON
     */
    public static final String REASON_EXPIRED = "EXPIRED";

    /**
     * Message was removed because the it was re-sent to the consumer
     * too many times or otherwise condisidered "undeliverable".
     * This string is a possible value for the UNDELIVERED_REASON property.
     * @see #UNDELIVERED_REASON
     */
    public static final String REASON_UNDELIVERABLE = "UNDELIVERABLE";

    /**
     * Message was removed because the broker received an Error processing it.
     * This string is a possible value for the UNDELIVERED_REASON property.
     * @see #UNDELIVERED_REASON
     */
    public static final String REASON_ERROR = "ERROR";



}
