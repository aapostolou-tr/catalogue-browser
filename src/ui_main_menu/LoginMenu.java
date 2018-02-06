package ui_main_menu;

import org.eclipse.swt.SWT;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.events.SelectionListener;
import org.eclipse.swt.widgets.Menu;
import org.eclipse.swt.widgets.MenuItem;
import org.eclipse.swt.widgets.Shell;

import dcf_user.User;
import messages.Messages;
import ui_main_panel.FormDCFLogin;

/**
 * Login button of the main panel
 * @author avonva
 *
 */
public class LoginMenu implements MainMenuItem {

	public static final int LOGIN_MI = 0;

	private MenuListener listener;

	private MainMenu mainMenu;
	private MenuItem loginMI;
	private Shell shell;

	/**
	 * Login button in the main menu
	 * @param mainMenu
	 * @param menu
	 */
	public LoginMenu( MainMenu mainMenu, Menu menu ) {
		this.mainMenu = mainMenu;
		this.shell = mainMenu.getShell();
		create( menu );
	}

	/**
	 * Set the listener to the login button
	 * @param listener
	 */
	public void setListener(MenuListener listener) {
		this.listener = listener;
	}
	
	/**
	 * Create the dcf login button
	 * @param menu
	 * @return
	 */
	public MenuItem create ( Menu menu ) {

		loginMI = new MenuItem ( menu, SWT.NONE );
		loginMI.setText( Messages.getString( "BrowserMenu.LoginMenuName" ) );

		loginMI.setEnabled(false);

		loginMI.addSelectionListener( new SelectionListener() {

			@Override
			public void widgetSelected(SelectionEvent e) {

				FormDCFLogin login = new FormDCFLogin( shell, 
						Messages.getString( "BrowserMenu.DCFLoginWindowTitle" ) );

				login.display();

				// if wrong credentials return
				if ( !login.isValid() )
					return;
				
				LoginActions.startLoggedThreads(shell, null);
				
				// disable the login button
				loginMI.setEnabled(false);

				// disable tools menu until we have
				// obtained the user access level
				// (avoid concurrence editing in db)
				mainMenu.tools.setEnabled( false );
				
				listener.buttonPressed(loginMI, LoginMenu.LOGIN_MI, null);
			}

			@Override
			public void widgetDefaultSelected(SelectionEvent e) {}
		});

		return loginMI;
	}

	public void refresh() {
		
		if (!loginMI.isDisposed())
			loginMI.setEnabled(!User.getInstance().isReauth() && !User.getInstance().isLoggedIn());
	};
}
