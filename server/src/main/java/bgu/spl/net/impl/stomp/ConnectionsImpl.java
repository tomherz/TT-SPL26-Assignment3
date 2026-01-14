package bgu.spl.net.impl.stomp;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;

import java.util.List;
import bgu.spl.net.srv.ConnectionHandler;
import bgu.spl.net.srv.Connections;

public class ConnectionsImpl<T> implements Connections<T> {
    // Fields:
    // connecting between client (connection id) and client handler
    private final ConcurrentHashMap<Integer, ConnectionHandler<T>> connectedHandlers = new ConcurrentHashMap<>();
    // keeping "CopyOnWriteArrayList" of subscriptions per channel
    private final ConcurrentHashMap<String, CopyOnWriteArrayList<ClientSub>> channelSubscribers = new ConcurrentHashMap<>();
    // keeping channels per client (connection id)
    private final ConcurrentHashMap<Integer, CopyOnWriteArrayList<String>> clientActiveSubscriptions = new ConcurrentHashMap<>();
    // atomic counter for serial number of messages
    private final AtomicInteger messageIdCounter = new AtomicInteger(0);

    public boolean send(int connectionId, T msg) {
        ConnectionHandler<T> handler = connectedHandlers.get(connectionId);
        // checking if we have the connectionID stored, if not - return false
        if (handler == null) {
            return false;
        }
        // send the message to the client and return true;
        handler.send(msg);
        return true;
    }

    public void send(String channel, T msg) {
        // getting the list of the relevant subs for the specific given channel
        CopyOnWriteArrayList<ClientSub> subscribers = channelSubscribers.get(channel);
        if (subscribers == null) {
            return;
        }
        String body = (String) msg;

        for (ClientSub sub : subscribers) {
            T messageFrame = createMessageFrame(sub.ClientSubscriptionId, channel, body);
            send(sub.ClientSubConnectionId, messageFrame);
        }
    }

    public void disconnect(int connectionId) {
        // removing the client from connections map
        connectedHandlers.remove(connectionId);

        List<String> channels = clientActiveSubscriptions.remove(connectionId);
        if (channels != null) {
            // loop over all the channels and disconect the specific connectionId
            for (String channel : channels) {
                // removeSubscriberFromchannel(channel, connectionId);
                CopyOnWriteArrayList<ClientSub> subscribers = channelSubscribers.get(channel);
                if (subscribers != null) {
                    // remove the subscriber if we find him registered from the channel
                    subscribers.removeIf(sub -> sub.ClientSubConnectionId == connectionId);
                }
            }
        }
    }

    public void connect(int connectionId, ConnectionHandler<T> handler) {
        // adding connections + handler to the relevant map
        connectedHandlers.put(connectionId, handler);
    }

    public void subscribe(String channel, int connectionId, int subscriptionId) {
        // creating new list for a channel if doesn't exist already
        channelSubscribers.putIfAbsent(channel, new CopyOnWriteArrayList<>());
        CopyOnWriteArrayList<ClientSub> subscribers = channelSubscribers.get(channel);
        // checking if the client is already subscribed to the channel, if so return
        for (ClientSub sub : subscribers) {
            if (sub.ClientSubConnectionId == connectionId) {
                return;
            }
        }
        // adding the new sub for the channel's subs list
        subscribers.add(new ClientSub(connectionId, subscriptionId));
        // adding new list of empty subsriptions for client
        clientActiveSubscriptions.putIfAbsent(connectionId, new CopyOnWriteArrayList<>());
        // adding the channel for the client's subsricbe list

        clientActiveSubscriptions.get(connectionId).addIfAbsent(channel);
    }

    public void unsubscribe(String channel, int connectionId, int subscriptionId) {
        // getting the sub list of the channel
        CopyOnWriteArrayList<ClientSub> subscribers = channelSubscribers.get(channel);
        if (subscribers != null) {
            // creating flag that indicates that we the user we want to remove exists
            boolean removed = subscribers.removeIf(sub -> sub.ClientSubConnectionId == connectionId && sub.ClientSubscriptionId == subscriptionId)
;
            // if the client was successfully removed: 
                if (removed) {
                // remove from the client's channels list the specific channel that we removed
                CopyOnWriteArrayList<String> userSubs = clientActiveSubscriptions.get(connectionId);
                if (userSubs != null) {
                    userSubs.remove(channel);
                }
            }
        }
    }

    private T createMessageFrame(int subscriptionId, String destination, String body) {
        String str = "MESSAGE\n" +
                "subscription:" + subscriptionId + "\n" +
                "message-id:" + messageIdCounter.incrementAndGet() + "\n" +
                "destination:" + destination + "\n" +
                "\n" +
                body;
        return (T) (Object) str;
    }

    private static class ClientSub {
        final int ClientSubConnectionId;
        final int ClientSubscriptionId;

        ClientSub(int ClientSubConnectionId, int ClientSubscriptionId) {
            this.ClientSubConnectionId = ClientSubConnectionId;
            this.ClientSubscriptionId = ClientSubscriptionId;
        }
    }
}