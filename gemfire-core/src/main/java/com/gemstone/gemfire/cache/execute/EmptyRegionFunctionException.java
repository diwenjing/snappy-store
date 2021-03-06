/*
 * Copyright (c) 2010-2015 Pivotal Software, Inc. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you
 * may not use this file except in compliance with the License. You
 * may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or
 * implied. See the License for the specific language governing
 * permissions and limitations under the License. See accompanying
 * LICENSE file.
 */

package com.gemstone.gemfire.cache.execute;

/**
 * Exception to indicate that Region is empty for data aware functions.
 * 
 * @author skumar
 * @since 6.5
 * 
 */
public class EmptyRegionFunctionException extends FunctionException {

  private static final long serialVersionUID = 6083497365897359571L;

  /**
   * Construct an instance of EmptyRegionFunctionException
   * 
   * @param cause
   *                a Throwable cause of this exception
   */
  public EmptyRegionFunctionException(Throwable cause) {
    super(cause);
  }

  /**
   * Construct an instance of EmptyRegionFunctionException
   * 
   * @param msg
   *                Exception message
   */
  public EmptyRegionFunctionException(String msg) {
    super(msg);
  }

  /**
   * Construct an instance of EmptyRegionFunctionException
   * 
   * @param msg
   *                the error message
   * @param cause
   *                a Throwable cause of this exception
   */
  public EmptyRegionFunctionException(String msg, Throwable cause) {
    super(msg, cause);
  }
}
