/*******************************************************************************
 * Copyright (c) 2005, 2009 ProSyst Software GmbH and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     ProSyst Software GmbH - initial API and implementation
 *******************************************************************************/
package org.tigris.mtoolkit.iagent.internal;

import java.util.Dictionary;

import org.tigris.mtoolkit.iagent.IAgentException;
import org.tigris.mtoolkit.iagent.RemoteBundle;
import org.tigris.mtoolkit.iagent.RemoteService;
import org.tigris.mtoolkit.iagent.internal.connection.PMPConnection;
import org.tigris.mtoolkit.iagent.internal.utils.DebugUtils;
import org.tigris.mtoolkit.iagent.pmp.RemoteObject;



public class RemoteServiceImpl implements RemoteService {

  private ServiceManagerImpl manager;
  private Long serviceId;
  private RemoteBundle registeredBundle;
  private String[] objectClass;

  private boolean stale;

  private static final String SERVICE_ID = "service.id";
  private static final String OBJECTCLASS = "objectClass";
  private PMPConnection initialPmpConnection;

  public RemoteServiceImpl(ServiceManagerImpl manager, Dictionary props) {
    this.manager = manager;
    this.serviceId = (Long) props.get(SERVICE_ID);
    this.objectClass = (String[]) props.get(OBJECTCLASS);
    log("[Constructor] >>> Create new RemoteService: service.id=" + serviceId + "; objectClass=[" + DebugUtils.convertForDebug(objectClass) + "]");
    try {
      this.initialPmpConnection = manager.getConnection(false);
      log("[Constructor] initial connection: " + initialPmpConnection);
    } catch (IAgentException e) {
      // unreachable
    }
  }

  public RemoteBundle getBundle() throws IAgentException {
    if (stale){
      log("[getBundle] service reference is stale");
      return null;
    }
    if (!(initialPmpConnection == manager.getConnection())) {
      if (DebugUtils.DEBUG)
        log("[getBundle] RemoteService object references other connection: " + manager.getConnection());
      throw new IllegalStateException("RemoteService is not synchronized with the device");
    }
    if (registeredBundle == null) {
      log("[getBundle] Querying for registered bundle...");
      Long bid = (Long) Utils.callRemoteMethod(getServiceAdmin(), Utils.GET_BUNDLE_METHOD, new Object[] { serviceId });
      if (bid.longValue() == -1) {
        stale = true;
        log("[getBundle] service reference is stale");
        return null;
      } else {
        registeredBundle = new RemoteBundleImpl(getDeploymentManager(), bid);
      }
    } else if (isStale()) {
      log("[getBundle] service reference is stale");
      return null;
    }
    log("[getBundle] Registered bundle: " + registeredBundle.getBundleId());
    return registeredBundle;
  }

  private DeploymentManagerImpl getDeploymentManager() throws IAgentException {
    return (DeploymentManagerImpl) manager.getDeviceConnector().getDeploymentManager();
  }

  private RemoteObject getServiceAdmin() throws IAgentException {
    return manager.getServiceAdmin(manager.getConnection());
  }

  private void checkState() throws IllegalStateException, IAgentException {
    if (stale){
      log("[checkState] Service reference is stale");
      throw new IllegalStateException("The service was unregistered");
    }
  }

  public String[] getObjectClass() throws IAgentException {
    return objectClass;
  }

  public Dictionary getProperties() throws IAgentException {
    log("[getProperties] >>>");
    checkState();
    if (!(initialPmpConnection == manager.getConnection())) {
      if (DebugUtils.DEBUG)
        log("[getProperties] RemoteService object references other connection: " + manager.getConnection());
      throw new IllegalStateException("RemoteService is not synchronized with the device");
    }
    Dictionary properties = (Dictionary) Utils.callRemoteMethod(getServiceAdmin(), Utils.GET_PROPERTIES_METHOD, new Object[] { serviceId });
    if (properties == null) {
      log("[getProperties] service reference is stale");
      stale = true;
      checkState();
    }
    log("[getProperties] props: " + DebugUtils.convertForDebug(properties));
    return properties;
  }

  public long getServiceId() {
    return serviceId.longValue();
  }

  public RemoteBundle[] getUsingBundles() throws IAgentException {
    log("[getUsingBundles] >>>");
    if (stale) {
      log("[getUsingBundles] service reference is stale");
      return new RemoteBundle[0];
    }
    if (!(initialPmpConnection == manager.getConnection())) {
      if (DebugUtils.DEBUG)
        log("[getUsingBundles] RemoteService object references other connection: " + manager.getConnection());
      throw new IllegalStateException("RemoteService is not synchronized with the device");
    }
    long[] bids = (long[]) Utils.callRemoteMethod(getServiceAdmin(), Utils.GET_USING_BUNDLES_METHOD, new Object[] { serviceId });
    if (bids == null) {
      log("[getUsingBundles] service reference is stale");
      stale = true;
      return new RemoteBundle[0];
    }
    log("[getUsingBundles] Using bundles: " + DebugUtils.convertForDebug(bids));
    RemoteBundle[] bundles = new RemoteBundle[bids.length];
    DeploymentManagerImpl deploymentManager = getDeploymentManager();
    for (int i = 0; i < bids.length; i++) {
      bundles[i] = new RemoteBundleImpl(deploymentManager, new Long(bids[i]));
    }
    return bundles;
  }

  public boolean isStale() throws IAgentException {
    log("[isStale] >>>");
    if (!(initialPmpConnection == manager.getConnection())) {
      log("[isStale] service reference was created with different connection: " + manager.getConnection());
      return false;
    }
    if (!stale) {
      log("[isStale] Quering remote status...");
      Boolean isStale = (Boolean) Utils.callRemoteMethod(getServiceAdmin(), Utils.IS_SERVICE_STALE_METHOD, new Object[] { serviceId });
      stale = isStale.booleanValue();
    }
    log("[isStale] result: " + stale);
    return stale;
  }

  public String toString() {
    return "RemoteService@" + Integer.toHexString(System.identityHashCode(this)) + "[" + serviceId + "][" + DebugUtils.convertForDebug(objectClass) + "]";
  }

  private final void log(String message) {
    DebugUtils.log(this.toString(), message);
  }

}