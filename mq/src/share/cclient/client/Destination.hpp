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
 * @(#)Destination.hpp	1.4 06/26/07
 */ 

#ifndef DESTINATION_HPP
#define DESTINATION_HPP


#include "../basictypes/AllBasicTypes.hpp"
#include "../error/ErrorCodes.h"
#include "../basictypes/HandledObject.hpp"

class Connection;

/**
 * This class implements Topic and Queue.  
 */
class Destination : public HandledObject {
protected:
  /** The Connection that created this Destination, used by temporary destination */
  Connection * connection;

  /** The name of the Destination.  */
  UTF8String * name;
  
  /** True iff the destination is a queue */
  PRBool isQueue;

  /** True iff the destination is a temporary */
  PRBool isTemporary;

public:
  /**
   * Constructor.  Stores name.
   */
  Destination(Connection * const connection,
              const UTF8String * const name, 
              const PRBool isQueue, 
              const PRBool isTemporary);

  /**
   * Constructor.
   */
  Destination(const UTF8String * const name, 
              const UTF8String * const className,
              Connection * const connection);

  /**
   * Destructor.  Virtual to allow subclasses' destructors to be called.
   */
  virtual ~Destination();

  /**
   * @return the name of the destination
   */
  virtual const UTF8String * getName() const;

  /**
   * @return the class name for this destination
   */
  virtual const UTF8String * getClassName() const;

  /**
   * @return true if the destination is a queue, and false if it's a topic
   */
  virtual PRBool getIsQueue() const;

  /**
   * @return true iff the destination is temporary
   */
  virtual PRBool getIsTemporary() const;

  /** To implement HandledObject */
  virtual HandledObjectType getObjectType() const;

  /** Deletes this destination from the broker.  This is only valid
      for temporary destinations. */
  iMQError deleteDestination();

  /** @return the Session that created this destination*/
  Connection * getConnection() const;
  
  /** @return a clone of this Destination */
  Destination * clone() const;
  
//
// Avoid all implicit shallow copies.  Without these, the compiler
// will automatically define implementations for us.
//
private:
  //
  // These are not supported and are not implemented
  //
  Destination(const Destination& destination);
  Destination& operator=(const Destination& destination);
};


#endif // DESTINATION_HPP
