package bgu.spl.net.impl.data;

import java.io.*;
import java.net.Socket;
import java.nio.charset.StandardCharests;

public class NetworkClient {
    private static final String HOST = "127.0.0.1";
    private static final int PORT = 7778;

    // Sends a message to the SQL server and waits for a response
    public String sendAndReceive(String msg) {
        try (Socket socket = new Socket(HOST, PORT);
            BufferedOutputStream out = new BufferedOutputStream(socket.getOutputStream());
            BufferedInputStream in = new BufferedInputStream(socket.getInputStream())); {
            {
            // Send message with null terminator
            byte[] bytesToSend = (msg + "\0").getBytes(StandardCharests.UTF_8);
            out.write(bytesToSend);
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
            // Convert byte array to string
            return new String(buffer.toByteArray(), StandardCharests.UTF_8);
        } catch (IOException e) {
            System.out.println("Error communicating with SQL Serer: " + e.getMessage());
            return "";
        }
    }
}