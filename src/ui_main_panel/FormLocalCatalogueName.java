package ui_main_panel;

import org.eclipse.swt.widgets.Shell;

import messages.Messages;
import ui_general_graphics.DialogSingleText;

/**
 * Form used to ask the name of a new local catalogue.
 * @author avonva
 *
 */
public class FormLocalCatalogueName {

	private static final String WINDOW_CODE = "FormLocalCatalogueName";
	private DialogSingleText dialog;
	
	/**
	 * Initialize the form
	 * @param shell
	 */
	public FormLocalCatalogueName( Shell shell ) {

		dialog = new DialogSingleText( shell, 3 );
		dialog.setTitle( Messages.getString( "NewLocalCatDialogTitle" ) );
		dialog.setMessage( Messages.getString( "NewLocalCatDialogMessage" ) );
		dialog.setWindowCode( WINDOW_CODE );
	}
	
	/**
	 * Open the dialog
	 * @return
	 */
	public String open() {
		return dialog.open();
	}
}
