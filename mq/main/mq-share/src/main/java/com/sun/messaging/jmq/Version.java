/*
 * Copyright (c) 2000, 2018 Oracle and/or its affiliates. All rights reserved.
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
 * @(#)Version.java	1.18 09/07/07
 */ 

package com.sun.messaging.jmq;

import java.util.*;
import java.io.*;
import com.sun.messaging.jmq.resources.SharedResources;

/**
 * This class provides the Version information about the product.
 * The information provided is only returning the version inofrmation for the client.
 * It is quite possible that the broker may be running at a different version level.
 * The version information such as Major release version , Minor release version 
 * service pack ,Product build date , JMS API version , Product name , Protocol version etc
 * can be obtained through the various helper methods exposed by this class.
 */

public class Version {

    /**
     * List of terms used to indicate a patch level. For example
     * 3.6sp2, 1.4.2_01, 5.0u1, 8.1ur1. Always list them in upper
     * case.
     */
    private static String  patchTermsList[] = {
        "SP", "UR", "_", "U", "PATCH", "UPDATE", 
    };

    /* A HashSet with the patch terms to allow fast lookup */
    private static HashSet patchTerms = new HashSet();

    static {
        /* Initialize HashSet with patch terms */
        for (int i = 0; i < patchTermsList.length; i++) {
            try {
                patchTerms.add(patchTermsList[i]);
            } catch (Exception e) {
                /* Should neverhappen */
                System.out.println("Version: Could not add patch term " + 
                    patchTermsList[i] + ": " + e);
            }
        }
    }

    /**
     *Private string representing the name and the path of the of the file which stores the version information 
     */
    private String propname = "/com/sun/messaging/jmq/version.properties";
    private String comm_propname = "/com/sun/messaging/jmq/brand_version.properties";

    /**
     *String representing the name of the property which is used for getting the home folder of the MQ product
     */
    private static String imqhome_propname = "imq.home";
    
    /**
     *String representing the variable name of the home folder of the MQ product
     */
    private static final String IMQ_HOME = System.getProperty(imqhome_propname,".");
    
    /**
     *String representing the location of the file that holds the pacth version of the product
     */
    private static final String PATCHIDFILE = IMQ_HOME + File.separator + 
					   "patches" + File.separator + "VERSION";
    /**
     * A private instance of the Properties object used to hold the properties read from the Version file.
     */
    private Properties props = null;

    /**
     *String representing the value of the mini copyright  of the product
     */
    private String miniCopyright =
        "Copyright (c) 2013, 2018 Oracle and/or its affiliates.  All rights reserved.";

    /**
     * String representing the shortCopyright of the product
     */
    private String shortCopyright =
        "Copyright (c) 2013, 2018 Oracle and/or its affiliates.  All rights reserved.";

    /**
     *String representing the long copyright value of the product
     */
    private String longCopyright =
        "Copyright (c) 2013, 2018 Oracle and/or its affiliates.  All rights reserved.";

    /**
     * Represents the mini copyright value for the product
     */
    public final static int MINI_COPYRIGHT	= 0;
    
    /**
     *Represents the short copyright value for the product
     */
    public final static int SHORT_COPYRIGHT	= 1;
    
    /**
     *Represents the long copyright value for the product
     */
    public final static int LONG_COPYRIGHT	= 2;

    /**
     *String representing the pacakgae value of the Version class
     */
    private static String thisPackage="com.sun.messaging";

    /**
     *Reference of the SharedResource object instance
     */
    private static SharedResources rb = SharedResources.getResources();

    private boolean isJar = false;

    /**
     *Constructor for this class  
     */
    public Version() {
        this(true);
    }

    public Version(boolean isJar) {
      this.isJar = isJar;
      try {
	InputStream is = getClass().getResourceAsStream(propname);
	if (is == null) {
	    System.err.println(rb.getString(rb.E_VERSION_PROPS));
	}
	props = new Properties();
	props.load(is);

	/*
	 * Load commercial version of property file if it exists.
	 * XXX might want to add version checks here prior to loading it.
	 */
	is = getClass().getResourceAsStream(comm_propname);
	if (is != null) {
	    props.load(is);
	}

      } catch (Exception ex) {
	    System.err.println(rb.getString(rb.E_VERSION_LOAD));
	    ex.printStackTrace();
      }

    }

    /**
     *Returns the properties object that holds the various version properties and their values.
     *This is a private method , not for general use.This method may be removed in the future release without
     *further warning
     *@return Properties object holding the name value pair of the version property name and it's 
     *corresponding values
     */
    public Properties getProps() {
	return props;
    }

    /**
     *Returns the product version as a string 
     *The returned string has the information about the major release, minor release and service packs if any example 3.6 SP1
     *This is a private method , not for general use.This method may be removed in the future release without further warning
     *@return String representing the product version 
     */
    public String getProductVersion() {
        return props.getProperty("imq.product.version");
    }

    /**
     *Returns the Major release of the Product version for example if the 
     *release value is 3.6.1 then the major version value will be 3.
     *@return an integer representing the major release version value 
     *        if there is an exception returns -1
     */
    public int getMajorVersion() {
      try {
	return Integer.parseInt(props.getProperty("imq.product.major"));
      } catch (Exception ex) {
        return -1;
      }

    }

    /**
     *Returns the Minor release of the Product version for example if 
     *the release value is 3.6.1 then the returned value of minor version will be 6 
     *@return an integer representing the minor release version value
     *        if there is an exception returns -1
     */
    public int getMinorVersion() {
       try {
	return Integer.parseInt(props.getProperty("imq.product.minor"));
      } catch (Exception ex) {
        return -1;
      }
   }

    /**
     *Returns the value of the particular version property
     *for example if the requested property is 'imq.product.version' the returned value will be 3.6
     *This is a private method , not for general use.This method may be removed in the future release without
     *further warning
     *@param name String representing the name of the property whose value is desired
     *@return String representing the value of the desired property  
     */
    public String getVersionProperty(String name) {
	return props.getProperty(name);
    }

    /**
     *Returns the Build Mile Stone value of the product being used for example 'Beta'
     *This is a private method , not for general use.This method may be removed in the future release without
     *further warning
     *@return String representing the Milestone Build value of the product 
     */
    public String getBuildMilestone() {
	String milestone = props.getProperty("imq.build.milestone");
        if ("FCS".equals(milestone)) {
            return ""; 
        } else {
            return milestone;
        }
    }

   /**
     *Returns the Build date value of the product in the following format 
     *'Day Month Date Time Year' example Mon June  1 09:03:29 IST 2007
     *This is a private method , not for general use.This method may be removed in the future release without
     *further warning
     *@return String representing the date of the build of the product being used
     */ 
    public String getBuildDate() {
	return props.getProperty("imq.build.date");
    }

    /**
     *Returns the Build version value of the product
     *The returned string is the concatnated value of the product version,build number and promoted build.   
     *This is a private method , not for general use.This method may be removed in the future release without
     *further warning
     *@return String representing the Build Version of the product being used
     */
    public String getBuildVersion() {
	return (props.getProperty("imq.product.version") + " " +
                getBuildMilestone() + " (Build " + props.getProperty("imq.build.number") +
                "-" + props.getProperty("imq.build.letter") + ")" );
    }

    /**
     *Returns the product name example Oracle GlassFish(tm) Server Message Queue
     *@return String representing the name of the product
     */
    public String getProductName() {
        if (isJar)
            return props.getProperty("imq.product.jarname");
	return props.getProperty("imq.product.name");
    }
    /**
     *Returns the product release qtr ID example 2004Q4
     *@return String representing the release quarter ID
     */
    public String getReleaseQID() {
	return props.getProperty("imq.product.releaseqid");
    }


   /**
    *Returns the abbreviated name of the product, for example "MQ".
    *This is a private method , not for general use.This method may be removed in the future release without
    *further warning
    *@return String representing the abbreviated name of the product
    */
    public String getAbbreviatedProductName() {
	return props.getProperty("imq.product.name.abbrev");
    }

   /**
    *Returns the lower case of the abbreviated name of the product,
    *for example "mq".
    *This is a private method , not for general use.This method may be removed in the future release without
    *further warning
    *@return String representing the abbreviated name of the product
    */
    public String getLowerCaseAbbreviatedProductName() {
	return props.getProperty("imq.product.name.abbrev.lowercase");
    }

    /**
    *Returns the short name of the product, for example "Message Queue".
    *This is a private method , not for general use.This method may be removed in the future release without
    *further warning
    *@return String representing the short name of the product
    */
    public String getShortProductName() {
	return props.getProperty("imq.product.name.short");
    }

    /**
     *Returns the copyright date of the product example copyright 2007
     *This is a private method , not for general use.This method may be removed in the future release without
     *further warning
     *@return String representing the copyright date of the product
     */
    public String getProductCopyrightDate() {
	return props.getProperty("imq.product.copyright.date");
    }

    /**
     *Returns the product company's name example Sun Microsystems Inc
     *This is a private method , not for general use.This method may be removed in the future release without
     *further warning
     *@return String representing the Product Company's name 
     */
    public String getProductCompanyName() {
	return  props.getProperty("imq.product.companyname");
    }

    /**
     *Returns the implementation package name of the product example (com.sun.messaging)
     *This is a private method , not for general use.This method may be removed in the future release without
     *further warning
     *@return String representing the pacakge name
    */
    public String getVersionPackageName() {
	return props.getProperty("imq.version.package");
    }

    /**
     *Returns the implementation version of the product example 3.6
     *@return String representing the implementation version of the product
    */
    public String getImplementationVersion() {
	return props.getProperty("imq.api.version");
    }

    /**
     * Returns the protocol version of the product. There is a properitory
     * protocol used to communicate with the broker and amongst the brokers.This value 
     * will tell which version is currently being used. If there are no change's then the previous value
     * will be carried forward and retuned. example 3.6
     *This is a private method , not for general use.This method may be removed in the future release without
     *further warning
     *@return String representing the protocol version of the product
    */
    public String getProtocolVersion() {
	return props.getProperty("imq.protocol.version");
    }

    /**
     *Returns the JMS API version the product implements example 1.1
     *@return String representing the JMS API version which the product is compliant to
    */
    public String getTargetJMSVersion() {
	return props.getProperty("imq.jms.api.version");
    }

    /**
     * Returns whether this is the commercial version of the product
     * @return boolean True if this is the commercial product (ie SJSMQ), false if this is not the 
     * commercial version (ie OpenMQ).
    */
    public boolean isCommercialProduct() {
	boolean ret = false;

	String val = props.getProperty("imq.product.brand");

	return (Boolean.parseBoolean(val));
    }

    /**
     * checks if the commercial product version matches the other jars
     *
     */
     public boolean isProductValid() {
         if (!isCommercialProduct()) return true;
         // ok, retrieve the major,micro,minor versions
         int major = getMajorVersion();
         int minor = getMinorVersion();

         // now get the product version in comm
         int major_comm = -1;
         int minor_comm = -1;
         try {
	        major_comm = Integer.parseInt(props.getProperty("imq.product.brand.major"));
	        minor_comm = Integer.parseInt(props.getProperty("imq.product.brand.minor"));
         } catch (Exception ex) { // bad value, oh well
         }
         return (major_comm == major) && (minor_comm == minor);

     }

    /**
     * Returns the User Agent value of the product. This is the concatnated string having 
     * values of Productversion, Operating System on which the product runs,
     * the version of the operating system and the operating system architecture
     * example : SJSMQ/3.6 (JMS; SunOS 5.9 sun4u)
     *This is a private method , not for general use.This method may be removed in the future release without
     *further warning
     * @return the String representing the value of the UserAgent 
     * 
     */
    public String getUserAgent() {
        return getShortProductName() + "/" + getProductVersion() + " " +
	"(JMS; " + System.getProperty("os.name") + " " +
	 System.getProperty("os.version") + " " +
	 System.getProperty("os.arch") + ")";
    }

    /**
     *Returns the banner for the product. The banner comprises of the Product name,Company name
     *Product version, Build value, Build Date, Copyright value in a formatted manner   
     *This is a private method , not for general use.This method may be removed in the future release without
     *further warning
     *@return String representing the banner 
     */
    public String toString() {
	return getBanner(false);
    }

/* banner:

==================================================================
Oracle GlassFish(tm) Server Message Queue
Sun Microsystems, Inc.
Version:  3.6 [Alpha] (Build 143-a)
Compile:  Thu Feb 27 11:48:41 PST 2007

<Short copyright notic>
==================================================================

*/
    /**
     *Returns the header value for the banner being used for the product
     *This header value will be for the SHORT_COPYRIGHT value by default.   
     *This is a private method , not for general use.This method may be removed in the future release without
     *further warning
     *@return a string reperesenting the header details 
     */
    public String getHeader() {
        return (getHeader(LONG_COPYRIGHT));
    }

    /**
     *Returns the header for the given copyright
     *This is a private method , not for general use.This method may be removed in the future release without
     *further warning
     *@param copyrightType an integer value of the valid copyright type MINI_COPYRIGHT, SHORT_COPYRIGHT, LONG_COPYRIGHT
     *@return String value of the header
     */
    public String getHeader(int copyrightType) {
	return 
	    rb.getString(rb.I_BANNER_LINE)
	    + getProductName() + " " + getReleaseQID() + rb.NL 

            + getProductCompanyName() + rb.NL

            + rb.getString(rb.I_VERSION) + getBuildVersion() + rb.NL

            + rb.getString(rb.I_COMPILE) + getBuildDate()  + rb.NL + rb.NL

	    + getCopyright(copyrightType) + rb.NL 
	    + rb.getString(rb.I_BANNER_LINE);
    }
    
    /**
     * Return a version string suitable for use by the JMSRA resource adapter
     * This is something like 
     * "GlassFish MQ JMS Resource Adapter: Version:  4.5  (Build 23-k)Compile:  15/12/2012"
     * @return
     */
    public String getRAVersion(){
    	return rb.getString(rb.I_VERSION) + getBuildVersion()+ " "+ rb.getString(rb.I_COMPILE) + getBuildDate();
    }

    
    /**
     *Returns the copyright value for the desired copyright type passed as an argument 
     *This is a private method , not for general use.This method may be removed in the future release without
     *further warning
     *@param copyrightType an integer value of the copyright type valid values are MINI_COPYRIGHT, SHORT_COPYRIGHT, LONG_COPYRIGHT
     *@return String value of the copyright
     */
    public String getCopyright(int copyrightType) {
	switch (copyrightType)  {
	case MINI_COPYRIGHT:
	    return (miniCopyright);
	case SHORT_COPYRIGHT:
	    return (shortCopyright);
	case LONG_COPYRIGHT:
	    return (longCopyright);
	default:
	    return (shortCopyright);
	}
    }

    /**
     * Returns patch ID's for the product which will be an array of String comprising of all the patches for this product.
     * Each entry will be in the following format 'Major version.Minor Version_patchid number'
     * Can be used to format the patchids in your own app.
     *This is a private method , not for general use.This method may be removed in the future release without
     *further warning
     *@return array of String comprising the patchid's
     */
    public String[] getPatchIds() {
        File patchFile = new File(PATCHIDFILE);

	// Check if patch version file exists.
	if (!patchFile.exists()) {
	    return null;
        }

	// Check if patch version file can be read.
	if (!patchFile.canRead()) {
	    return null;
	}

	// Read the patch version file.
 	FileInputStream fis;
        Properties patchProps = new Properties();

        try {
	   fis = new FileInputStream(PATCHIDFILE);
	   patchProps.load(fis);
        } catch (Exception e) {
	   return null;	
        }

	// Parse any lines that start with "2.0".
	String propName = getMajorVersion() + "." + getMinorVersion();
/*
	String propValue = patchProps.getProperty(propName);

	if (propValue == null) {
	    return null;
	}

	// Parse out the patch ids and stuff then into array.
        // They can be in the format NNNNNN:DATE. 
	StringTokenizer toks = new StringTokenizer(propValue, ",");
	String[] patchids = new String[toks.countTokens()];
	int i = 0;

	while (toks.hasMoreTokens()) {
	    String fullString = toks.nextToken().trim();
	    int endIndex = fullString.indexOf(":");
	    if (endIndex == -1 && !fullString.equals(""))  // no :date 111111-01
	        patchids[i++] = fullString;
	    else if (endIndex == 0) {    // Nothing in front of :date
	   	continue; 
	    } else if (endIndex != -1) {
	        patchids[i++] = fullString.substring(0, endIndex);
	    }
	}
*/
	/* Find properties in the format:
	 * 		   2.0_1=111111-08
	 *                 2.0_2=222222-01
         */
	String[] patchids = new String[1000];

	int num = 0;
	for (int i = 1; i < patchids.length; i++) {
	    String propValue = patchProps.getProperty(propName + "_" + i);
	    if (propValue == null)
	        break;
	    patchids[num++] = propValue;
	}

	/*
	 * Return the actual length of the array.
	 */
	String[] retids = new String[num];
	for (int i = 0; i < num; i++) {
	    retids[i] = patchids[i];
	}

	return retids;
    }

    /**
     * Returns the patch string in the format:
     *    Installed Patch ID(s):           111111-01  
     * 					   222222-01   
     *                                     NNNNNN-NN
     *This is a private method , not for general use.This method may be removed in the future release without
     *further warning
     *@return a string representing all the patches for the product.
     */
    public String getPatchString() {

	String[] patchids = getPatchIds();
	StringBuffer ret = new StringBuffer();

	if (patchids != null && patchids.length >= 1) {
	    for (int j = 0; j < patchids.length; j++) {
		if (patchids[j] == null)
		    break;
		else if (ret.toString().equals(""))  // first line, so prepend description.
	    	    ret.append(rb.getString(rb.I_PATCHES) + patchids[j] + rb.NL);
		else if (patchids[j] != null) {    // prepend indent 
		    ret.append(rb.getString(rb.I_PATCH_INDENT) + patchids[j] + rb.NL);
		}
	    }
	}

	return ret.toString();
    }

    /**
     *Returns the JMSAdmin SPI version value of the product. This is the Admin SPI implementation value.
     *The value is not the same as the Product API implementation or the JMS API version. example 2.0
     *This is a private method , not for general use.This method may be removed in the future release without
     *further warning
     *@return String value of the JMSAdmin SPI version  
     */
    public String getJMSAdminSpiVersion() {
	return  props.getProperty("imq.jmsadmin.spi.version");
    }


    /**
     *Returns the Version info of the product, this string is the concatanated value
     *of pacakage name, API version, Protocol version, JMS API version, and the patch information.
     *@return the String value of the product version info
     */
    public String getVersion() {
	return getVersionPackageName() + rb.getString(rb.I_VERSION_INFO) +
	          rb.getString(rb.I_IMPLEMENTATION) + getImplementationVersion() + rb.NL +
	          rb.getString(rb.I_PROTOCOL_VERSION) + getProtocolVersion() + rb.NL +
	          rb.getString(rb.I_TARGET_JMS_VERSION) + getTargetJMSVersion() + rb.NL +
		  getPatchString();
   }

    /**
     *Returns the banner details for the default copyright type: SHORT_COPYRIGHT
     *This is a private method , not for general use.This method may be removed in the future release without
     *further warning
     *@param alldata a boolean value indicating that is it desired to get a detailed info
     *@return a String representing the banner 
     */
    public String getBanner(boolean alldata) {
	return (getBanner(alldata, SHORT_COPYRIGHT));
    }

    /**Returns the banner details for a given copyright type.
     *This is a private method , not for general use.This method may be removed in the future release without
     *further warning
     *@param alldata  a boolean value indicating whether you want a detailed info(true) or not(false) 
     *@param copyrightType an integer representing the copyright for which the banner detail is required
     *valid values are SHORT_COPYRIGHT, MINI_COPYRIGHT, LONG_COPYRIGHT
     *@return the string representing the banner 
     */
    public String getBanner(boolean alldata, int copyrightType) {
 	if (props == null) {
		return rb.getString(rb.E_VERSION_INFO) + thisPackage;
	}
	if (alldata) return getHeader(copyrightType) + getVersion();
	return getHeader(copyrightType);
			
   }


    /**
     * Returns the Version info in the form of an array of integer. 
     * Parse a version string into four integers. The four integers
     * represent the major product version, minor product version,
     * micro product version and patch level.
     *
     * The parser supports a variey of patch level syntax convetions,
     * for example "sp2", "_01", "ur4", etc. These are listed in the
     * patchTerms() hashTable.
     *
     * Anything at the end of the version string that does not match
     * a patch syntax is ignored, so this handles the JDK convetion
     * of using a '-' to delimit a  milestone string. 
     * Examples:
     *
     * 3.0.1sp2     = 3 0 1 2 
     * 3.0.1 sp 2   = 3 0 1 2
     * 3.5 sp 2     = 3 5 0 2
     * 3.5.0.2      = 3 5 0 2
     * 1.4.1_02     = 1 4 1 2
     * 1.4_02       = 1.4 0 2
     * 2            = 2 0 0 0
     * 1.4.1-beta2  = 1 4 1 0
     * 8.1ur1       = 8 1 0 1
     * 5.0u1        = 5 0 0 1
     *
     * Note that this implementation has not been optimized for speed!
     *
     * @param str String representing the product version
     * 
     * @return array of integers where
     *    int[0] = major Version
     *    int[1] = minor version
     *    int[3] = micro version
     *    int[4] = patch level
     *
     */
    public static int[] getIntVersion(String str) throws NumberFormatException
    {
        int[] returnver = new int[4]; // major, minor, micro, patch

        // Remove any non-digits from the end of the string.
        String newstr = stripTrailingLetters(str.toUpperCase().trim());
        String spstr = null;


        StringTokenizer tkn = new StringTokenizer(newstr, ".");
        String token = "";

        int i = 0;
        while (tkn.hasMoreTokens() && i < 4) {
            token = tkn.nextToken();
            //System.out.println("token=" + token);
            try {
                // Normal '.' seperated version number
                returnver[i] = Integer.parseInt(token);
                i++;
            } catch (NumberFormatException e) {
                int pos = 0;

                // In the case of 3.6sp2 we are now at
                // "6sp2". We need to get the "6"
                if (Character.isDigit(token.charAt(pos))) {
                    String value = getNumber(token);
                    try {
                        returnver[i] = Integer.parseInt(value);
                        i++;
                    } catch (NumberFormatException ex) {
                        // Should never occur
                        System.out.println("Can't parse " + token + ": " + e);
                    }

                    pos += value.length();

                    token = token.substring(pos);
                }

                // Now at patch identifier (i.e. "sp2")
                // Scan until we hit a number
                for (pos = 0; pos < token.length(); pos++) {
                    if (Character.isDigit(token.charAt(pos))) {
                        break;
                    }
                }

                // Make sure patch string is something we recognize
                String patchString = token.substring(0, pos).trim();
                if (patchTerms.contains(patchString)) {
                    // Number follows patchstring.
                    String patchNumber = token.substring(pos);
                    patchNumber = getNumber(patchNumber);
                    try {
                        returnver[3] = Integer.parseInt(patchNumber);
                    } catch (NumberFormatException e2) {
                        // patch number is invalid
                    }
                } 

                break;
            }
        }
        return returnver;
    }

    /**
     * Get the number that starts a string
     */
    static String getNumber(String s) {

        int j;
        for (j = 0; j < s.length(); j++) {
            // Scan until we hit a non-digit
            if (! Character.isDigit(s.charAt(j))) {
                break;
            }
        }
        return s.substring(0, j);
    }

    /**
     * Strip any trailing non-digit characters from a string.
     * So
     *   1.4.2_02a becomes 1.4.2_02
     *   3.5 beta  becomes 3.5
     *   01b       becomes 01
     *   3.5 beta1 stays   3.5 beta1
     * 
     */
    private static String stripTrailingLetters(String s) {

        boolean stripping = false;

        // Scan from end of string
        int n = s.length() - 1;
        while (!Character.isDigit(s.charAt(n))) {
            // Skip over letters
            n--;
        }

        if (n == s.length() - 1) {
            // No letters
            return s;
        } else {
            return s.substring(0, n+1);
        }
    }

    /**
     *Returns the comparison result of the two versions of the product passed as argument
     *assuming service packs have no incompatibilities
     *This is a private method , not for general use.This method may be removed in the future release without
     *further warning
     *@param stra representing the version which need to be compared
     *@param strb representing the second version string that needs to be compared.
     *@return   -1 If stra is less than strB
     *           0 If stra is equal to strb
     *           1 If stra is greater than strbB
    */
    public static int compareVersions(String stra,String strb)
    {
        return compareVersions(stra, strb, true);

    }

    /**
     * Returns the Comparison results of the two product versions.
     * This is a private method , not for general use.This method may be removed in the future release without
     * further warning
     * @param   verA    First version string
     * @param   verB    Second version string
     * @param   ignoreServicePack   true to ignore the service pack or patch
     *                              level
     * @return  -1 If verA is less than verB
     *           0 If verA is equal to verB
     *           1 If verA is greater than verB
     */
    public static int compareVersions(String verA, String verB, boolean ignoreServicePack) throws NumberFormatException
    {
        int[] aver = getIntVersion(verA);
        int[] bver = getIntVersion(verB);
        if (ignoreServicePack) {
            aver[3] = 0;
            bver[3] = 0;
        }
        return compareVersions(aver, bver);

    }

    /**
     *Compares the two versions which are in the form of array,s. The array encapsulate's
     *the major,minor,micro and service pack details of each version string.
     *This is a private method , not for general use.This method may be removed in the future release without
     *further warning
     *@param s1 the first array of integer representing the versin info
     *@param s2 the second array of interger representing the version info that has to be compared
     *@return    -1 If s1 is less than s2
     *           0 If s1 is equal to s2
     *           1 If s1 is greater than s2
     */
    public static int compareVersions(int[] s1, int[] s2) {

        //OK compare the longest of the lengths
        // assume any missing length is 0
        int comparelen = (s1.length > s2.length ? s1.length : s2.length);

        int indx = 0;

        while (indx < comparelen) {
            int val1 = (s1.length >indx ? s1[indx] : 0);
            int val2 = (s2.length >indx ? s2[indx] : 0);
            if (val1 > val2)
                return 1;
            if (val1 < val2)
                return -1;
            indx ++;
        }
        
        return 0;
    }
    
    /**
     *Returns the Version String value of the version info passed as an array of integer
     *This is a private method , not for general use.This method may be removed in the future release without
     *further warning
     *@param ver an array of integer reperesenting the version info int[0]=major int[1]=minor int[2]=micro int[3]=service pack 
     *@return a String value of the data passed in the integer array
     *
    */
    public static String toVersionString(int[] ver) {
        StringBuffer s = new StringBuffer();
        for (int i =0; i < ver.length; i ++) {
            s.append(ver[i]);
            if (i+1 < ver.length)
               s.append(".");
        }
        return s.toString();
    }
   
    /**
     *This method makes the class executable. When the class is executed  the product banner
     *will be printed providing information comprising of product version , product name , organization name
     *build details and the short copyright info.
     *This is a private method , not for general use.This method may be removed in the future release without
     *further warning
     */
    public static void main(String[] args)
    {
      Version v = new Version();
      System.out.println(v);
    }
    
}

