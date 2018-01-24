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
 * ConnectionFactory} resource that it requires in its operational 
 * environment. This provides information that can be used at the 
 * application's deployment to provision the required resource
 * and allows an application to be deployed into a Java EE environment 
 * with more minimal administrative configuration.
 * <p>
 * The {@code ConnectionFactory} resource may be configured by 
 * setting the annotation elements for commonly used properties. 
 * Additional properties may be specified using the {@code properties}
 * element. Once defined, a {@code ConnectionFactory} resource may be referenced by a
 * component in the same way as any other {@code ConnectionFactory} resource,
 * for example by using the {@code lookup} element of the {@code Resource}
 * annotation.
 * 
 * @version JMS 2.0
 * @since JMS 2.0
 * 
 * @see javax.annotation.Resource
 */
@Target({ ElementType.TYPE })
@Retention(RetentionPolicy.RUNTIME)
public @interface JMSConnectionFactoryDefinition {

	/**
	 * Description of this JMS connection factory.
	 */
	String description() default "";

	/**
	 * JNDI name of the JMS connection factory being defined.
	 */
	String name();

	/**
	 * Fully qualified name of the JMS connection factory interface.
	 * Permitted values are
	 * {@code javax.jms.ConnectionFactory} or
	 * {@code javax.jms.QueueConnectionFactory} or
	 * {@code javax.jms.TopicConnectionFactory}.
	 * If not specified then {@code javax.jms.ConnectionFactory} will be used. 
	 */
	String interfaceName() default "javax.jms.ConnectionFactory";
	
	/**
	 * Fully-qualified name of the JMS connection factory implementation class.
	 * Ignored if a resource adapter is used.
	 */
	String className() default "";

	/**
	 * Resource adapter name.
	 * If not specified then the application server will define the default behaviour,
	 * which may or may not involve the use of a resource adapter.
	 */
	String resourceAdapter() default "";

	/**
	 * User name to use for connection authentication.
	 */
	String user() default "";

	/**
	 * Password to use for connection authentication.
	 */
	String password() default "";

	/**
	 * Client id to use for connection.
	 */
	String clientId() default "";

	/**
	 * JMS connection factory property. This may be a vendor-specific property
	 * or a less commonly used {@code ConnectionFactory} property.
	 * <p>
	 * Properties are specified using the format:
	 * <i>propertyName=propertyValue</i> with one property per array element.
	 */
	String[] properties() default {};

	/**
	 * Set to {@code false} if connections should not participate in
	 * transactions.
	 * <p>
	 * Default is to enlist in a transaction when one is active or becomes
	 * active.
	 */
	boolean transactional() default true;

	/**
	 * Maximum number of connections that should be concurrently allocated for a
	 * connection pool.
	 * <p>
	 * Default is vendor-specific.
	 */
	int maxPoolSize() default -1;

	/**
	 * Minimum number of connections that should be concurrently allocated for a
	 * connection pool.
	 * <p>
	 * Default is vendor-specific.
	 */ 
	int minPoolSize() default -1;

}
