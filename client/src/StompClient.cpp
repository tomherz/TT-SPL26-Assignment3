#include <iostream>
#include <thread>
#include <stdlib.h>
#include "../include/StompProtocol.h"
#include "../include/ConnectionHandler.h"

int main(int argc, char *argv[])
{
	// checking arguments
	if (argc < 3)
	{
		std::cerr << "Usage: " << argv[0] << " host port" << std::endl;
		return -1;
	}
	std::string host = argv[1];
	short port = atoi(argv[2]);

	// creating connection handler and connecting to the server
	ConnectionHandler connectionHandler(host, port);
	if (!connectionHandler.connect())
	{
		std::cerr << "Cannot connect to " << host << ":" << port << std::endl;
		return 1;
	}

	StompProtocol protocol;
	bool isRunning = true;
	// thread to handle server responses
	std::thread socketThread([&connectionHandler, &protocol, &isRunning]()
							 {
		while (isRunning){
			std::string answer;
			if (!connectionHandler.getLine(answer)) {
				std::cout << "Disconnected from server. Press entere to exit." << std::endl;
				isRunning = false;
				break;
			}
			//process the server response
			if (!protocol.processServerResponse(answer)) {
				isRunning = false;
				break;
			}
		} });
	// main thread to handle user input
	while (isRunning)
	{
		const short bufsize = 1024;
		char buf[bufsize];

		std::cin.getline(buf, bufsize);
		std::string line(buf);

		if (!isRunning)
		{
			break;
		}

		if (line.length() > 0)
		{
			protocol.processInput(line, connectionHandler);
		}
		// join the socket thread before exiting
		socketThread.join();
		return 0;
	}
}