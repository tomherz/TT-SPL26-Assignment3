package bgu.spl.net.impl.stomp;

import bgu.spl.net.srv.Server;

public class StompServer {

    public static void main(String[] args) {
        if (args.length < 2) {
            System.out.println("Usage: StompServer <port> <server_type(tpc,reactor)>");
            System.exit(1);
        }

        int port = Integer.parseInt(args[0]);
        String serverType = args[1];



        if(serverType.equals("tpc")){
            Server.<String>threadPerClient(port, 
                ()-> new StompMessagingProtocolImpl(),
                ()-> new StompEncoderDecoder()).serve();
        }
    }
}
