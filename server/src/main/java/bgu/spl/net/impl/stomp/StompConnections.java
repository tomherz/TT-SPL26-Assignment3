package bgu.spl.net.impl.stomp;

import java.util.concurrent.ConcurrentHashMap;

import bgu.spl.net.srv.ConnectionHandler;
import bgu.spl.net.srv.Connections;

public class StompConnections<T> implements Connections<T> {
    // Fields:
    private ConcurrentHashMap<Integer, ConnectionHandler<T>> connections = new ConcurrentHashMap<>();

    public boolean send(int connectionId, T msg){
        ConnectionHandler<T> handler = connections.get(connectionId);
        //checking if we have the connectionID stored, if not - return false
        if(handler == null){
            return false;
        }
        //send the message to the client and return true;
        handler.send(msg);
        return true;
    }

    public void send(String channel, T msg){
        //TODO: אנחנו הוספנו כי צריך לממש
    }

    public void disconnect(int connectionId){
        //removing the client from connections map
        connections.remove(connectionId);
    }

    //adding method: connect
    public void connect(int connectionId, ConnectionHandler<T> handler){
        //adding a new 
        connections.remove(connectionId);
    }
}
