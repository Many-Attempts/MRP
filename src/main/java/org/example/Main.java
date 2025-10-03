package org.example;

import com.sun.net.httpserver.HttpServer;
import org.example.utils.Router;

import java.io.IOException;
import java.net.InetSocketAddress;

public class Main {
    public static void main(String[] args) {
        try {
            // Create HTTP server on port 8080
            HttpServer server = HttpServer.create(new InetSocketAddress(8080), 0);

            // Set up router for all API endpoints
            server.createContext("/", new Router());

            // Start the server
            server.setExecutor(null); // creates a default executor spawns a new thread for each request
            server.start();

            System.out.println("===========================================");
            System.out.println("Media Ratings Platform (MRP) Server");
            System.out.println("===========================================");
            System.out.println("Server started on http://localhost:8080");
            System.out.println("API endpoints available at http://localhost:8080/api");
            System.out.println("");
            System.out.println("Available endpoints:");
            System.out.println("  POST   /api/auth/register     - Register new user");
            System.out.println("  POST   /api/auth/login        - Login user");
            System.out.println("");
            System.out.println("Database: PostgreSQL on localhost:5433");
            System.out.println("Press Ctrl+C to stop the server");
            System.out.println("===========================================");

        } catch (IOException e) {
            System.err.println("Failed to start server: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
    }
}