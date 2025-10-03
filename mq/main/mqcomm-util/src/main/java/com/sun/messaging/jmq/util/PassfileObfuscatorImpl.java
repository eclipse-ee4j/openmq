/*
 * Copyright (c) 2000, 2017 Oracle and/or its affiliates. All rights reserved.
 * Copyright 2021, 2024 Contributors to the Eclipse Foundation
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
package com.sun.messaging.jmq.util;

import java.io.*;
import java.util.List;
import java.util.ArrayList;
import java.util.Collections;
import java.math.BigInteger;
import java.security.SecureRandom;
import java.security.MessageDigest;

public class PassfileObfuscatorImpl implements PassfileObfuscator {

    private static final String FORMAT_VERSION = "01F4"; // 500
    private static final String MAGIC = "4D51" + FORMAT_VERSION; // MQ
    private static String OBSUFFIX_START = "{";
    private static String OBSUFFIX_END = "}";
    private static String OBSUFFIX_MAGIC_END = MAGIC + OBSUFFIX_END;

    @Override
    public void obfuscateFile(String source, String target, String prefix) throws IOException {

        try {

            FileReader fr = new FileReader(source);
            BufferedReader br = new BufferedReader(fr);
            FileWriter fw = new FileWriter(target);
            BufferedWriter bw = new BufferedWriter(fw);

            MessageDigest md = MessageDigest.getInstance("SHA-256");
            BASE64Encoder encoder = new BASE64Encoder();

            String line = null;
            while ((line = br.readLine()) != null) {
                if (line.startsWith(prefix)) {
                    int ind = line.indexOf("=");
                    if (ind > 0 && line.length() > (ind + 1)) {
                        String key = line.substring(0, ind);
                        key = key.trim();
                        if (!key.endsWith(OBSUFFIX_MAGIC_END)) {
                            String pw = line.substring(ind + 1);
                            String epw = encoder.encode(pw.getBytes("UTF8"));

                            SecureRandom random = new SecureRandom();
                            byte randombytes[] = new byte[4];
                            random.nextBytes(randombytes);

                            String finalmagic = new BigInteger(randombytes).toString(16) + MAGIC;

                            String hashstr = hashpw(pw, finalmagic, md);

                            bw.write(key + OBSUFFIX_START + finalmagic + OBSUFFIX_END + "=" + hashstr + ":" + epw);
                            bw.newLine();
                            continue;
                        }
                    }
                }
                bw.write(line);
                bw.newLine();
            }

            bw.close();
            fw.close();
            br.close();
            fr.close();

        } catch (IOException e) {
            throw e;
        } catch (Exception ee) {
            throw new IOException(ee.toString(), ee);
        }
    }

    @Override
    public void deobfuscateFile(String source, String target, String prefix) throws IOException {
        deobfuscateFile(source, target, prefix, false);
    }

    private List<String> unobfuscatedKeys = Collections.synchronizedList(new ArrayList<>());

    private StringBuilder deobfuscateFile(String source, String target, String prefix, boolean returnContentOnly) throws IOException {

        try {

            unobfuscatedKeys.clear();

            FileReader fr = new FileReader(source);
            BufferedReader br = new BufferedReader(fr);

            MessageDigest md = MessageDigest.getInstance("SHA-256");
            BASE64Decoder decoder = new BASE64Decoder();

            StringBuilder contents = new StringBuilder();

            String line = null;
            while ((line = br.readLine()) != null) {
                if (line.startsWith(prefix)) {
                    int ind = line.indexOf("=");
                    if (ind > 0 && line.length() > (ind + 1)) {
                        String key = line.substring(0, ind);
                        key = key.trim();
                        if (key.endsWith(OBSUFFIX_MAGIC_END)) {
                            String hashepw = line.substring(ind + 1);
                            int ind2 = hashepw.indexOf(":");
                            if (ind2 > 0 && hashepw.length() > (ind2 + 1)) {
                                String epw = hashepw.substring(ind2 + 1);
                                String hashstr = hashepw.substring(0, ind2);
                                byte[] hashbytes = decoder.decodeBuffer(epw);
                                String pw = new String(hashbytes, "UTF8");

                                int indstart = key.lastIndexOf(OBSUFFIX_START);
                                if (indstart < 0 || indstart >= key.lastIndexOf(OBSUFFIX_MAGIC_END)) {
                                    throw new IOException("Corrupted line[" + indstart + ", " + key.lastIndexOf(OBSUFFIX_MAGIC_END) + "]: " + line);
                                }
                                int indend = key.lastIndexOf(OBSUFFIX_END);
                                if (indend < 0 || indend <= (indstart + OBSUFFIX_MAGIC_END.length())) {
                                    throw new IOException("Corrupted line[" + indend + ", " + indstart + "+" + OBSUFFIX_MAGIC_END.length() + "]: " + line);
                                }
                                String finalmagic = key.substring(indstart + 1, indend);

                                String myhashstr = hashpw(pw, finalmagic, md);
                                if (!hashstr.equals(myhashstr)) {
                                    throw new IOException("Password corrupted in line: " + line);
                                }
                                contents.append(key.substring(0, indstart) + "=" + pw);
                                contents.append(System.getProperty("line.separator"));
                                continue;
                            } else {
                                throw new IOException("Corrupted line[" + ind2 + ", " + hashepw.length() + ", " + (ind2 + 1) + "]: " + line);
                            }
                        } else {
                            unobfuscatedKeys.add(key);
                        }
                    }

                }
                contents.append(line);
                contents.append(System.getProperty("line.separator"));
            }

            if (returnContentOnly) {
                br.close();
                fr.close();
                return contents;
            }

            FileWriter fw = new FileWriter(target);
            BufferedWriter bw = new BufferedWriter(fw);
            String str = contents.toString();
            bw.write(str);

            bw.close();
            fw.close();
            br.close();
            fr.close();

            return null;

        } catch (IOException e) {
            throw e;
        } catch (Exception ee) {
            throw new IOException(ee.toString(), ee);
        }
    }

    @Override
    public InputStream retrieveObfuscatedFile(String source, String prefix) throws IOException {

        StringBuilder contents = deobfuscateFile(source, null, prefix, true);

        byte[] bytes = contents.toString().getBytes("UTF8");
        ByteArrayInputStream pipeis = new ByteArrayInputStream(bytes);
        return pipeis;
    }

    @Override
    public boolean isObfuscated(String source, String prefix) throws IOException {
        return unobfuscatedKeys.isEmpty();
    }

    private String hashpw(String pw, String finalmagic, MessageDigest md) throws Exception {
        byte[] salt = finalmagic.getBytes("UTF8");
        boolean even = false;
        if (salt[0] % 2 == 0) {
            even = true;
            byte b0 = salt[0];
            salt[0] = salt[salt.length - 1];
            salt[salt.length - 1] = b0;
        } else {
            byte b1 = salt[1];
            salt[1] = salt[salt.length - 1];
            salt[salt.length - 1] = b1;
        }
        md.reset();
        md.update(salt);
        byte[] hashbytes = md.digest(pw.getBytes("UTF8"));
        if (even) {
            md.reset();
            hashbytes = md.digest(hashbytes);
            md.reset();
            hashbytes = md.digest(hashbytes);
        } else {
            md.reset();
            hashbytes = md.digest(hashbytes);
        }
        return new BigInteger(hashbytes).toString(16);
    }
}
