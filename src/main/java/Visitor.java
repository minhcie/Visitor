package src.main.java;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

import com.sforce.soap.partner.Connector;
import com.sforce.soap.partner.DeleteResult;
import com.sforce.soap.partner.PartnerConnection;
import com.sforce.soap.partner.QueryResult;
import com.sforce.soap.partner.SaveResult;
import com.sforce.soap.partner.Error;
import com.sforce.soap.partner.sobject.SObject;
import com.sforce.ws.ConnectionException;
import com.sforce.ws.ConnectorConfig;

import org.apache.log4j.Logger;

public class Visitor {
    private static final Logger log = Logger.getLogger(Visitor.class.getName());
    private static final String USERNAME = "mtran@211sandiego.org";
    private static final String PASSWORD = "m1nh@211KsmlvVA4mvtI6YwzKZOLjbKF9";
    private static PartnerConnection connection;

    public static void main(String[] args) {
        // Establish DB connection in order to look up taxonomy name.
        Connection sqlConn = DbUtils.getDBConnection();
        if (sqlConn == null) {
            System.exit(-1);
        }

    	ConnectorConfig config = new ConnectorConfig();
    	config.setUsername(USERNAME);
    	config.setPassword(PASSWORD);
    	//config.setTraceMessage(true);

        try {
            // Establish Salesforce web service connection.
    		connection = Connector.newConnection(config);

    		// @debug.
    		log.info("Auth EndPoint: " + config.getAuthEndpoint());
    		log.info("Service EndPoint: " + config.getServiceEndpoint());
    		log.info("Username: " + config.getUsername());
    		log.info("SessionId: " + config.getSessionId());

            // Query general contact record type id.
            String defAcctRecordTypeId = queryRecordType("Company/ Organization");
            String contactRecordTypeId = queryRecordType("General Contact");

            // Search for visitors with non-blank email.
            StringBuilder sb = new StringBuilder();
            sb.append("SELECT p.participant_id, p.firstname, p.lastname, ");
            sb.append("p.MobilePhone, b.custom_field, p.company ");
            sb.append("FROM pronestordb.dbo.badge b ");
            sb.append("INNER JOIN pronestordb.dbo.participant p ON p.participant_id = b.participant_id ");
            sb.append("WHERE (b.custom_field IS NOT NULL AND LEN(LTRIM(RTRIM(b.custom_field))) > 0)");

            // New contacts.
            List<ContactInfo> contacts = new ArrayList<ContactInfo>();

            Statement stmt = sqlConn.createStatement();
            ResultSet rs = stmt.executeQuery(sb.toString());
            while (rs.next()) {
                ContactInfo contact = new ContactInfo();
                contact.firstName = rs.getString("firstname");
                contact.lastName = rs.getString("lastname");
                contact.phone = rs.getString("MobilePhone");
                contact.email = rs.getString("custom_field");
                contact.company = rs.getString("company");

                // Check to see if contact has already existed.
                if (contact.email != null && contact.email.trim().length() > 0) {
                    SObject so = queryContact(connection, contact.email);
                    if (so != null) {
                        log.info("Contact: " + contact.email + " already existed!");
                    }
                    else {
                        if (contact.lastName == null || contact.lastName.trim().length() <= 0) {
                            log.info("New contact with missing last name");
                        }
                        else if (contact.company == null || contact.company.trim().length() <= 0) {
                            log.info("New contact with missing company name");
                        }
                        else {
                            log.info("Adding new contact: " + contact.email + "...");
                            contacts.add(contact);
                        }
                    }
                }
            }

            // Clean up.
            rs.close();
            stmt.close();

            // Add new contacts in Salesforce.
            int n = contacts.size();
            if (n > 0) {
                SObject[] records = new SObject[n];
                for (int i = 0; i < n; i++) {
                    ContactInfo ci = contacts.get(i);

                    // Check to see if organization is created?
                    String acctId = queryAccount(connection, ci.company);
                    if (acctId == null) {
                        acctId = createAccount(connection, defAcctRecordTypeId,
                                               ci.company);
                    }

                    // Create contact.
                    if (acctId != null) {
                        createContact(connection, acctId, contactRecordTypeId, ci);
                    }
                }
            }
        }
    	catch (ConnectionException e) {
            log.error(e.getMessage());
            e.printStackTrace();
    	}
        catch (Exception e) {
            log.error(e.getMessage());
            e.printStackTrace();
        }
        finally {
            DbUtils.closeConnection(sqlConn);
        }
    }

    private static String queryRecordType(String name) {
    	log.info("Querying for " + name + " record type...");
        String recordTypeId = null;
    	try {
            // Query for record type name.
    		String sql = "SELECT Id, Name FROM RecordType " +
                         "WHERE Name = '" + name + "' ";
    		QueryResult queryResults = connection.query(sql);
    		if (queryResults.getSize() > 0) {
    			for (SObject s: queryResults.getRecords()) {
                    recordTypeId = s.getId();
    			}
    		}
    	}
    	catch (Exception e) {
    		e.printStackTrace();
    	}

        // @debug.
        if (recordTypeId != null) {
			log.info("Record Type Id: " + recordTypeId);
        }
        else {
            log.info(name + " record type not found!");
        }

        return recordTypeId;
    }

    private static String queryAccount(PartnerConnection conn, String name) {
        String accountId = null;
        try {
    		StringBuilder sb = new StringBuilder();
    		sb.append("SELECT Id, Name ");
    		sb.append("FROM Account ");
    		sb.append("WHERE Name = '" + SqlString.encode(name) + "'");

    		QueryResult queryResults = conn.query(sb.toString());
    		if (queryResults.getSize() > 0) {
                SObject so = (SObject)queryResults.getRecords()[0];
                accountId = so.getId();
    		}
    	}
    	catch (Exception e) {
    		e.printStackTrace();
    	}
        return accountId;
    }

    private static SObject queryContact(PartnerConnection conn, String email) {
        SObject result = null;
        try {
    		StringBuilder sb = new StringBuilder();
    		sb.append("SELECT Id, Email, AccountId ");
    		sb.append("FROM Contact ");
    		sb.append("WHERE AccountId != NULL ");
    		sb.append("  AND Email = '" + SqlString.encode(email) + "'");

    		QueryResult queryResults = conn.query(sb.toString());
    		if (queryResults.getSize() > 0) {
                result = queryResults.getRecords()[0];
    		}
    	}
    	catch (Exception e) {
    		e.printStackTrace();
    	}
        return result;
    }

    private static String createAccount(PartnerConnection conn, String defAcctRecordTypeId,
                                        String name) {
        log.info("Creating new account name: " + name);
        String acctId = null;
    	try {
            SObject[] records = new SObject[1];

		    SObject so = new SObject();
			so.setType("Account");
	        so.setField("RecordTypeId", defAcctRecordTypeId);
			so.setField("Name", name);
            records[0] = so;

            // Create new agency.
            SaveResult[] saveResults = connection.create(records);

    		// Check the returned results for any errors.
    		for (int i = 0; i < saveResults.length; i++) {
    			if (saveResults[i].isSuccess()) {
    				acctId = saveResults[i].getId();
    				log.info(i + ". Successfully created record - Id: " + acctId);
    			}
    			else {
    				Error[] errors = saveResults[i].getErrors();
    				for (int j=0; j< errors.length; j++) {
    					log.info("ERROR creating record: " + errors[j].getMessage());
    				}
    			}
    		}
    	}
    	catch (Exception e) {
    		e.printStackTrace();
    	}
        return acctId;
    }

    private static String createContact(PartnerConnection conn, String acctId,
                                        String contactRecordTypeId, ContactInfo ci) {
        log.info("Creating new contact name: " + ci.firstName + " " + ci.lastName);
        String contactId = null;
    	try {
            SObject[] records = new SObject[1];

		    SObject so = new SObject();
            so.setType("Contact");
	        so.setField("AccountId", acctId);
	        so.setField("RecordTypeId", contactRecordTypeId);;
            so.setField("FirstName", ci.firstName);
            so.setField("LastName", ci.lastName);
            so.setField("MobilePhone", ci.phone);
            so.setField("Email", ci.email);
            records[0] = so;

            // Create new agency.
            SaveResult[] saveResults = connection.create(records);

    		// Check the returned results for any errors.
    		for (int i = 0; i < saveResults.length; i++) {
    			if (saveResults[i].isSuccess()) {
    				contactId = saveResults[i].getId();
    				log.info(i + ". Successfully created record - Id: " + contactId);
    			}
    			else {
    				Error[] errors = saveResults[i].getErrors();
    				for (int j=0; j< errors.length; j++) {
    					log.info("ERROR creating record: " + errors[j].getMessage());
    				}
    			}
    		}
    	}
    	catch (Exception e) {
    		e.printStackTrace();
    	}
        return contactId;
    }
}
