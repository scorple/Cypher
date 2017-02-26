package com.semaphore_soft.apps.cypher.networking;

import com.semaphore_soft.apps.cypher.utils.Logger;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.net.SocketOptions;
import java.util.ArrayList;

/**
 * Class to hold server threads and helper methods
 *
 * @author Evan
 *
 * @see ServerService
 * @see AcceptorThread
 * @see ClientHandler
 */

public class Server
{
    private static ArrayList<ClientHandler> clients      = new ArrayList<>();
    private static Boolean                  accepting    = false;
    private static ServerSocket             serverSocket = null;
    private        int                      maxPlayers   = 4;
    private ServerService serverService;


    public Server()
    {
    }

    /**
     * Sets whether or not the {@link AcceptorThread AcceptorThread} should continue to accept connections.
     *
     * @param bool Whether or not to continue accepting connections.
     */
    public static void setAccepting(Boolean bool)
    {
        accepting = bool;
    }

    /**
     * Starts the {@link AcceptorThread AcceptorThread}.
     *
     * @param service An instance of {@link ServerService} that will interact with the thread.
     */
    public void startAcceptor(ServerService service)
    {
        serverService = service;
        AcceptorThread acceptorThread = new AcceptorThread();
        acceptorThread.start();
    }

    /**
     * Write a message to all connected clients.
     *
     * @param str Message to write.
     *
     * @see ServerService#writeAll(String)
     */
    public void writeAll(String str)
    {
        Logger.logD("Attempting to write to all clients");
        for (ClientHandler server : clients)
        {
            server.write(str);
        }
    }

    /**
     * Write a message to a specific client.
     *
     * @param str Message to write.
     * @param index The specific client to connect to.
     *
     * @see ServerService#writeToClient(String, int)
     */
    public void writeToClient(String str, int index)
    {
        Logger.logD("Attempting to write to client " + String.valueOf(index));
        if (!clients.isEmpty() && index >= 0 && index < clients.size())
        {
            clients.get(index).write(str);
        }
        else
        {
            Logger.logD("Could not write to client");
        }
    }

    /**
     * Class that listens for clients to connect.
     * <p>
     * Once connected the socket is passed to {@link ClientHandler ClientHandler}.
     * The {@link AcceptorThread AcceptorThread} will continue to listen until either
     * a maximum number of players has connected or
     * the host determines that all players have connected.
     *
     * @see Server#setAccepting(Boolean)
     */
    private class AcceptorThread extends Thread
    {
        Socket mySocket;

        /**
         * Creates a new {@link ServerSocket} that listens on port {@value NetworkConstants#SERVER_PORT}.
         * <p>
         * The {@link ServerSocket} will timeout after a delay to ensure that new clients cannot
         * connect after the host has started the game.
         */
        public AcceptorThread()
        {
            try
            {
                serverSocket = new ServerSocket(NetworkConstants.SERVER_PORT);

            }
            catch (IOException e)
            {
                e.printStackTrace();
                Logger.logE("Failed to start server");
                // Pass 0 since this does not receive input from client
                serverService.threadError(NetworkConstants.ERROR_SERVER_START, 0);
            }
        }

        public void run()
        {
            accepting = true;
            int index = 0;
            while (accepting && index < maxPlayers - 1)
            {
                try
                {
                    Logger.logI("Waiting on accept");
                    // Pass 0 since this does not receive input from client
                    serverService.threadUpdate(NetworkConstants.STATUS_SERVER_WAIT, 0);

                    // Set a timeout for the serverSocket to block
                    serverSocket.setSoTimeout(SocketOptions.SO_TIMEOUT);
                    mySocket = serverSocket.accept();
                    ClientHandler serverThread = new ClientHandler(mySocket);
                    clients.add(serverThread);
                    serverThread.start();
                    index++;
                }
                catch (SocketException e)
                {
                    Logger.logI("Socket closed");
                }
                catch (IOException e)
                {
                    e.printStackTrace();
                }
            }
        }
    }

    /**
     * Class that communicates with a single connected client.
     *
     * @see AcceptorThread
     * @see com.semaphore_soft.apps.cypher.networking.Client.ClientThread
     */
    private class ClientHandler extends Thread
    {
        // The local server socket
        Socket mySocket;

        private boolean running = true;

        /**
         * Starts a new thread to communicate with a client.
         *
         * @param socket {@link Socket} that is connected to a client.
         */
        public ClientHandler(Socket socket)
        {
            mySocket = socket;
            serverService.threadUpdate(NetworkConstants.STATUS_SERVER_START,
                                       clients.indexOf(this) + 1);
        }

        public void run()
        {
            while (running)
            {
                String msg = read();
                if (msg != null)
                {
                    processMessage(msg);
                }
            }
        }

        /**
         * Writes a message to the connected client.
         *
         * @param str Message to write.
         */
        public void write(String str)
        {
            try
            {
                DataOutputStream out = new DataOutputStream(mySocket.getOutputStream());
                out.writeUTF(str);
                // Flush after write or inputStream will hang on read
                out.flush();
                Logger.logD("sent message: " + str);
            }
            catch (IOException e)
            {
                e.printStackTrace();
                serverService.threadError(NetworkConstants.ERROR_WRITE, clients.indexOf(this) + 1);
            }
        }

        /**
         * Reads in data from the network.
         * Will attempt to reconnect if {@link Socket} connection is broken.
         *
         * @return Message that was read.
         *
         * @see ClientHandler#reconnectSocket()
         */
        private String read()
        {
            try
            {
                DataInputStream in = new DataInputStream(mySocket.getInputStream());
                try
                {
                    return in.readUTF();
                }
                catch (EOFException e)
                {
                    return null;
                }
            }
            catch (IOException e)
            {
                serverService.threadError(NetworkConstants.ERROR_DISCONNECT_SERVER,
                                          clients.indexOf(this) + 1);
                e.printStackTrace();
                running = false;
                reconnectSocket();
            }
            return null;
        }

        /**
         * Sends message that has been read to be processed by other activities.
         *
         * @param msg Message that was read.
         *
         * @see ServerService#threadRead(String, int)
         */
        private void processMessage(String msg)
        {
            Logger.logI(msg);
            Logger.logD(String.valueOf(clients.indexOf(this) + 1));
            serverService.threadRead(msg, clients.indexOf(this) + 1);
        }

        /**
         * Will try to reconnect to client if {@link Socket} connection is lost.
         * There is no timeout for the {@link ServerSocket} in this method.
         */
        private void reconnectSocket()
        {
            // Assume only one client needs to reconnect at a time
            if (serverSocket != null)
            {
                try
                {
                    Logger.logI("Waiting on accept");
                    serverService.threadUpdate(NetworkConstants.STATUS_SERVER_WAIT,
                                               clients.indexOf(this) + 1);

                    // Disable timeout
                    serverSocket.setSoTimeout(0);
                    mySocket = serverSocket.accept();
                    running = true;
                    serverService.threadUpdate(NetworkConstants.STATUS_SERVER_START,
                                               clients.indexOf(this) + 1);
                }
                catch (SocketException e)
                {
                    Logger.logI("Socket closed");
                }
                catch (IOException e)
                {
                    e.printStackTrace();
                }
            }
        }
    }
}
