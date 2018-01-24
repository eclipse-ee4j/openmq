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
 * @(#)MD5.java	1.8 06/29/07
 */ 

package com.sun.messaging.jmq.util;

import java.security.*;
import java.math.BigInteger;

public class MD5 {

  public static String getHashString(String plaintext) {
      try {
      return convertToString(getHash(plaintext.getBytes("UTF8"))); 
      } catch (java.io.UnsupportedEncodingException  e) {
	  throw new RuntimeException(e.toString());
      }
  }

  public static byte[] getHash(byte[] plainText)  {
    try {      
      MessageDigest md = MessageDigest.getInstance("MD5");
      return md.digest(plainText);
    }
    catch(Exception e) {      
      throw new RuntimeException(e.toString());
    }
  }

  public static String convertToString(byte[] digest_ba) {
    return new BigInteger(digest_ba).toString(16);
  }
}
