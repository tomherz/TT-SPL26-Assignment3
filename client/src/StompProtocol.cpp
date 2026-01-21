
#include "../include/StompProtocol.h"
#include "../include/StompEncoder.h"
#include "../include/event.h"
#include <sstream>
#include <fstream>
#include <string>
#include <iostream>
#include <vector>
#include <algorithm>

std::string trim(const std::string& str) {
    size_t first = str.find_first_not_of(" \t\r\n");
    if (std::string::npos == first) {
        return "";
    }
    size_t last = str.find_last_not_of(" \t\r\n");
    return str.substr(first, (last - first + 1));
}

StompProtocol::StompProtocol() : isConnected(false), subscriptionIdCounter(0), receiptIdCounter(0), disconnectReceiptId(-1), currentUser(""), topicToSubscriptionId(), gamesData() {}
bool StompProtocol::processInput(const std::string &line, ConnectionHandler &ConnectionHandler)
{
    // parse the input line
    std::stringstream ss(line);
    std::string command;
    ss >> command;
    // process commands
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

        // build and send CONNECT frame
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
        // generate subscription id and receipt id
        int subId = subscriptionIdCounter++;
        int receiptId = receiptIdCounter++;
        topicToSubscriptionId[topic] = subId;
        // build and send SUBSCRIBE frame
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
        // generate receipt id
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
        // get the json file path
        std::string jsonFile;
        ss >> jsonFile;

        if (!isConnected)
        {
            std::cout << "User is not logged in" << std::endl;
            return true;
        }
        // parse the json file
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
        // for each event build and send a SEND frame
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
        }
        return true;
    }
    else if (command == "summary")
    {
        std::string gameName, user, file;
        ss >> gameName >> user >> file;
        // save the summary to file
        saveSummaryToFile(gameName, user, file);
        return true;
    }

    std::cout << "Unknown command: " << command << std::endl;
    return true;
}
// helping method to process server responses
bool StompProtocol::processServerResponse(std::string &frame)
{
    std::stringstream ss(frame);
    std::string command;
    std::getline(ss, command);
    command = trim(command);
    // parse headers
    std::map<std::string, std::string> headers;
    std::string line;
    while (std::getline(ss, line) && line != "\r" && line != "")
    {
        if (!line.empty() && line.back() == '\r')
        {
            line.pop_back();
        }
        size_t colonPos = line.find(':');
        if (colonPos != std::string::npos)
        {
            std::string key = trim(line.substr(0, colonPos));
            std::string value = trim(line.substr(colonPos + 1));
            headers[key] = value;
        }
    }
    // parse body
    std::stringstream bodySS;
    bodySS << ss.rdbuf();
    std::string body = bodySS.str();

    if (!body.empty() && body.back() == '\0')
    {
        body.pop_back();
    }

    // process commands
    if (command == "CONNECTED")
    {
        isConnected = true;
        std::cout << "Login successful" << std::endl;
    }
    else if (command == "ERROR")
    {
        std::cout << "Error from server: " << headers["message"] << std::endl;
        if (!body.empty())
        {
            std::cout << body << std::endl;
        }
        isConnected = false;
        return false;
    }
    else if (command == "RECEIPT")
    {
        if (headers["receipt-id"] == std::to_string(disconnectReceiptId))
        {
            isConnected = false;
            std::cout << "Logout successful" << std::endl;
            return false;
        }
        else
        {
            std::cout << "Receipt " << headers["receipt-id"] << " processed" << std::endl;
        }
    }
    else if (command == "MESSAGE")
    {
        std::string user = "";
        std::string topic = "";

        if (headers.count("user")) user = headers["user"];
        if (headers.count("destination")) topic = headers["destination"];

        if (!topic.empty() && topic[0] == '/') {
            topic = topic.substr(1);
        }
          
        if (!topic.empty() && !user.empty()) {
            // update local game data
            updateGameData(topic, user, body);
        }
     
        std::cout << "MESSAGE from " << topic << ":" << std::endl;
        std::cout << body << std::endl;
    }
    return true;
}

// Implementation to update game data based on the received message body
void StompProtocol::updateGameData(const std::string &gameName, const std::string &user, const std::string &body)
{
    // lock the mutex for thread safety
    std::lock_guard<std::mutex> lock(gamesDataMutex);
    // parse the body to create an Event object
    try
    {
        // create Event object
        Event event(body);
        // insert event into the correct position in the user's event vector
        std::vector<Event>& userEvents = gamesData[gameName][user];
        if(userEvents.empty()){
            userEvents.push_back(event);
        }
        else{
            //insert while maintaining sorted order by event time
            std::vector<Event>::iterator it = std::upper_bound(userEvents.begin(), userEvents.end(), event, [](const Event& a, const Event& b){
                return a.get_time() < b.get_time();
            });
            userEvents.insert(it, event);
        }
    }
    catch (const std::exception &e)
    {
        std::cerr << "Error parsing event body: " << e.what() << std::endl;
    }
}

// Implementation to save game summary to a file
void StompProtocol::saveSummaryToFile(const std::string &gameName, const std::string &user, const std::string &fileName)
{
    // lock the mutex for thread safety
    std::lock_guard<std::mutex> lock(gamesDataMutex);
    // check if we have data for the requested game and user
    if (gamesData.find(gameName) == gamesData.end() ||
        gamesData[gameName].find(user) == gamesData[gameName].end())
    {
        std::cerr << "No data available for game " << gameName << " from user " << user << std::endl;
        return;
    }
    const std::vector<Event> &events = gamesData[gameName][user];
    if (events.empty())
    {
        return;
    }

    std::map<std::string, std::string> final_general_stats;
    std::map<std::string, std::string> final_team_a_stats;
    std::map<std::string, std::string> final_team_b_stats;
    // aggregate final stats from all events
    for (const auto &event : events)
    {
        for (const auto &update : event.get_game_updates())
        {
            final_general_stats[update.first] = update.second;
        }
        for (const auto &update : event.get_team_a_updates())
        {
            final_team_a_stats[update.first] = update.second;
        }
        for (const auto &update : event.get_team_b_updates())
        {
            final_team_b_stats[update.first] = update.second;
        }
    }
    // write summary to file
    std::ofstream file(fileName);
    if (!file.is_open())
    {
        std::cerr << "Error opening file: " << fileName << std::endl;
        return;
    }

    file << events[0].get_team_a_name() << " vs " << events[0].get_team_b_name() << "\n";
    file << "Game stats:\n";

    file << "General stats:\n";
    for (const auto &stat : final_general_stats)
    {
        file << stat.first << ": " << stat.second << "\n";
    }

    file << events[0].get_team_a_name() << " stats:\n";
    for (const auto &stat : final_team_a_stats)
    {
        file << stat.first << ": " << stat.second << "\n";
    }

    file << events[0].get_team_b_name() << " stats:\n";
    for (const auto &stat : final_team_b_stats)
    {
        file << stat.first << ": " << stat.second << "\n";
    }

    file << "Game event reports:\n";
    for (const auto &event : events)
    {
        file << event.get_time() << " - " << event.get_name() << ":\n\n";
        file << event.get_discription() << "\n\n";
    }
    file.close();
}