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
package org.tigris.mtoolkit.common.installation;

import java.io.IOException;
import java.io.InputStream;

import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;

/**
 * InstallationItem is the item that can be installed to
 * {@link InstallationTarget}. Such items are provided by
 * {@link InstallationItemProvider} and can be used from
 * {@link InstallationItemProcessor}
 */
public interface InstallationItem {
	/**
	 * Prepares the item for installation if it is not yet prepared. Otherwise
	 * does nothing.
	 * 
	 * @param monitor
	 *            the progress monitor for displaying current progress
	 * @return status, describing the result of prepare operation
	 */
	public IStatus prepare(IProgressMonitor monitor);

	/**
	 * Returns open {@link InputStream} which provides the installation data.
	 * The client is responsible for closing the stream when finished.
	 * 
	 * @return the output stream
	 */
	public InputStream getInputStream() throws IOException;

	/**
	 * Returns the MIME type of this item.
	 * 
	 * @return the MIME type
	 */
	public String getMimeType();

	/**
	 * Returns the name of this item.
	 * 
	 * @return the item name
	 */
	public String getName();
}