CS 3251 Programming Assignment 2: Reliable Data Transfer
03APR2017

Group members:
Connor Lindquist (connorlindquist@gatech.edu)
Charlie McKnight (cmcknight9@gatech.edu)
Stephen Zolnik (szolnik3@gatech.edu)

Submitted files:
Packet.java: Server side Java implementation for interpreting received packets and creating outgoing packets.
packet.py: Client side python implementation for interpreting received packets and creating outgoing packets.
PacketStats.java: Java 'struct' to maintain status about sent out packets.
reldat-client.py: Client side python file that handles file deconstruction and reconstruction as well as order of execution and terminal inputs.
ReldatServer.java: Main server side file to handle receiving and sending out packets. Maintains the server side.
selective_repeat.py: Client side selective repeat implementation to handle all things selective repeat including resending packets, acks, and bad checksums.
SelectiveRepeat.java: Server side selective repeat implementation to handle all things selective repeat including resending packets, acks, and bad checksums.

Compiling and Running:
Java Server Side:
	Compile: javac *.java
	Run: java ReldatServer port_number window_size
	Example: java ReldatServer 4096 5

Python Client Side:
	Run: python reldat-client.py serverIP:port_number window_size
	Example: python reldat-client.py 127.0.0.1:4096 3

Bugs and Limitations:
We do not have bugs that we are aware of.

Design Documentation

Introduction
In this assignment we were given the task to design and implement a client-server application for bidirectional reliable data transfers. Instead of using TCP’s reliable service, we implemented our own connectionless reliable service much like UDP.

Description and Features:
- 3-way handshake: The server is always in a state of listening for a connection request. Once it receives a connection request from a client, the server sends an acknowledgement to the client to confirm it received the connection request. The server will then expect an acknowledgement from the client to confirm the connection. At this stage the connection has been officially made between client and server. This process is commonly known as the 3-way handshake.

- Selective Repeat:
*Client:
*Server:

Header Structure:
