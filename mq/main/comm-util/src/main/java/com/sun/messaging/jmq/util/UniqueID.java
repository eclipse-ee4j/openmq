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
 * @(#)UniqueID.java	1.8 06/29/07
 */ 

package com.sun.messaging.jmq.util;

import java.util.Random;

/**
 * A UniqueID is a 64 bit value that has the following uniqueness
 * properties:
 *
 *  1. Is unique in this VM
 *  2. Will stay unique for a very long time (over 34 years)
 *  3. Will stay unique across VM restarts (due to #2)
 *  4. Can be made more unique by the caller providing a 16 bit prefix
 *  5. Will never be 0
 *
 * Because a UniqueID incorporates a timestamp it also has the property
 * of having an age. So you can tell how old an ID is, or if one ID is
 * older than another.
 *     
 * The optional 16 bit prefix can be used to generate additional uniqueness.
 * For example if you have two applications running in two seperate VMs and
 * you need to generate unique IDs accross those two applications, then
 * those two applications would need to be assigned unique 16bit values
 * that could then be passed to generateID(short).
 *
 * The format of the id is:
 *                   1                 3                 4        5      6
 * 0                 6                 2                 8        6      3
 *+--------+--------+--------+--------+--------+--------+--------+--------+
 *|   prefix ID     |              timestamp                     | counter|
 *+--------+--------+--------+--------+--------+--------+--------+--------+
 *|    16 bits      |                40 bits                     | 8 bits |
 *
 * Where:
 *
 *    prefix ID is a signed 16 bit value passed in by the caller. It can
 *              be used to generate additional uniqueness when, for example,
 *              the ID will be shared between multiple VMs.
 *
 *    timestamp is the 40 least significant unsigned bits of the system time as
 *              returned from System.getCurrentTimeMillis(). This gives
 *              us ~34 years before wrapping back to a duplicate value.
 *
 *    counter   is an unsigned 8 bit counter used to help resolve timestamp
 *              collisions within the vm.
 *
 * The timestamp generates most of the uniqueness. The prefix ID can be used
 * to ensure uniqueness across VMs. The counter helps resolve timestamp
 * collisions within a VM. When a collision is detected the counter field is
 * incremented. If the counter is going to wrap, the code sleeps
 * for 1 ms, wraps the counter, and regenerates the timestamp. Since the
 * max value for the counter is 255 we are limited to generating 
 * 255 IDs a millisecond. In practice sleeping for 1 ms almost always takes
 * longer than 1 millisecond (more like 10ms, but this is platform dependent),
 * so our actual throughput is more like 25 IDs a millisecond (25,000 IDs a
 * second).
 *
 * CAVEAT: Setting the system clock back in time can confuse this algorithm.
 * If the system clock is set back in time while the JVM is running then the
 * algorithm will detect this  and compensate to continue generating unique
 * IDS. But when the JVM is restarted (or if the time was changed when this
 * algorithm was not running) then the algorithm will have no idea that the
 * time was changed and may generate non-unique IDs.
 *
 * For this reason it is recommended that the prefix be made unique on
 * every JVM invocation. I.e. it should sequentially increase, or at least
 * be random.
 *
 */
public class UniqueID {

    // Number of bits reserved for the various fields
    static final int    PREFIX_BITS = 16;
    static final int TIMESTAMP_BITS = 40;
    static final int   COUNTER_BITS =  8;

    // Max amount of time in ms we will sleep to account for the system
    // clock being set backwards.
    static final long MAX_SLEEP_SHIFT = 1000 * 7;
    static long max_sleep_shift = MAX_SLEEP_SHIFT;

    // 40 on bits. This is the max value the timestamp can have before it
    // wraps. I think it will wrap in November of 2004. We need this value
    // to correctly compute the age of older timestamps after the wrap.
    static final long     WRAP_TIME = 0xFFFFFFFFFFL;

    // Masks off top 24 bits of timestamp (keeping bottom 40). This
    // gives us ~34 years
    static final long TIMESTAMP_MASK = 0xFFFFFFFFFFL;

    // Max value the counter can have. Counter is unsigned 8 bits, so it is 255
    static final int MAX_COUNTER = 255;

    // Last timestamp generated. Used to check for collisions
    static long last_timestamp = 0;

    // Current counter value
    static short counter = 0;

    // For diagnositcs only. Tells us how many times the counter wrapped
    // (each wrap triggers a sleep).
    static long counter_wraps = 0;

    // Tells us the number of times we artificially
    // advanced the current timestamp due to the system clock being set
    // backwards
    static long timestamp_advances = 0;
    // For diagnositcs only. Tells us the number of times we forced a sleep
    // to advance the current timestamp due to the system clock being set
    // backwards
    static long timestamp_delays = 0;

    /*
     * Generate an ID with no prefix
     */
    public static long generateID() {
        return UniqueID.generateID((short)0);
    }

    /*
     * Generate an ID using the passed short as a prefix
     */
    public static synchronized long generateID(short prefix) {

	long  id = 0;

	long curr_timestamp = System.currentTimeMillis();

	// Check for timestamp collision

	if (curr_timestamp > last_timestamp) {
	    // No collision. Remember this timestamp and reset counter
	    last_timestamp = curr_timestamp;
	    counter = 0;
	} else if (curr_timestamp < last_timestamp) {
            // Bummer. Clock was set backwards. 
            long delta = last_timestamp - curr_timestamp;

            // If it's not off by much sleep to let the current time catch up
            if (delta <= max_sleep_shift) {
                while (curr_timestamp <= last_timestamp) {
		    try {
	                Thread.currentThread().sleep(last_timestamp -
                                                     curr_timestamp);
                        timestamp_delays++;
		    } catch (Exception e) {
		    }
		    curr_timestamp = System.currentTimeMillis();
                }
            } else {
                // We don't want to pause for too long so we
                // artificially make the current timestamp unique by
                // setting the current timestamp to be the last timestamp + 1
                // Hopefully over time the current time will catchup
                curr_timestamp = last_timestamp + 1;
                timestamp_advances++;
                if ((timestamp_advances % 200) == 0) {
                    // Every 200 times we do this sleep 200 ms so we don't
                    // outrun realtime. Note that when we hit this mode
                    // our throughput drops to ~1000 ids a second.
		    try {
	                Thread.currentThread().sleep(100);
		    } catch (Exception e) {
		    }
                }
            }
            last_timestamp = curr_timestamp;
	    counter = 0;
	} else if (counter < MAX_COUNTER) {
	    // Collision, but we can use the counter to resolve
	    counter++;
	} else {
	    // Collision, and counter is about to wrap which would generate
	    // a duplicate id. Sleep for 1 ms and get a new timestamp.
	    counter = 0;
            counter_wraps++;
	    // This is in a loop in case sleep doesn't go for a full ms.
	    // Unfortunately the problem is sleep usually (on Solaris)
	    // sleeps for a  minimum ~10 ms.
	    // That means instead of getting ~255,000 ids
	    // a second we get ~25,000 ids a second. To generate ids faster
	    // remove the sleep (this gets us to ~248,000), but that 
	    // generates a larger load on the system as we spin in the loop.
	    while (curr_timestamp <= last_timestamp) {
		try {
	            Thread.currentThread().sleep(1);
		} catch (Exception e) {
		}
		curr_timestamp = System.currentTimeMillis();
            }
	    last_timestamp = curr_timestamp;
	}

	// prefix becomes bits 0-15 of id
	id = prefix;
	id = (id << (TIMESTAMP_BITS + COUNTER_BITS));

	// Mask off top 24 bits of timestamp. That means keep bottom 40 bits
        // Then shift curr_timestamp up 8 bits so it becomes bits
        // 16-55 of id
	curr_timestamp = ((curr_timestamp & TIMESTAMP_MASK) << COUNTER_BITS);
	id = id | curr_timestamp;

	// counter becomes last 8 bits
	id = id | counter;

        if (id == 0) {
            //System.out.println("Ack!! Zero ID");
            // We need to guarantee an id is never 0. Note that this
            // is incredibly unlikely to ever happen. We sleep to
            // force the timestamp to increment, and regenerate the ID.
	    try {
                Thread.currentThread().sleep(100);
	    } catch (Exception e) {
	    }
            id = generateID(prefix);
        }

	return id;
    }

    /*
     * Returns the age of an ID in milliseconds.
     */
    public static long age(long id) {
	return (age(id, System.currentTimeMillis()));
    }

    public static long age(long id, long currentTime) {
	long curr_time = currentTime;

        // Convert current time to 40 bit value
	curr_time = (curr_time & TIMESTAMP_MASK);

	long id_time = getTimestamp(id);

	if (curr_time > id_time) {
	    return curr_time - id_time;
        } else {
	    // ID was generated before wrap date, and current time is
	    // after wrap date. Adjust accordingly.
	    return curr_time + (WRAP_TIME - id_time);
	}
    }

    /**
     * Return a string of the ID in a human readable form
     */
    public static String toString(long id) {
        return getPrefix(id) + "_" + getTimestamp(id) + "_" +
            getCounter(id);
    }

    /**
     * Return a string of the ID in a short human readable form
     */
    public static String toShortString(long id) {
        return String.valueOf(getPrefix(id)) +
               String.valueOf(getTimestamp(id)) +
               String.valueOf(getCounter(id));
    }

    /**
     * Set the maxSleepShift parameter.
     * Max amount of time in ms we will sleep to account for the system
     * clock being set backwards. Default is 7 seconds. If the shift
     * is larger than this we just compensate by adding 1 to the last
     * ID's timestamp and hope enough idle time passes at some point
     * to catch us up.
     */
    public static void setMaxSleepShift(long n) {
        max_sleep_shift = n;
    }

    public synchronized static String toLongString(long id) {

        return (
            "ID:" + UniqueID.toString(id) + "\n" +
            "       PREFIX_BITS = " + PREFIX_BITS + "\n" +
            "    TIMESTAMP_BITS = " + TIMESTAMP_BITS + "\n" +
            "      COUNTER_BITS = " + COUNTER_BITS + "\n" +
            "    TIMESTAMP_MASK = " + Long.toHexString(TIMESTAMP_MASK) + "\n" +
            "       MAX_COUNTER = " + MAX_COUNTER + "\n" +
            "   max_sleep_shift = " + max_sleep_shift + "\n" +
            "    last_timestamp = " + last_timestamp + "\n" +
            "           counter = " + counter + "\n" +
            "     counter_wraps = " + counter_wraps + "\n" +
            "timestamp_advances = " + timestamp_advances  + "\n" +
            "  timestamp_delays = " + timestamp_delays 
            );
    }

    /**
     * Return a 32 bit hashcode
     */
    public static int hashCode(long id) {
        // Taken from JDK Long's hashCode
        return (int)(id ^ (id >>> 32));
    }


    /*
     * Run some simple tests on the ID code. Iter is the number of IDs
     * to generate when doing the check-for-duplicates test. prefix is
     * the id prefix to use.
     */
    public synchronized static boolean diagnostic(int iter, short prefix) {

        System.out.println("----- UniqueID diagnostics -----");
        System.out.println("Generating one ID. prefix = " + prefix);

        counter_wraps = 0;
        timestamp_advances = 0;
        timestamp_delays = 0;

        boolean passed = true;

        // Generate one ID and get its age
        long start_time = System.currentTimeMillis();
        long id = UniqueID.generateID(prefix);
        System.out.println(" ID:" + id + " (" + toString(id) + ")");
        try {
	    Thread.currentThread().sleep(1000);
        } catch (Exception e) {
        }
        long stop_time = System.currentTimeMillis();
        System.out.println("Age:" + UniqueID.age(id) +
            " ms (should be  ~" + (stop_time - start_time) + " ms)");

        // Verify components are what we think they should be
        if (prefix == getPrefix(id)) {
            System.out.println("Prefix=" + prefix + "=" + getPrefix(id));
        } else {
            System.out.println("ERROR! Prefix=" + prefix + "!=" +
                getPrefix(id));
            passed = false;
        }

        if ((last_timestamp & TIMESTAMP_MASK) == getTimestamp(id)) {
            System.out.println("Timestamp=" +
                    (last_timestamp & TIMESTAMP_MASK) + "=" + getTimestamp(id));
        } else {
            System.out.println("ERROR! Timestamp=" +
                    (last_timestamp & TIMESTAMP_MASK) + "!=" +
                    getTimestamp(id));
            passed = false;
        }

        if (counter == getCounter(id)) {
            System.out.println("Counter=" + counter + "=" + getCounter(id));
        } else {
            System.out.println("ERROR! Counter=" + counter + "!=" +
                getCounter(id));
            passed = false;
        }

	// Generate a set of IDs and check for dups
        long[] idarray = new long[iter];

        System.out.println("Generating " + iter + " IDs...");
        long start = System.currentTimeMillis();

        for (int n = 0; n < iter; n++) {
            //System.out.print("\015" + n);
            idarray[n] = generateID(prefix);
        }
        long stop = System.currentTimeMillis();

        System.out.println("Called getID() " + iter + " times in " +
            (stop - start) + " ms (" +
            ((float)iter / (float)(stop - start)) * 1000
            + " ids per second)" );

        System.out.println(toLongString(0));

        System.out.println("Sorting ids...");
        java.util.Arrays.sort(idarray);

        long last_id = 0;
        System.out.println("Checking ids for dups...");
        int ndups = 0;
        for (int n = 0; n < iter; n++) {
            if (idarray[n] == last_id) {
                System.out.println("ERROR! Duplicate ID: " + last_id);
                ndups++;
                passed = false;
            }
            if (idarray[n] == 0) {
                System.out.println("ERROR! Zero ID: " + idarray[n]);
                passed = false;
            }
            last_id = idarray[n];
        }

        if (ndups == 0) {
            System.out.println("Passed -- no duplicates detected");
        }
        System.out.println("Done");

        return passed;
    }

    // Extracts the 40 bit timestamp from an id
    public static long getTimestamp(long id) {
        return ((id >>> COUNTER_BITS) & TIMESTAMP_MASK);
    }

    // Extracts the 16 bit prefix from an id
    public static short getPrefix(long id) {
        return (short)(id >>> (TIMESTAMP_BITS + COUNTER_BITS));
    }

    // Extracts the 8 bit counter from an id
    private static short getCounter(long id) {
        return (short)(id & 0xFF);
    }

    public static void main (String args[]) {
        int iter = 10000;
        Random rand = new Random();
        int prefix = rand.nextInt();

        for (int i = 0; i < args.length; i++) {
            if (args[i].equals("-n")) {
                iter = Integer.parseInt(args[++i]);
            } else if (args[i].equals("-p")) {
                prefix = Integer.parseInt(args[++i]);
            } else {
                System.out.println("-n <itertions>  -p <prefix>");
                System.exit(1);
            }
        }

        System.out.println("Starting UID test...");
        boolean passed = diagnostic(iter, (short)prefix);

        if (passed) {
            System.out.println("PASSED");
            System.exit(0);
        } else {
            System.out.println("FALIED");
            System.exit(1);
        }
    }

}
