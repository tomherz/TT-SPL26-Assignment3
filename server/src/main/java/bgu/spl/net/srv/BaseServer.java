package bgu.spl.net.srv;

import bgu.spl.net.api.MessageEncoderDecoder;
import bgu.spl.net.api.MessagingProtocol;
import bgu.spl.net.api.StompMessagingProtocol;
import bgu.spl.net.impl.stomp.ConnectionsImpl;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.function.Supplier;

public abstract class BaseServer<T> implements Server<T> {

    private final int port;
    private final Supplier<MessagingProtocol<T>> protocolFactory;
    private final Supplier<MessageEncoderDecoder<T>> encdecFactory;
    private ServerSocket sock;

    private final ConnectionsImpl<T> connections;
    private int connectionIdCounter = 0;

    public BaseServer(
            int port,
            Supplier<MessagingProtocol<T>> protocolFactory,
            Supplier<MessageEncoderDecoder<T>> encdecFactory) {

        this.port = port;
        this.protocolFactory = protocolFactory;
        this.encdecFactory = encdecFactory;
		this.sock = null;
        this.connections = new ConnectionsImpl<>();
    }

    @Override
    public void serve() {

        try (ServerSocket serverSock = new ServerSocket(port)) {
			System.out.println("Server started");

            this.sock = serverSock; //just to be able to close

            while (!Thread.currentThread().isInterrupted()) {

                Socket clientSock = serverSock.accept();
                // create a new protocol and encdec for the connection
                MessagingProtocol<T> protocol = protocolFactory.get();
                MessageEncoderDecoder<T> encdec = encdecFactory.get();
                // create a unique connection ID
                int connectionId = connectionIdCounter++;
                // register the connection
                if (protocol instanceof StompMessagingProtocol) {
                    // start the protocol with the connection ID and connections, after casting to STOMP
                    ((StompMessagingProtocol<T>) protocol).start(connectionId, connections);
                }
                // create the connection handler
                BlockingConnectionHandler<T> handler = new BlockingConnectionHandler<>(clientSock, encdec, protocol);

                // connect the handler to connections
                connections.connect(connectionId, handler);
                execute(handler);
            }
        } catch (IOException ex) {
        }

        System.out.println("Server closed!!!");
    }

    @Override
    public void close() throws IOException {
		if (sock != null)
			sock.close();
    }

    protected abstract void execute(BlockingConnectionHandler<T>  handler);

}
