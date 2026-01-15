package bgu.spl.net.srv;

import bgu.spl.net.api.MessageEncoderDecoder;
import bgu.spl.net.api.MessagingProtocol;
import bgu.spl.net.api.StompMessagingProtocol;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.IOException;
import java.net.Socket;

public class BlockingConnectionHandler<T> implements Runnable, ConnectionHandler<T> {

    private final MessagingProtocol<T> protocol;
    private final StompMessagingProtocol<T> stompProtocol;
    private final MessageEncoderDecoder<T> encdec;
    private final Socket sock;
    private BufferedInputStream in;
    private BufferedOutputStream out;
    private volatile boolean connected = true;

    public BlockingConnectionHandler(Socket sock, MessageEncoderDecoder<T> reader, MessagingProtocol<T> protocol) {
        this.sock = sock;
        this.encdec = reader;
        this.protocol = protocol;
        this.stompProtocol = null;
    }

    // override for StompMessagingProtocol
    public BlockingConnectionHandler(Socket sock, MessageEncoderDecoder<T> reader,
            StompMessagingProtocol<T> stompProtocol) {
        this.sock = sock;
        this.encdec = reader;
        this.protocol = null;
        this.stompProtocol = stompProtocol;
    }

    @Override
    public void run() {
        try (Socket sock = this.sock) { // just for automatic closing

            int read;
            // initialize input and output streams
            in = new BufferedInputStream(sock.getInputStream());
            out = new BufferedOutputStream(sock.getOutputStream());
            // start the protocol
            while (!isProtocolTerminated() && connected && (read = in.read()) >= 0) {
                // decode the next byte
                T nextMessage = encdec.decodeNextByte((byte) read);
                // if a complete message has been decoded
                if (nextMessage != null) {
                    // check which protocol to use, note we use only stomp
                    if (stompProtocol != null) {
                        // process the message
                        stompProtocol.process(nextMessage);
                    } else {
                        //if not stomp, use the generic protocol
                        if (protocol.process(nextMessage) != null) {
                            send(protocol.process(nextMessage));
                        }
                    }
                }
            }
        } catch (IOException ex) {
            ex.printStackTrace();
        }
    }
    private boolean isProtocolTerminated() {    
        if (stompProtocol != null) {
            return stompProtocol.shouldTerminate();
        }
        return protocol.shouldTerminate();

    }

    @Override
    public void close() throws IOException {
        connected = false;
        sock.close();
    }

    @Override
    public void send(T msg) {
        // encode the message and write it to the output stream
        try {
            if (msg != null) {
                // synchronized to avoid collisions in messages
                synchronized (this) {
                    out.write(encdec.encode(msg));
                    out.flush();
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
