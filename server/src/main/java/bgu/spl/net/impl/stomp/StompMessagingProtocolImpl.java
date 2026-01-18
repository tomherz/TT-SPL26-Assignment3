package bgu.spl.net.impl.stomp;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import bgu.spl.net.srv.Connections;

import bgu.spl.net.api.StompMessagingProtocol;
import bgu.spl.net.impl.data.Database;
import bgu.spl.net.impl.data.LoginStatus;

public class StompMessagingProtocolImpl implements StompMessagingProtocol<String> {

    private int connectionId;
    private ConnectionsImpl<String> connections;
    private boolean shouldTerminate = false;
    private boolean isConnected = false;

    // creating a map that will match subscriptionId to channel name
    private Map<Integer, String> activeSubscriptions = new ConcurrentHashMap<>();

    public void start(int connectionId, Connections<String> connections) {
        this.connectionId = connectionId;
        this.connections = (ConnectionsImpl<String>) connections;
    }

    public void process(String message) {
        StompFrame frame = StompFrame.fromString(message);
        if (frame == null) {
            return;
        }

        if (!isConnected && !frame.getCommand().equals("CONNECT")) {
            // if the client is not connected yet, we only accept CONNECT frames
            sendError(frame, "Client isn't logged in", "You must log in first");
            return;
        }

        try {
            if (frame.getCommand().equals("CONNECT")) {
                handleConnect(frame);
            } else if (frame.getCommand().equals("SUBSCRIBE")) {
                handleSubscribe(frame);
            } else if (frame.getCommand().equals("UNSUBSCRIBE")) {
                handleUnsubscribe(frame);
            } else if (frame.getCommand().equals("SEND")) {
                handleSend(frame);
            } else if (frame.getCommand().equals("DISCONNECT")) {
                handleDisconnect(frame);
            } else {
                sendError(frame, "Unknown command", "The command " + frame.getCommand() + " is not recognized.");
            }
        } catch (Exception e) {
            sendError(frame, "Server Error", e.getMessage());
        }
    }

    public boolean shouldTerminate() {
        return shouldTerminate;
    }

    // Private helper methods:

    private void handleConnect(StompFrame frame) {
        // extracting headers
        String login = frame.getHeader("login");
        String passcode = frame.getHeader("passcode");
        String version = frame.getHeader("accept-version");

        // if missing headers, send error
        if (login == null || passcode == null || version == null) {
            sendError(frame, "Missing headers", "CONNECT frame must contain login, passcode, and accept-version headers.");
            shouldTerminate = true;
            return;
        }
        // try to log in the user

        LoginStatus status = Database.getInstance().login(connectionId, login, passcode);

        // checking the login status
        if (status == LoginStatus.LOGGED_IN_SUCCESSFULLY || status == LoginStatus.ADDED_NEW_USER) {
            isConnected = true;

            // sending the CONNECTED frame back to the client
            StompFrame connectedFrame = new StompFrame("CONNECTED");
            connectedFrame.addHeader("version", "1.2");
            connections.send(connectionId, connectedFrame.toString());
        } else {
            // failed to log in
            sendError(frame, "Login Failed", "Invalid login or passcode.");
            shouldTerminate = true;
        }
    }

    private void handleSubscribe(StompFrame frame) {
        // extracting headers
        String destination = frame.getHeader("destination");
        String idStr = frame.getHeader("id");
        // if missing headers, send error
        if (destination == null || idStr == null) {
            sendError(frame, "Missing Headers", "SUBSCRIBE frame must contain destination and ID headers.");
            return;
        }
        int subscriptionId = Integer.parseInt(idStr);
        // adding the subscription
        activeSubscriptions.put(subscriptionId, destination);
        connections.subscribe(destination, connectionId, subscriptionId);

        handleReceipt(frame);
    }

    private void handleUnsubscribe(StompFrame frame) {
        // extracting headers
        String ID = frame.getHeader("id");
        // if missing headers, send error
        if (ID == null) {
            sendError(frame, "Missing Headers", "UNSUBSCRIBE frame must contain ID header.");
            return;
        }
        // trying to remove the subscription
        int subscriptionId = Integer.parseInt(ID);
        String channel = activeSubscriptions.remove(subscriptionId);
        // if the channel existed, unsubscribe
        if (channel != null) {
            connections.unsubscribe(channel, connectionId, subscriptionId);
            handleReceipt(frame);
            // if the channel didn't exist, send error
        } else {
            sendError(frame, "Not Subscribed", "You are not subscribed to the given channel.");
        }
    }

    private void handleSend(StompFrame frame) {
        // check who is the sender
        String username = Database.getInstance().getUsername(connectionId);

        if (username == null) {
            sendError(frame, "Not logged in", "You must login before sending messages.");
            return;
        }
        // extracting headers
        String destination = frame.getHeader("destination");
        String body = frame.getBody();
        // if missing headers, send error
        if (destination == null) {
            sendError(frame, "Missing Headers", "SEND frame must contain destination header.");
            return;
        }
        // sending the message to all subscribers
        connections.send(destination, body);
        handleReceipt(frame);
    }

    private void handleDisconnect(StompFrame frame) {
        // handling receipt first
        handleReceipt(frame);
        isConnected = false;
        shouldTerminate = true;
        // logging out the user
        Database.getInstance().logout(connectionId);
        // disconnecting the user
        connections.disconnect(connectionId);
        // clearing active subscriptions
        activeSubscriptions.clear();
    }

    private void handleReceipt(StompFrame frame) {
        String receiptId = frame.getHeader("receipt");
        // if receipt header exists, send RECEIPT frame
        if (receiptId != null) {
            StompFrame receiptFrame = new StompFrame("RECEIPT");
            receiptFrame.addHeader("receipt-id", receiptId);
            connections.send(connectionId, receiptFrame.toString());
        }
    }

    private void sendError(StompFrame frame, String msg, String description) {
        StompFrame errorFrame = new StompFrame("ERROR");
        // if the original frame has a receipt header, include it in the error frame
        String receiptId = frame.getHeader("receipt");
        // if receipt header exists, send RECEIPT frame
        if (frame != null && receiptId != null) {
            errorFrame.addHeader("receipt-id", receiptId);
        }
        // adding error details
        errorFrame.addHeader("message", msg);
        errorFrame.setBody(description);
        connections.send(connectionId, errorFrame.toString());
        shouldTerminate = true;
    }
}
