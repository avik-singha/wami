package edu.mit.csail.sls.wami.database;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Vector;

import javax.servlet.ServletContext;

import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import edu.mit.csail.sls.wami.WamiConfig;

/**
 * An extension of WamiConfig for including database information in the
 * config.xml file, and some convenience methods for easily creating
 * connections.
 * 
 * @author alexgru
 * 
 */
public class DatabaseConfig extends WamiConfig {
	private Map<String, Connection> connections = new HashMap<String, Connection>();

	public DatabaseConfig() {
		super();
	}

	public DatabaseConfig(ServletContext sc) {
		super(sc);
	}

	public static DatabaseConfig getConfiguration(ServletContext sc) {
		return (DatabaseConfig) WamiConfig.getConfiguration(sc);
	}

	/**
	 * You should always access the database connection through this method. The
	 * database is lazily created it as needed. This assumes you only have one
	 * database connected.
	 * 
	 * @return a (lazily created) connection to the DB.
	 */
	public synchronized Connection getSingletonDatabaseConnection() {
		return getSingletonDatabaseConnection(getOnlyDatabaseTag());
	}

	public synchronized Connection getSingletonDatabaseConnection(
			String databaseTag) {
		Connection dbConn = connections.get(databaseTag);

		if (dbConn == null) {
			try {
				dbConn = DatabaseConfig.createConnection(this, databaseTag);
				connections.put(databaseTag, dbConn);
			} catch (ClassNotFoundException e1) {
				System.out
						.println("WARNING: Database connection NOT returned!");
				e1.printStackTrace();
				// throw new RuntimeException(e1);
			} catch (SQLException e1) {
				System.out
						.println("WARNING: Database connection NOT returned!");
				// e1.printStackTrace();
				// hrow new RuntimeException(e1);
			}
		}

		return dbConn;
	}

	public synchronized void closeAllDBConnections() {
		Object[] keys = connections.keySet().toArray();

		for (int i = 0; i < keys.length; i++) {
			String key = (String) keys[i];
			closeConnection(key);
		}
	}

	public synchronized void closeDBConnection() {
		closeConnection(getOnlyDatabaseTag());
	}

	public synchronized void closeConnection(String databaseTag) {
		Connection dbConn = connections.get(databaseTag);

		try {
			if (dbConn != null) {
				dbConn.close();
			}
		} catch (SQLException e) {
			e.printStackTrace();
		} finally {
			dbConn = null;
		}

		connections.remove(databaseTag);
	}

	/*
	 * Database Configuration Accessors
	 */

	/**
	 * Returns whether or not database is being used at all. Note that a
	 * database must be specified if any of the log-in features are used.
	 */
	public boolean getUseDatabase() {
		Element databaseE = getUniqueDescendant(getDocument().getFirstChild(),
				"database");
		return (databaseE != null);
	}

	/**
	 * Information necessary to log-in to the database.
	 */
	public String getDatabaseUser() {
		return getDatabaseUser(getOnlyDatabaseTag());
	}

	/**
	 * Information necessary to log-in to the database.
	 */
	public String getDatabasePassword() {
		return getDatabasePassword(getOnlyDatabaseTag());
	}

	/**
	 * Information necessary to log-in to the database.
	 */
	public String getDatabaseUser(String databaseTag) {
		Element databaseE = getDatabaseElementByTag(databaseTag);
		return databaseE.getAttribute("user");
	}

	/**
	 * Information necessary to log-in to the database.
	 */
	public String getDatabasePassword(String databaseTag) {
		Element databaseE = getDatabaseElementByTag(databaseTag);
		return databaseE.getAttribute("password");
	}

	/**
	 * Information necessary to log-in to the database.
	 */
	public String getDatabaseDriverName() {
		return getDatabaseDriverName(getOnlyDatabaseTag());
	}

	/**
	 * Information necessary to log-in to the database.
	 */
	public String getDatabaseDriverName(String databaseTag) {
		Element databaseE = getDatabaseElementByTag(databaseTag);
		return databaseE.getAttribute("driver");
	}

	public String getDatabaseURI() {
		return getDatabaseURI(getOnlyDatabaseTag());
	}

	/**
	 * Information necessary to log-in to the database.
	 */
	public String getDatabaseURI(String databaseTag) {
		Element databaseE = getDatabaseElementByTag(databaseTag);

		String dbName = databaseE.getAttribute("name");
		String dbHost = databaseE.getAttribute("host");
		String dbPort = databaseE.getAttribute("port");
		String dbPrefix = databaseE.getAttribute("urlPrefix");

		String dbURI = String.format("%s://%s:%s/%s", dbPrefix, dbHost, dbPort,
				dbName);

		return dbURI;
	}

	/**
	 * Open a connection to the SQL server
	 */
	public static Connection createConnection(DatabaseConfig dbConfig,
			String databaseTag, boolean readOnly) throws ClassNotFoundException {
		// only try to make a database connection
		if (dbConfig.getUseDatabase()) {
			Class.forName(dbConfig.getDatabaseDriverName(databaseTag));
			System.out.println("Connecting to: "
					+ dbConfig.getDatabaseURI(databaseTag) + " with user "
					+ dbConfig.getDatabaseUser(databaseTag) + " and pwrd "
					+ dbConfig.getDatabasePassword(databaseTag));

			try {
				Connection c = DriverManager.getConnection(dbConfig
						.getDatabaseURI(databaseTag), dbConfig
						.getDatabaseUser(databaseTag), dbConfig
						.getDatabasePassword(databaseTag));
				c.setReadOnly(readOnly);
				return c;
			} catch (SQLException e) {
				System.out
						.println("WARNING: Database connection requested but not available, null returned for the connection, make sure to handle this case robustly.");
				e.printStackTrace();
				return null;
			}
		} else {
			System.out
					.println("DBUTILS: No DB specification set, so *not* creating a database connection");
			return null;
		}
	}

	/**
	 * Open a connection to the SQL server
	 */
	public static Connection createConnection(DatabaseConfig dbConfig,
			String databaseTag) throws ClassNotFoundException, SQLException {
		return createConnection(dbConfig, databaseTag, false);
	}

	/**
	 * Deprecated in favor of creating connections with a database tag. This
	 * will still work assuming that there is just one database specified in the
	 * config file (regardless of its tag).
	 */
	public static Connection createConnection(DatabaseConfig dbConfig)
			throws ClassNotFoundException {
		return createConnection(dbConfig, dbConfig.getOnlyDatabaseTag(), false);
	}

	protected String getOnlyDatabaseTag() {
		List<Element> elements = getDatabaseElements();

		if (elements.size() != 1) {
			String message = "Code assumes single database, however, "
					+ elements.size() + " were found on config.xml.";

			if (elements.size() > 1) {
				message += "  If you would like to use multiple databases, please "
						+ "ensure that all relevant code makes use of database tags.";
			}
			throw new RuntimeException(message);
		}

		return elements.get(0).getAttribute("tag");
	}

	private List<Element> getDatabaseElements() {
		NodeList databaseNodes = getDocument().getElementsByTagName("database");
		List<Element> elements = new Vector<Element>();

		for (int i = 0; i < databaseNodes.getLength(); i++) {
			elements.add((Element) databaseNodes.item(i));
		}
		return elements;
	}

	private Element getDatabaseElementByTag(String databaseTag) {
		List<Element> elements = getDatabaseElements();

		for (Element databaseElement : elements) {
			String tag = databaseElement.getAttribute("tag");
			if (tag.equals(databaseTag)) {
				return databaseElement;
			}
		}

		throw new RuntimeException("No database with tag '" + databaseTag
				+ "' found in the config.xml.");
	}
}
