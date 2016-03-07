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
package com.gemstone.gemfire.internal.cache;

import com.gemstone.gemfire.StatisticsFactory;
import com.gemstone.gemfire.cache.Scope;
import com.gemstone.gemfire.internal.cache.DirectoryHolder;

/**
 * Unit testing for ComplexDiskRegion API's
 * 
 * @author Mitul Bid
 * 
 *  
 */
public class ComplexDiskRegionJUnitTest extends DiskRegionTestingBase
{

  DiskRegionProperties diskProps = new DiskRegionProperties();

  public ComplexDiskRegionJUnitTest(String name) {
    super(name);
  }
  
  

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    diskProps.setDiskDirs(dirs);
    DiskStoreImpl.SET_IGNORE_PREALLOCATE = true;
  }

  public void tearDown() throws Exception {
    super.tearDown();
    DiskStoreImpl.SET_IGNORE_PREALLOCATE = false;
  }


  /**
   * Test method for
   * 'com.gemstone.gemfire.internal.cache.ComplexDiskRegion.addToBeCompacted(Oplog)'
   * 
   * The test will test that an oplog is correctly being added to be rolled
   */
  public void testAddToBeCompacted()
  {
    deleteFiles();
    diskProps.setRolling(false);
    diskProps.setAllowForceCompaction(true);
    region = DiskRegionHelperFactory.getSyncPersistOnlyRegion(cache, diskProps, Scope.LOCAL);
    DiskRegion dr = ((LocalRegion)region).getDiskRegion();
    StatisticsFactory factory = region.getCache().getDistributedSystem();
    Oplog oplog1 = new Oplog(11, dr.getOplogSet(), new DirectoryHolder(factory,dirs[1], 1000, 0));
    Oplog oplog2 = new Oplog(12, dr.getOplogSet(), new DirectoryHolder(factory,dirs[2], 1000, 1));
    Oplog oplog3 = new Oplog(13, dr.getOplogSet(), new DirectoryHolder(factory,dirs[3], 1000, 2));
    // give these guys some fake "live" entries
    oplog1.incTotalCount();
    oplog1.incLiveCount();
    oplog2.incTotalCount();
    oplog2.incLiveCount();
    oplog3.incTotalCount();
    oplog3.incLiveCount();

    dr.addToBeCompacted(oplog1);
    dr.addToBeCompacted(oplog2);
    dr.addToBeCompacted(oplog3);

    assertEquals(null, dr.getOplogToBeCompacted());

    oplog1.incTotalCount();
    if (oplog1 != dr.getOplogToBeCompacted()[0]) {
      fail(" expected oplog1 to be the first oplog but not the case !");
    }
    dr.removeOplog(oplog1.getOplogId());
    assertEquals(null, dr.getOplogToBeCompacted());

    oplog2.incTotalCount();
    if (oplog2 != dr.getOplogToBeCompacted()[0]) {
      fail(" expected oplog2 to be the first oplog but not the case !");
    }
    dr.removeOplog(oplog2.getOplogId());
    assertEquals(null, dr.getOplogToBeCompacted());

    oplog3.incTotalCount();
    if (oplog3 != dr.getOplogToBeCompacted()[0]) {
      fail(" expected oplog3 to be the first oplog but not the case !");
    }
    dr.removeOplog(oplog3.getOplogId());

    oplog1.destroy();
    oplog2.destroy();
    oplog3.destroy();
    closeDown();
    deleteFiles();

  }

  /**
   *  
   * Test method for
   * 'com.gemstone.gemfire.internal.cache.ComplexDiskRegion.removeFirstOplog(Oplog)'
   * 
   * The test verifies the FIFO property of the oplog set (first oplog to be added should be
   * the firs to be rolled).
   */
  public void testRemoveFirstOplog()
  {
    deleteFiles();
    diskProps.setRolling(false);
    region = DiskRegionHelperFactory.getSyncPersistOnlyRegion(cache, diskProps, Scope.LOCAL);
    DiskRegion dr = ((LocalRegion)region).getDiskRegion();
    StatisticsFactory factory = region.getCache().getDistributedSystem();
    Oplog oplog1 = new Oplog(11, dr.getOplogSet(), new DirectoryHolder(factory,dirs[1], 1000, 0));
    Oplog oplog2 = new Oplog(12, dr.getOplogSet(), new DirectoryHolder(factory,dirs[2], 1000, 1));
    Oplog oplog3 = new Oplog(13, dr.getOplogSet(), new DirectoryHolder(factory,dirs[3], 1000, 2));
    // give these guys some fake "live" entries
    oplog1.incTotalCount();
    oplog1.incLiveCount();
    oplog2.incTotalCount();
    oplog2.incLiveCount();
    oplog3.incTotalCount();
    oplog3.incLiveCount();

    dr.addToBeCompacted(oplog1);
    dr.addToBeCompacted(oplog2);
    dr.addToBeCompacted(oplog3);

    if (oplog1 != dr.removeOplog(oplog1.getOplogId())) {
      fail(" expected oplog1 to be the first oplog but not the case !");
    }

    if (oplog2 != dr.removeOplog(oplog2.getOplogId())) {
      fail(" expected oplog2 to be the first oplog but not the case !");
    }
    if (oplog3 != dr.removeOplog(oplog3.getOplogId())) {
      fail(" expected oplog3 to be the first oplog but not the case !");
    }
    oplog1.destroy();
    oplog2.destroy();
    oplog3.destroy();

    closeDown();
    deleteFiles();
  }

}