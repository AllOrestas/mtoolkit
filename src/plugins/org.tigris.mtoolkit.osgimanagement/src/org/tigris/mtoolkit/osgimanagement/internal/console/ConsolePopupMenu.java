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
package org.tigris.mtoolkit.osgimanagement.internal.console;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;

import org.eclipse.jface.action.MenuManager;
import org.eclipse.jface.action.Separator;
import org.eclipse.swt.SWT;
import org.eclipse.swt.dnd.Clipboard;
import org.eclipse.swt.dnd.TextTransfer;
import org.eclipse.swt.events.MenuEvent;
import org.eclipse.swt.events.MenuListener;
import org.eclipse.swt.widgets.FileDialog;
import org.eclipse.swt.widgets.Menu;
import org.eclipse.swt.widgets.MessageBox;
import org.eclipse.ui.IActionBars;
import org.eclipse.ui.texteditor.ITextEditorActionConstants;
import org.tigris.mtoolkit.osgimanagement.internal.FrameworkPlugin;
import org.tigris.mtoolkit.osgimanagement.internal.Messages;

public class ConsolePopupMenu implements MenuListener, MenuItemListener {

  private Menu mainMenu;
  
  private MenuManager popupMenu;
  private MenuManager editMenu;
  
  private MenuItem stateType;
  private MenuItem cutItem;
  private MenuItem copyItem;
  private MenuItem pasteItem;
  private MenuItem deleteItem;
  private MenuItem clearItem;
  private MenuItem saveasItem;
  private MenuItem printItem;
  private Console console;
  private Clipboard cb;
  private FileDialog fdialog;
  private MessageBox mbox;
  public ConsolePopupMenu(Console cons, IActionBars actionBars) {
    console = cons;
    popupMenu = new MenuManager(Messages.ConsoleM_N);
    editMenu = new MenuManager(Messages.edit_action_N);
    popupMenu.add(editMenu);
    stateType = new MenuItem(Messages.edit_action_mark_N, MenuItem.AS_CHECK_BOX);
    editMenu.add(stateType);
    editMenu.add(new Separator());
    cutItem = new MenuItem(Messages.edit_action_cut_N);
    editMenu.add(cutItem);
    copyItem = new MenuItem(Messages.edit_action_copy_N);
    editMenu.add(copyItem);
    pasteItem = new MenuItem(Messages.edit_action_paste_N);
    editMenu.add(pasteItem);
    deleteItem = new MenuItem(Messages.edit_action_delete_N);
    editMenu.add(deleteItem);
    clearItem = new MenuItem(Messages.clear_action_N);
    popupMenu.add(clearItem);
    saveasItem = new MenuItem(Messages.file_action_saveas_N);
    popupMenu.add(saveasItem);
    printItem = new MenuItem(Messages.file_action_print_N);
    popupMenu.add(printItem);
    popupMenu.add(new Separator());
    mainMenu = popupMenu.createContextMenu(cons);
    mainMenu.addMenuListener(this);
    cb = new Clipboard(console.getDisplay());
  	actionBars.setGlobalActionHandler(ITextEditorActionConstants.CUT, cutItem);
  	actionBars.setGlobalActionHandler(ITextEditorActionConstants.COPY, copyItem);
  	actionBars.setGlobalActionHandler(ITextEditorActionConstants.PASTE, pasteItem);
	  actionBars.setGlobalActionHandler(ITextEditorActionConstants.DELETE, deleteItem);
	  actionBars.setGlobalActionHandler(ITextEditorActionConstants.PRINT, printItem);
	  stateType.addMenuItemListener(this);
    cutItem.addMenuItemListener(this);
    copyItem.addMenuItemListener(this);
    pasteItem.addMenuItemListener(this);
    deleteItem.addMenuItemListener(this);
    clearItem.addMenuItemListener(this);
    saveasItem.addMenuItemListener(this);
    printItem.addMenuItemListener(this);
  }
  public Menu getMenu() {
    return mainMenu;
  }
  public void menuSelected(MenuItem mi) {
    if (mi == stateType) {
      console.switchState();
    } else if (mi == cutItem) {
      console.getStyledText().cut();
    } else if (mi == copyItem) {
      console.getStyledText().copy();
    } else if (mi == pasteItem) {
      console.getStyledText().paste();
    } else if (mi == deleteItem) {
      console.getStyledText().insert(""); //$NON-NLS-1$
    } else if (mi == saveasItem) {
      saveasAction();
    } else if (mi == clearItem) {
      console.clear();
    } else if (mi == printItem) {
      print();
    }
  }
  void saveasAction() {
    String consoleText = console.getText();
    if (consoleText.trim().length() == 0) {
      return;
    }
    initFileDialog();
    fdialog.setFileName("console.txt"); //$NON-NLS-1$
    fdialog.setFilterPath(FrameworkPlugin.fileDialogLastSelection);
    String file = fdialog.open();
    if (file != null) {
      File theFile = new File(file);
      FrameworkPlugin.fileDialogLastSelection = theFile.getAbsolutePath();
      if (theFile.exists()) {
        initMessageBox();
        if (mbox.open() != SWT.YES) return;
      }
      try {
        FileOutputStream fos = new FileOutputStream(file);
        fos.write(consoleText.getBytes());
        fos.close();
      } catch (IOException _) {}
    }
  }
  private void initFileDialog() {
    if (fdialog == null) {
      fdialog = new FileDialog(console.getShell(), SWT.SAVE);
      fdialog.setFilterExtensions(new String[] {"*.txt", "*.*"}); //$NON-NLS-1$ //$NON-NLS-2$
      fdialog.setFilterNames(new String[] {Messages.text_files_filter_label, Messages.all_files_filter_label});
    }
  }
  private void initMessageBox() {
    if (mbox == null) {
      mbox = new MessageBox(console.getShell(), SWT.ICON_QUESTION + SWT.YES | SWT.NO);
      mbox.setMessage(Messages.overwriting_file_confirmation_text);
      mbox.setText(Messages.question_dialog_title);
    }
  }
  private void print() {
    org.tigris.mtoolkit.common.PluginUtilities.print(console.getShell(), console.getStyledText());
  }
  public void menuHidden(MenuEvent e) {
  }
  public void menuShown(MenuEvent e) {
    boolean eState = console.hasSelection() && console.getState() == Console.COMMAND_MODE;
		String text = console.getText();
		boolean isEmpty = (text.length() == 0);
    cutItem.setEnabled(eState);
    deleteItem.setEnabled(eState);
    copyItem.setEnabled(console.hasSelection());
    Object cbcontents = cb.getContents(TextTransfer.getInstance());
    pasteItem.setEnabled(console.isEditable() && 
    										 console.getState() == Console.COMMAND_MODE &&
                         cbcontents != null &&
                         cbcontents instanceof String);
		clearItem.setEnabled(!isEmpty);
		saveasItem.setEnabled(!isEmpty);
		printItem.setEnabled(!isEmpty);
  }
}