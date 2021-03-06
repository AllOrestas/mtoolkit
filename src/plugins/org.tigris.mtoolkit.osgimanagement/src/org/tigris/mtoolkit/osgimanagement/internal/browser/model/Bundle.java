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
package org.tigris.mtoolkit.osgimanagement.internal.browser.model;

import org.eclipse.core.runtime.Assert;
import org.eclipse.osgi.util.NLS;
import org.tigris.mtoolkit.iagent.IAgentErrors;
import org.tigris.mtoolkit.iagent.IAgentException;
import org.tigris.mtoolkit.iagent.RemoteBundle;
import org.tigris.mtoolkit.osgimanagement.internal.Messages;
import org.tigris.mtoolkit.osgimanagement.internal.browser.logic.BrowserErrorHandler;
import org.tigris.mtoolkit.osgimanagement.model.Framework;
import org.tigris.mtoolkit.osgimanagement.model.Model;

public class Bundle extends Model {

	private long id;
	private boolean needsUpdate;
	private int state;
	private String version;

	// bundle types
	public static final int BUNDLE_TYPE_FRAGMENT = RemoteBundle.BUNDLE_TYPE_FRAGMENT;
	public static final int BUNDLE_TYPE_EXTENSION = BUNDLE_TYPE_FRAGMENT + 1;
	// 0 for regular bundles
	private int type = -1;
	private final RemoteBundle rBundle;
	private String category;

	public Bundle(String name, RemoteBundle rBundle, int state, int type, String category, String version)
			throws IAgentException {
		super(name);
		Assert.isNotNull(rBundle);
		this.rBundle = rBundle;
		this.id = rBundle.getBundleId();
		// state is not get from rBundle to avoid unnecessary remote method
		// calls
		this.state = state;
		this.type = type;
		this.category = category;
		this.version = version;
		needsUpdate = true;
	}
	
	public Bundle(Bundle master) {
		super(master.getName(), master);
		rBundle = master.rBundle;
		id = master.getID();
		state = master.getState();
		type = master.getType();
		category = master.getCategory();
		needsUpdate = true;
		version = master.version;
	}

	public RemoteBundle getRemoteBundle() {
		return rBundle;
	}

	public long getID() {
		return id;
	}

	// Overrides method in Model class
	public boolean testAttribute(Object target, String name, String value) {
		if (!(target instanceof org.tigris.mtoolkit.osgimanagement.internal.browser.model.Bundle)) {
			return false;
		}
		FrameworkImpl framework = (FrameworkImpl) findFramework();
		if (framework == null)
			return false;
		if (!framework.isConnected()) {
			return false;
		}

		if (name.equalsIgnoreCase(BUNDLE_STATE_NAME)) {
			if (value.equalsIgnoreCase(BUNDLE_UNINSTALLED_VALUE)) {
				return state == org.osgi.framework.Bundle.UNINSTALLED;
			}
			if (value.equalsIgnoreCase(BUNDLE_INSTALLED_VALUE)) {
				return state == org.osgi.framework.Bundle.INSTALLED;
			}
			if (value.equalsIgnoreCase(BUNDLE_RESOLVED_VALUE)) {
				return state == org.osgi.framework.Bundle.RESOLVED;
			}
			if (value.equalsIgnoreCase(BUNDLE_STARTING_VALUE)) {

				return state == org.osgi.framework.Bundle.STARTING;
			}
			if (value.equalsIgnoreCase(BUNDLE_STOPPING_VALUE)) {
				return state == org.osgi.framework.Bundle.STOPPING;
			}
			if (value.equalsIgnoreCase(BUNDLE_ACTIVE_VALUE)) {
				return state == org.osgi.framework.Bundle.ACTIVE;
			}
		}

		return false;
	}

	public void removeAll() {
		Model[] categories = getChildren();
		if (categories != null) {
			for (int i = 0; i < categories.length; i++) {
				removeElement(categories[i]);
			}
		}
	}

	public boolean isShowID() {
		return findFramework() != null ? ((FrameworkImpl) findFramework()).isShowBundlesID() : false;
	}

	public boolean isShowVersion() {
		return findFramework() != null ? ((FrameworkImpl) findFramework()).isShowBundlesVersion() : false;
	}

	// this method will always ask the remote side, so it needs to throw
	// exception
	public void update() throws IAgentException {
		Framework framework = findFramework();
//		if (framework != null && framework.getConnector() != null) {
			try {
				refreshStateFromRemote();
				RemoteBundle rBundle = getRemoteBundle();
				version = rBundle.getVersion();
			} finally {
				// always update the viewers
				updateElement();
			}
//		}
	}

	public boolean isNeedUpdate() {
		return needsUpdate;
	}

	public int getState() {
		if (getMaster() != null) {
			return ((Bundle) getMaster()).getState();
		}
		return state;
	}

	public void setState(int i) {
		state = i;
	}

	public void refreshStateFromRemote() {
		try {
			setState(getRemoteBundle().getState());
		} catch (IAgentException e) {
			// ignore
			// TODO: Add logging of this exception in debug mode
		}
	}

	public String getVersion() throws IAgentException {
		if (version == null) {
			try {
				version = rBundle.getVersion();
			} catch (IAgentException e) {
				if (e.getErrorCode() != IAgentErrors.ERROR_BUNDLE_UNINSTALLED)
					// ignore uninstalled bundles
					throw e;
			}
		}
		version = version == null ? Messages.missing_version : version;
		return version;
	}

	public int getType() {
		return type;
	}

	public String getCategory() {
		return category;
	}

	public String getString() {
		StringBuffer buff = new StringBuffer();
		buff.append("ID: ").append(getID()).append(" Bundle name: ").append(getName()); //$NON-NLS-1$ //$NON-NLS-2$
		return buff.toString();
	}

	public String toString() {
		try {
			return name + " " + getVersion();
		} catch (IAgentException e) {
		}
		return name;
	}

	public String getLabel() {
		String label = getName();
		if (isShowID()) {
			label += " [" + String.valueOf(getID()) + "]";
		}
		if (isShowVersion()) {
			try {
				label += " [" + String.valueOf(getVersion()) + "]";
			} catch (IAgentException e) {
				BrowserErrorHandler.processError(e,
						NLS.bind(Messages.cant_get_bundle_version, String.valueOf(getID())), false);
			}
		}
		return label;
	}
}
