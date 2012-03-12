/*******************************************************************************
 * Copyright (c) 2005, 2012 ProSyst Software GmbH and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     ProSyst Software GmbH - initial API and implementation
 *******************************************************************************/
package org.tigris.mtoolkit.iagent.transport;

import java.io.IOException;

public interface Transport {

	TransportConnection createConnection(int port) throws IOException;
	
	TransportType getType();
	
	String getId();
	
	void dispose();
	
	boolean isDisposed();
}
