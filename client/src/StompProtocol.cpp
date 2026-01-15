
#include "../include/StompProtocol.h"
#include "../include/StompEncoder.h"
#include "../include/event.h"
#include <sstream>
#include <fstream>
#include <string>
#include <iostream>
#include <vector>

static int disconnectReceiptId = -1;
StompProtocol::StompProtocol() : isConnected(false), subscriptionIdCounter(0), receiptIdCounter(0), currentUser(""), topicToSubscriptionId(), gamesData() {}

bool StompProtocol::processInput(const std::string &line, ConnectionHandler &ConnectionHandler)
{
    std::stringstream ss(line);
    std::string command;
    ss >> command;

    if (command == "login")
    {
        std::string hostPort, username, password;
        ss >> hostPort >> username >> password;

        if (isConnected)
        {
            std::cout << "User is already logged in" << std::endl;
            return true;
        }

        size_t colonPos = hostPort.find(':');
        std::string host = hostPort.substr(0, colonPos);
        // Extract port number, convert to short and convert to int
        short port = (short)stoi(hostPort.substr(colonPos + 1));

        if (!ConnectionHandler.connect())
        {
            std::cout << "Could not connect to server" << std::endl;
            return true;
        }

        std::string frame = StompEncoder::buildConnect(host, port, username, password);
        if (ConnectionHandler.sendFrameAscii(frame, '\0'))
        {
            currentUser = username;
            return true;
        }
        return false;
    }

    else if (command == "join")
    {
        std::string topic;
        ss >> topic;

        if (!isConnected)
        {
            std::cout << "User is not logged in" << std::endl;
            return true;
        }

        int subId = subscriptionIdCounter++;
        int receiptId = receiptIdCounter++;
        topicToSubscriptionId[topic] = subId;

        std::string frame = StompEncoder::buildSubscribe(topic, subId, receiptId);
        ConnectionHandler.sendFrameAscii(frame, '\0');

        std::cout << "Joined channel " << topic << std::endl;
        return true;
    }

    else if (command == "exit")
    {
        std::string topic;
        ss >> topic;

        if (!isConnected)
        {
            std::cout << "User is not connected" << std::endl;
            return true;
        }

        if (topicToSubscriptionId.count(topic) == 0)
        {
            std::cout << "User is not subscribed to channel " << topic << std::endl;
            return true;
        }

        int subId = topicToSubscriptionId[topic];
        int receiptId = receiptIdCounter++;
        topicToSubscriptionId.erase(topic);

        std::string frame = StompEncoder::buildUnsubscribe(subId, receiptId);
        ConnectionHandler.sendFrameAscii(frame, '\0');

        std::cout << "Exited channel " << topic << std::endl;
        return true;
    }

    else if (command == "logout")
    {
        if (!isConnected)
        {
            std::cout << "User is not logged in" << std::endl;
            return true;
        }
        int receiptId = receiptIdCounter++;
        disconnectReceiptId = receiptId;

        std::string frame = StompEncoder::buildDisconnect(receiptId);
        return ConnectionHandler.sendFrameAscii(frame, '\0');
    }

    else if (command == "report")
    {
        std::string jsonFile;
        ss >> jsonFile;

        if (!isConnected)
        {
            std::cout << "User is not logged in" << std::endl;
            return true;
        }

        names_and_events parsedEvents;
        try
        {
            parsedEvents = parseEventsFile(jsonFile);
        }
        catch (std::exception &e)
        {
            std::cout << "Failed to parse events file: " << e.what() << std::endl;
            return true;
        }

        for (const Event &event : parsedEvents.events)
        {
            std::stringstream body;
            body << "user:" << currentUser << "\n";
            body << "team a:" << event.get_team_a_name() << "\n";
            body << "team b:" << event.get_team_b_name() << "\n";
            body << "event name:" << event.get_name() << "\n";
            body << "time:" << event.get_time() << "\n";

            body << "general game updates:\n";
            for (const auto &update : event.get_game_updates())
            {
                body << update.first << ":" << update.second << "\n";
            }

            body << "team a updates:\n";
            for (const auto &update : event.get_team_a_updates())
            {
                body << "\t" << update.first << ":" << update.second << "\n";
            }

            body << "team b updates:\n";
            for (const auto &update : event.get_team_b_updates())
            {
                body << "\t" << update.first << ":" << update.second << "\n";
            }

            body << "description:" << event.get_discription() << "\n";

            std::string topic = parsedEvents.team_a_name + "_" + parsedEvents.team_b_name;
            ;
            std::string frame = StompEncoder::buildSend(topic, body.str());
            ConnectionHandler.sendFrameAscii(frame, '\0');

            updateGameData(topic, currentUser, body.str());
        }
        return true;
    }
    else if (command == "summary")
    {
        std::string gameName, user, file;
        ss >> gameName >> user >> file;

        if (gamesData.find(gameName) == gamesData.end() ||
            gamesData[gameName].find(user) == gamesData[gameName].end())
        {
            std::cout << "No data available for game " << gameName << " from user " << user << std::endl;
        }
        else
        {
            saveSummaryToFile(gameName, user, file);
        }
        return true;
    }

    std::cout << "Unknown command: " << command << std::endl;
    return true;
}

bool StompProtocol::processServerResponse(std::string &frame) {

}

// Implementation to update game data based on the received message body
void StompProtocol::updateGameData(const std::string& topic, const std::string& user, const std::string& body) {
    
}

// Implementation to save game summary to a file
void StompProtocol::saveSummaryToFile(const std::string& gameName, const std::string& user, const std::string& file) {
    
}