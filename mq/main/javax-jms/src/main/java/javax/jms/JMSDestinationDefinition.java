/*
 * Copyright (c) 2012, 2017 Oracle and/or its affiliates. All rights reserved.
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

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * An application may use this annotation to specify a JMS {@code 
 * Destination} resource that it requires in its operational 
 * environment. This provides information that can be used at the 
 * application's deployment to provision the required resource
 * and allows an application to be deployed into a Java EE environment 
 * with more minimal administrative configuration.
 * <p>
 * The {@code Destination} resource may be configured by 
 * setting the annotation elements for commonly used properties. 
 * Additional properties may be specified using the {@code properties}
 * element. Once defined, a {@code Destination} resource may be referenced by a
 * component in the same way as any other {@code Destination} resource,
 * for example by using the {@code lookup} element of the {@code Resource}
 * annotation.
 * 
 * @see javax.annotation.Resource
 * 
 * @version JMS 2.0
 * @since JMS 2.0
 * 
 */
@Target({ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
public @interface JMSDestinationDefinition {

    /**
     *  Description of this JMS destination.
     */
    String description() default "";

    /**
     *  JNDI name of the destination resource being defined.
     */
    String name();

	/**
	 * Fully qualified name of the JMS destination interface.
	 * Permitted values are
	 * {@code javax.jms.Queue} or
	 * {@code javax.jms.Topic}.
	 */
	String interfaceName();
	
	/**
	 * Fully-qualified name of the JMS destination implementation class.
	 * Ignored if a resource adapter is used unless the resource adapter 
	 * defines more than one JMS destination implementation class for the specified interface
	 */
	String className() default "";

	/**
	 * Resource adapter name.
	 * If not specified then the application server will define the default behaviour,
	 * which may or may not involve the use of a resource adapter.
	 */
	String resourceAdapter() default "";

    /**
     *  Name of the queue or topic.
     */
    String destinationName() default "";

    /**
     *  JMS destination property.  This may be a vendor-specific property
     *  or a less commonly used {@code ConnectionFactory} property.
     *  <p>
     *  Properties are specified using the format:
     *  <i>propertyName=propertyValue</i> with one property per array element.
     */
    String[] properties() default {};
}


