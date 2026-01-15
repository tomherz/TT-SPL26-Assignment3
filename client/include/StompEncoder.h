
#include <string>

using namespace std;

class StompEncoder {
    // frame building functions
    public:
        static string buildConnect (const string& host, short port, const string& login, const string& passcode) {
            return "CONNECT\n"
                   "accept-version:1.2\n"
                   "host:stomp.cs.bgu.ac.il\n"
                   "login:" + login + "\n"
                   "passcode:" + passcode + "\n"
                   "\n";
        }
    
        static string buildSubscribe (const string& topic, int id, int receipt) {
            return "SUBSCRIBE\n"
                   "destination:/" + topic + "\n"
                   "id:" + to_string(id) + "\n"
                   "receipt:" + to_string(receipt) + "\n"
                   "\n";
        }

        static string buildUnsubscribe (int id, int receipt) {
            return "UNSUBSCRIBE\n"
                   "id:" + to_string(id) + "\n"
                   "receipt:" + to_string(receipt) + "\n"
                   "\n";
        }

        static string buildSend (const string& topic, const string& msg) {
            return "SEND\n"
                   "destination:/" + topic + "\n"
                   "\n" +
                   msg + "\n";
        }
        
        static string buildDisconnect(int reciept) {
            return "DISCONNECT\n"
                    "receipt:" + to_string(reciept) + "\n"
                    "\n";
        }
}
;