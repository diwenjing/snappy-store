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
package com.gemstone.gemfire.cache.client.internal;

import com.gemstone.gemfire.SystemFailure;
import com.gemstone.gemfire.cache.Region;
import com.gemstone.gemfire.cache.client.ServerConnectivityException;
import com.gemstone.gemfire.cache.client.ServerOperationException;
import com.gemstone.gemfire.distributed.internal.ServerLocation;
import com.gemstone.gemfire.i18n.LogWriterI18n;
import com.gemstone.gemfire.internal.cache.LocalRegion;
import com.gemstone.gemfire.internal.cache.tier.MessageType;
import com.gemstone.gemfire.internal.cache.tier.sockets.ChunkedMessage;
import com.gemstone.gemfire.internal.cache.tier.sockets.Message;
import com.gemstone.gemfire.internal.cache.tier.sockets.Part;
import com.gemstone.gemfire.internal.cache.tier.sockets.VersionedObjectList;
import com.gemstone.gemfire.internal.i18n.LocalizedStrings;
import com.gemstone.gemfire.internal.shared.Version;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Does a region getAll on a server
 * @author darrel
 * @since 5.7
 */
public class GetAllOp {
  
  static LogWriterI18n logger = null;
  /**
   * Does a region getAll on a server using connections from the given pool
   * to communicate with the server.
   * @param pool the pool to use to communicate with the server.
   * @param region the name of the region to do the getAll on
   * @param keys list of keys to get
   * @return the map of values found by the getAll if any
   */
  public static VersionedObjectList execute(ExecutablePool pool,
                                               String region,
                                               List keys)
  {
    AbstractOp op = new GetAllOpImpl(pool.getLoggerI18n(), region, keys, false);
    op.initMessagePart();
    return ((VersionedObjectList)pool.execute(op)).setKeys(keys);
  }
  
  public static VersionedObjectList executeOnPrimary(ExecutablePool pool,
      String region, List keys, boolean getSerializedValues) {

    AbstractOp op = new GetAllOpImpl(pool.getLoggerI18n(), region, keys, getSerializedValues);
    op.initMessagePart();
    return ((VersionedObjectList)pool.executeOnPrimary(op)).setKeys(keys);
  }
  
  public static VersionedObjectList executeOn(Connection con, ExecutablePool pool,
      String region, List keys, boolean getSerializedValues) {
    AbstractOp op = new GetAllOpImpl(pool.getLoggerI18n(), region, keys, getSerializedValues);
    op.initMessagePart();
    return ((VersionedObjectList)pool.executeOn(con,op)).setKeys(keys);
  }

  public static VersionedObjectList execute(ExecutablePool pool,
      Region region, List keys, int retryAttempts) {
    logger = pool.getLoggerI18n();
    AbstractOp op = new GetAllOpImpl(pool.getLoggerI18n(),
        region.getFullPath(), keys, false);
    ClientMetadataService cms = ((LocalRegion)region).getCache()
        .getClientMetadataService();

    Map<ServerLocation, HashSet> serverToFilterMap = cms.getServerToFilterMap(
        keys, region, true);
    
    if (serverToFilterMap == null || serverToFilterMap.isEmpty()) {
      op.initMessagePart();
      return ((VersionedObjectList)pool.execute(op)).setKeys(keys);
    }
    else {
      VersionedObjectList result = null;
      ServerConnectivityException se = null;
      List retryList = new ArrayList();
      List callableTasks = constructGetAllTasks(region.getFullPath(),
          serverToFilterMap, (PoolImpl)pool);
      Map<ServerLocation, Object> results = SingleHopClientExecutor.submitGetAll(
          serverToFilterMap, callableTasks, cms, (LocalRegion)region);
      for (ServerLocation server : results.keySet()) {
        Object serverResult = results.get(server);
        if (serverResult instanceof ServerConnectivityException) {
          se = (ServerConnectivityException)serverResult;
          retryList.addAll(serverToFilterMap.get(server));
        }
        else {
          if (result == null) {
            result = (VersionedObjectList)serverResult;
          } else {
            result.addAll((VersionedObjectList)serverResult);
          }
        }
      }
      
      if (se != null) {
        if (retryAttempts == 0) {
          throw se;
        }
        else {
          VersionedObjectList retryResult = GetAllOp.execute(pool,
              region.getFullPath(), retryList);
          if (result == null) {
            result = retryResult;
          } else {
            result.addAll(retryResult);
          }
        }
      }

      return result;
    }
  }
  
  private GetAllOp() {
    // no instances allowed
  }
  
  static List constructGetAllTasks(String region,
      final Map<ServerLocation, HashSet> serverToFilterMap, final PoolImpl pool) {
    final List<SingleHopOperationCallable> tasks = new ArrayList<SingleHopOperationCallable>();
    ArrayList<ServerLocation> servers = new ArrayList<ServerLocation>(
        serverToFilterMap.keySet());

    if (logger.fineEnabled()) {
      logger.fine("Constructing tasks for the servers" + servers);
    }
    for (ServerLocation server : servers) {
      Set filterSet = serverToFilterMap.get(server);
      AbstractOp getAllOp = new GetAllOpImpl(logger, region, new ArrayList(
          filterSet), false);

      SingleHopOperationCallable task = new SingleHopOperationCallable(
          new ServerLocation(server.getHostName(), server.getPort()), pool,
          getAllOp,UserAttributes.userAttributes.get());
      tasks.add(task);
    }
    return tasks;
  }
  
  static class GetAllOpImpl extends AbstractOp {
    
    private List keyList;
    
    private boolean forRegisterInterest;

    /**
     * @throws com.gemstone.gemfire.SerializationException if serialization fails
     */
    public GetAllOpImpl(LogWriterI18n lw,
                        String region,
                        List keys,
                        boolean forRegisterInterest)
    {
      super(lw, MessageType.GET_ALL_70, 3);
      this.keyList = keys;
      getMessage().addStringPart(region);
      this.forRegisterInterest = forRegisterInterest;
    }
        
    @Override
    protected void initMessagePart() {
      Object[] keysArray = new Object[this.keyList.size()];
      this.keyList.toArray(keysArray);
      getMessage().addObjPart(keysArray);
      getMessage().addIntPart(forRegisterInterest?1:0);
    }
    
    public List getKeyList() {
      return this.keyList;
    }


    @Override  
    protected Message createResponseMessage() {
      return new ChunkedMessage(1, Version.CURRENT);
    }
    
    @Override
    protected Object processResponse(Message msg) throws Exception {
      throw new UnsupportedOperationException();
    }

    @Override  
    protected Object processResponse(Message msg, final Connection con) throws Exception {
      final VersionedObjectList result = new VersionedObjectList(forRegisterInterest);
      final Exception[] exceptionRef = new Exception[1];
      processChunkedResponse((ChunkedMessage)msg,
                             "getAll",
                             new ChunkHandler() {
                               public void handle(ChunkedMessage cm) throws Exception {
                                 Part part = cm.getPart(0);
                                 try {
                                   Object o = part.getObject();
                                   if (o instanceof Throwable) {
                                     String s = "While performing a remote getAll";
                                     exceptionRef[0] = new ServerOperationException(s, (Throwable)o);
                                   } else {
                                     VersionedObjectList chunk = (VersionedObjectList)o;
                                     chunk.replaceNullIDs(con.getEndpoint().getMemberId());
                                     result.addAll(chunk);
                                   }
                                 } catch(Exception e) {
                                   exceptionRef[0] = new ServerOperationException("Unable to deserialize value" , e);
                                 }
                               }
                             });
      if (exceptionRef[0] != null) {
        throw exceptionRef[0];
      } else {
        return result;
      }
    }

    @Override  
    protected boolean isErrorResponse(int msgType) {
      return msgType == MessageType.GET_ALL_DATA_ERROR;
    }

    @Override  
    protected long startAttempt(ConnectionStats stats) {
      return stats.startGetAll();
    }
    @Override  
    protected void endSendAttempt(ConnectionStats stats, long start) {
      stats.endGetAllSend(start, hasFailed());
    }
    @Override  
    protected void endAttempt(ConnectionStats stats, long start) {
      stats.endGetAll(start, hasTimedOut(), hasFailed());
    }
  }
}
