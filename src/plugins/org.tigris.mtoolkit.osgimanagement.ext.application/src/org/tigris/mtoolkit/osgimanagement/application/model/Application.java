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
package org.tigris.mtoolkit.osgimanagement.application.model;

import org.tigris.mtoolkit.iagent.RemoteApplication;
import org.tigris.mtoolkit.osgimanagement.browser.model.Model;

public class Application extends Model {
	
	private RemoteApplication remoteApplication;
	
	public Application(String name, RemoteApplication remoteApplication) {
		super(name);
		this.remoteApplication = remoteApplication;
	}
	
	public RemoteApplication getRemoteApplication() {
		return remoteApplication;
	}
}
