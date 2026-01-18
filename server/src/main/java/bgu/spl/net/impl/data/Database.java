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

	public static class DBHolder {
		private static final Database db = new Database();
	}

	public static Database getInstance() {
		return DBHolder.db;
	}

	/**
	 * Execute SQL query and return result
	 * 
	 * @param sql SQL query string
	 * @return Result string from SQL server
	 */
	private String executeSQL(String sql) {
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

	public synchronized LoginStatus login(int connectionId, String username, String password) {
		if (activeLogins.containsValue(username)) {
			return LoginStatus.ALREADY_LOGGED_IN;
		}

		if (addNewUserCase(connectionId, username, password)) {
			return LoginStatus.ADDED_NEW_USER;
		}

		return userExistsCase(connectionId, username, password);
	}

	private void logLogin(String username) {
		String safeUser = escapeSql(username);
		String sql = "INSERT INTO Logins (username, login_time) VALUES ('" + safeUser + "', datetime('now'))";
		executeSQL(sql);
	}

	private void completeLogin(int connectionId, String username) {
		activeLogins.put(connectionId, username);
		logLogin(username);
	}

	private LoginStatus userExistsCase(int connectionId, String username, String password) {
		String safeUser = escapeSql(username);
		String existingPassword = executeSQL("SELECT password FROM Users WHERE username='" + safeUser + "'");

		if (existingPassword != null && existingPassword.trim().equals(password)) {
			completeLogin(connectionId, username);
			return LoginStatus.LOGGED_IN_SUCCESSFULLY;
		}
		return LoginStatus.WRONG_PASSWORD;
	}

	private boolean addNewUserCase(int connectionId, String username, String password) {
		String safeUser = escapeSql(username);
		String existingPassword = executeSQL("SELECT password FROM Users WHERE username='" + safeUser + "'");
		if (existingPassword == null || existingPassword.isEmpty()) {
			String safePass = escapeSql(password);
			executeSQL("INSERT INTO Users (username, password) VALUES ('" + safeUser + "', '" + safePass + "')");

			completeLogin(connectionId, username);
			return true;
		}
		return false;
	}

	public void logout(int connectionsId) {
		String username = activeLogins.remove(connectionsId);
		if (username != null) {
			String safeUser = executeSQL(username);
			executeSQL("UPDATE Logins SET logout_time=datetime('now') WHERE username='" + safeUser + "'AND logout_time IS NULL");
		}
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
				"INSERT INTO file_tracking (username, filename, upload_time, game_channel) " +
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
		String usersSQL = "SELECT username, registration_date FROM users ORDER BY registration_date";
		String usersResult = executeSQL(usersSQL);
		if (usersResult.startsWith("SUCCESS")) {
			String[] parts = usersResult.split("\\|");
			if (parts.length > 1) {
				for (int i = 1; i < parts.length; i++) {
					System.out.println("   " + parts[i]);
				}
			} else {
				System.out.println("   No users registered");
			}
		}

		// Login history for each user
		System.out.println("\n2. LOGIN HISTORY:");
		System.out.println(repeat("-", 80));
		String loginSQL = "SELECT username, login_time, logout_time FROM Logins ORDER BY username, login_time DESC";
		String loginResult = executeSQL(loginSQL);
		if (loginResult.startsWith("SUCCESS")) {
			String[] parts = loginResult.split("\\|");
			if (parts.length > 1) {
				String currentUser = "";
				for (int i = 1; i < parts.length; i++) {
					String[] fields = parts[i].replace("(", "").replace(")", "").replace("'", "").split(", ");
					if (fields.length >= 3) {
						if (!fields[0].equals(currentUser)) {
							currentUser = fields[0];
							System.out.println("\n   User: " + currentUser);
						}
						System.out.println("      Login:  " + fields[1]);
						System.out
								.println("      Logout: " + (fields[2].equals("None") ? "Still logged in" : fields[2]));
					}
				}
			} else {
				System.out.println("   No login history");
			}
		}

		// File uploads for each user
		System.out.println("\n3. FILE UPLOADS:");
		System.out.println(repeat("-", 80));
		String filesSQL = "SELECT username, filename, upload_time, game_channel FROM file_tracking ORDER BY username, upload_time DESC";
		String filesResult = executeSQL(filesSQL);
		if (filesResult.startsWith("SUCCESS")) {
			String[] parts = filesResult.split("\\|");
			if (parts.length > 1) {
				String currentUser = "";
				for (int i = 1; i < parts.length; i++) {
					String[] fields = parts[i].replace("(", "").replace(")", "").replace("'", "").split(", ");
					if (fields.length >= 4) {
						if (!fields[0].equals(currentUser)) {
							currentUser = fields[0];
							System.out.println("\n   User: " + currentUser);
						}
						System.out.println("      File: " + fields[1]);
						System.out.println("      Time: " + fields[2]);
						System.out.println("      Game: " + fields[3]);
						System.out.println();
					}
				}
			} else {
				System.out.println("   No files uploaded");
			}
		}

		System.out.println(repeat("=", 80));
	}

	private String repeat(String str, int times) {
		StringBuilder sb = new StringBuilder();
		for (int i = 0; i < times; i++) {
			sb.append(str);
		}
		return sb.toString();
	}

	private static class Instance {
		static Database instance = new Database();
	}
}