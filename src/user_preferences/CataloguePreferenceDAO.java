package user_preferences;

import java.sql.Connection;
import java.sql.SQLException;

import already_described_terms.Picklist;
import already_described_terms.PicklistDAO;
import catalogue.Catalogue;
import catalogue_object.Hierarchy;
import catalogue_object.Term;

public class CataloguePreferenceDAO extends PreferenceDAO {

	private Catalogue catalogue;

	/**
	 * Initialize the preference dao with the catalogue
	 * we want to communicate with.
	 * @param catalogue
	 */
	public CataloguePreferenceDAO( Catalogue catalogue ) {
		this.catalogue = catalogue;
	}

	/**
	 * Insert into the db the default preferences (used when a new catalogue is created)
	 */
	public void insertDefaultPreferences() {

		// first we remove all the preferences (avoid errors of duplicated keys)
		removeAll();

		// create the min search char preference
		insert( new CataloguePreference( CataloguePreference.minSearchChar, 
				PreferenceType.INTEGER, 3, true) );

		// create the max recent terms preference
		insert( new CataloguePreference( CataloguePreference.maxRecentTerms, 
				PreferenceType.INTEGER, 15, true) );

		// create the copy implicit facets preference
		insert( new CataloguePreference( CataloguePreference.copyImplicitFacets, 
				PreferenceType.BOOLEAN, false, true) );

		// create the business check rules enabled if the catalogue is the MTX
		if ( catalogue.isMTXCatalogue() )
			insert( new CataloguePreference( CataloguePreference.enableBusinessRules, 
					PreferenceType.BOOLEAN, true, true) );

		// create the favourite picklist preference
		insert( new CataloguePreference( CataloguePreference.currentPicklistKey, 
				PreferenceType.STRING, null, false) );

		// create the logging preference
		insert( new CataloguePreference( CataloguePreference.logging, 
				PreferenceType.BOOLEAN, false, true) );
	}

	/**
	 * Save in which hierarchy we are and on which 
	 * term we have the cursor for the current catalogue.
	 * @param hierarchy
	 * @param term
	 */
	public void saveMainPanelState ( Hierarchy hierarchy, Term term ) {

		int hierId = hierarchy != null ? hierarchy.getId() : -1;
		int termId = term != null ? term.getId() : -1;
		
		CataloguePreference hPref = new CataloguePreference( CataloguePreference.LAST_HIER_PREF, 
				PreferenceType.INTEGER, hierId, false);
		
		CataloguePreference tPref = new CataloguePreference( CataloguePreference.LAST_TERM_PREF, 
				PreferenceType.INTEGER, termId, false);

		if ( hierId == -1 )
			remove ( hPref );
		else
			insertUpdate( hPref );
		
		if ( termId == -1 )
			remove ( tPref );
		else
			insertUpdate( tPref );
	}
	
	/**
	 * Get the last selected term for the current catalogue
	 * @return
	 * @throws PreferenceNotFoundException
	 */
	public Hierarchy getLastHierarchy () throws PreferenceNotFoundException {
		
		CataloguePreference pref = getPreference( CataloguePreference.LAST_HIER_PREF );
		
		Hierarchy hierarchy;
		
		try {
			int id = Integer.valueOf( pref.getValue() );
			hierarchy = catalogue.getHierarchyById( id );
		} catch ( NumberFormatException e ) {
			e.printStackTrace();
			throw new PreferenceNotFoundException();
		}
		
		return hierarchy;
	}
	
	/**
	 * Get the last selected term for the current catalogue
	 * @return
	 * @throws PreferenceNotFoundException
	 */
	public Term getLastTerm () throws PreferenceNotFoundException {
		
		CataloguePreference pref = getPreference( CataloguePreference.LAST_TERM_PREF );
		
		Term term;
		
		try {
			int id = Integer.valueOf( pref.getValue() );
			term = catalogue.getTermById( id );
		} catch ( NumberFormatException e ) {
			e.printStackTrace();
			throw new PreferenceNotFoundException();
		}
		
		return term;
	}

	/**
	 * Set the favourite picklist for the preferences of the catalogue
	 * @param picklist
	 */
	public void setFavouritePicklist ( Picklist picklist ) {

		// create a preference for the currently selected picklist
		// in order to store it into the database
		CataloguePreference pref = new CataloguePreference ( 
				CataloguePreference.currentPicklistKey, PreferenceType.STRING,
				String.valueOf( picklist.getCode() ), false );

		// update the preference related to the picklist
		update( pref );
	}


	/**
	 * Check if a favourite picklist was set or not
	 * @return
	 */
	public boolean hasFavouritePicklist() {
		return getFavouritePicklist() != null;
	}

	/**
	 * Get the favourite picklist if there is one
	 * @return
	 */
	public Picklist getFavouritePicklist () {

		// get the preferred picklist code
		String code = getPreferenceValue ( CataloguePreference.currentPicklistKey );

		// return null if no favourite picklist was selected
		if ( code == null || code.equals("null") )
			return null;

		PicklistDAO pickDao = new PicklistDAO( catalogue );

		// get the picklist using the code
		Picklist picklist = pickDao.getPicklistFromCode( code );

		// set the picklist terms
		picklist.setTerms( pickDao.getPicklistTerms( picklist ) );

		return picklist;
	}

	@Override
	public Connection getConnection() throws SQLException {
		return catalogue.getConnection();
	}
}