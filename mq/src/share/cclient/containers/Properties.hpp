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
 * @(#)Properties.hpp	1.5 06/26/07
 */ 

#ifndef PROPERTIES_HPP
#define PROPERTIES_HPP


#include "BasicTypeHashtable.hpp"
#include "../util/PRTypesUtils.h"
#include "../basictypes/AllBasicTypes.hpp"
#include "../basictypes/HandledObject.hpp"

#include <stdio.h>


static const PRInt32 PROPERTIES_MAX_STRING_SIZE = USHRT_MAX;


/** This class maintains a mapping from strings to BasicType's.  It
 *  delegates most responsibility to BasicTypeHashtable.  */
class Properties : public HandledObject
{
private:
  /** A hashtable that stores all of the properties.*/
  BasicTypeHashtable hashtable;

  /** Sets the value of the propertyName property to propertyValue. */
  iMQError setBasicTypeProperty( UTF8String * propertyName,
                                 BasicType  * propertyValue);

  /** Returns the value of the propertyName property in propertyValue */
  iMQError getBasicTypeProperty(const UTF8String *  const propertyName,
                                const BasicType  ** const propertyValue) const;


  /** This currently isn't called from anywhere, so it is made private.
   *  It could be made public, however. */
  iMQError setUTF8StringProperty(UTF8String * const propertyName,
                                 UTF8String * const propertyValue);


public:
  Properties();
  Properties(PRBool lazy);
  Properties(const Properties& properties);
  virtual ~Properties();

  void reset();

  /** Returns the type of the propertyName property in propertyType */
  iMQError getPropertyType(const char *     const propertyName,
                                 TypeEnum * const propertyType) const;


  /** getStringProperty returns the string property associated with
   *  propertyName in the output parameter propertyValue.  The caller
   *  should not modify or free propertyValue.  An error is returned if
   *  either parameter is NULL, propertyName is not a valid property,
   *  or if the property associated with propertyName is not a string
   *  property */
  iMQError getStringProperty(const char * const propertyName,
                             const char **      propertyValue) const;


  /*  setStringProperty sets the string property associated with
   *  propertyName to propertyValue.  The values for propertyName and
   *  propertyValue are copied, so the caller is free to modify these
   *  after getStringProperty returns.  An error is returned if either
   *  parameter is NULL, or if memory cannot be allocated to return the
   *  string. */
  iMQError setStringProperty(const char * const propertyName,
                             const char * const propertyValue);
  
  iMQError setBooleanProperty(const char     * const propertyName,
                              const PRBool           propertyValue);
  iMQError getBooleanProperty(const char     * const propertyName,
                                    PRBool   * const propertyValue) const;

  iMQError setByteProperty(const char     * const propertyName,
                           const PRInt8           propertyValue);
  iMQError getByteProperty(const char     * const propertyName,
                                 PRInt8   * const propertyValue) const;

  iMQError setShortProperty(const char     * const propertyName,
                            const PRInt16           propertyValue);
  iMQError getShortProperty(const char     * const propertyName,
                                 PRInt16   * const propertyValue) const;

  iMQError setIntegerProperty(const char     * const propertyName,
                              const PRInt32          propertyValue);
  iMQError getIntegerProperty(const char     * const propertyName,
                                    PRInt32  * const propertyValue) const;

  iMQError setLongProperty(const char     * const propertyName,
                           const PRInt64          propertyValue);
  iMQError getLongProperty(const char     * const propertyName,
                                 PRInt64  * const propertyValue) const;

  iMQError setFloatProperty(const char      * const propertyName,
                            const PRFloat32         propertyValue);
  iMQError getFloatProperty(const char      * const propertyName,
                                  PRFloat32 * const propertyValue) const;

  iMQError setDoubleProperty(const char      * const propertyName,
                             const PRFloat64         propertyValue);
  iMQError getDoubleProperty(const char      * const propertyName,
                                   PRFloat64 * const propertyValue) const;

  /** Deletes the propertyName property. */
  iMQError removeProperty(const char * propertyName);

  /** Necessary to implement HandledObject. */
  virtual HandledObjectType getObjectType() const;


  /** Add the contents of the file 'input' to the list of properties.
   *  The properties present before calling readFromFile are left *
   *  unchanged unless one of the properties in file collides with it.
   *
   *  The contents of 'input' must be in the following format:
   *  propertyName1 = propertyValue1
   *  propertyName2 = propertyValue2
   *  propertyName3 = propertyValue3
   *
   *  No whitespace may appear in a property name or property value.
   *
   *  @param fileName the name of the input file to read from
   *  @return IMQ_SUCCESS if successful and an error otherwise
   */
  iMQError readFromFile(const char * const fileName);

  // Methods from BasicTypeHashtable that this class wraps
  virtual iMQError  keyIterationStart();
  virtual PRBool    keyIterationHasNext();
  virtual iMQError  keyIterationGetNext(const char ** const key);
  virtual iMQError  print(FILE * const out);
  virtual iMQError  getNumKeys(PRUint32 * const numKeys) const;
  const char * toString( const char * const linePrefix = "" );

  /** Returns a clone of this object */
  Properties * clone() const;

  /** This is only here for backwards compatible reasons */
  BasicTypeHashtable * getHashtable();

//
// Avoid all implicit shallow copies.  Without these, the compiler
// will automatically define implementations for us.
//
private:
  //
  // This are not supported and are not implemented
  //
  Properties& operator=(const Properties& properties);
};


#endif // PROPERTIES_HPP
