package dcf_pending_action;

import java.io.File;
import java.io.IOException;

import javax.xml.parsers.ParserConfigurationException;
import javax.xml.soap.SOAPException;
import javax.xml.transform.TransformerException;

import org.apache.log4j.LogManager;
import org.apache.log4j.Logger;
import org.eclipse.swt.widgets.Event;
import org.eclipse.swt.widgets.Listener;
import org.xml.sax.SAXException;

import catalogue.Catalogue;
import dcf_log.DcfLog;
import dcf_log.DcfLogParser;
import dcf_log.DcfResponse;
import dcf_log.LogDownloader;
import dcf_manager.Dcf.DcfType;
import dcf_user.User;
import dcf_webservice.PublishLevel;
import dcf_webservice.ReserveLevel;
import import_catalogue.CatalogueImporterThread;
import progress_bar.FormProgressBar;

/**
 * Class which models a generic pending action, that is, a
 * web request which needs to be completed. In fact,
 * a {@link #PendingAction(Catalogue, String, String, Priority)}
 * contains only the code of the log document related to the
 * web request. This log needs to be retrieved and only at this
 * point we can get the dcf response and close the pending action.
 * When the pending action is finished, call {@link #terminate()} to
 * close it.
 * @author avonva
 *
 */
public abstract class PendingAction {
	
	private static final Logger LOGGER = LogManager.getLogger(PendingAction.class);
	
	// id in the database
	private int id;
	
	// the catalogue related to this action
	private Catalogue catalogue;
	
	// the reserve log code that we need to retrieve
	private String logCode;
	private DcfLog parsedLog;

	// the username of the user who made the reserve action
	private String username;
	
	// pending action note
	private String note;
	
	/**
	 * Additional data used for the pending action
	 * we can have {@link ReserveLevel} or {@link PublishLevel}
	 * for example. We need a generic field to generalize
	 * the concept of pending action and to use a single table
	 * for all the pending action types.
	 */
	private String data;

	// the priority of the pending reserve
	private Priority priority;
	
	// on which dcf we make the action
	private DcfType dcfType;

	// the status of the pending reserve
	private PendingActionStatus status;

	//  the dcf response to the pending reserve
	private DcfResponse response;

	// listener called for events
	private PendingActionListener listener;

	private FormProgressBar progressBar;

	/**
	 * Initialize a pending request
	 * @param catalogue the catalogue related to this pending action
	 * @param logCode the reserve log code
	 * @param username the name of the user who made the reserve
	 * @param priority the action priority
	 */
	public PendingAction( Catalogue catalogue, String logCode, 
			String username, String note, Priority priority, DcfType dcfType ) {
		this.catalogue = catalogue;
		this.logCode = logCode;
		this.username = username;
		this.note = note;
		this.priority = priority;
		this.dcfType = dcfType;
		this.data = "";
	}

	/**
	 * Start the pending action for the current catalogue
	 * @param notifyStart if true the user will be notified
	 * that the pending action is started
	 * @throws SOAPException 
	 */
	public void start ( boolean notifyStart ) throws SOAPException {
		
		LOGGER.info( "Starting " + this );
		
		// we are starting the process
		if ( notifyStart )
			setStatus( PendingActionStatus.STARTED );
		
		// send the pending reserve request
		// to the dcf
		send();
	}
	
	/**
	 * Send the pending action to the dcf in order to retrieve its log.
	 * Note that this call is a blocking call. We are stuck here until
	 * we find the log.
	 * @throws SOAPException 
	 */
	private void send() throws SOAPException {
		
		// update the status
		setStatus( PendingActionStatus.SENDING );

		File log = getLog();

		// if no log in high priority => the available time is finished
		if ( log == null && priority == Priority.HIGH ) {
			
			// manage this case
			manageBusyStatus();
			
			// notify the user that the dcf was found busy
			// and that the pending reserve was queued
			// we call it after having forced the editing
			// in order to refresh correctly the UI
			setStatus( PendingActionStatus.QUEUED );

			LOGGER.info( "Downgrading to LOW priority " + this );
			
			// downgrade the pending reserve priority
			downgradePriority();

			// restart the process with low priority
			log = getLog();
		}

		// here the log was retrieved for sure
		// since if it was LOW priority we have
		// found the log, if it was HIGH priority
		// or it was found within the allowed time
		// or it was downgraded to LOW priority and
		// then it was found since we found it for
		// sure if we are in LOW priority
		
		this.parsedLog = parseLog ( log );
		
		processLog ( parsedLog );
		
		// get the response of the dcf looking
		// into the log document
		this.response = extractLogResponse( parsedLog );
		
		// notify that a response was received
		if ( listener != null )
			listener.responseReceived ( this, response );
		
		// process the dcf response
		processResponse ( response );
	}
	
	/**
	 * Parse the log file and return it
	 * @param log
	 * @return
	 */
	private DcfLog parseLog ( File log ) {
		
		// analyze the log to get the result
		DcfLogParser parser = new DcfLogParser ();
		DcfLog parsedLog = null;

		try {
			parsedLog = parser.parse( log );
		} catch (SAXException | IOException e) {
			e.printStackTrace();
			LOGGER.error("Cannot parse dcf log in file=" + log, e);
		}
		
		return parsedLog;
	}
	
	/**
	 * Terminate the pending action. Call this method
	 * when all the actions are finished.
	 */
	public synchronized void terminate() {
		
		LOGGER.info( "Terminating " + this );
		
		PendingActionDAO prDao = new PendingActionDAO();
		prDao.remove( this );

		// set the status as completed
		setStatus( PendingActionStatus.COMPLETED );
		
		// update the catalogue status
		catalogue.setRequestingAction( false );
	}
	
	/**
	 * Downgrade the priority of the pending action
	 * and save this change into the database
	 */
	private synchronized void downgradePriority() {
		
		// downgrade the pending reserve to LOW priority
		priority = Priority.LOW;
		
		PendingActionDAO prDao = new PendingActionDAO();
		prDao.update( this );
	}
	
	/**
	 * Import a the last internal version of the catalogue
	 * if there is one. If no newer internal versions are
	 * found, no action is performed and the {@code doneListener}
	 * is called.
	 * If a new internal version is found the import process
	 * starts and the the status of the 
	 * pending reserve is set to 
	 * {@link PendingActionStatus#OLD_VERSION}. In this case,
	 * only when the import process is finished the 
	 * {@code doneListener} is called.
	 * 
	 * Note that if a new internal version is found, the
	 * {@link #catalogue} of the pending reserve will be
	 * updated with the new internal version.
	 * @param doneListener listener which specify the actions
	 * needed when we finish the method.
	 * @return true if we already had the last internal version, 
	 * false otherwise
	 * @throws SOAPException 
	 */
	public CatalogueImporterThread importLastVersion ( final Listener doneListener ) 
			throws SOAPException {
		
		try {
			
			Catalogue catalogue = getCatalogue();
			
			final NewCatalogueInternalVersion lastVersion = 
					catalogue.getLastInternalVersion();
			
			// if no version is found => we have the last one
			if ( lastVersion == null ) {
				
				// call the listener since we have finished
				doneListener.handleEvent( new Event() );
				return null;
			}
			
			// update the status of the pending reserve
			setStatus( PendingActionStatus.IMPORTING_LAST_VERSION );
			
			LOGGER.info ( this + ": This is not the last version "
					+ "of the catalogue, importing " + lastVersion );

			
			// reset progress bar to avoid disposed shell
			progressBar.reset();
			
			// and import the last internal version
			// and when the process is finished
			// reserve the new version of the catalogue
			lastVersion.setProgressBar( progressBar );
			
			progressBar.open();
			
			// import the new version
			CatalogueImporterThread thread = 
					lastVersion.importNewCatalogueVersion( new Listener() {

				@Override
				public void handleEvent(Event arg0) {

					// update the pending reserve catalogue
					setCatalogue( lastVersion.getNewCatalogue() );

					doneListener.handleEvent( arg0 );
				}
			} );
			
			return thread;
			
		} catch (IOException | TransformerException | 
				ParserConfigurationException | SAXException e) {
			e.printStackTrace();
			
			LOGGER.error("Cannot import last version of the catalogue=" + catalogue, e);
			
			setStatus( PendingActionStatus.ERROR );
			return null;
		}
	}
	
	/**
	 * Download the log using the pending action. The speed
	 * behavior of the process is defined by {@link #priority}
	 * @return the log related to the reserve operation if it
	 * was found in the available time, otherwise null
	 * @throws SOAPException 
	 */
	private File getLog() throws SOAPException {
		
		File log = null;
		
		// 12 attempts, one every 10 seconds -> 2 minutes total
		int maxAttempts = 12;
		long interAttemptsTime = 10000; 
		
		// set inter attempts time according to the priority
		switch ( priority ) {
		case HIGH:
			interAttemptsTime = 10000;  // 10 seconds
			break;
		case LOW:
			interAttemptsTime = 300000; // 5 minutes
			break;
		default:
			break;
		}

		// initialize a log downloader with the current priority
		LogDownloader logDown = new LogDownloader(User.getInstance());
		
		// get the log
		log = logDown.getLog(logCode, interAttemptsTime, maxAttempts);
		
		return log;
	}
	
	
	/**
	 * Set the status of the pending action
	 * @param status
	 */
	protected void setStatus( PendingActionStatus status ) {
		
		this.status = status;
		
		// notify that the pending action status changed
		listener.statusChanged( this, status );
	}
	
	/**
	 * Get the current status
	 * @return
	 */
	public PendingActionStatus getStatus() {
		return status;
	}
	
	/**
	 * Get on which dcf we have made the
	 * request for this pending action.
	 * @return
	 */
	public DcfType getDcfType() {
		return dcfType;
	}
	
	/**
	 * Check if this pending action was made
	 * by the {@code user}
	 * @param user
	 * @return true if it was the {@code user} who made the pending
	 * action, false otherwise
	 */
	public boolean madeBy ( User user ) {
		return user.getUsername().equals( username );
	}
	
	@Override
	public String toString() {
		return "PendingAction: id=" + id + ",priority=" + priority 
				+ ",logCode=" + logCode + ",type=" + getType();
	}
	
	/**
	 * Set the id of the pending action object
	 * @param id
	 */
	public void setId(int id) {
		this.id = id;
	}
	
	/**
	 * Get the id in the db of the pending action object
	 * if it was set
	 * @return
	 */
	public int getId() {
		return id;
	}
	
	/**
	 * Update the catalogue with the new version of it
	 * @param newVersion
	 */
	protected synchronized void setCatalogue( Catalogue catalogue ) {
		
		// update flag
		this.catalogue.setRequestingAction( false );
		catalogue.setRequestingAction( true );
		
		this.catalogue = catalogue;
		
		// update the pending action also in the database
		PendingActionDAO prDao = new PendingActionDAO();
		prDao.update( this );
	}
	
	/**
	 * Get the current catalogue
	 * @return
	 */
	public Catalogue getCatalogue() {
		return catalogue;
	}
	
	/**
	 * Get the log code of the pending action request
	 * @return
	 */
	public String getLogCode() {
		return logCode;
	}
	
	/**
	 * Get the parsed log if it was created.
	 * @return
	 */
	public DcfLog getParsedLog() {
		return parsedLog;
	}
	
	/**
	 * Get the username of the user who made 
	 * the pending action
	 * @return
	 */
	public String getUsername() {
		return username;
	}
	
	/**
	 * Get the pending action note
	 * @return
	 */
	public String getNote() {
		return note;
	}
	
	/**
	 * Get the state of the pending action
	 * @return
	 */
	public Priority getPriority() {
		return priority;
	}
	
	/**
	 * Get the dcf response of this pending action
	 * Note that you should call {@link #send()} before
	 * to get a real result.
	 * @return the dcf response
	 */
	public DcfResponse getResponse() {
		return response;
	}
	
	/**
	 * Set the listener which is used to listen
	 * several pending action events
	 * @param listener
	 */
	public void setListener(PendingActionListener listener) {
		this.listener = listener;
	}
	
	
	/**
	 * Enumerator to identify the pending
	 * reserve as new or as retry.
	 * If new we try to get the log each 10 seconds for 2 minutes
	 * if retry we try to get the log each 5 minutes until
	 * we find the log
	 * @author avonva
	 *
	 */
	public enum Priority {
		HIGH,
		LOW
	}
	
	/**
	 * Set the data which need to be included
	 * into the pending action
	 * @param data
	 */
	public void setData(String data) {
		this.data = data;
	}
	
	/**
	 * Get the pending action data
	 * @return
	 */
	public String getData() {
		return data;
	}
	
	/**
	 * Set the progress bar which is used for possible
	 * import actions related to new catalogue versions
	 * @param progressBar
	 */
	public void setProgressBar(FormProgressBar progressBar) {
		this.progressBar = progressBar;
	}
	
	/**
	 * Get the progress bar
	 * @return
	 */
	public FormProgressBar getProgressBar() {
		return progressBar;
	}
	
	public PendingActionListener getListener() {
		return listener;
	}
	
	/**
	 * Get the soap action type. This field should
	 * be a unique code to define which web action
	 * is this pending action. For example, if we
	 * have a {@link PendingReserve} we could use as constant
	 * type the string "RESERVE".
	 * @return
	 */
	public abstract String getType();
	
	/**
	 * Actions performed if the dcf is busy and
	 * the pending action was put in queue.
	 * Note that this method is called only once
	 * in the life cycle of the pending action!
	 */
	public abstract void manageBusyStatus ();
	
	/**
	 * Process the log content
	 * @param log
	 */
	public abstract void processLog( DcfLog log );
	
	/**
	 * Extract the dcf response from the retrieved log document
	 * @param log
	 * @return
	 */
	public abstract DcfResponse extractLogResponse ( DcfLog log );
	
	/**
	 * Process the dcf response related to this pending action
	 * @param response the retrieved response
	 * @throws SOAPException 
	 */
	public abstract void processResponse ( DcfResponse response ) throws SOAPException;
}
