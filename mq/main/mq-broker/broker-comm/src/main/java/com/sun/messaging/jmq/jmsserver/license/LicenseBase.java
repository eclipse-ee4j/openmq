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
 * @(#)LicenseBase.java	1.16 06/28/07
 */ 

package com.sun.messaging.jmq.jmsserver.license;

import com.sun.messaging.jmq.jmsserver.util.BrokerException;
import com.sun.messaging.jmq.jmsserver.comm.CommGlobals;
import com.sun.messaging.jmq.jmsserver.resources.*;
import com.sun.messaging.jmq.util.log.Logger;

import java.io.*;
import java.util.*;
import java.security.*;

/**
 * iMQ broker license base class.
 */
public class LicenseBase {
    //
    // BEGIN core licence attributes and constants.
    //

    /** License attributes. */
    protected Properties props = new Properties();

    private boolean autoChecking = true;

    // Following attributes are derived from PROP_DATE_STRING.
    private int daysToTry = 0;
    private Date expirationDate = null; // expiration date
    private Date startDate = null; // start date of valid date range

    //////// Constant values  ////////

    /** License file magic number */
    protected static final long LICENSE_MAGIC_NUMBER = 1011902605893L;

    /** The license file format version */
    private static final String CURRENT_FILE_VERSION = "4";
    //Bug 6157397  
    /** The release version */
    public static final String CURRENT_LICENSE_VERSION = "4.4 FCS";

    //////// License attribute property names ////////
    public static final String PROP_FILE_VERSION = "imq.file_version";
    public static final String PROP_LICENSE_VERSION = "imq.license_version";
    public static final String PROP_LICENSE_TYPE = "imq.license_type";
    public static final String PROP_DATE_STRING = "date_string";
    public static final String PROP_PRECEDENCE = "imq.precedence";
    public static final String PROP_DESCRIPTION = "description";

    //////// Expiration date string constants ////////
    protected static final String NONE_STRING = "NONE";
    protected static final String TRY_STRING = "TRY";
    protected static final String VALID_STRING = "VALID";
    protected static final String OPEN_BRACKET = "[";
    protected static final String CLOSE_BRACKET = "]";
    protected static final String DASH = "-";

    //////// Other (feature specific) license attributes ////////
    public static final String PROP_CLIENT_CONNLIMIT = "imq.max_client_conns";
    public static final String PROP_BROKER_CONNLIMIT = "imq.max_broker_conns";
    public static final String PROP_MAX_BACKUP_CONS = "imq.max_backup_cons";
    public static final String PROP_MAX_ACTIVE_CONS = "imq.max_active_cons";

    //////// Feature licensing ////////
    public static final String PROP_ENABLE_CLUSTER =
        "imq.enable_cluster";
    public static final String PROP_ENABLE_HTTP =
        "imq.enable_http";
    public static final String PROP_ENABLE_SSL =
        "imq.enable_ssl";
    public static final String PROP_ENABLE_SHAREDPOOL =
        "imq.enable_sharedpool";
    public static final String PROP_ENABLE_C_API =
        "imq.enable_c_api";
    public static final String PROP_ENABLE_FAILOVER =
        "imq.enable_failover";
    public static final String PROP_ENABLE_MONITORING =
        "imq.enable_monitoring";
    public static final String PROP_ENABLE_LOCALDEST =
        "imq.enable_localdest";
    
    //The following features have been added for Shrike
     public static final String PROP_ENABLE_DMQ = "imq.enable_dmq";
     public static final String PROP_ENABLE_CLIENTPING = "imq.enable_clientping";
     public static final String PROP_ENABLE_MSGBODY_COMPRESSION = "imq.enable_msgbody_compression";
     public static final String PROP_ENABLE_SHARED_SUB = "imq.enable_shared_sub";
     public static final String PROP_ENABLE_AUDIT_CCC = "imq.enable_audit_ccc";
     public static final String PROP_ENABLE_NO_ACK = "imq.enable_no_ack";
     public static final String PROP_ENABLE_RECONNECT = "imq.enable_reconnect";

    //The following features have been added for Hawk
     public static final String PROP_ENABLE_HA = "imq.enable_ha";
      
    //
    // END licence attributes and constants.
    //

    protected static final BrokerResources br = CommGlobals.getBrokerResources();
    protected static final Logger logger = CommGlobals.getLogger();

    //
    // License information public access methods.
    //

    /**
     * Get any license attribute.
     */
    public String getProperty(String name) {
        return props.getProperty(name);
    }

    /**
     * Get an integer property.
     */
    public int getIntProperty(String name, int defval) {
        try {
            return Integer.parseInt(getProperty(name));
        }
        catch (Exception e) {
            return defval;
        }
    }

    /**
     * Get a boolean property.
     */
    public boolean getBooleanProperty(String name, boolean defval) {
        try {
            return Boolean.valueOf(getProperty(name)).booleanValue();
        }
        catch (Exception e) {
            return defval;
        }
    }

    /**
     * Get all the license properties.
     */
    public Properties getProperties() {
        return props;
    }

    public int getPrecedence() {
        int ret = 0;
        try {
            ret = Integer.parseInt(props.getProperty(PROP_PRECEDENCE));
        }
        catch (Exception e) {}
        return ret;
    }

    /**
     * Does this license expire.
     */
    public boolean willExpire() {
        String dateString = props.getProperty(PROP_DATE_STRING);
        if (dateString == null || ! dateString.equals(NONE_STRING))
            return true;

        return false;
    }

    public Date getStartDate() {
        return startDate;
    }

    public void setStartDate(Date d) {
        startDate = d;
    }

    public Date getExpirationDate() {
        return expirationDate;
    }

    public void setExpirationDate(Date d) {
        expirationDate = d;
    }

    public int getDaysToTry() {
        return daysToTry;
    }

    public void setDaysToTry(int d) {
        daysToTry = d;
    }

    /**
     * This method is implemented by the subclasses of LicenseBase.
     * It tells the LicenseManager whether to raise an exception if
     * there are no license files.
     */
    public boolean isLicenseFileRequired() {
        return true;
    }

    /**
     * When license is loaded by the LicenseCmd command (used by
     * release engineers only), there is no need to perform the
     * license checks.
     */
    public void setAutoChecking(boolean autoChecking) {
        this.autoChecking = autoChecking;
    }

    /**
     * Superimpose new properties on a license base class.
     */
    public void superimpose(Properties newprops) throws BrokerException {
        // The new superimposed information always takes precedence over the
        // default attributes.
        Enumeration names = newprops.propertyNames();
        while (names.hasMoreElements()) {
            String name = (String) names.nextElement();
            String value = (String) newprops.getProperty(name);

            props.setProperty(name, value);
        }

        processLicenseInfo();
    }

    /**
     * This method is called after the properties are loaded.
     * It does some post-processing and performs some simple sanity checks on
     * the license attributes.
     */
    private void processLicenseInfo() throws BrokerException {
        if (autoChecking == false)
            return;

        // Check the file version.
        String fileVersion = props.getProperty(PROP_FILE_VERSION);
        if (fileVersion == null ||
            ! fileVersion.equals(CURRENT_FILE_VERSION))
            throw new BrokerException(br.getString(br.E_BAD_LICENSE_DATA));

        String licenseVersion = props.getProperty(PROP_LICENSE_VERSION);

        if (licenseVersion == null ||
            ! licenseVersion.equals(CURRENT_LICENSE_VERSION))
            throw new BrokerException(br.getString(br.E_BAD_LICENSE_DATA));

        parseDateString();
        checkValidity();
    }

    /**
     * Parse the expiration date string into appropriate fields.
     */
    private void parseDateString() throws BrokerException {
        String dateString = props.getProperty(PROP_DATE_STRING);
        if (dateString == null)
            throw new BrokerException(br.getString(br.E_BAD_LICENSE_DATA));

        if (dateString.startsWith(NONE_STRING)) {
            // Do nothing.
        }
        else if (dateString.startsWith(TRY_STRING)) {
            // this license contains the number of days to try
            int oindex = dateString.indexOf(OPEN_BRACKET);
            int cindex = dateString.indexOf(CLOSE_BRACKET);
            int d = Integer.parseInt(
                dateString.substring(oindex+1, cindex));
            setDaysToTry(d);
        } else if (dateString.startsWith(VALID_STRING)) {
            // this license contains a date range
            int oindex = dateString.indexOf(OPEN_BRACKET);
            int dashindex = dateString.indexOf(DASH);
            int cindex = dateString.indexOf(CLOSE_BRACKET);

            if ((dashindex - oindex) > 1) {
                // we have a start date
                long start = Long.parseLong(
                    dateString.substring(oindex+1, dashindex));
                setStartDate(new Date(start));
            }

            if ((cindex - dashindex) > 1) {
                // we have an exipriation date
                long end = Long.parseLong(
                    dateString.substring(dashindex+1, cindex));
                setExpirationDate(new Date(end));
            }
        } else {
            // bad format
            throw new BrokerException(br.getString(br.E_BAD_LICENSE_DATA));
        }
    }

    /**
     * Throws exception if the license has expired or not valid yet.
     */
    private void checkValidity() throws BrokerException {
        if (! willExpire())
            return;

        if (getExpirationDate() != null) {
            Calendar cal = Calendar.getInstance();
            cal.setTime(getExpirationDate());
            cal.set(cal.HOUR_OF_DAY, 0);
            cal.set(cal.MINUTE, 0);
            cal.set(cal.SECOND, 0);
            cal.set(cal.MILLISECOND, 0);
            if (cal.getTime().getTime() <= (new Date()).getTime()) {
                throw new BrokerException(
                    br.getString(br.E_LICENSE_EXPIRED, cal.getTime()));
            }
        } else if (getStartDate() != null) {
            Calendar cal = Calendar.getInstance();
            cal.setTime(getStartDate());

            cal.set(cal.HOUR_OF_DAY, 0);
            cal.set(cal.MINUTE, 0);
            cal.set(cal.SECOND, 0);
            cal.set(cal.MILLISECOND, 0);

            if ((new Date()).getTime() < cal.getTime().getTime()) {
                // now is before the valid start date
                throw new BrokerException(
                    br.getString(br.E_LICENSE_NOT_VALID_YET,
                        getStartDate()));
            }
        }
    }
}

/*
 * EOF
 */
