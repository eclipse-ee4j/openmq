/*
 * Copyright (c) 2006, 2017 Oracle and/or its affiliates. All rights reserved.
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

package com.sun.messaging.jmq.util.log;


import org.jvnet.hk2.annotations.ContractsProvided;
import org.jvnet.hk2.annotations.Service;
import org.glassfish.hk2.api.PerLookup;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.text.MessageFormat;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.logging.*;
import java.util.logging.Formatter;

/**
 * UniformLogFormatter conforms to the logging format defined by the
 * Log Working Group in Java Webservices Org.
 * The specified format is
 * "[#|DATETIME|LOG_LEVEL|PRODUCT_ID|LOGGER NAME|OPTIONAL KEY VALUE PAIRS|
 * MESSAGE|#]\n"
 *
 * @author Hemanth Puttaswamy
 *         <p/>
 *         TODO:
 *         1. Performance improvement. We can Cache the LOG_LEVEL|PRODUCT_ID strings
 *         and minimize the concatenations and revisit for more performance
 *         improvements
 *         2. Need to use Product Name and Version based on the version string
 *         that is part of the product.
 *         3. Stress testing
 *         4. If there is a Map as the last element, need to scan the message to
 *         distinguish key values with the message argument.
 */
@Service()
@ContractsProvided(Formatter.class)
@PerLookup
public class UniformLogFormatter extends Formatter {
    // loggerResourceBundleTable caches references to all the ResourceBundle
    // and can be searched using the LoggerName as the key 
    private HashMap loggerResourceBundleTable;
    private LogManager logManager;
    // A Dummy Container Date Object is used to format the date
    private Date date = new Date();
    private static String PRODUCTID_CONTEXTID = null;
    private static com.sun.messaging.jmq.Version version = new com.sun.messaging.jmq.Version(false);
    // This is temporary, in the next phase of implementation the product Id
    // will be obtained from the version object that is part of Sun One AppServ
    // Bug 4882896: string initialized using Version.java
//    private static final String PRODUCT_VERSION =
//            com.sun.appserv.server.util.Version.getAbbreviatedVersion();
    private static final String PRODUCT_VERSION = version.getVersion();
    private static final int FINE_LEVEL_INT_VALUE = Level.FINE.intValue();

    private static boolean LOG_SOURCE_IN_KEY_VALUE = false;

    private static boolean RECORD_NUMBER_IN_KEY_VALUE = false;

    private com.sun.messaging.jmq.util.log.FormatterDelegate _delegate = null;

    static {
        String logSource = System.getProperty(
                "com.sun.aas.logging.keyvalue.logsource");
        if ((logSource != null)
                && (logSource.equals("true"))) {
            LOG_SOURCE_IN_KEY_VALUE = true;
        }

        String recordCount = System.getProperty(
                "com.sun.aas.logging.keyvalue.recordnumber");
        if ((recordCount != null)
                && (recordCount.equals("true"))) {
            RECORD_NUMBER_IN_KEY_VALUE = true;
        }
    }

    private long recordNumber = 0;

    private static final String LINE_SEPARATOR =
            (String) java.security.AccessController.doPrivileged(
                    new sun.security.action.GetPropertyAction("line.separator"));

    private String recordBeginMarker;
    private String recordEndMarker;
    private String recordFieldSeparator;
    private String recordDateFormat;

    private static final String RECORD_BEGIN_MARKER = "[#|";
    private static final String RECORD_END_MARKER = "|#]" + LINE_SEPARATOR;
    private static final char FIELD_SEPARATOR = '|';
    public static final char NVPAIR_SEPARATOR = ';';
    public static final char NV_SEPARATOR = '=';

    private static final String RFC_3339_DATE_FORMAT =
            "yyyy-MM-dd'T'HH:mm:ss.SSSZ";

    public UniformLogFormatter() {
        super();
        loggerResourceBundleTable = new HashMap();
        logManager = LogManager.getLogManager();
    }

    public UniformLogFormatter(com.sun.messaging.jmq.util.log.FormatterDelegate delegate) {
        this();
        _delegate = delegate;
    }


    public void setDelegate(com.sun.messaging.jmq.util.log.FormatterDelegate delegate) {
        _delegate = delegate;
    }

    /**
     * _REVISIT_: Replace the String Array with an HashMap and do some
     * benchmark to determine whether StringCat is faster or Hashlookup for
     * the template is faster.
     */


    @Override
    public String format(java.util.logging.LogRecord record) {
        return uniformLogFormat(record);
    }

    @Override
    public String formatMessage(java.util.logging.LogRecord record) {
        return uniformLogFormat(record);
    }


    /**
     * GlassFish can override to specify their product version
     */
    protected String getProductId() {

//        String version = Version.getAbbreviatedVersion() + Version.getVersionPrefix() + 
//                Version.getMajorVersion() + "." + Version.getMinorVersion();
//        return (version);
    	return version.getProductVersion();
    	
    }


    /**
     * Sun One Appserver SE/EE? can override to specify their product specific
     * key value pairs.
     */
    protected void getNameValuePairs(StringBuilder buf, java.util.logging.LogRecord record) {

        Object[] parameters = record.getParameters();
        if ((parameters == null) || (parameters.length == 0)) {
            return;
        }

        try {
            for (Object obj : parameters) {
                if (obj == null) {
                    continue;
                }
                if (obj instanceof Map) {
                    for (Map.Entry<Object, Object> entry : ((Map<Object, Object>) obj).entrySet()) {
                        // there are implementations that allow <null> keys...
                        if (entry.getKey() != null) {
                            buf.append(entry.getKey().toString());
                        } else {
                            buf.append("null");
                        }

                        buf.append(NV_SEPARATOR);

                        // also handle <null> values...
                        if (entry.getValue() != null) {
                            buf.append(entry.getValue().toString());
                        } else {
                            buf.append("null");
                        }
                        buf.append(NVPAIR_SEPARATOR);

                    }
                } else if (obj instanceof java.util.Collection) {
                    for (Object entry : ((Collection) obj)) {
                        // handle null values (remember the specs)...
                        if (entry != null) {
                            buf.append(entry.toString());
                        } else {
                            buf.append("null");
                        }
                        buf.append(NVPAIR_SEPARATOR);

                    }
//                } else {
//                    buf.append(obj.toString()).append(NVPAIR_SEPARATOR);
                }
            }
        } catch (Exception e) {
            new ErrorManager().error(
                    "Error in extracting Name Value Pairs", e,
                    ErrorManager.FORMAT_FAILURE);
        }
    }

    /**
     * Note: This method is not synchronized, we are assuming that the
     * synchronization will happen at the Log Handler.publish( ) method.
     */
    private String uniformLogFormat(java.util.logging.LogRecord record) {

        try {

            SimpleDateFormat dateFormatter = new SimpleDateFormat(getRecordDateFormat() != null ? getRecordDateFormat() : RFC_3339_DATE_FORMAT);

            StringBuilder recordBuffer = new StringBuilder(getRecordBeginMarker() != null ? getRecordBeginMarker() : RECORD_BEGIN_MARKER);
            // The following operations are to format the date and time in a
            // human readable  format.
            // _REVISIT_: Use HiResolution timer to analyze the number of
            // Microseconds spent on formatting date object
            date.setTime(record.getMillis());
            recordBuffer.append(dateFormatter.format(date));
            recordBuffer.append(getRecordFieldSeparator() != null ? getRecordFieldSeparator() : FIELD_SEPARATOR);

            recordBuffer.append(record.getLevel()).append(getRecordFieldSeparator() != null ? getRecordFieldSeparator() : FIELD_SEPARATOR);
            recordBuffer.append(getProductId()).append(getRecordFieldSeparator() != null ? getRecordFieldSeparator() : FIELD_SEPARATOR);
            recordBuffer.append(record.getLoggerName()).append(getRecordFieldSeparator() != null ? getRecordFieldSeparator() : FIELD_SEPARATOR);

            recordBuffer.append("_ThreadID").append(NV_SEPARATOR);
            //record.setThreadID((int) Thread.currentThread().getId());
            recordBuffer.append(record.getThreadID()).append(NVPAIR_SEPARATOR);

            recordBuffer.append("_ThreadName").append(NV_SEPARATOR);
            recordBuffer.append(Thread.currentThread().getName());
            recordBuffer.append(NVPAIR_SEPARATOR);

            // See 6316018. ClassName and MethodName information should be
            // included for FINER and FINEST log levels.
            Level level = record.getLevel();
            if (LOG_SOURCE_IN_KEY_VALUE ||
                    (level.intValue() <= Level.FINE.intValue())) {
                recordBuffer.append("ClassName").append(NV_SEPARATOR);
                recordBuffer.append(record.getSourceClassName());
                recordBuffer.append(NVPAIR_SEPARATOR);
                recordBuffer.append("MethodName").append(NV_SEPARATOR);
                recordBuffer.append(record.getSourceMethodName());
                recordBuffer.append(NVPAIR_SEPARATOR);
            }

            if (RECORD_NUMBER_IN_KEY_VALUE) {
                recordBuffer.append("RecordNumber").append(NV_SEPARATOR);
                recordBuffer.append(recordNumber++).append(NVPAIR_SEPARATOR);
            }

            // Not needed as per the current logging message format. Fixing bug 16849.
            // getNameValuePairs(recordBuffer, record);

            if (_delegate != null) {
                _delegate.format(recordBuffer, level);
            }

            recordBuffer.append(getRecordFieldSeparator() != null ? getRecordFieldSeparator() : FIELD_SEPARATOR);

            String logMessage = record.getMessage();
            // in some case no msg is passed to the logger API. We assume that either:
            // 1. A message was logged in a previous logger call and now just the exception is logged.
            // 2. There is a bug in the calling code causing the message to be missing.
            if (logMessage == null || logMessage.trim().equals("")) {

                if (record.getThrown() != null) {
                    // case 1: Just log the exception instead of a message
                    StringWriter sw = new StringWriter();
                    PrintWriter pw = new PrintWriter(sw);
                    record.getThrown().printStackTrace(pw);
                    pw.close();
                    recordBuffer.append(sw.toString());
                    sw.close();
                } else {
                    // case 2: Log an error message (using original severity)
                    logMessage = "The log message is empty or null. Please log an issue against the component in the logger field.";
                    recordBuffer.append(logMessage);
                }
            } else {
                if (logMessage.indexOf("{0") >= 0 && logMessage.contains("}") && record.getParameters() != null) {
                    // If we find {0} or {1} etc., in the message, then it's most
                    // likely finer level messages for Method Entry, Exit etc.,
                    logMessage = java.text.MessageFormat.format(
                            logMessage, record.getParameters());
                } else {
                    ResourceBundle rb = getResourceBundle(record.getLoggerName());
                    if (rb != null) {
                        try {
                            logMessage = MessageFormat.format(
                                    rb.getString(logMessage),
                                    record.getParameters());
                        } catch (java.util.MissingResourceException e) {
                            // If we don't find an entry, then we are covered 
                            // because the logMessage is initialized already
                        }
                    }
                }
                recordBuffer.append(logMessage);
    
                if (record.getThrown() != null) {
                    recordBuffer.append(LINE_SEPARATOR);
                    StringWriter sw = new StringWriter();
                    PrintWriter pw = new PrintWriter(sw);
                    record.getThrown().printStackTrace(pw);
                    pw.close();
                    recordBuffer.append(sw.toString());
                    sw.close();
                }
            }

            recordBuffer.append(getRecordEndMarker() != null ? getRecordEndMarker() : RECORD_END_MARKER).append(LINE_SEPARATOR).append(LINE_SEPARATOR);
            return recordBuffer.toString();

        } catch (Exception ex) {
            new ErrorManager().error(
                    "Error in formatting Logrecord", ex,
                    ErrorManager.FORMAT_FAILURE);
            // We've already notified the exception, the following
            // return is to keep javac happy
            return "";
        }
    }

    private synchronized ResourceBundle getResourceBundle(String loggerName) {
        if (loggerName == null) {
            return null;
        }
        ResourceBundle rb = (ResourceBundle) loggerResourceBundleTable.get(
                loggerName);
        /*
                * Note that logManager.getLogger(loggerName) untrusted code may create loggers with
                * any arbitrary names this method should not be relied on so added code for checking null.
                */
        if (rb == null && logManager.getLogger(loggerName) != null) {
            rb = logManager.getLogger(loggerName).getResourceBundle();
            loggerResourceBundleTable.put(loggerName, rb);
        }
        return rb;
    }

    public String getRecordBeginMarker() {
        return recordBeginMarker;
    }

    public void setRecordBeginMarker(String recordBeginMarker) {
        this.recordBeginMarker = recordBeginMarker;
    }

    public String getRecordEndMarker() {
        return recordEndMarker;
    }

    public void setRecordEndMarker(String recordEndMarker) {
        this.recordEndMarker = recordEndMarker;
    }

    public String getRecordFieldSeparator() {
        return recordFieldSeparator;
    }

    public void setRecordFieldSeparator(String recordFieldSeparator) {
        this.recordFieldSeparator = recordFieldSeparator;
    }

    public String getRecordDateFormat() {
        return recordDateFormat;
    }

    public void setRecordDateFormat(String recordDateFormat) {
        this.recordDateFormat = recordDateFormat;
    }
}
