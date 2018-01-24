/*
 * Copyright (c) 2015, 2017 Oracle and/or its affiliates. All rights reserved.
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

package com.sun.messaging.jmq.util.io;

import java.util.HashSet;
import java.util.StringTokenizer;

/**
 * a Class filter utility which is utilized as a mechanism
 * which hooks into resolveClass, to prevent any black listed classes or packages from loading
 * By Default the following packages are black listed:
 *
 *  @code org.apache.commons.collections.functors
 *  @code com.sun.org.apache.xalan.internal.xsltc.trax
 *  @code javassist
 *
 *  The black list mechanism may be globally disabled by setting @code com.sun.messaging.io.disableblacklist to true
 *  The above defaults may be disabled by setting @code com.sun.messaging.io.disabledefaultblacklist to true
 *
 *  Alternatively, the blacklist mechanism maybe modified by setting the black list property as follows :
 *
 *  To add elements to the list :
 *
 *  com.sun.messaging.io.blacklist=+org.apache.commons.collections.functors,+com.sun.org.apache.xalan.internal.xsltc.trax,+javassist
 *
 *  To remove elements from the blacklist :
 *
 *  com.sun.messaging.io.blacklist=-org.apache.commons.collections.functors,-javassist
 *
 *  It's important to note that blacklisting a package overrides any class exclusion.
 *
 *
 */
abstract public class ClassFilter {
  static final String BLACK_LIST_PROPERTY = "com.sun.messaging.io.blacklist";
  static final String DISABLE_DEFAULT_BLACKLIST_PROPERTY = "com.sun.messaging.io.disabledefaultblacklist";
  static final String DISABLE_BLACK_LIST_PROPERTY = "com.sun.messaging.io.disableblacklist";

  private static final String DEFAULT_BLACK_LIST = "+org.apache.commons.collections.functors," +
    "+com.sun.org.apache.xalan.internal.xsltc.trax," +
    "+javassist," +
    "+org.codehaus.groovy.runtime.ConvertedClosure," +
    "+org.codehaus.groovy.runtime.ConversionHandler," +
    "+org.codehaus.groovy.runtime.MethodClosure";

  private static final HashSet<String> BLACK_LIST = new HashSet<>(32);


  static {
    if (!isBlackListDisabled()) {
      if (!isDefaultBlacklistEntriesDisabled()) updateBlackList(DEFAULT_BLACK_LIST);
      updateBlackList(System.getProperty(BLACK_LIST_PROPERTY, null));
    }
  }

  private static boolean isBlackListDisabled() {
    return Boolean.getBoolean(DISABLE_BLACK_LIST_PROPERTY);
  }

  private static boolean isDefaultBlacklistEntriesDisabled() {
    return Boolean.getBoolean(DISABLE_DEFAULT_BLACKLIST_PROPERTY);
  }

  private static void updateBlackList(String blackList) {
    if (blackList != null) {
      StringTokenizer st = new StringTokenizer(blackList, ",");
      while (st.hasMoreTokens()) {
        String token = st.nextToken();
        processToken(token);
      }
    }
  }

  private static void processToken(String token) {
    if (token.startsWith("+"))
      BLACK_LIST.add(token.substring(1));
    else if (token.startsWith("-"))
      BLACK_LIST.remove(token.substring(1));
    else // no operand specified: first character must be part of class name
      BLACK_LIST.add(token);
  }

  /**
   * Returns true if the named class, or its package is black listed
   * @param className the class name to check
   * @return true if black listed
   */
  public static boolean isBlackListed(String className) {
    if (!className.isEmpty() && BLACK_LIST.contains(className)) {
      return true;
    }
    String pkgName;
    try {
      pkgName = className.substring(0, className.lastIndexOf('.'));
    } catch (Exception ignored) {
      return false;
    }
    return !pkgName.isEmpty() && BLACK_LIST.contains(pkgName);
  }

}
