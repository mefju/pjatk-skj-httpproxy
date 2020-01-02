package pl.edu.pja.s19880;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;

public class Main {

    public static void main(String[] args) throws IOException {
        ServerSocket server = new ServerSocket();
        server.bind(new InetSocketAddress(8080));
        while(true) {
            new ConnectionHandler(server.accept());
        }
    }
}