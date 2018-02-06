package dcf_pending_action;

import java.io.File;
import java.io.IOException;

import javax.xml.soap.SOAPException;

import org.apache.log4j.LogManager;
import org.apache.log4j.Logger;

import catalogue.Catalogue;
import dcf_log.DcfLog;
import dcf_log.DcfResponse;
import dcf_manager.Dcf;
import dcf_manager.Dcf.DcfType;
import sas_remote_procedures.XmlUpdateFile;
import sas_remote_procedures.XmlUpdateFileDAO;

public class PendingXmlDownload extends PendingAction {

	private static final Logger LOGGER = LogManager.getLogger(PendingXmlDownload.class);
	
	public static final String TYPE = "DOWNLOAD_XML_UPDATES";
	
	/**
	 * Create a pending xml download action.
	 * @param catalogue the catalogue related to this action
	 * @param username the user who made the action
	 * @param dcfType the dcf on which we made the action
	 */
	public PendingXmlDownload( Catalogue catalogue, String username, DcfType dcfType ) {
		super( catalogue, "", username, "", Priority.HIGH, dcfType );
	}
	
	/**
	 * Create a pending upload data object and insert it into the db
	 * @param catalogue
	 * @param username
	 * @param dcfType
	 * @return
	 */
	public static PendingXmlDownload addPendingDownload ( Catalogue catalogue, 
			String username, DcfType dcfType ) {
		
		// create the pending download action
		PendingXmlDownload pa = new PendingXmlDownload( catalogue, 
				username, dcfType);
		
		// insert in db
		PendingActionDAO prDao = new PendingActionDAO();
		int id = prDao.insert( pa );
		
		pa.setId( id );
		
		return pa;
	}

	@Override
	public void start(boolean notifyStart) throws SOAPException {
		
		if ( notifyStart )
			setStatus( PendingActionStatus.STARTED );
		
		// set that the file does not need the upload
		// anymore, since it was uploaded (we are only
		// waiting for the response)
		XmlUpdateFileDAO xmlDao = new XmlUpdateFileDAO();
		
		final XmlUpdateFile file = xmlDao.getById( getCatalogue().getId() );
		
		if ( file == null ) {
			LOGGER.error ( "No xml filename was found for " + getCatalogue() );
			return;
		}
		
		File xmlFile = null;
		
		// download the file
		try {
			xmlFile = file.downloadXml( 5000 );
		} catch (IOException e) {
			e.printStackTrace();
			LOGGER.error("Cannot download xml file from server, file=" + file, e);
			return;
		}
		
		// create upload data request and terminate
		Dcf dcf = new Dcf();
		dcf.uploadDataBG( getCatalogue(), xmlFile, getListener() );
		
		// finish this action
		terminate();
		
		// we are still requesting an action
		// the terminate call set this to false therefore
		// we reset to true since we are uploading the data
		getCatalogue().setRequestingAction( true );
	}
	
	@Override
	public String getType() {
		return TYPE;
	}

	@Override
	public void manageBusyStatus() {}

	@Override
	public void processLog(DcfLog log) {}

	@Override
	public DcfResponse extractLogResponse(DcfLog log) {
		return null;
	}

	@Override
	public void processResponse(DcfResponse response) throws SOAPException {}
}
