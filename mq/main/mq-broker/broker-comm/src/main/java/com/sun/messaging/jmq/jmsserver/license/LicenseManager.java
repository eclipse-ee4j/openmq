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
 * @(#)LicenseManager.java	1.22 06/28/07
 */ 

package com.sun.messaging.jmq.jmsserver.license;

import com.sun.messaging.jmq.jmsserver.util.BrokerException;
import com.sun.messaging.jmq.jmsserver.comm.CommGlobals;
import com.sun.messaging.jmq.jmsserver.resources.*;
import com.sun.messaging.jmq.util.log.Logger;

import java.io.*;
import java.util.*;
import java.security.*;
import java.lang.reflect.*;

/**
 * Represents a broker license.
 */
public class LicenseManager {
    private static BrokerResources br = CommGlobals.getBrokerResources();
    private static Logger logger = CommGlobals.getLogger();

    /** Cache the current license object */
    private LicenseBase currentLicense = null;
    private LicenseBase base = null;

    public LicenseManager() {}

    /**
     * This method loads the appropriate license.
     */
    public LicenseBase getLicense(String filestr) throws BrokerException {
        if (currentLicense != null)
            return currentLicense;
    // Step 1 : Choose the best LicenseBase implementation class.
    base = getLicenseBase();
    if (base == null) {
            // No license base class in the package!
            // This should never happen. No I18N necessary.
            throw new BrokerException(
                "Could not find license base class." +
                "This is a broker packaging error.");
        }

        // Step 2 : Load the specified license file. If no license
        // file is specified, load all license files and select the
        // one that is most generous.
        FileLicense fl = null;
        try {
            fl = loadLicenseFile(filestr);
       }
        catch (BrokerException e) {
           //BUG FIX 5025985
           throw e;
        }

        // Step 3 : If a license file was loaded from the disk
        // it must be re-written in order to handle the license
        // expiration correctly.
        if (fl != null) {
            try {
                fl.rewriteLicense();
            } catch (IOException e) {
                throw new BrokerException(
                    br.getString(br.E_BAD_LICENSE_DATA), e);
            }
        }

        // Step 4 : Superimpose the license file contents on the
        // base class chosen in step 1.
        if (fl != null) {
            if (filestr != null ||
                fl.getPrecedence() > base.getPrecedence()) {
                base.superimpose(fl.getProperties());

                // log where the license comes from
                logger.log(Logger.DEBUG, br.getString(br.I_LICENSE_FILE,
                        fl.getLicenseFile()));
            }
        }
        else if (base.isLicenseFileRequired()) {
            // If there is no license file, and the base class
            // requires a license file, throw an exception.
            throw new BrokerException(
                br.getString(br.E_NO_VALID_LICENSE));
        }

        currentLicense = base;
        return base;
    }

    //
    // Following constants are used by getLicenseBase() method.
    //
    private static final String LICENSE_BASE_PKG_PREFIX =
        "com.sun.messaging.jmq.jmsserver.license.";
    private static final String STANDALONE_LICENSE_BASE =
        "StandaloneLicense";
    private static final String LICENSE_BASE_CLASS_PREFIX = "L";
    private static final int MAX_LICENSE_BASE_CLASSES = 16;

    /**
     * Load the most generous license base class.
     */
    private LicenseBase getLicenseBase() throws BrokerException {
        Class cl = null;
        
        // First try to load the StandaloneLicense class.
        String cname = LICENSE_BASE_PKG_PREFIX + STANDALONE_LICENSE_BASE;
        try {
            cl = Class.forName(cname);
            return newInstance(cl);
        }
        catch (Exception e) { /* Ignore */ }

        // If StandaloneLicense is not found, search for other
        // license classes.
        ArrayList list = new ArrayList();
        for (int i = 0; i < MAX_LICENSE_BASE_CLASSES; i++) {
            cname = LICENSE_BASE_PKG_PREFIX +
                LICENSE_BASE_CLASS_PREFIX +
                i;

            try {
               cl = Class.forName(cname);
               list.add(newInstance(cl));
           }
           catch (Exception e) {
           /* Ignore */ }
        }

        LicenseBase[] licenses = (LicenseBase[])list.toArray(new LicenseBase[list.size()]);
        return selectBestLicense(licenses);
    }

    private LicenseBase newInstance(Class cl) throws Exception {
        Constructor co = cl.getConstructor(null);
        return (LicenseBase) co.newInstance(null);
    }

    /**
     * Select the most generous license.
     */
    private LicenseBase selectBestLicense(LicenseBase[] licenses)
        throws BrokerException {
        if (licenses == null || licenses.length == 0) {
            throw new BrokerException(
                br.getString(br.E_NO_VALID_LICENSE));
        }

        // pick a license to use:
        // - pick the one with the highest precedence value.
        // - a non-expiring license has higher priority, i.e., a
        //   license with no expiration date but lower precedence
        //   will be picked instead of a license with an
        //   expiration date but higher precedence.

        LicenseBase lb = licenses[0];
        
        for (int i = 1; i < licenses.length; i++) {
            boolean current = lb.willExpire();
            boolean next = licenses[i].willExpire();
            // If the current license expires, and the next one
            // does not, then use the next one.
            if (current && !next) {
                lb = licenses[i];
            }

            // If the current and next license have same
            // expiration condition, then use the one with higher
            // precedence.
            if (current == next &&
                lb.getPrecedence() <
                    licenses[i].getPrecedence()) {
                lb = licenses[i];
            }
        }

        return lb;
    }

    //
    // Following constants are used by the loadLicenseFile() method.
    //
    private static final String LICENSE_DIR =
            CommGlobals.getJMQ_ETC_HOME()
                + File.separator + "lic" + File.separator;
    private static final String TRIAL_LICENSE_DIR = CommGlobals.getJMQ_VAR_HOME() + File.separator + "lic" + File.separator; 

    private static final String LICENSE_FILE_PREFIX = "imqbroker";
    private static final String LICENSE_FILE_SUBFIX = ".lic";

    // file name filter for licence files (imqbroker*.lic)
    private static FilenameFilter licFilter = new FilenameFilter() {
        public boolean accept(File dir, String name) {
            return (name.startsWith(LICENSE_FILE_PREFIX)
                && name.endsWith(LICENSE_FILE_SUBFIX));
        }
    };

    private FileLicense loadLicenseFile(String filestr)
        throws BrokerException {
        
        File dir = new File(LICENSE_DIR);
        File trialdir = new File(TRIAL_LICENSE_DIR);
        FileLicense fl = null;
        LicenseBase lbase = null;
        if (filestr != null) {
            //Bug 6157397
            String strname = base.getProperty(LicenseBase.PROP_LICENSE_TYPE);
            if(filestr.equalsIgnoreCase(strname))
             return null;
                     
            // try to load the specified file
            String licenseFile = LICENSE_FILE_PREFIX + filestr + LICENSE_FILE_SUBFIX;
            File file = null;
            //For bug fix 4995767
            if(filestr.equalsIgnoreCase("try"))
               file = new File(trialdir, licenseFile);
            else 
               file = new File(dir,licenseFile); 
            
            //BUG FIX 5025985
            if(!file.exists())
              throw new BrokerException(br.getString(br.E_LOAD_LICENSE,filestr));  
            //BUG FIX 5057293
            else if(!file.canRead())
              throw new BrokerException(br.getString(br.E_LICENSE_FILE_NOT_READABLE,licenseFile));  
            
            fl = new FileLicense(file);
        }
        else {
            FileLicense[] licenses = loadFileLicenses();
            if(loadFileLicenses().length !=0)
            {    
              fl = (FileLicense) selectBestLicense(licenses);
              File f = fl.getLicenseFile();
              if(!f.canRead())
                throw new BrokerException(br.getString(br.E_LICENSE_FILE_NOT_READABLE,f.toString())); 
              
              //BUG FIX 4996564
              LicenseBase lb = (LicenseBase)fl;
              Properties prop = lb.props;
              String datestring = prop.getProperty(lb.PROP_DATE_STRING);
              if(datestring.startsWith(lb.TRY_STRING)){
               //BUG FIX 5054057
               return null;
              }
            }
        }

        return fl;
    }

    /**
     * Load all valid licenses from from the license directory.
     * @return an array of License objects
     */
    public static FileLicense[] loadFileLicenses() {
        File dir = new File(LICENSE_DIR);
        File trialdir = new File(TRIAL_LICENSE_DIR);
        String[] names = dir.list(licFilter);

        ArrayList list = new ArrayList();
        for (int i = 0; names != null && i < names.length; i++) {
            try {
                File file = new File(dir, names[i]);

                FileLicense lic = new FileLicense(file);
                list.add(lic);
            } catch (BrokerException e) {
                // ignore all license files with bad format or that
                // are expired log it and continue don't print out any
                // info in production code
                /*
                logger.log(logger.DEBUG, "loading " + names[i] + ", got: "
                    + e.toString());
                */
            }
        }
        /** For Bug Fix 4995767 
            The trial license is present in a new location now
            var_home/lic
        */
        String[] trialnames = trialdir.list(licFilter);
        for(int j=0;trialnames!=null && j < trialnames.length; j++)
        {
          try{
             File file = new File(trialdir, trialnames[j]);
             FileLicense lic = new FileLicense(file);
             list.add(lic);
             } catch(BrokerException e) {
             }
        } 

        return (FileLicense[])list.toArray(new FileLicense[list.size()]);
    }

    public static LicenseBase[] loadLicenses() {
        // return all licenses
        LicenseManager lm = new LicenseManager();
        LicenseBase lb = null;
        try {
            lb = lm.getLicenseBase();
        } catch (Exception ex) {
// log some error here
        }
        LicenseBase fl[] =  loadFileLicenses();

        //int length = fl.length + (lb == null? 0 : 1);
        LicenseBase rl[] = new LicenseBase[fl.length + 1];
        for (int i= 0; i < fl.length ; i ++)
           rl[i]=fl[i];
        if (lb != null)
           rl[fl.length] = lb;
        return rl;
    }
}

/*
 * EOF
 */
