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
 * @(#)Version.java	1.46 06/28/07
 */ 

package com.sun.messaging;

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

public final class Version {

    private com.sun.messaging.jmq.Version version = null;

    /**
     * Represents the mini copyright value for the product
     *@deprecated 
     */
    public final static int MINI_COPYRIGHT	= 
                com.sun.messaging.jmq.Version.MINI_COPYRIGHT;
    
    /**
     *Represents the short copyright value for the product
     *@deprecated 
     */
    public final static int SHORT_COPYRIGHT	= 
                com.sun.messaging.jmq.Version.SHORT_COPYRIGHT;
    
    /**
     *Represents the long copyright value for the product
     *@deprecated 
     */
    public final static int LONG_COPYRIGHT	= 
                com.sun.messaging.jmq.Version.LONG_COPYRIGHT;

    /**
     *Constructor for this class  
     */
    public Version() {
        version = new com.sun.messaging.jmq.Version(false /* not jar */);
    }

    /**
     *Returns the properties object that holds the various version properties and their values.
     *This is a private method , not for general use.This method may be removed in the future release without
     *further warning
     *@return Properties object holding the name value pair of the version property name and it's 
     *corresponding values
     *@deprecated 
     */
    public Properties getProps() {
	return version.getProps();
    }

    /**
     *Returns the product version as a string 
     *The returned string has the information about the major release, minor release and service packs if any example 3.6 SP1
     *This is a private method , not for general use.This method may be removed in the future release without further warning
     *@return String representing the product version 
     *@deprecated 
     */
    public String getProductVersion() {
        return version.getProductVersion();
    }

    /**
     *Returns the Major release of the Product version for example if the 
     *release value is 3.6.1 then the major version value will be 3.
     *@return an integer representing the major release version value 
     *        if there is an exception returns -1
     */
    public int getMajorVersion() {
        return version.getMajorVersion();
    }

    /**
     *Returns the Minor release of the Product version for example if 
     *the release value is 3.6.1 then the returned value of minor version will be 6 
     *@return an integer representing the minor release version value
     *        if there is an exception returns -1
     */
    public int getMinorVersion() {
        return version.getMinorVersion();
   }

    /**
     *Returns the value of the particular version property
     *for example if the requested property is 'imq.product.version' the returned value will be 3.6
     *This is a private method , not for general use.This method may be removed in the future release without
     *further warning
     *@param name String representing the name of the property whose value is desired
     *@return String representing the value of the desired property  
     *@deprecated
     */
    public String getVersionProperty(String name) {
        return version.getVersionProperty(name);
    }

    /**
     *Returns the Build Mile Stone value of the product being used for example 'Beta'
     *This is a private method , not for general use.This method may be removed in the future release without
     *further warning
     *@return String representing the Milestone Build value of the product 
     *@deprecated 
     */
    public String getBuildMilestone() {
        return version.getBuildMilestone();
    }

   /**
     *Returns the Build date value of the product in the following format 
     *'Day Month Date Time Year' example Mon June  1 09:03:29 IST 2004
     *This is a private method , not for general use.This method may be removed in the future release without
     *further warning
     *@return String representing the date of the build of the product being used
     *@deprecated 
     */ 
    public String getBuildDate() {
        return version.getBuildDate();
    }

    /**
     *Returns the Build version value of the product
     *The returned string is the concatnated value of the product version,build number and promoted build.   
     *This is a private method , not for general use.This method may be removed in the future release without
     *further warning
     *@return String representing the Build Version of the product being used
     *@deprecated 
     */
    public String getBuildVersion() {
        return version.getBuildVersion();
    }

    /**
     *Returns the product name example Oracle GlassFish(tm) Server Message Queue
     *@return String representing the name of the product
     */
    public String getProductName() {
        return version.getProductName();
    }
    /**
     *Returns the product release
     *@return String representing version (no longer returns release quarter)
     *@deprecated 
     */
    public String getReleaseQID() {
        return version.getReleaseQID();
    }


   /**
    *Returns the abbreviated name of the product, for example "MQ".
    *This is a private method , not for general use.This method may be removed in the future release without
    *further warning
    *@return String representing the abbreviated name of the product
    *@deprecated 
    */
    public String getAbbreviatedProductName() {
        return version.getAbbreviatedProductName();
    }

   /**
    *Returns the lower case of the abbreviated name of the product,
    *for example "mq".
    *This is a private method , not for general use.This method may be removed in the future release without
    *further warning
    *@return String representing the abbreviated name of the product
    *@deprecated 
    */
    public String getLowerCaseAbbreviatedProductName() {
        return version.getLowerCaseAbbreviatedProductName();
    }

    /**
    *Returns the short name of the product, for example "Message Queue".
    *This is a private method , not for general use.This method may be removed in the future release without
    *further warning
    *@return String representing the short name of the product
    *@deprecated 
    */
    public String getShortProductName() {
        return version.getShortProductName();
    }

    /**
     *Returns the copyright date of the product example copyright 2004
     *This is a private method , not for general use.This method may be removed in the future release without
     *further warning
     *@return String representing the copyright date of the product
     *@deprecated 
     */
    public String getProductCopyrightDate() {
        return version.getProductCopyrightDate();
    }

    /**
     *Returns the product company's name example Sun Microsystems Inc
     *This is a private method , not for general use.This method may be removed in the future release without
     *further warning
     *@return String representing the Product Company's name 
     *@deprecated 
     */
    public String getProductCompanyName() {
        return version.getProductCompanyName();
    }

    /**
     *Returns the implementation package name of the product example (com.sun.messaging)
     *This is a private method , not for general use.This method may be removed in the future release without
     *further warning
     *@return String representing the pacakge name
     *@deprecated 
    */
    public String getVersionPackageName() {
        return version.getVersionPackageName();
    }

    /**
     *Returns the implementation version of the product example 3.6
     *@return String representing the implementation version of the product
    */
    public String getImplementationVersion() {
        return version.getImplementationVersion();
    }

    /**
     * Returns the protocol version of the product. There is a properitory
     * protocol used to communicate with the broker and amongst the brokers.This value 
     * will tell which version is currently being used. If there are no change's then the previous value
     * will be carried forward and retuned. example 3.6
     *This is a private method , not for general use.This method may be removed in the future release without
     *further warning
     *@return String representing the protocol version of the product
     *@deprecated 
    */
    public String getProtocolVersion() {
        return version.getProtocolVersion();
    }

    /**
     *Returns the JMS API version the product implements example 1.1
     *@return String representing the JMS API version which the product is compliant to
    */
    public String getTargetJMSVersion() {
        return version.getTargetJMSVersion();
    }

    /**
     * Returns the User Agent value of the product. This is the concatnated string having 
     * values of Productversion, Operating System on which the product runs,
     * the version of the operating system and the operating system architecture
     * example : SJSMQ/3.6 (JMS; SunOS 5.9 sun4u)
     *This is a private method , not for general use.This method may be removed in the future release without
     *further warning
     * @return the String representing the value of the UserAgent 
     *@deprecated 
     * 
     */
    public String getUserAgent() {
        return version.getUserAgent();
    }

    /**
     *Returns the banner for the product. The banner comprises of the Product name,Company name
     *Product version, Build value, Build Date, Copyright value in a formatted manner   
     *This is a private method , not for general use.This method may be removed in the future release without
     *further warning
     *@return String representing the banner 
     *@deprecated 
     */
    public String toString() {
        return version.toString();
    }

/* banner:

==================================================================
Oracle GlassFish(tm) Server Message Queue
Sun Microsystems, Inc.
Version:  3.6 [Alpha] (Build 143-a)
Compile:  Thu Feb 27 11:48:41 PST 2003

<Short copyright notic>
<RSA license credit>
==================================================================

*/
    /**
     *Returns the header value for the banner being used for the product
     *This header value will be for the SHORT_COPYRIGHT value by default.   
     *This is a private method , not for general use.This method may be removed in the future release without
     *further warning
     *@return a string reperesenting the header details 
     *@deprecated 
     */
    public String getHeader() {
        return version.getHeader();
    }

    /**
     *Returns the header for the given copyright
     *This is a private method , not for general use.This method may be removed in the future release without
     *further warning
     *@param copyrightType an integer value of the valid copyright type MINI_COPYRIGHT, SHORT_COPYRIGHT, LONG_COPYRIGHT
     *@return String value of the header
     *@deprecated 
     */
    public String getHeader(int copyrightType) {
        return version.getHeader(copyrightType);
    }

    
    /**
     *Returns the copyright value for the desired copyright type passed as an argument 
     *This is a private method , not for general use.This method may be removed in the future release without
     *further warning
     *@param copyrightType an integer value of the copyright type valid values are MINI_COPYRIGHT, SHORT_COPYRIGHT, LONG_COPYRIGHT
     *@return String value of the copyright
     *@deprecated 
     */
    public String getCopyright(int copyrightType) {
        return version.getCopyright(copyrightType);
    }

    /**
     * Returns patch ID's for the product which will be an array of String comprising of all the patches for this product.
     * Each entry will be in the following format 'Major version.Minor Version_patchid number'
     * Can be used to format the patchids in your own app.
     *This is a private method , not for general use.This method may be removed in the future release without
     *further warning
     *@return array of String comprising the patchid's
     *@deprecated 
     */
    public String[] getPatchIds() {
        return version.getPatchIds();
    }

    /**
     * Returns the patch string in the format:
     *    Installed Patch ID(s):           111111-01  
     * 					   222222-01   
     *                                     NNNNNN-NN
     *This is a private method , not for general use.This method may be removed in the future release without
     *further warning
     *@return a string representing all the patches for the product.
     *@deprecated 
     */
    public String getPatchString() {
        return version.getPatchString();
    }

    /**
     *Returns the JMSAdmin SPI version value of the product. This is the Admin SPI implementation value.
     *The value is not the same as the Product API implementation or the JMS API version. example 2.0
     *This is a private method , not for general use.This method may be removed in the future release without
     *further warning
     *@return String value of the JMSAdmin SPI version  
     *@deprecated 
     */
    public String getJMSAdminSpiVersion() {
        return version.getJMSAdminSpiVersion();
    }


    /**
     *Returns the Version info of the product, this string is the concatanated value
     *of pacakage name, API version, Protocol version, JMS API version, and the patch information.
     *@return the String value of the product version info
     */
    public String getVersion() {
        return version.getVersion();
   }

    /**
     *Returns the banner details for the default copyright type: SHORT_COPYRIGHT
     *This is a private method , not for general use.This method may be removed in the future release without
     *further warning
     *@param alldata a boolean value indicating that is it desired to get a detailed info
     *@return a String representing the banner 
     *@deprecated 
     */
    public String getBanner(boolean alldata) {
        return version.getBanner(alldata);
    }

    /**Returns the banner details for a given copyright type.
     *This is a private method , not for general use.This method may be removed in the future release without
     *further warning
     *@param alldata  a boolean value indicating whether you want a detailed info(true) or not(false) 
     *@param copyrightType an integer representing the copyright for which the banner detail is required
     *valid values are SHORT_COPYRIGHT, MINI_COPYRIGHT, LONG_COPYRIGHT
     *@return the string representing the banner 
     *@deprecated 
     */
    public String getBanner(boolean alldata, int copyrightType) {
        return version.getBanner(alldata, copyrightType);
			
   }


    /**
     * Returns the Version info in the form of an array of integer. 
     * Parse a version string into four integers. The four integers represent the 
     * major product version , minor product version , micro product version and the service pack.
     * This method handles both MQ service pack convetions (sp1) and JDK patch convention (_01).
     * It also handles the JDK convetion of using a '-' to delimit a
     * milestone string. In this case everything after the - is ignored.
     * Examples:
     *
     * 3.0.1sp2     = 3 0 1 2 ,
     * 3.0.1 sp 2   = 3 0 1 2 ,
     * 3.5 sp 2     = 3 5 0 2 ,
     * 3.5.0.2      = 3 5 0 2 ,
     * 1.4.1_02     = 1 4 1 2 ,
     * 1.4_02       = 1.4 0 2 ,
     * 2            = 2 0 0 0 ,
     * 1.4.1-beta2  = 1 4 1 0
     *@param str String representing the product version
     *@return array of integers where int[0] = major Version int[1]=minor version int[3]=micro version int[4]= service pack
     *
     */
    public static int[] getIntVersion(String str) throws NumberFormatException
    {
        return com.sun.messaging.jmq.Version.getIntVersion(str);
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
     *@deprecated 
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
     *@deprecated 
     */
    public static int compareVersions(String verA, String verB, boolean ignoreServicePack) throws NumberFormatException
    {
        return com.sun.messaging.jmq.Version.compareVersions(verA,
                     verB, ignoreServicePack);

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
     *@deprecated 
     */
    public static int compareVersions(int[] s1, int[] s2) {

        return com.sun.messaging.jmq.Version.compareVersions(s1,
                     s2);
    }
    
    /**
     *Returns the Version String value of the version info passed as an array of integer
     *This is a private method , not for general use.This method may be removed in the future release without
     *further warning
     *@param ver an array of integer reperesenting the version info int[0]=major int[1]=minor int[2]=micro int[3]=service pack 
     *@return a String value of the data passed in the integer array
     *@deprecated 
     *
    */
    public static String toVersionString(int[] ver) {
        return com.sun.messaging.jmq.Version.toVersionString(ver);
    }
   
    /**
     *This method makes the class executable. When the class is executed  the product banner
     *will be printed providing information comprising of product version , product name , organization name
     *build details and the short copyright info.
     *This is a private method , not for general use.This method may be removed in the future release without
     *further warning
     *@deprecated 
     */
    public static void main(String[] args)
    {
        com.sun.messaging.jmq.Version.main(args);
    }
    
}

