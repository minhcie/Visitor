package src.main.java;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

import org.apache.log4j.Logger;

public class DbUtils {

    private static final Logger log = Logger.getLogger(DbUtils.class.getName());

    private static final String DB_DRIVER = "com.microsoft.sqlserver.jdbc.SQLServerDriver";
	private static final String DB_CONNECTION = "jdbc:sqlserver://211-SQL2012:1433;" +
                                                "databaseName=PRONESTORDB;integratedSecurity=true;";

    public static Connection getDBConnection() {
        log.info("Establish DB Connection...");

        try {
            Class.forName(DB_DRIVER);
        }
        catch (ClassNotFoundException e) {
            log.error("SQLServer JDBC driver not found!");
            e.printStackTrace();
            return null;
        }

        log.info("SQLServer JDBC driver registered!");
        Connection connection = null;

        try {
            connection = DriverManager.getConnection(DB_CONNECTION);
        }
        catch (SQLException e) {
            log.error("SQLException while trying to establish DB connection!");
            e.printStackTrace();
            return null;
        }

        return connection;
    }

    public static void closeConnection(Connection conn) {
        log.info("Closing DB connection...");

        if (conn == null) {
            return;
        }

        try {
            conn.close();
        }
        catch (SQLException sqle) {
            log.error("Failed to close DB connection!");
        }
    }
}
