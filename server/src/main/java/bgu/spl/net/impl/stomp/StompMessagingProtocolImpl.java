package bgu.spl.net.impl.stomp;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import bgu.spl.net.srv.Connections;

import bgu.spl.net.api.StompMessagingProtocol;

public class StompMessagingProtocolImpl implements StompMessagingProtocol<String> {

    private int connectionId;
    private ConnectionsImpl<String> connections;
    private boolean shouldTerminate = false;
    private boolean isConnected = false;


    // creating a map that will match subscriptionId -> channel name
    private Map<Integer, String> activeSubscriptions = new ConcurrentHashMap<>();

    public void start(int connectionId, Connections<String> connections) {
        this.connectionId = connectionId;
        this.connections = (ConnectionsImpl<String>) connections;
    }
    
    public void process (String message) {
        String[] lines = message.split("\n");
        if (lines.length == 0) return;

        String command = lines[0];



    }





}
