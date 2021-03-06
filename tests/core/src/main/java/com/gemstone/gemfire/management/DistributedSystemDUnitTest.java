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
package com.gemstone.gemfire.management;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.management.InstanceNotFoundException;
import javax.management.ListenerNotFoundException;
import javax.management.MBeanServer;
import javax.management.Notification;
import javax.management.NotificationBroadcasterSupport;
import javax.management.NotificationListener;
import javax.management.ObjectName;

import com.gemstone.gemfire.cache.Cache;
import com.gemstone.gemfire.distributed.DistributedMember;
import com.gemstone.gemfire.distributed.internal.InternalDistributedSystem;
import com.gemstone.gemfire.internal.LogWriterImpl;
import com.gemstone.gemfire.internal.ManagerLogWriter;
import com.gemstone.gemfire.internal.admin.Alert;
import com.gemstone.gemfire.internal.cache.DiskStoreStats;
import com.gemstone.gemfire.internal.cache.GemFireCacheImpl;
import com.gemstone.gemfire.management.internal.MBeanJMXAdapter;
import com.gemstone.gemfire.management.internal.ManagementConstants;
import com.gemstone.gemfire.management.internal.NotificationHub;
import com.gemstone.gemfire.management.internal.SystemManagementService;
import com.gemstone.gemfire.management.internal.NotificationHub.NotificationHubListener;
import com.gemstone.gemfire.management.internal.beans.MemberMBean;
import com.gemstone.gemfire.management.internal.beans.MemberMBeanBridge;
import com.gemstone.gemfire.management.internal.beans.ResourceNotification;
import com.gemstone.gemfire.management.internal.beans.SequenceNumber;

import dunit.Host;
import dunit.SerializableCallable;
import dunit.SerializableRunnable;
import dunit.VM;
import dunit.DistributedTestCase.ExpectedException;
import dunit.DistributedTestCase.WaitCriterion;

/**
 * Distributed System tests
 * 
 * a) For all the notifications 
 * 
 *  i) gemfire.distributedsystem.member.joined
 * 
 *  ii) gemfire.distributedsystem.member.left
 * 
 *  iii) gemfire.distributedsystem.member.suspect
 * 
 *  iv ) All notifications emitted by member mbeans
 * 
 *  vi) Alerts
 * 
 * b) Concurrently modify proxy list by removing member and accessing the
 * distributed system MBean
 * 
 * c) Aggregate Operations like shutDownAll
 * 
 * d) Member level operations like fetchJVMMetrics()
 * 
 * e ) Statistics
 * 
 * 
 * @author rishim
 * 
 */
public class DistributedSystemDUnitTest extends ManagementTestBase {

  private static final long serialVersionUID = 1L;

 
  private static final int MAX_WAIT = 100 * 1000;
  
  private static MBeanServer mbeanServer = MBeanJMXAdapter.mbeanServer;
  
  static List<Notification> notifList = new ArrayList<Notification>();
  
  static Map<ObjectName , NotificationListener> notificationListenerMap = new HashMap<ObjectName , NotificationListener>();

  
  public DistributedSystemDUnitTest(String name) {
    super(name);
  }

  public void setUp() throws Exception {
    super.setUp();
    

  }

  public void tearDown2() throws Exception {
    super.tearDown2();
    
  }

  /**
   * Tests each and every operations that is defined on the MemberMXBean
   * 
   * @throws Exception
   */
  public void testDistributedSystemAggregate() throws Exception {
    VM managingNode = getManagingNode();
    createManagementCache(managingNode);
    startManagingNode(managingNode);
    addNotificationListener(managingNode);

    for (VM vm : getManagedNodeList()) {
      createCache(vm);
    }
    
    checkAggregate(managingNode);
    for (VM vm : getManagedNodeList()) {
      closeCache(vm);
    }

    closeCache(managingNode);

  }
  
  /**
   * Tests each and every operations that is defined on the MemberMXBean
   * 
   * @throws Exception
   */
  public void testAlert() throws Exception {
    VM managingNode = getManagingNode();
   
    createManagementCache(managingNode);
    startManagingNode(managingNode);
    addAlertListener(managingNode);
    resetAlertCounts(managingNode);
    
    final DistributedMember managingMember = getMember(managingNode);
    
    
    for (VM vm : getManagedNodeList()) {
      
      createCache(vm);
      // Default is severe ,So only Severe level alert is expected
      
      ensureLoggerState(vm, managingMember, Alert.SEVERE);
      
      warnLevelAlert(vm);
      severeLevelAlert(vm);
      
    }
    checkAlertCount(managingNode, 3, 0);
    resetAlertCounts(managingNode);
    setAlertLevel(managingNode, "warning");

    
    for (VM vm : getManagedNodeList()) {
      // warning and severe alerts both are to be checked
      ensureLoggerState(vm, managingMember, Alert.WARNING);
      warnLevelAlert(vm);
      severeLevelAlert(vm);
    }

    checkAlertCount(managingNode, 3, 3);
    
    resetAlertCounts(managingNode);
    
    setAlertLevel(managingNode, "none");
    
    for (VM vm : getManagedNodeList()) {
     // ensureLoggerState(vm, managingMember, Alert.OFF);
      warnLevelAlert(vm);
      severeLevelAlert(vm);
    }
    checkAlertCount(managingNode, 0, 0);
    resetAlertCounts(managingNode);
    
    for (VM vm : getManagedNodeList()) {
      closeCache(vm);
    }

    closeCache(managingNode);

  }
  
   
  /**
   * Tests each and every operations that is defined on the MemberMXBean
   * 
   * @throws Exception
   */
  public void testAlertManagedNodeFirst() throws Exception {
    

    for (VM vm : getManagedNodeList()) {
      createCache(vm);
      warnLevelAlert(vm);
      severeLevelAlert(vm);
      
    }
    VM managingNode = getManagingNode();
    createManagementCache(managingNode);
    startManagingNode(managingNode);
    
    addAlertListener(managingNode);
      
    checkAlertCount(managingNode, 0, 0);
    
    final DistributedMember managingMember = getMember(managingNode);
    
    for (VM vm : getManagedNodeList()) {
      ensureLoggerState(vm, managingMember, Alert.SEVERE);
      warnLevelAlert(vm);
      severeLevelAlert(vm);
    }
    checkAlertCount(managingNode, 3, 0);
    resetAlertCounts(managingNode);
    setAlertLevel(managingNode, "warning");
 
    for (VM vm : getManagedNodeList()) {
      ensureLoggerState(vm, managingMember, Alert.WARNING);
      warnLevelAlert(vm);
      severeLevelAlert(vm);
    }
    
    checkAlertCount(managingNode, 3, 3);
    resetAlertCounts(managingNode);
    
    for (VM vm : getManagedNodeList()) {
      closeCache(vm);
    }

    closeCache(managingNode);

  }
  
  @SuppressWarnings("serial")
  public void ensureLoggerState(VM vm1, final DistributedMember member,
      final int alertLevel) throws Exception {
    {
      vm1.invoke(new SerializableCallable("Ensure Logger State") {

        public Object call() throws Exception {
          
          waitForCriterion(new WaitCriterion() {
            public String description() {
              return "Waiting for all alert Listener to register with managed node";
            }

            public boolean done() {

              if (((ManagerLogWriter) InternalDistributedSystem.getLoggerI18n())
                  .hasAlertListenerFor(member, alertLevel)) {
                return true;
              }
              return false;
            }

          }, MAX_WAIT, 500, true);

          return null;
        }
      });

    }
  }
  
  
  /**
   * Tests each and every operations that is defined on the MemberMXBean
   * 
   * @throws Exception
   */
  public void testShutdownAll() throws Exception {
    final Host host = Host.getHost(0);
    VM managedNode1 = host.getVM(0);
    VM managedNode2 = host.getVM(1);
    VM managedNode3 = host.getVM(2);

    VM managingNode = host.getVM(3);

    // Managing Node is created first
    createManagementCache(managingNode);
    startManagingNode(managingNode);
    
    createCache(managedNode1);
    createCache(managedNode2);
    createCache(managedNode3);
    shutDownAll(managingNode);
    closeCache(managingNode);
  }
  
  public void testNavigationAPIS() throws Exception{
    
    final Host host = Host.getHost(0); 
    
    createManagementCache(managingNode);
    startManagingNode(managingNode);
    
    for(VM vm : managedNodeList){
      createCache(vm);
    }
    
    checkNavigationAPIs(managingNode);    
  }

  public void testNotificationHub() throws Exception {
    this.initManagement(false);

    class NotificationHubTestListener implements NotificationListener {
      @Override
      public void handleNotification(Notification notification, Object handback) {
        notifList.add(notification);

      }
    }

    managingNode
        .invoke(new SerializableRunnable("Add Listener to MemberMXBean") {

          public void run() {
            Cache cache = getCache();
            ManagementService service = getManagementService();
            final DistributedSystemMXBean bean = service.getDistributedSystemMXBean();
            
            waitForCriterion(new WaitCriterion() {
              public String description() {
                return "Waiting for all members to send their initial Data";
              }

              public boolean done() {
                if (bean.listMemberObjectNames().length == 5) {// including locator 
                  return true;
                } else {
                  return false;
                }

              }

            }, MAX_WAIT, 500, true);
            for (ObjectName objectName : bean.listMemberObjectNames()) {
              NotificationHubTestListener listener = new NotificationHubTestListener();
              try {
                mbeanServer.addNotificationListener(objectName, listener, null,
                    null);
                notificationListenerMap.put(objectName, listener);
              } catch (InstanceNotFoundException e) {
                getLogWriter().error(e);
              }
            }
          }
        });

    // Check in all VMS

    for (VM vm : managedNodeList) {
      vm.invoke(new SerializableRunnable("Check Hub Listener num count") {

        public void run() {
          Cache cache = getCache();
          SystemManagementService service = (SystemManagementService) getManagementService();
          NotificationHub hub = service.getNotificationHub();
          Map<ObjectName, NotificationHubListener> listenerObjectMap = hub
              .getListenerObjectMap();
          assertEquals(1, listenerObjectMap.keySet().size());
          ObjectName memberMBeanName = MBeanJMXAdapter.getMemberMBeanName(cache
              .getDistributedSystem().getDistributedMember());

          NotificationHubListener listener = listenerObjectMap
              .get(memberMBeanName);

          /*
           * Counter of listener should be 2 . One for default Listener which is
           * added for each member mbean by distributed system mbean One for the
           * added listener in test
           */

          assertEquals(2, listener.getNumCounter());

          // Raise some notifications

          NotificationBroadcasterSupport memberLevelNotifEmitter = (MemberMBean) service
              .getMemberMXBean();

          String memberSource = MBeanJMXAdapter.getMemberNameOrId(cache
              .getDistributedSystem().getDistributedMember());

          // Only a dummy notification , no actual region is creates
          Notification notification = new Notification(
              ResourceNotification.REGION_CREATED, memberSource, SequenceNumber
                  .next(), System.currentTimeMillis(),
              ResourceNotification.REGION_CREATED_PREFIX + "/test");
          memberLevelNotifEmitter.sendNotification(notification);

        }
      });
    }

    managingNode.invoke(new SerializableRunnable(
        "Check notifications && Remove Listeners") {

      public void run() {

        waitForCriterion(new WaitCriterion() {
          public String description() {
            return "Waiting for all Notifications to reach the Managing Node";
          }

          public boolean done() {
            if (notifList.size() == 3) {
              return true;
            } else {
              return false;
            }

          }

        }, MAX_WAIT, 500, true);

        notifList.clear();

        Iterator<ObjectName> it = notificationListenerMap.keySet().iterator();
        while (it.hasNext()) {
          ObjectName objectName = it.next();
          NotificationListener listener = notificationListenerMap
              .get(objectName);
          try {
            mbeanServer.removeNotificationListener(objectName, listener);
          } catch (ListenerNotFoundException e) {
            getLogWriter().error(e);
          } catch (InstanceNotFoundException e) {
            getLogWriter().error(e);
          }
        }

      }
    });

    // Check in all VMS again

    for (VM vm : managedNodeList) {
      vm.invoke(new SerializableRunnable("Check Hub Listener num count Again") {

        public void run() {
          Cache cache = getCache();
          SystemManagementService service = (SystemManagementService) getManagementService();
          NotificationHub hub = service.getNotificationHub();
          Map<ObjectName, NotificationHubListener> listenerObjectMap = hub
              .getListenerObjectMap();

          assertEquals(1, listenerObjectMap.keySet().size());

          ObjectName memberMBeanName = MBeanJMXAdapter.getMemberMBeanName(cache
              .getDistributedSystem().getDistributedMember());

          NotificationHubListener listener = listenerObjectMap
              .get(memberMBeanName);

          /*
           * Counter of listener should be 2 . One for default Listener which is
           * added for each member mbean by distributed system mbean One for the
           * added listener in test
           */

          assertEquals(1, listener.getNumCounter());

        }
      });
    }

    // Now clean up the listeners && check if the listener are still there in
    // the VM

    for (VM vm : managedNodeList) {
      vm.invoke(new SerializableRunnable("Check Hub Listeners clean up") {

        public void run() {
          Cache cache = getCache();
          SystemManagementService service = (SystemManagementService) getManagementService();
          NotificationHub hub = service.getNotificationHub();
          hub.cleanUpListeners();
          assertEquals(0, hub.getListenerObjectMap().size());

          Iterator<ObjectName> it = notificationListenerMap.keySet().iterator();
          while (it.hasNext()) {
            ObjectName objectName = it.next();
            NotificationListener listener = notificationListenerMap
                .get(objectName);
            try {
              mbeanServer.removeNotificationListener(objectName, listener);
              fail("Found Listeners inspite of clearing them");
            } catch (ListenerNotFoundException e) {
              // Expected Exception Do nothing
            } catch (InstanceNotFoundException e) {
              getLogWriter().error(e);
            }
          }
        }
      });
    }

  }
  
  
  @SuppressWarnings("serial")
  public void checkAlertCount(VM vm1, final int expectedSevereAlertCount,
      final int expectedWarningAlertCount) throws Exception {
    {
      vm1.invoke(new SerializableCallable("Check Alert Count") {

        public Object call() throws Exception {
          final AlertNotifListener nt = AlertNotifListener.getInstance();
          waitForCriterion(new WaitCriterion() {
            public String description() {
              return "Waiting for all alerts to reach the Managing Node";
            }
            public boolean done() {
              if (expectedSevereAlertCount == nt.getseverAlertCount()
                  && expectedWarningAlertCount == nt.getWarnigAlertCount()) {
                return true;
              } else {
                return false;
              }

            }

          }, MAX_WAIT, 500, true);

          return null;
        }
      });

    }
  }
  


  
  @SuppressWarnings("serial")
  public void setAlertLevel(VM vm1, final String alertLevel) throws Exception {
    {
      vm1.invoke(new SerializableCallable("Set Alert level") {

        public Object call() throws Exception {
          GemFireCacheImpl cache = GemFireCacheImpl.getInstance();
          ManagementService service = getManagementService();
          DistributedSystemMXBean bean = service.getDistributedSystemMXBean();
          assertNotNull(bean);
          bean.changeAlertLevel(alertLevel);

          return null;
        }
      });

    }
    // Two Seconds for each member
    //sleeps to avoid false failures
    pause(1000 *10);
  }
  
  @SuppressWarnings("serial")
  public void warnLevelAlert(VM vm1) throws Exception {
    {
      vm1.invoke(new SerializableCallable("Warning level Alerts") {

        public Object call() throws Exception {
          final ExpectedException warnEx = addExpectedException("Warninglevel Alert Message");
          ((LogWriterImpl)InternalDistributedSystem.getLoggerI18n()).warning(
              "Warninglevel Alert Message ");
          warnEx.remove();
          return null;
        }
      });

    }
  }
  
  
  @SuppressWarnings("serial")
  public void resetAlertCounts(VM vm1) throws Exception {
    {
      vm1.invoke(new SerializableCallable("Reset Alert Ccount") {

        public Object call() throws Exception {
          AlertNotifListener nt =  AlertNotifListener.getInstance();
          nt.resetCount();
          return null;
        }
      });

    }
  }

  @SuppressWarnings("serial")
  public void severeLevelAlert(VM vm1) throws Exception {
    {
      vm1.invoke(new SerializableCallable("Severe Level Alert") {

        public Object call() throws Exception {
          // add expected exception strings         
          
          final ExpectedException severeEx = addExpectedException("Severelevel Alert Message");
          
          ((LogWriterImpl)InternalDistributedSystem.getLoggerI18n()).severe(
              "Severelevel Alert Message");
          severeEx.remove();
          return null;
        }
      });

    }
  }
  
  @SuppressWarnings("serial")
  public void addAlertListener(VM vm1) throws Exception {
    {
      vm1.invoke(new SerializableCallable("Add Alert Listener") {

        public Object call() throws Exception {
          GemFireCacheImpl cache = GemFireCacheImpl.getInstance();
          ManagementService service = getManagementService();
          DistributedSystemMXBean bean = service.getDistributedSystemMXBean();
          AlertNotifListener nt =  AlertNotifListener.getInstance();
          nt.resetCount();
          mbeanServer.addNotificationListener(MBeanJMXAdapter
              .getDistributedSystemName(), nt, null, null);

          return null;
        }
      });

    }
  }
  
  /**
   * Check aggregate related functions and attributes
   * @param vm1
   * @throws Exception
   */
  @SuppressWarnings("serial")
  public void checkAggregate(VM vm1) throws Exception {
    {
      vm1.invoke(new SerializableCallable("Chech Aggregate Attributes") {

        public Object call() throws Exception {
          GemFireCacheImpl cache = GemFireCacheImpl.getInstance();

          ManagementService service = getManagementService();

          final DistributedSystemMXBean bean = service.getDistributedSystemMXBean();
          assertNotNull(service.getDistributedSystemMXBean());
          
          waitForCriterion(new WaitCriterion() {
            public String description() {
              return "Waiting All members to intitialize DistributedSystemMBean expect 5 but found " + bean.getMemberCount();
            }
            public boolean done() {
              // including locator
              if (bean.getMemberCount() == 5) {
                return true;
              } else {
                return false;
              }

            }

          }, MAX_WAIT, 500, true);



          final Set<DistributedMember> otherMemberSet = cache
              .getDistributionManager().getOtherNormalDistributionManagerIds();
          Iterator<DistributedMember> memberIt = otherMemberSet.iterator();
          while (memberIt.hasNext()) {
            DistributedMember member = memberIt.next();
            getLogWriter().info(
                "JVM Metrics For Member " + member.getId() + ":"
                    + bean.showJVMMetrics(member.getId()));
            getLogWriter().info(
                "OS Metrics For Member " + member.getId() + ":"
                    + bean.showOSMetrics(member.getId()));
          }

          return null;
        }
      });

    }
  }

  @SuppressWarnings("serial")
  public void addNotificationListener(VM vm1) throws Exception {
    {
      vm1.invoke(new SerializableCallable("Add Notification Listener") {

        public Object call() throws Exception {
          GemFireCacheImpl cache = GemFireCacheImpl.getInstance();
          ManagementService service = getManagementService();
          DistributedSystemMXBean bean = service.getDistributedSystemMXBean();
          assertNotNull(bean);
          TestDistributedSystemNotif nt = new TestDistributedSystemNotif();
          mbeanServer.addNotificationListener(MBeanJMXAdapter
              .getDistributedSystemName(), nt, null, null);

          return null;
        }
      });

    }
  }

 

  @SuppressWarnings("serial")
  public void shutDownAll(VM vm1) throws Exception {
    {
      vm1.invoke(new SerializableCallable("Shut Down All") {

        public Object call() throws Exception {
          GemFireCacheImpl cache = GemFireCacheImpl.getInstance();
          ManagementService service = getManagementService();
          DistributedSystemMXBean bean = service.getDistributedSystemMXBean();
          assertNotNull(service.getDistributedSystemMXBean());
          bean.shutDownAllMembers();
          staticPause(2000);
          assertEquals(
              cache.getDistributedSystem().getAllOtherMembers().size(), 1);
          return null;
        }
      });

    }
  }
  

  
  @SuppressWarnings("serial")
  public void checkNavigationAPIs(VM vm1) throws Exception {
    {
      vm1.invoke(new SerializableCallable("Check Navigation APIS") {

        public Object call() throws Exception {
          GemFireCacheImpl cache = GemFireCacheImpl.getInstance();
          ManagementService service = getManagementService();
          final DistributedSystemMXBean bean = service.getDistributedSystemMXBean();
          
          assertNotNull(service.getDistributedSystemMXBean());
          
          waitForAllMembers(4);
          
          for(int i =0; i< bean.listMemberObjectNames().length ; i++){
            getLogWriter().info(
                "ObjectNames Of the Mmeber" + bean.listMemberObjectNames()[i] );
          }

          
          ObjectName thisMemberName = MBeanJMXAdapter
              .getMemberMBeanName(InternalDistributedSystem
                  .getConnectedInstance().getDistributedMember().getId());

          ObjectName memberName = bean
              .fetchMemberObjectName(InternalDistributedSystem
                  .getConnectedInstance().getDistributedMember().getId());
          assertEquals(thisMemberName, memberName);
          
          return null;
        }
      });

    }
  }


  /**
   * Notification handler
   * 
   * @author rishim
   * 
   */
  private static class TestDistributedSystemNotif implements
      NotificationListener {

    @Override
    public void handleNotification(Notification notification, Object handback) {
      assertNotNull(notification);      
    }

  }
  
  /**
   * Notification handler
   * 
   * @author rishim
   * 
   */
  private static class AlertNotifListener implements NotificationListener {
    
    private static AlertNotifListener listener = new AlertNotifListener();
    
    public static AlertNotifListener getInstance(){
      return listener;
    }

    private int warnigAlertCount = 0;

    private int severAlertCount = 0;

    @Override
    public void handleNotification(Notification notification, Object handback) {
      assertNotNull(notification);
      String notifStr = notification.toString();

      if (notifStr.contains("Warninglevel")) {
        ++warnigAlertCount;
      }
      if (notifStr.contains("Severelevel")) {
        ++severAlertCount;
      }
    }

    public void resetCount() {
      warnigAlertCount = 0;

      severAlertCount = 0;
    }

    public int getWarnigAlertCount() {
      return warnigAlertCount;
    }

    public int getseverAlertCount() {
      return severAlertCount;
    }

  }

}
