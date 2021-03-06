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
package org.tigris.mtoolkit.common;

public class PluginExporter {

	public static IPluginExporter getInstance() {
		if (PluginExporter_34.isCompatible())
			return new PluginExporter_34();
		if (PluginExporter_35.isCompatible())
			return new PluginExporter_35();
		else
			return null;
	}

	
}
