package bgu.spl.net.impl.stomp;

import bgu.spl.net.srv.BaseServer;
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
            new BaseServer<String>(port,() -> new StompMessagingProtocolImpl(),() -> new StompEncoderDecoder(),null){
                protected void execute(BlockingConnectionHandler<String> handler){
                    new Thread(handler).start();
                }
            }.serve();
        }

        else if(serverType.equals("reactor")){
            //choosing default number of threads, because we didnt get it in args
            int numThreads = 5;
            new Reactor<String>(numThreads, port, () -> new StompMessagingProtocolImpl(), () -> new StompEncoderDecoder(), null).serve();
        }
        else{
            System.out.println("Unknown server type: " + serverType);
            System.exit(1);
        }
    }
}
