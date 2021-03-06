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
package org.tigris.mtoolkit.dpeditor.wizard;

import java.io.File;
import java.io.IOException;

import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.Path;
import org.eclipse.jface.dialogs.MessageDialog;
import org.eclipse.jface.window.Window;
import org.eclipse.jface.wizard.WizardPage;
import org.eclipse.pde.internal.ui.PDEPlugin;
import org.eclipse.pde.internal.ui.util.FileExtensionFilter;
import org.eclipse.pde.internal.ui.util.FileValidator;
import org.eclipse.swt.SWT;
import org.eclipse.swt.events.KeyEvent;
import org.eclipse.swt.events.KeyListener;
import org.eclipse.swt.events.ModifyEvent;
import org.eclipse.swt.events.ModifyListener;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.events.SelectionListener;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.FileDialog;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.swt.widgets.Text;
import org.eclipse.ui.dialogs.ElementTreeSelectionDialog;
import org.eclipse.ui.model.WorkbenchContentProvider;
import org.eclipse.ui.model.WorkbenchLabelProvider;
import org.tigris.mtoolkit.dpeditor.util.DPPUtil;
import org.tigris.mtoolkit.dpeditor.util.ResourceManager;
import org.tigris.mtoolkit.util.DPPFile;

/**
 * Create the page of the <code>AntExportWizard</code>
 */
public class AntPage extends WizardPage implements ModifyListener, KeyListener,
		SelectionListener {

	/** Browse button label */
	public static final String BROWSE_BUTTON = "DPPEditor.BuildSection.Ant_Browse_Button_Text";

	/** Ant file name label */
	public static final String ANT_FILE_NAME_LABEL = "AntExportWizard.AntFileNameLabel"; //$NON-NLS-1$
	/** Ant property file name label */
	public static final String ANT_PROPERTIES_LABEL = "DPPEditor.BuildSection.Ant_Properties_File_Label"; //$NON-NLS-1$

	private static final String DPP_FILE_LABEL = "AntExportWizard.DPPFileNameLabel";

	/** Text field for the ant file name */
	private Text antFileText;
	/** The Browse button for the ant file name */
	private Button antFileButton;
	/**
	 * The string value of the ant file name loaded from the given deployment
	 * package file
	 */
	private String antFileTxt = "";
	/** The given deployment package file */
	private DPPFile dppFile;
	/** The project of the deployment package file */
	private IProject prj;

	private Text dppFileText;
	private String prevBuildLocation = "";

	private Button dppFileButton;

	/**
	 * Constructor of the AntPage. Creates the new wizard page with given name,
	 * title and description
	 * 
	 * @param pageName
	 *            the name of the page
	 * @param title
	 *            the title for this wizard page or <code>null</code> if none
	 * @param description
	 *            the description of the page
	 */
	protected AntPage(String pageName, String title, String description) {
		super(pageName, title, null);
		setDescription(description);
		setPageComplete(false);
	}

	/**
	 * Creates the fields of this page
	 * 
	 * @see org.eclipse.jface.dialogs.IDialogPage#createControl(org.eclipse.swt.widgets.Composite)
	 */
	public void createControl(Composite parent) {
		Composite composite = new Composite(parent, SWT.NONE);
		GridLayout layout = new GridLayout();
		layout.numColumns = 1;
		composite.setLayout(layout);
		createFields(composite);

		setControl(composite);
	}

	/**
	 * Creates label with the given parent and text.
	 * 
	 * @param parent
	 *            the parent of the label
	 * @param text
	 *            the text of the label
	 * @return created label
	 */
	public Label createLabel(Composite parent, String text) {
		Label label = new Label(parent, SWT.NONE);
		if (text != null) {
			label.setText(text);
		}
		return label;
	}

	/**
	 * Creates text field with the given parent, text and tool tip
	 * 
	 * @param parent
	 *            the parent of the text field
	 * @param label
	 *            the text of the label of the text field
	 * @param tooltip
	 *            the tool tip text of the label
	 * @return created text field
	 */
	protected Text createText(Composite parent, String label, String tooltip) {
		Label l = createLabel(parent, label);
		if (tooltip != null) {
			l.setToolTipText(tooltip);
		}
		Text text = new Text(parent, SWT.BORDER | SWT.SINGLE);
		text.setText("");
		GridData gd = new GridData(GridData.FILL_HORIZONTAL | GridData.VERTICAL_ALIGN_CENTER);
		gd.grabExcessHorizontalSpace = true;
		gd.horizontalSpan = 1;
		text.setLayoutData(gd);
		text.addKeyListener(this);
		text.addModifyListener(this);
		return text;
	}

	/**
	 * Creates the button with given parent, text and style.
	 * 
	 * @param parent
	 *            the parent of the button
	 * @param text
	 *            button's text
	 * @param style
	 *            button's style
	 * @return
	 */
	public Button createButton(Composite parent, String text, int style) {
		Button button = new Button(parent, style | SWT.NONE);
		if (text != null) {
			button.setText(text);
		}
		return button;
	}

	/**
	 * Creates all fields of this wizard page
	 * 
	 * @param container
	 *            the parent container
	 */
	private void createFields(Composite container) {
		container.setLayout(new GridLayout(3, false));
		Label selectDPPFilelbl = new Label(container, SWT.NONE);
		selectDPPFilelbl.setText(ResourceManager.getString("AntExportWizard.SelectDPPFileLabel"));
		GridData gd = new GridData(GridData.FILL_HORIZONTAL);
		gd.horizontalSpan = 3;
		selectDPPFilelbl.setLayoutData(gd);

		dppFileText = createText(container, ResourceManager.getString(DPP_FILE_LABEL, ""), ResourceManager.getString(DPP_FILE_LABEL, "")); //$NON-NLS-1$
		dppFileButton = createButton(container, ResourceManager.getString(BROWSE_BUTTON), SWT.PUSH);
		dppFileButton.addSelectionListener(this);

		Label exportDestinationLabel = new Label(container, SWT.NONE);
		gd = new GridData(GridData.FILL_HORIZONTAL);
		exportDestinationLabel.setText(ResourceManager.getString("AntExportWizard.ExportDestinationLabel"));
		exportDestinationLabel.setLayoutData(gd);
		gd.horizontalSpan = 3;

		antFileText = createText(container, ResourceManager.getString(ANT_FILE_NAME_LABEL, ""), ResourceManager.getString(ANT_FILE_NAME_LABEL, "")); //$NON-NLS-1$
		antFileButton = createButton(container, ResourceManager.getString(BROWSE_BUTTON), SWT.PUSH);
		antFileButton.addSelectionListener(this);
		if (dppFile != null) {
			try {
				IPath path = new Path(dppFile.getFile().getAbsolutePath());
				IFile[] files = ResourcesPlugin.getWorkspace().getRoot().findFilesForLocation(path);
				if (files.length != 0) {
					dppFileText.setText(files[0].getFullPath().toString());
				}
			} catch (Exception e) {
			}
		}
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see
	 * org.eclipse.swt.events.ModifyListener#modifyText(org.eclipse.swt.events
	 * .ModifyEvent)
	 */
	public void modifyText(ModifyEvent e) {
		if (e.getSource().equals(dppFileText)) {
			if (!handleDPPFileSelected()) {
				return;
			}
			String newAntBuildLocation = this.dppFile.getBuildInfo().getAntFileName();
			String antFileTextValue = antFileText.getText();
			try {
				String canonicalPathToAnt = new File(antFileTextValue).getCanonicalPath();
				if (canonicalPathToAnt.equals(prevBuildLocation) || antFileTextValue.equals("")) {
					antFileText.setText(newAntBuildLocation);
					prevBuildLocation = new File(newAntBuildLocation).getCanonicalPath();
				} else {
					prevBuildLocation = new File(newAntBuildLocation).getCanonicalPath();
				}
			} catch (IOException e1) {
				// nothing fatal will happen
			}
			File exportDestination = new File(antFileText.getText());
			boolean valid = isValidExportDestination(exportDestination);
			// refresh warning meassage
			if (valid) {
				setErrorMessage(null);
				setPageComplete(true);
			}
			return;
		}
		if (e.getSource().equals(antFileText)) {
			String value = antFileText.getText();
			File exportDestination = new File(value);
			if (!isValidExportDestination(exportDestination)) {
				return;
			} else {
				boolean handle = handleDPPFileSelected();
				// refresh warning message
				if (handle) {
					setErrorMessage(null);
					setPageComplete(true);
				}
			}
			return;
		}
	}

	private boolean isValidExportDestination(File exportDest) {
		String path = exportDest.getAbsolutePath();
		if (!path.endsWith(".xml")) {
			setPageComplete(false);
			String newMessage = ResourceManager.getString("BuildExportWizard.errorInvalidExportDestination");
			setErrorMessage(newMessage);
			return false;
		}
		setErrorMessage(null);
		return true;
	}

	private boolean handleDPPFileSelected() {
		// check if selected .dpp file is valid
		String dppFileRelativeLocation = dppFileText.getText();
		// relative location according to project
		if (dppFileRelativeLocation.equals("")) {
			setPageComplete(false);
			String newMessage = ResourceManager.getString("BuildExportWizard.errorDPPFileNotSpecified");
			setErrorMessage(newMessage);
			return false;
		}
		if (!dppFileRelativeLocation.endsWith(".dpp")) {
			setPageComplete(false);
			String newMessage = ResourceManager.getString("BuildExportWizard.errorNotDPPFile");
			setErrorMessage(newMessage);
			return false;
		}
		IPath path = new Path(dppFileRelativeLocation);
		boolean exist = ResourcesPlugin.getWorkspace().getRoot().exists(path);
		if (!exist) {
			setPageComplete(false);
			String newMessage = ResourceManager.getString("BuildExportWizard.errorFileNotFoundInWorkspace");
			setErrorMessage(newMessage);
			return false;
		}
		boolean isSync = ResourcesPlugin.getWorkspace().getRoot().getFile(path).isSynchronized(0);
		if (!isSync) {
			setPageComplete(false);
			String newMessage = ResourceManager.getString("BuildExportWizard.errorNotSynchronized");
			setErrorMessage(newMessage);
			return false;
		}
		try {
			IFile f = ResourcesPlugin.getWorkspace().getRoot().getFile(path);
			String absPath = f.getLocation().toOSString();
			String project = f.getProject().getLocation().toOSString();
			this.dppFile = new DPPFile(new File(absPath), project);
		} catch (IOException e) {
			e.printStackTrace();
			return false;
		}
		return true;

	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see
	 * org.eclipse.swt.events.KeyListener#keyPressed(org.eclipse.swt.events.
	 * KeyEvent )
	 */
	public void keyPressed(KeyEvent e) {
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see
	 * org.eclipse.swt.events.KeyListener#keyReleased(org.eclipse.swt.events
	 * .KeyEvent )
	 */
	public void keyReleased(KeyEvent e) {
		keyReleaseOccured(e);
	}

	/**
	 * If escape is pressed restore old value of the text field
	 * 
	 * @param e
	 *            the key event
	 */
	protected void keyReleaseOccured(KeyEvent e) {
		if (e.character == '\u001b') { // Escape character
			if (e.getSource() instanceof Text) {
				Text text = (Text) e.getSource();
				String value = text.getText();
				text.setText(value); // restore old
			}
		}
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see
	 * org.eclipse.swt.events.SelectionListener#widgetDefaultSelected(org.eclipse
	 * .swt.events.SelectionEvent)
	 */
	public void widgetDefaultSelected(SelectionEvent e) {
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see
	 * org.eclipse.swt.events.SelectionListener#widgetSelected(org.eclipse.swt
	 * .events.SelectionEvent)
	 */
	public void widgetSelected(SelectionEvent e) {
		Button but = (Button) e.getSource();
		if (but == dppFileButton) {
			handleDPPBrowseButtonPressed();
		}
		if (but == antFileButton) {
			handleAntBrowseButtonPressed();
		}
	}

	private void handleDPPBrowseButtonPressed() {
		ElementTreeSelectionDialog dialog = new ElementTreeSelectionDialog(getShell(), new WorkbenchLabelProvider(), new WorkbenchContentProvider());

		dialog.setValidator(new FileValidator());
		dialog.setAllowMultiple(false);
		dialog.setTitle(ResourceManager.getString("AntExportWizard.DPPFileSelectDialog_title"));
		dialog.setMessage(ResourceManager.getString("AntExportWizard.DPPFileSelectDialog_message"));
		dialog.addFilter(new FileExtensionFilter("dpp")); //$NON-NLS-1$
		dialog.setInput(PDEPlugin.getWorkspace().getRoot());
		setInitialSelection(dialog);

		if (dialog.open() == Window.OK) {

			IFile file = (IFile) dialog.getFirstResult();
			String value = file.getFullPath().toString();
			if (value != null) {
				dppFileText.setText(value);
			}

		}
	}

	private void setInitialSelection(ElementTreeSelectionDialog dialog) {

		if (dppFileText == null) {
			return;
		}
		String value = dppFileText.getText();
		if (value.equals("")) {
			return;
		}
		IPath path = new Path(value);
		if (!ResourcesPlugin.getWorkspace().getRoot().exists(path)) {
			return;
		}
		if (path.segmentCount() == 1) { // project
			dialog.setInitialSelection(ResourcesPlugin.getWorkspace().getRoot().getProject(value));
		}
		if (path.segmentCount() >= 2) {
			IFile f = ResourcesPlugin.getWorkspace().getRoot().getFile(path);
			if (f.exists()) { // file
				dialog.setInitialSelection(ResourcesPlugin.getWorkspace().getRoot().getFile(path));
			} else { // folder
				dialog.setInitialSelection(ResourcesPlugin.getWorkspace().getRoot().getFolder(path));
			}
		}
	}

	private void handleAntBrowseButtonPressed() {
		FileDialog dialog = new FileDialog(getContainer().getShell(), SWT.SAVE);
		dialog.setFilterExtensions(new String[] { "*.xml" }); //$NON-NLS-1$
		String filterPath = antFileText.getText();
		dialog.setFilterPath(filterPath);
		String selectedFileName = dialog.open();
		if (selectedFileName != null) {
			DPPUtil.fileDialogLastSelection = selectedFileName;
			antFileText.setText(selectedFileName);
		}
	}

	/**
	 * Sets the given deployment package file.
	 * 
	 * @param dppFile
	 *            the deployment package file
	 * @param project
	 *            the project of the dpp file
	 */
	public void setDPPFileProject(DPPFile dppFile, IProject project) {
		this.dppFile = dppFile;
		this.prj = project;
	}

	/**
	 * Returns the deployment package file.
	 */
	public DPPFile getDPPFile() {
		return dppFile;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see org.eclipse.jface.wizard.WizardPage#getShell()
	 */
	public Shell getShell() {
		Display display = Display.getCurrent();
		if (display == null) {
			display = Display.getDefault();
		}
		Shell shell = display.getActiveShell();
		return shell;
	}

	/**
	 * Sets the chosen ant file in the BuildInfo of the given deployment package
	 * file.
	 * 
	 * @see Wizard#performFinish
	 */
	public boolean performFinish() {
		try {
			String customBuildPath = antFileText.getText();
			customBuildPath = customBuildPath.trim();
			File file = new File(customBuildPath);
			if (file.exists()) {
				StringBuffer sb = new StringBuffer();
				sb.append(ResourceManager.getString("AntExportWizard.errorFileAlreadyExist1"));
				sb.append(" ");
				sb.append(customBuildPath);
				sb.append(" ");
				sb.append(ResourceManager.getString("AntExportWizard.errorFileAlreadyExist2"));
				boolean replaceFile = MessageDialog.openQuestion(null, ResourceManager.getString("AntExportWizard.ConfirmReplace"), sb.toString());
				if (replaceFile) {
					dppFile.getBuildInfo().setAntFileName(customBuildPath);
					return true;
				}
				return false;
			}
			dppFile.getBuildInfo().setAntFileName(customBuildPath);
			return true;
		} catch (NullPointerException e) {
			return false;
		}
	}

}