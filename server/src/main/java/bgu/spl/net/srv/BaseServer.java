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
    private final Supplier<StompMessagingProtocol<T>> stompFactory;
    private final Supplier<MessageEncoderDecoder<T>> encdecFactory;
    private ServerSocket sock;

    private final ConnectionsImpl<T> connections = new ConnectionsImpl<>();
    private int connectionIdCounter = 0;

    public BaseServer(
            int port,
            Supplier<MessagingProtocol<T>> protocolFactory,
            Supplier<MessageEncoderDecoder<T>> encdecFactory) {

        this.port = port;
        this.protocolFactory = protocolFactory;
        this.stompFactory = null;
        this.encdecFactory = encdecFactory;
		this.sock = null;
    }

    public BaseServer(
            int port,
            Supplier<StompMessagingProtocol<T>> stompFactory,
            Supplier<MessageEncoderDecoder<T>> encdecFactory, 
            //adding a dummy parameter to differentiate constructors
            Void ignore) {

        this.port = port;
        this.protocolFactory = null;
        this.stompFactory = stompFactory;
        this.encdecFactory = encdecFactory;
		this.sock = null;
    }

    @Override
    public void serve() {

        try (ServerSocket serverSock = new ServerSocket(port)) {
			System.out.println("Server started");

            this.sock = serverSock; //just to be able to close

            while (!Thread.currentThread().isInterrupted()) {
                BlockingConnectionHandler<T> handler;
                Socket clientSock = serverSock.accept();
                //checking which factory is not null to decide which protocol to create
                if(stompFactory != null){
                    StompMessagingProtocol<T> protocol = stompFactory.get();
                    MessageEncoderDecoder<T> encdec = encdecFactory.get();
                    int connectionId = connectionIdCounter++;
                    //initialize the protocol with connectionId and connections
                    protocol.start(connectionId, connections);

                    handler = new BlockingConnectionHandler<>(clientSock, encdec, protocol);
                    connections.connect(connectionId, handler);
                }
                //else, use the regular protocol factory
                else{
                    handler = new BlockingConnectionHandler<>(clientSock, encdecFactory.get(), protocolFactory.get());
                }
                //now execute the right handler
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
