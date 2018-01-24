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
 * @(#)AdministeredObject.java	1.49 06/28/07
 */ 

package com.sun.messaging;

import java.util.Collections;
import java.util.HashMap;
import java.util.Hashtable;
import java.util.Map;
import java.util.Properties;
import java.util.Enumeration;
import java.io.InputStream;
import java.lang.reflect.Method;
import java.lang.reflect.InvocationTargetException;
import java.util.MissingResourceException;
import javax.jms.*;

import com.sun.messaging.jmq.jmsclient.resources.ClientResources;

/**
 * An <code>AdministeredObject</code> encapsulates behavior common
 * to all Sun MQ Administered Objects and MQ Administered Object
 * classes extend this class.
 * <p>
 * All MQ Administered Objects contain a configuration and
 * maintain a state flag indicating whether they are modifiable
 * or read only. They are also versioned to support migration of
 * previous formats of MQ Administered Objects to the current
 * format.
 * <p>
 * When a MQ Administered Object is first created it contains the
 * default configuration and the configuration is modifiable.
 * After modification (either programmatically, or by the administrator
 * using the MQ Administration utilities), an <code>AdministeredObject</code>
 * can be set to read only to prevent further modification.
 * <p>
 * Typically, the Sun MQ Administration utilities would be used to create
 * MQ Administered Objects, optionally set their state to read only,
 * and store them using the Java Naming and Directory Service (JNDI).
 * <p>
 * When Sun MQ applications use JNDI and <code>lookup()</code> MQ Administered
 * Objects that have had their state set to read only, the applications
 * will not be able to modify the administrator stored configurations.
 * <p>
 * Sun MQ automatically converts JMQ, iMQ and Sun ONE MQ Administered Objects from the
 * following previous versions of JMQ, iMQ and Sun ONE MQ to the current version
 * of Oracle GlassFish(tm) Server Message Queue Administered Objects.
 * <p><dl><dt>
 * <dd>JMQ  1.1 Beta</dd>
 * <dd>JMQ  1.1 FCS</dd>
 * <dd>JMQ  2.0 FCS</dd>
 * <dd>JMQ  2.1 SP1</dd>
 * <dd>iMQ  3.0 FCS</dd>
 * <dd>S1MQ 3.5 FCS</dd>
 * </dt>
 * </dl>
 * <p>
 * Conversion of JMQ Administered Objects created using JMQ1.1 EA and
 * versions of JMQ prior to JMQ1.1 EA is <b>not supported</b>.
 */

/*
 * IMPORTANT:
 *
 * This class holds the static version number of the
 * MQ Administered Objects. This number needs to be updated
 * whenever any MQ Administered Object changes its format
 * and the corresponding com.sun.naming.*ObjectFactory
 * classes need to be updated to handle JNDI lookup of
 * the new as well as the old Administered Objects.
 *
 */

public abstract class AdministeredObject implements java.io.Serializable {

    /** The Version string of this <code>AdministeredObject</code> is <code>3.0</code> */
    public static final String VERSION = "3.0";

    /** AdministeredObject configuration property type designator for String */
    public static final String AO_PROPERTY_TYPE_STRING = "java.lang.String";

    /** AdministeredObject configuration property type designator for Integer */
    public static final String AO_PROPERTY_TYPE_INTEGER = "java.lang.Integer";

    /** AdministeredObject configuration property type designator for Long */
    public static final String AO_PROPERTY_TYPE_LONG = "java.lang.Long";

    /** AdministeredObject configuration property type designator for Boolean */
    public static final String AO_PROPERTY_TYPE_BOOLEAN = "java.lang.Boolean";

    /** AdministeredObject configuration property type designator for PropertyOwner */
    public static final String AO_PROPERTY_TYPE_PROPERTYOWNER = "com.sun.messaging.PropertyOwner";

    /** AdministeredObject configuration property type designator for List */
    public static final String AO_PROPERTY_TYPE_LIST = "List";

    /** The read only state */
    private boolean readOnly;

    /** The original version # of this object stored in JNDI */
    public String storedVersion;

    /** The configuration of this <code>AdministeredObject</code> */
    protected Properties configuration;
    protected Properties configurationTypes;
    protected Properties configurationLabels;

    /** The modifiers to the defaults basename for acquiring configuration values */
    private static final String AO_TYPES = "_types";
    private static final String AO_LABELS = "_labels";
    private static final String AO_DEFAULTS = "_defaults";
    private static final String AO_PROP_EXT = ".properties";

    /** Const strings for property types etc */
    private static final String AO_PROPERTY_LIST_PROPERTY = ".property";
    private static final String AO_PROPERTY_LIST_SEPARATOR = "|";
    private static final String AO_PROPERTY_LIST_VALUES = ".List";
    private static final String AO_PROPERTY_LIST_OTHER_NAME = ".....property";

    /** These constants are used for admin properties */
    private static final String AO_PROPERTY_ADMIN_GROUPLIST = "GroupList";
    private static final String AO_PROPERTY_ADMIN_GROUP = "Group.";
    private static final String AO_PROPERTY_ADMIN_GROUPLABEL = ".Label";

    /* Client JVM Resources */
    public static final transient ClientResources cr = ClientResources.getResources();

    /* Mapping for Deprecated Configuration System Property Names */
    private static transient Hashtable deprecatedSysProperties = new Hashtable(6);

    /* Mapping for Deprecated Configuration Property Names */
    private static transient Hashtable deprecatedProperties = new Hashtable(6);

    static {
        //Used to find System properties specified with the deprecated name
        deprecatedSysProperties.put("imqReconnectEnabled", "imqReconnect");
        deprecatedSysProperties.put("imqReconnectAttempts", "imqReconnectRetries");
        deprecatedSysProperties.put("imqReconnectInterval", "imqReconnectDelay");
        deprecatedSysProperties.put("imqConnectionFlowCount", "imqFlowControlCount");
        deprecatedSysProperties.put("imqConnectionFlowLimitEnabled", "imqFlowControlIsLimited");
        deprecatedSysProperties.put("imqConnectionFlowLimit", "imqFlowControlLimit");

        //Used to replace deprecated properties names specified in Java code
        //in the getProp..., setProp..., isProp... methods with their current name
        deprecatedProperties.put("imqReconnect", "imqReconnectEnabled");
        deprecatedProperties.put("imqReconnectRetries", "imqReconnectAttempts");
        deprecatedProperties.put("imqReconnectDelay", "imqReconnectInterval");
        deprecatedProperties.put("imqFlowControlCount", "imqConnectionFlowCount");
        deprecatedProperties.put("imqFlowControlIsLimited", "imqConnectionFlowLimitEnabled");
        deprecatedProperties.put("imqFlowControlLimit", "imqConnectionFlowLimit");
    }
    
    /**
     * cachedConfigurationMap
     * key = defaultsBase, value is a Properties[] 
     * where value[0] is the cached initial value of configuration
     * where value[1] is the cached initial value of configurationTypes
     * where value[2] is the cached initial value of configurationLabels
     */
    protected static final Map<String, Properties[]> cachedConfigurationMap = Collections.synchronizedMap(new HashMap());

    /* prevent null constructor instantiation */
    @SuppressWarnings("unused")
	private AdministeredObject() {}

    public AdministeredObject(String defaultsBase) {
    	
        initialiseConfiguration(defaultsBase);
        readOnly = false;
        storedVersion = VERSION;
        initOwnedProperties();
    }

	private void initialiseConfiguration(String defaultsBase) {
				
		// get initial configurations (an array containing three Properties objects: defaults, types, base) from cache
		Properties[] configurations = cachedConfigurationMap.get(defaultsBase);
		if (configurations==null){
			// not in cache
			try {
	            // Attempt to initialize the configuration from property files
	            InputStream is1 = getClass().getResourceAsStream(defaultsBase + AO_DEFAULTS + AO_PROP_EXT);
	            InputStream is2 = getClass().getResourceAsStream(defaultsBase + AO_TYPES + AO_PROP_EXT);
	            InputStream is3 = getClass().getResourceAsStream(defaultsBase + AO_LABELS + AO_PROP_EXT);
	            if (is1!=null && is2!=null && is3!=null){
		            configuration = new Properties();
		            configuration.load(is1);
		            is1.close();
		            
		            configurationTypes = new Properties();
		            configurationTypes.load(is2);
		            is2.close();
		            	
		            configurationLabels = new Properties();
		            configurationLabels.load(is3);
	            } else {
		            //Resort to default configuration that *must* be provided by sub-classes
		            setDefaultConfiguration();
	            }
	        } catch (Exception e) {
	            //Resort to default configuration that *must* be provided by sub-classes
	            setDefaultConfiguration();
	        }
	        // now save in cache to avoid the performance of repeating all that IO (CR
	        configurations = new Properties[3];
	        configurations[0]=(Properties) configuration.clone();
	        configurations[1]=(Properties) configurationTypes.clone();
	        configurations[2]=(Properties) configurationLabels.clone();
	        cachedConfigurationMap.put(defaultsBase, configurations);
	        
		} else {
			// found in cache
			configuration = (Properties) configurations[0].clone();
			configurationTypes = (Properties) configurations[1].clone();
			configurationLabels = (Properties) configurations[2].clone();
		}
		
	}

    /**
     * Subclasses must implement this to set their default configuration.
     */
    public abstract void setDefaultConfiguration();
 
    /**
     * Returns the readOnly state of this <code>AministeredObject</code>.
     *
     * @return the readOnly state of this <code>AministeredObject<code>
     */
    public final boolean isReadOnly() {
        return readOnly;
    }

    /**
     * Returns the storedVersion of this <code>AministeredObject</code>.
     *
     * @return the storedVersion of this <code>AministeredObject<code>
     */
    public final String getStoredVersion() {
        return storedVersion;
    }

    /**
     * Tests whether the storedVersion of this <code>AministeredObject</code>
     * is compatible with the current runtime VERSION
     * <p>
     * This methods flags mismatches between this <code>Administered Object</code>
     * and the version stored in JNDI using an earlier or different version of this class.
     * It is advisable to check before an earlier version of Administered Object
     * is being overwritten by a newer version, otherwise an earlier version
     * of the MQ Client will fail when attempting to use JNDI to <code>lookup()</code>
     * that earlier version of the Administered Object.
     *
     * @return the storedVersion of this <code>AministeredObject<code>
     */
    public final boolean isStoredVersionCompatible() {
        return VERSION.equals(storedVersion);
    }

    /**
     * Sets this <code>AdministeredObject</code> to be read only.
     *
     * Once this <code>AdministeredObject</code> has been set to
     * read only it cannot be reset back to read write.
     */
    public final void setReadOnly() {
        readOnly = true;
    }

    /**
     * Returns the configuration of this <code>AdministeredObject</code>.
     * <p>
     * The configuration returned is the one that has been administratively
     * or programmatically set in this <code>AdministeredObject</code>
     * This configuration is unaffected by runtime overrides using System properties.
     * <p>
     * Use <code>getCurrentConfiguration()</code> to get the current
     * configuration, which takes into account any overriding
     * System properties that have been set at runtime.
     *   
     * @return the configuration of this <code>AdministeredObject</code>.
     *
     * @see com.sun.messaging.AdministeredObject#getCurrentConfiguration()
     */  
    public Properties getConfiguration() {
        return configuration;
    }

    /**
     * Returns an ordered property groups list.
     * 
     * @return The String that represents an ordered property groups list.
     *         This list uses the ' <code><b>|</b></code> ' character as the separator.
     */
    public String getPropertyGroups() {
        return (String)configurationTypes.get(AO_PROPERTY_ADMIN_GROUPLIST);
    }

    /**
     * Returns an ordered property list for a given group.
     * 
     * @param group The group for which the property list is desired.
     *
     * @return The String that represents an ordered property list for a given group.
     *         This list uses the ' <code><b>|</b></code> ' character as the separator.
     */
    public String getPropertiesForGroup(String group) {
        return (String)configurationTypes.get(AO_PROPERTY_ADMIN_GROUP + group);
    }

    /**
     * Returns the label for a given group.
     * 
     * @param group The group for which the label is desired.
     *
     * @return The label the given group.
     */
    public String getLabelForGroup(String group) {
        String lbl = "";
        try {
            lbl = (String)configurationTypes.get(AO_PROPERTY_ADMIN_GROUP + group + AO_PROPERTY_ADMIN_GROUPLABEL);
            lbl = AdministeredObject.cr.getString(lbl);
        } catch (MissingResourceException mre) {
        }
        return lbl;
    }

    /**
     * Returns an ordered property list for Properties of Type List.
     * 
     * @param propname The name of the configuration property that is of type List.
     *
     * @return The String that represents an ordered property list.
     *         This list uses the ' <code><b>|</b></code> ' character as the separator.
     */
    public String getPropertyListValues(String propname) {
        return (String)configurationTypes.get(propname + AO_PROPERTY_LIST_VALUES);
    }

    /**
     * Returns the property name for any selection on Properties of Type List.
     * 
     * @param listpropname The name of the configuration property that is of type List.
     * @param listvalue The value of the configuration property that is of type List.
     *
     * @return The property that is pre set when a property of type List
     *         is set to the value <code>listvalue</code>.
     */
    public String getPropertyForListValue(String listpropname, String listvalue) {
        return (String)configurationTypes.get(listpropname + "." + listvalue + AO_PROPERTY_LIST_PROPERTY);
    }

    /**
     * Returns the property values set when a property of type List is set to a particular value.
     * 
     * @param listpropname The name of the configuration property that is of type List.
     * @param listvalue The value of the configuration property that is of type List.
     * @param propname The name of the property for which the value is being sought.
     *
     * @return The value that property <code>propname</code> is pre set to when the
     *         property <code>listpropname</code> of type list is set to the value
     *         <code>listvalue</code>.
     */
    public String getPropertyValueForListValue(String listpropname, String listvalue, String propname) {
        return (String)configurationTypes.get(listpropname + "." + listvalue + "." + propname);
    }

    /**
     * Returns the property name for the ... selection on Properties of Type List.
     * 
     * @return The property to be exposed for editing when the <b><code>...</code></b> selection on 
     *         Properties of Type List is selected.
     */
    public String getPropertyListOtherName(String propname) {
        return (String)configurationTypes.get(propname + AO_PROPERTY_LIST_OTHER_NAME);
    }

    /**
     * Returns an enumeration of the configuration property names of this <code>AdministeredObject</code>.
     *
     * @return The enumeration of configuration property names.
     */
    public Enumeration enumeratePropertyNames() {
        return configuration.keys();
    }

    /**
     * Sets a single configuration property in this <code>AdministeredObject</code>.
     *
     * @param propname The name of the configuration property to set.
     * @param propval The value of the configuration property to set.
     *
     * @return The previous value of the configuration property being set.
     *
     * @exception InvalidPropertyException If an invalid property name is being set.
     * The Exception string is the name of the invalid property.
     * @exception InvalidPropertyValueException If an invalid property value is being set.
     * The Exception string is the invalid value of the property.
     * @exception ReadOnlyPropertyException If an attempt is made to modify this
     * <code>AdministeredObject</code> when the readOnly flag has been set.
     */
    public String setProperty(String propname, String propval) throws JMSException {
        if (!readOnly) {
            if (propname == null) {
                throw new InvalidPropertyException(propname);
            }
            //Convert JMQ prefix if present to imq prefix
            if ((propname.length() > 3) && (propname.startsWith("JMQ"))) {
                propname = "imq" + propname.substring(3);
            }
            //Convert deprecated but supported properties to the correct current key
            if (deprecatedProperties.containsKey(propname)) {
                //System.out.print("setProp=Xforming propname:"+propname+"-to:");
                propname = (String)deprecatedProperties.get(propname);
                //System.out.println(propname);
            }

            if (!configuration.containsKey(propname)) {
                throw new InvalidPropertyException(propname);
            } else {
                //Migrate old propval to new propval
                if (ConnectionConfiguration.imqConnectionType.equals(propname) && "SSL".equals(propval)) {
                    propval = "TLS";
                }
                if (ConnectionConfiguration.imqJMSDeliveryMode.equals(propname)) {
                    if ("1".equals(propval)) {
                        propval = "NON_PERSISTENT";
                    }
                    if ("2".equals(propval)) {
                        propval = "PERSISTENT";
                    }
                }
                if (isPropertyValid(propname, propval, configurationTypes)) {
                    //System.out.println("AO:propname="+propname+":propval="+propval);
                    String oldprop = (String)(configuration.put(propname, propval));
                    String proptype = getPropertyType(propname);
                    //If it's a List type we have to update the dependant property
                    if (AO_PROPERTY_TYPE_LIST.equals(proptype)) {
                        String lpropname = getPropertyForListValue(propname, propval);
                        String lproptype = getPropertyType(lpropname);
                        String lpropvalue = getPropertyValueForListValue(propname, propval, lpropname);
                        configuration.put(lpropname, lpropvalue);
                        //then we need to check and add any owned properties
                        if (AO_PROPERTY_TYPE_PROPERTYOWNER.equals(lproptype)) {
                            addOwnedProperties(lpropvalue);
                        }
                    } else {
                        if (AO_PROPERTY_TYPE_PROPERTYOWNER.equals(proptype)) {
                            addOwnedProperties(propval);
                       }
                    }
                    return oldprop;
                } else {
                    throw new InvalidPropertyValueException(propname, propval);
                }
            }
        } else {
            throw new ReadOnlyPropertyException(propname);
        }
    }

    /**
     * Returns a single configuration property value given the property name.
     *
     * @param propname The name of the configuration property.
     *
     * @return The value of the configuration property <code>propname</code>.
     *
     * @exception InvalidPropertyException If an invalid property name is being requested.
     * The Exception string is the name of the invalid property.
     */
    public String getProperty(String propname) throws JMSException {
        if (propname == null) {
            throw new InvalidPropertyException(propname);
        }
        //Convert JMQ prefix if present to imq prefix
        if ((propname.length() > 3) && (propname.startsWith("JMQ"))) {
            propname = "imq" + propname.substring(3);
        }
        //Convert deprecated but supported properties to their correct current key
        if (deprecatedProperties.containsKey(propname)) {
            //System.out.print("getProp=Xforming propname:"+propname+"-to:");
            propname = (String)deprecatedProperties.get(propname);
            //System.out.println(propname);
        }

        String propval = (String)configuration.get(propname);
        if (propval == null) {
                throw new InvalidPropertyException(propname);
        }
        return propval;
    }

    /**
     * Returns the configuration type of a single configuration property name.
     *
     * @param propname The name of the configuration property.
     *
     * @return The type of the configuration property <code>propname</code>.
     *
     * @exception InvalidPropertyException If an invalid property name is being requested.
     * The Exception string is the name of the invalid property.
     */
    public String getPropertyType(String propname) throws JMSException {
        //Convert JMQ prefix if present to imq prefix
        if ((propname != null) && (propname.length() > 3) && (propname.startsWith("JMQ"))) {
            propname = "imq" + propname.substring(3);
        }
        if (!configuration.containsKey(propname)) {
                throw new InvalidPropertyException(propname);
        } else {
            return (String)configurationTypes.get(propname);
        }
    }

    /**
     * Returns the configuration label of a single configuration property name.
     *
     * @param propname The name of the configuration property.
     *
     * @return The label of the configuration property <code>propname</code>.
     *
     * @exception InvalidPropertyException If an invalid property name is being requested.
     * The Exception string is the name of the invalid property.
     */
    public String getPropertyLabel(String propname) throws JMSException {
        //Convert JMQ prefix if present to imq prefix
        if ((propname != null) && (propname.length() > 3) && (propname.startsWith("JMQ"))) {
            propname = "imq" + propname.substring(3);
        }
        if (!configuration.containsKey(propname)) {
                throw new InvalidPropertyException(propname);
        } else {
            String lbl = "";
            try {
                lbl = (String)configurationLabels.get(propname);
                lbl = AdministeredObject.cr.getString(lbl);
            } catch (MissingResourceException mre) {
            }
            return lbl;
        }
    }

    /**
     * Returns whether a single configuration property name should be hidden or not.
     *
     * @param propname The name of the configuration property.
     *
     * @return <code>true</code> If the configuration property <code>propname</code>
     *         should be hidden; <code>false</code> if it should not be hidden
     *         i.e. it is still supported in this version.
     *
     * @exception InvalidPropertyException If an invalid property name is being requested.
     * The Exception string is the name of the invalid property.
     */
    public boolean isPropertyHidden(String propname) throws JMSException {
        //Convert JMQ prefix if present to imq prefix
        if ((propname != null) && (propname.length() > 3) && (propname.startsWith("JMQ"))) {
            propname = "imq" + propname.substring(3);
        }
        if (!configuration.containsKey(propname)) {
                throw new InvalidPropertyException(propname);
        } else {
            if (configurationTypes.containsKey(propname+".hidden")) {
                return true;
            }
        }
        return false;
    }

    /**
     * Returns whether a single configuration property name is deprecated or not.
     *
     * @param propname The name of the configuration property.
     *
     * @return <code>true</code> If the configuration property <code>propname</code>
     *         has been deprecated; <code>false</code> if it has not been deprecated
     *         i.e. it is still supported in this version.
     *
     * @exception InvalidPropertyException If an invalid property name is being requested.
     * The Exception string is the name of the invalid property.
     */
    public boolean isPropertyDeprecated(String propname) throws JMSException {
        //Convert JMQ prefix if present to imq prefix
        if ((propname != null) && (propname.length() > 3) && (propname.startsWith("JMQ"))) {
            propname = "imq" + propname.substring(3);
        }
        if (!configuration.containsKey(propname)) {
                throw new InvalidPropertyException(propname);
        } else {
            if (configurationTypes.containsKey(propname+".deprecated")) {
                return true;
            }
        }
        return false;
    }

    /**
     * Returns the provider specific name for this <code>AdministeredObject</code> along with
     * a listing of its configuration.
     *   
     * @return A formatted String containing the provider specific name for this
     * <code>AdministeredObject</code> along with a listing of its configuration.
     *   
     */
    public String toString() {
        return "\nClass:\t\t\t" + getClass().getName() +
               "\ngetVERSION():\t\t" + VERSION +
               "\nisReadonly():\t\t" + readOnly +
               "\ngetProperties():\t" + configuration.toString() ;
    }

    /**
     * Returns the provider specific name for this <code>AdministeredObject</code> along with
     * a complete listing of its configuration, configuration attribute label keys and
     * configuration attribute types.
     *   
     * @return A formatted String containing the provider specific name for this
     * <code>AdministeredObject</code> along with a listing of its configuration.
     *   
     */
    public String dump() {
        return (this.toString() + 
                "\n\ngetLabels():\t" + configurationLabels.toString() +
                "\n\ngetTypes():\t" + configurationTypes.toString() );
    }
 
    /**
     * Returns the Version string used for this MQ <code>AdministeredObject</code>.
     *
     * @return the Version string
     */
    public static final String getVERSION() {
        return VERSION;
    }

    /**
     * Returns the current (runtime) configuration of this
     * <code>AdministeredObject</code> modified by any System properties.
     * <p>
     * If the readOnly flag of this <code>AdministeredObject</code> is set, then
     * this <code>AdministeredObject</code> is not affected by any System Properties
     * set at runtime.
     *
     * @return The current (runtime) configuration of this
     * <code>AdministeredObject</code> modified by any System properties.
     *
     * @see com.sun.messaging.AdministeredObject#getConfiguration()
     */
    public Properties getCurrentConfiguration() {
        Properties currentConfiguration = null;
        boolean cloned = false;

        //Need only return the baked in configuration if readOnly
        if (readOnly) {
            return configuration;
        } else {
            String propkey;
            String syspropval;
            for (Enumeration e = configuration.keys(); e.hasMoreElements() ; ) {
                propkey = (String)e.nextElement();
                //System.out.println("gCC:key="+propkey+"\n");
                //syspropval = System.getProperty(propkey);
                syspropval = _sysPropVal(propkey);
                //Override a configuration property iff its System Prop is non-null
                if (syspropval != null) {
                    //System.out.println("gCC:sysprop(key,val)="+propkey+"|"+syspropval);
                    //Use configuration as the defaults for java.util.Properties
                    //currentConfiguration = new Properties(configuration);
                    //System.out.println("gCC:creating prop clone...");
                    if (!cloned) {
                        try {
                            currentConfiguration = (Properties)configuration.clone();
                            cloned = true;
                        } catch (Exception ce) { 
                            //Failed to clone - return existing configuration
                            /*System.err.println("getCurrentConfiguration:CloneException:" +
                                                     getClass().getName() +
                                                     ":" +
                                                     ce.getMessage() +
                                                     ":returning unoverridden configuration");
                            */
                            return configuration;
                        }
                    }
                    //If List property - Substitute full prop val for List abbrev.
                    if (AO_PROPERTY_TYPE_LIST.equals((String)(configurationTypes.get(propkey)))) {
                        if (isListPropertyValid(propkey, syspropval, configurationTypes)) {
                            //System.out.println("gCC:validated; putting(key,val)="+propkey+"|"+syspropval);
                            currentConfiguration.put(propkey, syspropval);
                            //Now we have to update the dependant property based on the abbreviation
                            String depndntpropkey = (String)(configurationTypes.get(
                                        propkey + "." + syspropval + AO_PROPERTY_LIST_PROPERTY));
                            String depndntpropval = (String)(configurationTypes.get(
                                        propkey + "." + syspropval + "." + depndntpropkey));
                            String depndntsyspropval = System.getProperty(depndntpropkey);
                            //Confirm valid depndntsyspropval
                            //String depndntproptype = (String)(configurationTypes.get(depndntpropkey));
                            /*currentConfiguration.put(depndntpropkey,
                                                     (depndntsyspropval != null) ? depndntsyspropval
                                                                                 : dependntpropval);
                            */
                            //Switch propkey, syspropval to complete rest of the processing
                            propkey = depndntpropkey;
                            syspropval = (depndntsyspropval != null) ? depndntsyspropval : depndntpropval;
                        }
                        //Either syspropval contained a `|' or it wasn't in the list of valid values
                        //Ignore this case - the original/default value will be used
                    }
                    //Now process the non-list property, handling the configurable case as well.
                    //System.out.println("gCC:validating(key,val)="+propkey+"|"+syspropval);
                    if (isPropertyValid(propkey, syspropval, configurationTypes)) {
                        //System.out.println("gCC:validated; putting(key,val)="+propkey+"|"+syspropval);
                        currentConfiguration.put(propkey, syspropval);
                    
                    //Update properties if propval is PropertyOwner
                    if (AO_PROPERTY_TYPE_PROPERTYOWNER.equals((String)(configurationTypes.get(propkey)))) {
                        try {
                            PropertyOwner propowner =
                                (PropertyOwner)Class.forName(syspropval).newInstance();
                            String[] ownedprops = propowner.getPropertyNames();
                            String ownedpropkey, ownedpropval, ownedproptype, ownedproplabel, ownedsyspropval;
                            for (int i=0; i<ownedprops.length; i++) {
                                ownedpropkey = ownedprops[i];
                                ownedpropval = propowner.getPropertyDefault(ownedpropkey);
                                ownedproptype = propowner.getPropertyType(ownedpropkey);
                                ownedproplabel = propowner.getPropertyLabel(ownedpropkey);
                                if (ownedpropkey != null && ownedpropval != null &&
                                        ownedproptype != null && ownedproplabel != null) {
                                    if (!configurationTypes.containsKey(ownedpropkey)) {
                                        configurationTypes.put(ownedpropkey, ownedproptype);
                                    }
                                    if (!configurationLabels.containsKey(ownedpropkey)) {
                                        configurationLabels.put(ownedpropkey, ownedproplabel);
                                    }
                                    ownedsyspropval = System.getProperty(ownedpropkey);
                                    if (ownedsyspropval != null) {
                                        //sys property used only if it is valid
                                        if (isPropertyValid(ownedpropkey, ownedsyspropval, configurationTypes)) {
                                            currentConfiguration.put(ownedpropkey, ownedsyspropval);
                                        }
                                    }
                                    //If this key has not been set by an ownedsyspropval and it doesn't exist, initialize it.
                                    if (!configuration.containsKey(ownedpropkey)) {
                                        configuration.put(ownedpropkey, ownedpropval);
                                    }
                                    //currentConfiguration.put(ownedpropkey, (ownedsyspropval == null ? ownedpropval : ownedsyspropval));
                                }
                            }
                        } catch (Exception ex) {
                            /*System.err.println("getCurrentConfiguration:Exception:" +
                                                getClass().getName() +
                                                ":" +
                                                ex.getMessage());
                            */
                            //InvalidPropertyException
                            //IllegalAccessException
                            //InstantiationException
                            //Exception
                            //An exception is preventing this configuration from being consistent
                            //Set current configuration to null to resort to the default
                            currentConfiguration = null;
                        }
                    }
                    }
                }
            }
            //May not have been modified by System Props!
            return (currentConfiguration == null ? configuration : currentConfiguration);
        }
    }


    /**
    * Checks for System property values for deprecated keys
    */
    private String _sysPropVal(String propkey) {

        String sysval = null;
        String dsyskey = null;
        String tsysval = null;

        if (propkey != null) {
            dsyskey = (String)deprecatedSysProperties.get(propkey);
            if (dsyskey != null && !dsyskey.equals("") ) {
                sysval = System.getProperty(dsyskey);
            }
            tsysval = System.getProperty(propkey);
            if (tsysval != null && !tsysval.equals("") ) {
                sysval = tsysval;
            }
        }
        return sysval;
    }

    /**
     * Initialize properties derived from ones that are type PropertyOwner
     */
    private void initOwnedProperties() {
        String propkey;
        String proptype;
        for (Enumeration e = configuration.keys(); e.hasMoreElements() ; ) {
            propkey = (String)e.nextElement();
            try {
                proptype = getPropertyType(propkey);
                if (AO_PROPERTY_TYPE_PROPERTYOWNER.equals(proptype)) {
                    String[] ownedprops;
                    String ownedpropkey, ownedpropval, ownedproptype, ownedproplabel;
                    PropertyOwner propowner =
                        (PropertyOwner)Class.forName(getProperty(propkey)).newInstance();
                    ownedprops = propowner.getPropertyNames();
                    for (int i=0; i<ownedprops.length; i++) {
                        ownedpropkey = ownedprops[i];
                        ownedpropval = propowner.getPropertyDefault(ownedpropkey);
                        ownedproptype = propowner.getPropertyType(ownedpropkey);
                        ownedproplabel = propowner.getPropertyLabel(ownedpropkey);
                        if (ownedpropkey != null && ownedpropval != null &&
                                    ownedproptype != null && ownedproplabel != null) {
                            configuration.put(ownedpropkey, ownedpropval);
                            configurationTypes.put(ownedpropkey, ownedproptype);
                            configurationLabels.put(ownedpropkey, ownedproplabel);
                        }
                    }
                }
            } catch (Exception ex) {
                //Class name defined as property owner type did not instantiate - ignore owned properties
                /*System.err.println("initOwnedProperties:Exception:" +
                                    getClass().getName() +
                                    ":" +
                                    ex.getMessage());
                */
                //InvalidPropertyException
                //IllegalAccessException
                //InstantiationException
                //Exception
            }
        }
    }

    /**
     * Returns whether a property value is valid.
     */
    private boolean isPropertyValid(String propkey, String propval, Properties configurationTypes) {
        String proptype = (String)(configurationTypes.get(propkey));
        //System.out.println("iPV:key="+propkey+";val="+propval+"\n");
        //System.out.println("iPV:cT="+configurationTypes.toString()+"\n");
        if (AO_PROPERTY_TYPE_LIST.equals(proptype)) {
            //System.out.println("iPV:listproptype\n");
            return isListPropertyValid(propkey, propval, configurationTypes);
        }
        try {
            if (AO_PROPERTY_TYPE_PROPERTYOWNER.equals(proptype)) {
                //System.out.println("iPV:propertyownertype\n");
                Class cp = Class.forName(propval);
                Class[] ca = cp.getInterfaces();
                for (int i=0; i<ca.length; i++) {
                    if (ca[i] == com.sun.messaging.PropertyOwner.class) {
                        return true;
                    }
                }
            } else {
                if (AO_PROPERTY_TYPE_INTEGER.equals(proptype)) {
                    //System.out.println("iPV:integerproptype\n");
                    //Integer int_prop = new Integer(propval);
                    new Integer(propval);
                    return true;
                } else {
                    if (AO_PROPERTY_TYPE_LONG.equals(proptype)) {
                        //System.out.println("iPV:longproptype\n");
                        //Long long_prop = new Long(propval);
                        new Long(propval);
                        return true;
                    } else {
                        if (AO_PROPERTY_TYPE_BOOLEAN.equals(proptype)) {
                            //System.out.println("iPV:booleanproptype\n");
                            //Boolean boolean_prop = Boolean.valueOf(propval);
                            Boolean.valueOf(propval);
                            return true;
                        } else {
                            if (AO_PROPERTY_TYPE_STRING.equals(proptype)) {
                                //Any string is valid *except* if there is a "validate_<PropertyName>" method
                                //System.out.println("iPV:stringproptype\n");
                                try {
                                    Method validatePropertyMethod = getClass().getMethod("validate_"+propkey,
                                            new Class[] { Class.forName(proptype) } );
                                    if (((Boolean)(validatePropertyMethod.invoke(this, new Object[] { propval }))).booleanValue()) {
                                        //String validates OK
                                        //System.out.println("iPV:stringprop is valid\n");
                                        return true;
                                    } else {
                                        //String does NOT validate
                                        //System.out.println("iPV:stringprop is invalid\n");
                                        return false;
                                    }
                                } catch (NoSuchMethodException nsme) {
                                    //System.out.println("iPV:NoSuchMethodException" + nsme.getMessage());
                                    //No validation method - continue
                                } catch (IllegalArgumentException iae) {
                                    //System.out.println("iPV:IllegalArgumentException" + iae.getMessage());
                                    return false;
                                } catch (InvocationTargetException ite) {
                                    //System.out.println("iPV:InvocationTargetException" + ite.getMessage());
                                    return false;
                                } catch (NullPointerException npe) {
                                    //System.out.println("iPV:NullPointerException" + npe.getMessage());
                                    return false;
                                }
                                //Any string is a valid one
                                //System.out.println("iPV:Returning true");
                                return true;
                            }
                        }
                    }
                }
            }
        } catch (Exception ex) {
            //Any of the cases below should be treated as an invalid property value.
            //System.err.println("isPropertyValid:Exception:AdministeredObject:" + ex.getMessage());
            //NumberFormatException
            //ClassNotFoundException
            //IllegalAccessException
            //InstantiationException
            //Exception
        }
        return false;
    }

    /**
     * Returns whether the property value is valid for `List' properties.
     *
     */
    private boolean isListPropertyValid(String propkey, String propval, Properties configurationTypes) {
        //Check that the propval does not contain the separator character `|'
        if (propval.indexOf(AO_PROPERTY_LIST_SEPARATOR) != -1) {
            //Invalid - it contains a `|'
            return false;
        }
        String valid_values = (String)configurationTypes.get(propkey + AO_PROPERTY_LIST_VALUES);
        if (valid_values != null) {
            if (valid_values.indexOf(propval, 0) != -1) {
                //The list of valid values *must* contain propval for propval to be valid
                return true;
            }
        }
        //Invalid propval
        return false;
    }

    /**
     * Adds the owned properties of propval to this configuration object.
     *
     * @param propval the property value classname that will supply the owned properties.
     */
    private void addOwnedProperties(String propval) {
        try {
            PropertyOwner propowner = (PropertyOwner)Class.forName(propval).newInstance();
            String[] ownedprops = propowner.getPropertyNames();
            String ownedpropkey, ownedpropval, ownedproptype, ownedproplabel;
            for (int i=0; i<ownedprops.length; i++) {
                ownedpropkey = ownedprops[i];
                ownedpropval = propowner.getPropertyDefault(ownedpropkey);
                ownedproptype = propowner.getPropertyType(ownedpropkey);
                ownedproplabel = propowner.getPropertyLabel(ownedpropkey);
                if (ownedpropkey != null && ownedpropval != null &&
                        ownedproptype != null && ownedproplabel != null) {
                    configurationTypes.put(ownedpropkey, ownedproptype);
                    configurationLabels.put(ownedpropkey, ownedproplabel);
                    /*
                    if ((ownedsyspropval != null) &&
                            (!isPropertyValid(ownedpropkey, ownedsyspropval, configurationTypes))) {
                        //Null out if sys property used is invalid
                        ownedsyspropval = null;
                    }
                    //System.out.println("gCC:validated; putting(key,val,sysval)="+ownedpropkey+"|"+ownedpropval+"|"+ownedsyspropval);
                    currentConfiguration.put(ownedpropkey,
                                            (ownedsyspropval == null ? ownedpropval
                                                                     : ownedsyspropval));
                    */
                    //Update value iff it does not exist
                    if (!configuration.containsKey(ownedpropkey)) {
                        configuration.put(ownedpropkey, ownedpropval);
                    }
                }
            }
        } catch (Exception ex) {
            //Class name defined as property owner type did not instantiate - ignore owned properties
            ////System.err.println("addOwnedProperties:Exception:" + getClass().getName() + ":" + ex.getMessage());
            //IllegalAccessException
            //InstantiationException
            //Exception
        }

    }
}
