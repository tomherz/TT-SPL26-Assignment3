package bgu.spl.net.impl.stomp;

import java.util.HashMap;
import java.util.Map;

public class StompFrame {

    private String command;
    private Map<String, String> headers = new HashMap<>();
    private String body = "";

    public StompFrame(String command) {
        this.command = command;
    }

    // getters and setters:

    public String getCommand() {
        return command;
    }

    public String getHeader(String key) {
        return headers.get(key);
    }

    public void addHeader(String key, String value) {
        headers.put(key, value);
    }

    public String getBody() {
        return body;
    }

    public void setBody(String body) {
        this.body = body;
    }

    public static StompFrame fromString(String msg) {
        String[] lines = msg.split("\n");
        if (lines.length == 0) {
            return null;
        }
        String command = lines[0];
        StompFrame frame = new StompFrame(command);

        int emptyLineIdx = -1;
        for (int i = 1; i < lines.length; i++) {
            if (lines[i].isEmpty()) {
                emptyLineIdx = i;
                break;
            }
        }
    
        int headersLimit;
        if (emptyLineIdx != -1) {
            headersLimit = emptyLineIdx;
        } else {
            headersLimit = lines.length;
        }
        
        for (int i = 1; i < headersLimit; i++) {
            String line = lines[i];
            int split = line.indexOf(':');
            if (split != -1) {
                String key = line.substring(0, split);
                String value = line.substring(split + 1);
                frame.addHeader(key, value);
            }
        }

        if (emptyLineIdx != -1) {
            int bodyStart = msg.indexOf("\n\n") + 2;
            if (bodyStart < msg.length()) {
                frame.setBody(msg.substring(bodyStart));
            }
        }
        return frame;
    }

    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append(command).append("\n");
        for (Map.Entry<String, String> entry : headers.entrySet()) {
            sb.append(entry.getKey()).append(":").append(entry.getValue()).append("\n");
        }
        sb.append("\n");
        if (body != null && !body.isEmpty()) {
            sb.append(body);
        }
        return sb.toString();
    }
}
