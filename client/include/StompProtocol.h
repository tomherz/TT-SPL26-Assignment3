#pragma once

#include "../include/ConnectionHandler.h"
#include "event.h"
#include <string>
#include <map>
#include <vector>
#include <mutex>

using namespace std;

class StompProtocol {
private:
    bool isConnected;
    int subscriptionIdCounter;
    int receiptIdCounter;
    int disconnectReceiptId;
    
    // to keep track of the current user
    std::string currentUser;
    // creating map to keep track of subscriptions and receipts
    std::map<std::string, int> topicToSubscriptionId;
    // data structure to hold games data
    std::map<std::string, std::map<std::string,vector<Event>>> gamesData;
    // mutex for thread safety
    std::mutex gamesDataMutex;

    void updateGameData(const std::string& topic, const std::string& user, const std::string& body);
    void saveSummaryToFile(const std::string& gameName, const std::string& user, const std::string& file);


public:
    StompProtocol();
    // processes a single line of input from the user
    bool processInput(const std::string& line, ConnectionHandler& connectionHandler);
    
    // processes a single frame received from the server
    bool processServerResponse(std::string& frame);

    std::string buildConnectFrame(string host, short port, string login, string passcode);

    std::string buildSendFrame(string topic, string message);
    
    std::string buildSubscribeFrame(string topic, int id, int reciept);

    std::string buildUnsubscribeFrame(int id, int reciept);

    std::string buildDisconnectFrame(int reciept);
};
