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
 * @(#)OptionParser.java	1.14 06/29/07
 */ 

package com.sun.messaging.jmq.util.options;

import java.util.Properties;

/**
 * This class is a generic command line options parser.
 * parseArgs() is the entry point for this class.
 *
 * <P>
 * Based on the option descrption table that is passed into
 * parseArgs(), it will go through each command line option
 * in the args[] array and add the relevant property
 * name=value pairs in the Properties object that is passed
 * in.
 *
 * <P>
 * REVISIT: should we add a flag to indicate if an exception
 * should be thrown is an option is used and it is not in
 * the options table ?
 *
 * <P>
 * parseArgs() will throw an OptionException if the command
 * line option expects an argument in the next element of
 * the args[] array and there is none.
 *
 * <P>
 * This class should be subclassed for use in specific
 * applications.
 *
 * @see		com.sun.messaging.jmq.admin.util.OptionType
 * @see		com.sun.messaging.jmq.admin.util.OptionDesc
 */
public class OptionParser implements OptionType {
    
    /**
     * Parses arg list using the specified option description
     * table and returns a Properties object which corresponds
     * to it.
     *
     * @param args	Array containing command line options
     * @param optDesc	Table defining the valid command line options
     * @param prop	Properties object that will be used to
     *			store the name=value pairs.
     */
    public static void parseArgs(String args[], OptionDesc optDesc[],
			Properties prop) throws OptionException  {

	if ((args == null) || (optDesc == null) || (prop == null))  {
	    return;
	}

	int	argsCount = args.length,
		i;

	for (i = 0; i < argsCount; ++i)  {
	    String curOption = args[i];

	    /*
	     * Search the options table for a matching option.
	     */
	    int match = findMatchingOption(optDesc, curOption);

	    /*
	     * Throw an exception if a match was not found
	     */
	    if (match < 0)  {
		UnrecognizedOptionException	uoe
			= new UnrecognizedOptionException();
		uoe.setOption(curOption);
		throw(uoe);
	    }

	    OptionDesc	matchOpt = optDesc[match];
	    boolean	ignore = matchOpt.ignore;
	    String	optBaseProp = matchOpt.baseProperty,
			optValue = matchOpt.value,
			optNameValuePair = matchOpt.nameValuePair,
			propName = null,
			propVal = null,
			name = null,
			nvPair = null;

	    switch (matchOpt.type)  {
	    case OPTION_VALUE_HARDCODED:
		propName = optBaseProp;
		propVal = optValue;

		/*
		 * If either the name or value is null,
		 * throw an exception.
		 */
		if (propName == null)  {
		    InvalidBasePropNameException ibpe
		     = new InvalidBasePropNameException();
		    ibpe.setOption(curOption);
		    throw(ibpe);
		}
		if (propVal == null)  {
		    InvalidHardCodedValueException ihcve
		     = new InvalidHardCodedValueException();
		    ihcve.setOption(curOption);
		    throw(ihcve);
		}
	    break;

	    case OPTION_VALUE_NEXT_ARG:
		/*
		 * Throw exception if argument holding value of property
		 * is missing.
		 */
		if ((i + 1) >= argsCount)  {
		    MissingArgException mae = new MissingArgException();
		    mae.setOption(curOption);
		    throw (mae);
		}
		propName = optBaseProp;
		propVal = args[++i];

		/*
		 * If the name is null, throw an exception.
		 */
		if (propName == null)  {
		    InvalidBasePropNameException ibpe
		     = new InvalidBasePropNameException();
		    ibpe.setOption(curOption);
		    throw(ibpe);
		}
	    break;

	    case OPTION_VALUE_NEXT_ARG_RES:
		/*
		 * Throw exception if argument holding value of property
		 * is missing.
		 */
		if ((i + 1) >= argsCount)  {
		    MissingArgException mae = new MissingArgException();
		    mae.setOption(curOption);
		    throw (mae);
		}

		nvPair = args[++i];
		name = getName(nvPair);

		propVal = getValue(nvPair);

		/*
		 * If either the name or value is null,
		 * throw an exception.
		 */
		if ((name == null) || (propVal == null))  {
		    BadNameValueArgException bnvae
		     = new BadNameValueArgException();
		    bnvae.setOption(curOption);
		    bnvae.setArg(nvPair);
		    throw(bnvae);
		}

		/*
		 * If base property is not null, append
		 * it to the name portion of the n/v pair.
		 *
		 * If base property is null, simply use
		 * the name portion of the n/v pair.
		 */
		if (optBaseProp != null)  {
		    propName = optBaseProp + "." + name;
		} else  {
		    propName = name;
		}

	    break;

	    case OPTION_VALUE_SUFFIX_RES:
		try  {
		    nvPair = curOption.substring(matchOpt.option.length());
		} catch(IndexOutOfBoundsException ibe)  {
		    MissingArgException mae = new MissingArgException();
		    mae.setOption(matchOpt.option);
		    throw (mae);
		}

		if (nvPair.equals(""))  {
		    MissingArgException mae = new MissingArgException();
		    mae.setOption(matchOpt.option);
		    throw (mae);
		}

		name = getName(nvPair);

		propVal = getValue(nvPair);

		/*
		 * If either the name or value is null,
		 * throw an exception.
		 */
		if ((name == null) || (propVal == null))  {
		    BadNameValueArgException bnvae
		     = new BadNameValueArgException();
		    bnvae.setOption(matchOpt.option);
		    bnvae.setArg(nvPair);
		    throw(bnvae);
		}

		/*
		 * If base property is not null, append
		 * it to the name portion of the n/v pair.
		 *
		 * If base property is null, simply use
		 * the name portion of the n/v pair.
		 */
		if (optBaseProp != null)  {
		    propName = optBaseProp + "." + name;
		} else  {
		    propName = name;
		}

	    break;
	    }

	    if ((propName != null) && (propVal != null))  {
		/*
		 * REVISIT: Enable this after add 'overwrite' flag
		 * to OptionDesc
		 *
		 * Check if property alrady exists before
		 * writing it.
		if (prop.containsKey(propName))  {
		    PropertyAlreadyExistsException pae
		     = new PropertyAlreadyExistsException();
		    pae.setOption(curOption);
		    pae.setPropertyName(propName);
		    throw(pae);
		}
		 */

		if (!ignore)  {
	            prop.put(propName, propVal);
		}
	    }

	    /*
	     * 'optNameValuePair' holds a name value pair that needs to
	     * be added to the properties object. This is independent of
	     * the processing above.
	     */
	    if (optNameValuePair != null)  {
		String	name2 = getName(optNameValuePair),
			value = getValue(optNameValuePair);
		
		if ((name2 != null) || (value != null))  {
	            prop.put(name2, value);
		}
	    }

	}
    }

    private static String getName(String nameValuePair)  {
	if (nameValuePair == null)  {
	    return (null);
	}

	int index = nameValuePair.indexOf("=");

	if (index > 0) {
	    return (nameValuePair.substring(0, index));
	}

	return (null);
    }

    private static String getValue(String nameValuePair)  {
	if (nameValuePair == null)  {
	    return (null);
	}

	int index = nameValuePair.indexOf("=");

	if (index > 0) {
	    if (nameValuePair.length() == 1)  {
		return ("");
	    } else  {
		return (nameValuePair.substring(index+1));
	    }
	}

	return (null);
    }

    private static int findMatchingOption(OptionDesc optDesc[], String arg)  {
	int matchIndex = -1;
	
	for (int i = 0; i < optDesc.length; ++i)  {
	    int type = optDesc[i].type;
	    String option = optDesc[i].option;

	    if (arg.equals(option))  {
		return (i);
	    } else if ((type == OPTION_VALUE_SUFFIX_RES) && (arg.startsWith(option))) {
		return (i);
	    }
	}
	return (matchIndex);
    }
}


