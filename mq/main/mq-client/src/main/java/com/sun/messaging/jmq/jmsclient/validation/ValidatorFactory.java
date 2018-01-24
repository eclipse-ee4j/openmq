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

package com.sun.messaging.jmq.jmsclient.validation;

import java.net.URI;
import java.util.Enumeration;
import java.util.Hashtable;
import java.util.Properties;
import javax.jms.JMSException;

public class ValidatorFactory {
    
    static final String TOPIC_PROP_NAME_PREFIX = "imq.xml.validate.topic";
    static final String QUEUE_PROP__NAME_PREFIX = "imq.xml.validate.queue";
   
    /**
     * Default constructor is protected on purpose.
     */
    protected ValidatorFactory() {
        
    }
    
    /**
     * new instance of this class.
     * @return a new instance of the factory.
     */
    public static ValidatorFactory newInstance() {
        return new ValidatorFactory();
    }
    
    /**
     * Construct a new instance of the XMLValidator.
     * This is used to validate against DTD defined in the
     * XML document.
     * 
     * @return a new instance of XMLValidator.
     * 
     * @throws javax.jms.JMSException
     */
    public static XMLValidator newValidator() throws JMSException {
        return new XMLValidator();
    }
    
     /**
     * Construct a new instance of validator with the 
     * specified schema language and xsd URI list.
     * 
     * BY default, the xml schema language is used: 
     * "http://www.w3.org/2001/XMLSchema"
     * 
     * @param xsdURIList the xsd used by this validator to
     * validate XML document.
     * 
     * @return a new instance of the xml validator.
     * 
     * @throws javax.jms.JMSException
     */
    public static XMLValidator 
        newValidator(String xsdURIList) throws JMSException {
        
        return new XMLValidator(xsdURIList);
    }
    
    /**
     * Construct a new instance of validator with the 
     * specified schema language and xsd URI list.
     * 
     * @param schemaLang the schema language for this
     * validator.
     * 
     * @param xsdURIList the xsd used by this validator to
     * validate XML document.
     * 
     * @return a new instance of the xml validator.
     * 
     * @throws javax.jms.JMSException
     */
    public static XMLValidator 
        newValidator(String schemaLang, String xsdURIList) throws JMSException {
        
        return new XMLValidator(schemaLang, xsdURIList);
    }
    
    public static Hashtable getTopicValidateTable() {
        return getValidateTable (TOPIC_PROP_NAME_PREFIX);
    }
    
    public static Hashtable getQueueValidateTable() {
        return getValidateTable (QUEUE_PROP__NAME_PREFIX);
    }
    
    /**
     * return topic validation table defined with System properties for
     * the client runtime JVM.
     * 
     * 
     * @return Hashtable -- key=topic name, value=XMLValodator instance
     * for the topic.
     * 
     */
    private static Hashtable getValidateTable(String prefix) {

        Hashtable table = new Hashtable();

        try {

            Properties props = System.getProperties();

            Enumeration enum2 = props.keys();

            while (enum2.hasMoreElements()) {

                String name = (String) enum2.nextElement();
                if (name.startsWith(prefix)) {

                    int fromIndex = prefix.length()+1;
                    int endIndex = name.indexOf('.', fromIndex);

                    String topicName = name.substring(fromIndex, endIndex);
                    String uri = System.getProperty(name);

                    //System.out.println ("destName=" + topicName + ", fromIndex=" + fromIndex + ", endIndex="+endIndex);
                    //System.out.println ("uri=" + uri);
                        
                    XMLValidator validator = null;
                    //http: or file:
                    if (uri.length() > 4) {
                        validator = newValidator(uri);    
                    } else {
                        validator = newValidator();
                    }

                    //add to table
                    table.put(topicName, validator);

                }

            }

        } catch (Exception e) {
            e.printStackTrace();
        }

        return table;

    }
}
 
