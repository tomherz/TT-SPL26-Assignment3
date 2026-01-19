package bgu.spl.net.impl.data;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ConcurrentHashMap;

public class Database {
	private final ConcurrentHashMap<Integer, String> activeLogins;
	private final String sqlHost;
	private final int sqlPort;

	private Database() {
		activeLogins = new ConcurrentHashMap<>();
		// SQL server connection details
		this.sqlHost = "127.0.0.1";
		this.sqlPort = 7778;
	}

	private static class Instance {
		static final Database instance = new Database();
	}

	public static Database getInstance() {
		return Instance.instance;
	}

	/**
	 * Execute SQL query and return result
	 * 
	 * @param sql SQL query string
	 * @return Result string from SQL server
	 */
	private String executeSQL(String sql) {
		// Connect to SQL server
		try (Socket socket = new Socket(sqlHost, sqlPort);
			BufferedOutputStream out = new BufferedOutputStream(socket.getOutputStream());
			BufferedInputStream in = new BufferedInputStream(socket.getInputStream())) {

			// Send SQL with null terminator
			byte[] bytes = (sql + "\0").getBytes(StandardCharsets.UTF_8);
			out.write(bytes);
			out.flush();

			// Read response until null terminator
			ByteArrayOutputStream buffer = new ByteArrayOutputStream();
			int readByte;
			while ((readByte = in.read()) != -1) {
				if (readByte == '\0') {
					break;
				}
				buffer.write(readByte);
			}
			//return response from the byte array
			return new String(buffer.toByteArray(), StandardCharsets.UTF_8);

		} catch (IOException e) {
			System.err.println("SQL Error: " + e.getMessage());
			return "";
		}
	}

	/**
	 * Escape SQL special characters to prevent SQL injection
	 */
	private String escapeSql(String str) {
		if (str == null)
			return "";
		return str.replace("'", "''");
	}
	// login status number codes
	public synchronized LoginStatus login(int connectionId, String username, String password) {
		if (activeLogins.containsValue(username)) {
			return LoginStatus.ALREADY_LOGGED_IN;
		}

		if (addNewUserCase(connectionId, username, password)) {
			return LoginStatus.ADDED_NEW_USER;
		}

		return userExistsCase(connectionId, username, password);
	}
	// log user login time in the database
	private void logLogin(String username) {
		String safeUser = escapeSql(username);
		String sql = "INSERT INTO Logins (username, login_time) VALUES ('" + safeUser + "', datetime('now'))";
		executeSQL(sql);
	}
	// complete login process
	private void completeLogin(int connectionId, String username) {
		activeLogins.put(connectionId, username);
		logLogin(username);
	}
	// handle existing user login case
	private LoginStatus userExistsCase(int connectionId, String username, String password) {
		String safeUser = escapeSql(username);
		String existingPassword = executeSQL("SELECT password FROM Users WHERE username='" + safeUser + "'");
		// check password match
		if (existingPassword != null && existingPassword.trim().equals(password)) {
			completeLogin(connectionId, username);
			return LoginStatus.LOGGED_IN_SUCCESSFULLY;
		}
		return LoginStatus.WRONG_PASSWORD;
	}

	private boolean addNewUserCase(int connectionId, String username, String password) {
		String safeUser = escapeSql(username);
		String existingPassword = executeSQL("SELECT password FROM Users WHERE username='" + safeUser + "'");
		// add new user if not exists
		if (existingPassword == null || existingPassword.isEmpty()) {
			String safePass = escapeSql(password);
			executeSQL("INSERT INTO Users (username, password) VALUES ('" + safeUser + "', '" + safePass + "')");

			completeLogin(connectionId, username);
			return true;
		}
		return false;
	}
	// log user logout time in the database
	public void logout(int connectionsId) {
		String username = activeLogins.remove(connectionsId);
		if (username != null) {
			String safeUser = escapeSql(username);
			// update logout time
			executeSQL("UPDATE Logins SET logout_time=datetime('now') WHERE username='" + safeUser + "'AND logout_time IS NULL");
		}
	}
	// get username by connection id
	public String getUsername(int connectionId) {
		return activeLogins.get(connectionId);
	}

	/**
	 * Track file upload in SQL database
	 * 
	 * @param username    User who uploaded the file
	 * @param filename    Name of the file
	 * @param gameChannel Game channel the file was reported to
	 */
	public void trackFileUpload(String username, String filename, String gameChannel) {
		String sql = String.format(
				"INSERT INTO Files (username, filename, upload_time, game_channel) " +
						"VALUES ('%s', '%s', datetime('now'), '%s')",
				escapeSql(username), escapeSql(filename), escapeSql(gameChannel));
		executeSQL(sql);
	}

	/**
	 * Generate and print server report using SQL queries
	 */
	public void printReport() {
		System.out.println(repeat("=", 80));
		System.out.println("SERVER REPORT - Generated at: " + java.time.LocalDateTime.now());
		System.out.println(repeat("=", 80));

		// List all users
		System.out.println("\n1. REGISTERED USERS:");
		System.out.println(repeat("-", 80));
		System.out.println(executeSQL("SELECT username, password FROM Users"));

		// Logged in users
		System.out.println("\n2. ACTIVE LOGINS (Memory):");
		System.out.println(repeat("-", 80));
		activeLogins.forEach((id, user) -> System.out.println("	ID: " + id + "User: " + user));
		
		// Login history for each user
		System.out.println("\n3. LOGIN HISTORY:");
		System.out.println(repeat("-", 80));
		System.out.println(executeSQL("SELECT username, login_time, logout_time FROM Logins"));
		
		// Uploaded files
		System.out.println("\n4. FILE UPLOADS:");
		System.out.println(repeat("-", 80));
		System.out.println(executeSQL("SELECT username, filename, game_channel FROM Files"));
		
		System.out.println(repeat("=", 80));
	}

	private String repeat(String str, int times) {
		StringBuilder sb = new StringBuilder();
		for (int i = 0; i < times; i++) {
			sb.append(str);
		}
		return sb.toString();
	}
}