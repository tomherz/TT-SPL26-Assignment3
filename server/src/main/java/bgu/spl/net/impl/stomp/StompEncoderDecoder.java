package bgu.spl.net.impl.stomp;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;

import bgu.spl.net.api.MessageEncoderDecoder;

public class StompEncoderDecoder implements MessageEncoderDecoder<String> {
    // Fields:
    private byte[] bytesBuff = new byte[1024];
    private int currMsgSize = 0;

    // methods:
    public String decodeNextByte(byte nextByte) {
        //checking if we got to the last char of the message
        if (nextByte == '\u0000'){
            //make the current bytes a string and return it
            String result = new String(bytesBuff,0,currMsgSize,StandardCharsets.UTF_8);
            currMsgSize = 0;
            return result;
        }
        //else - we are not done yet, so keep the next byte and return nothing
        if( currMsgSize >= bytesBuff.length){
            bytesBuff = Arrays.copyOf(bytesBuff, currMsgSize*2);
        }
        bytesBuff[currMsgSize] = nextByte;
        currMsgSize++;
        return null;
    }

    public byte[] encode(String msg){
        //casting the string into bytes
        return (msg + '\u0000').getBytes(StandardCharsets.UTF_8);
    }

}
