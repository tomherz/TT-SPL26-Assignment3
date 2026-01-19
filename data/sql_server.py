#!/usr/bin/env python3
"""
Basic Python Server for STOMP Assignment – Stage 3.3

IMPORTANT:
DO NOT CHANGE the server name or the basic protocol.
Students should EXTEND this server by implementing
the methods below.
"""

import socket
import sys
import threading
import sqlite3
import os


SERVER_NAME = "STOMP_PYTHON_SQL_SERVER"  # DO NOT CHANGE!
DB_FILE = "stomp_server.db"              # DO NOT CHANGE!


def recv_null_terminated(sock: socket.socket) -> str:
    data = b""
    while True:
        chunk = sock.recv(1024)
        if not chunk:
            return ""
        data += chunk
        if b"\0" in data:
            msg, _ = data.split(b"\0", 1)
            return msg.decode("utf-8", errors="replace")


def init_database():
    try:
        conn = sqlite3.connect(DB_FILE)
        c = conn.cursor()
        
        # Init Users table
        c.execute('''CREATE TABLE IF NOT EXISTS Users (
                        username TEXT PRIMARY KEY,
                        password TEXT NOT NULL
                    )''')
        # Init Logins table 
        c.execute('''CREATE TABLE IF NOT EXISTS Logins (
                        id INTEGER PRIMARY KEY AUTOINCREMENT,
                        username TEXT NOT NULL,
                        login_time DATETIME NOT NULL,
                        logout_time DATETIME,
                        FOREIGN KEY(username) REFERENCES Users(username)
                    )''')
        
        # Init Files table - uploaded via report 
        c.execute('''CREATE TABLE IF NOT EXISTS Files (
                        id INTEGER PRIMARY KEY AUTOINCREMENT,
                        username TEXT NOT NULL,
                        filename TEXT NOT NULL,
                        game_channel TEXT,
                        upload_time DATETIME NOT NULL,
                        FOREIGN KEY(username) REFERENCES Users(username)
                    )''')
        
        conn.commit()
        conn.close()
        print(f"[{SERVER_NAME}] Database initialized successfully.")
    except Exception as e:
        print(f"[{SERVER_NAME}] Error initializing database: {e}")


def execute_sql_command(sql_command: str) -> str:
    conn = None
    try:
        # create a new database connection
        conn = sqlite3.connect(DB_FILE)
        # create a cursor
        c = conn.cursor()
        # Execute the command
        c.execute(sql_command)
        # Commit the changes
        conn.commit()
        return "done"
    except sqlite3.Error as e:
        return f"SQL Error: {e}"
    finally:
        if conn:
            conn.close()

def execute_sql_query(sql_query: str) -> str:
    conn = None
    try:
        #create a new data base connection
        conn = sqlite3.connect(DB_FILE)
        #create a cursor
        c = conn.cursor()
        #execute the query
        c.execute(sql_query)
        #fetch all results
        rows = c.fetchall()
        #format the results
        if not rows:
            return ""
        #if it's single value
        if len(rows) == 1 and len(rows[0]) == 1:
            return str(rows[0][0])
        #if it's multiple values
        result = []
        #format the output with | separator
        for row in rows:
            result.append("|".join(str(item) for item in row))
        return "\n".join(result)
    
    except sqlite3.Error as e:
        return f"SQL Error: {e}"
    finally:
        if conn:
            conn.close()


def handle_client(client_socket: socket.socket, addr):
    print(f"[{SERVER_NAME}] Client connected from {addr}")
    
    try:
        # Receive and process messages
        while True:
            message = recv_null_terminated(client_socket)
            if message == "":
                break

            print(f"[{SERVER_NAME}] Received:")
            print(message)
        
            response = ""
            # Decide if it's a query or command
            if message.strip().upper().startswith("SELECT"):
                response = execute_sql_query(message)
            else:
                response = execute_sql_command(message)
        
            # Send response back to client    
            response_bytes = response.encode("utf-8") + b"\0"
            client_socket.sendall(response_bytes)    

    except Exception as e:
        print(f"[{SERVER_NAME}] Error handling client {addr}: {e}")
    finally:
        try:
            client_socket.close()
        except Exception:
            pass
        print(f"[{SERVER_NAME}] Client {addr} disconnected")


def start_server(host="127.0.0.1", port=7778):
    
    init_database()
    
    server_socket = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
    server_socket.setsockopt(socket.SOL_SOCKET, socket.SO_REUSEADDR, 1)
    
    try:
        server_socket.bind((host, port))
        server_socket.listen(5)
        print(f"[{SERVER_NAME}] Server started on {host}:{port}")
        print(f"[{SERVER_NAME}] Waiting for connections...")

        while True:
            client_socket, addr = server_socket.accept()
            t = threading.Thread(
                target=handle_client,
                args=(client_socket, addr),
                daemon=True
            )
            t.start()
    
    except KeyboardInterrupt:
        print(f"\n[{SERVER_NAME}] Shutting down server...")
    finally:
        try:
            server_socket.close()
        except Exception:
            pass


if __name__ == "__main__":
    port = 7778
    if len(sys.argv) > 1:
        raw_port = sys.argv[1].strip()
        try:
            port = int(raw_port)
        except ValueError:
            print(f"Invalid port '{raw_port}', falling back to default {port}")

    start_server(port=port)
