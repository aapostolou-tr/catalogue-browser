package term_type;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collection;

import catalogue.Catalogue;
import catalogue_browser_dao.CatalogueEntityDAO;

/**
 * Dao to communicate with the term type table
 * @author avonva
 *
 */
public class TermTypeDAO implements CatalogueEntityDAO<TermType> {

	private Catalogue catalogue;

	/**
	 * Initialize the term type dao with the catalogue
	 * we want to communicate with.
	 * @param catalogue
	 */
	public TermTypeDAO( Catalogue catalogue ) {
		this.catalogue = catalogue;
	}

	/**
	 * Get all the term types from the catalogue db
	 * @return
	 */
	public ArrayList< TermType > getAll () {

		ArrayList< TermType >  values = new ArrayList<>();

		Connection con = null;

		// get all the distinct values of the attribute and return them
		// we get the term type in the order they were inserted in
		String query = "select * from APP.TERM_TYPE";

		try {

			// open the db connection to the main db
			con = catalogue.getConnection();

			// prepare the statement and its parameters
			PreparedStatement stmt = con.prepareStatement( query );
			stmt.clearParameters();

			// get the results
			ResultSet rs = stmt.executeQuery();

			// get all the detail levels
			while ( rs.next() ) {
				TermType tt = getByResultSet( rs );
				values.add( tt );
			}

			rs.close();
			stmt.close();
			con.close();

		} catch ( SQLException e ) {
			e.printStackTrace();
		}

		return values;
	}

	/**
	 * Insert a batch of term types
	 * @param termTypes
	 */
	public synchronized void insert ( Collection<TermType> termTypes ) {

		Connection con;

		String query = "insert into APP.TERM_TYPE (TERM_TYPE_CODE, TERM_TYPE_LABEL) values (?, ?)";

		try {

			con = catalogue.getConnection();

			PreparedStatement stmt = con.prepareStatement( query );

			// for each term type
			for ( TermType type : termTypes ) {

				// clear the parameters
				stmt.clearParameters();

				// add the term type code
				stmt.setString( 1, type.getCode() );

				// add the term type description
				stmt.setString( 2, type.getLabel() );

				// add the batch
				stmt.addBatch();
			}

			// insert all the term types into the database
			stmt.executeBatch();

			// close the connection
			stmt.close();
			con.close();

		} catch ( SQLException e ) {
			e.printStackTrace();
		}
	}

	@Override
	public int insert(TermType object) {
		// TODO Auto-generated method stub
		return -1;
	}

	@Override
	public boolean remove(TermType object) {
		// TODO Auto-generated method stub
		return true;
	}

	@Override
	public boolean update(TermType object) {
		// TODO Auto-generated method stub
		return true;
	}

	@Override
	public TermType getById(int id) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public TermType getByResultSet(ResultSet rs) throws SQLException {

		int id = rs.getInt( "TERM_TYPE_ID" );
		String code = rs.getString( "TERM_TYPE_CODE" );
		String name = rs.getString( "TERM_TYPE_LABEL" );

		TermType termType = new TermType( id, code, name );

		return termType;
	}
}
