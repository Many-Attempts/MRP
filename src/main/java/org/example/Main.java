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
            System.out.println("");
            System.out.println("Authentication:");
            System.out.println("  POST   /api/auth/register            - Register new user");
            System.out.println("  POST   /api/auth/login               - Login user");
            System.out.println("");
            System.out.println("Media:");
            System.out.println("  GET    /api/media                    - Get media list (supports filters: search, type, genre, year, age, sort)");
            System.out.println("  POST   /api/media                    - Create new media entry");
            System.out.println("  GET    /api/media/{id}               - Get specific media with ratings");
            System.out.println("  PUT    /api/media/{id}               - Update media entry");
            System.out.println("  DELETE /api/media/{id}               - Delete media entry");
            System.out.println("  POST   /api/media/{id}/favorite      - Add media to favorites");
            System.out.println("  DELETE /api/media/{id}/favorite      - Remove media from favorites");
            System.out.println("");
            System.out.println("Ratings:");
            System.out.println("  POST   /api/media/{id}/ratings       - Create rating for media");
            System.out.println("  PUT    /api/ratings/{id}             - Update rating");
            System.out.println("  DELETE /api/ratings/{id}             - Delete rating");
            System.out.println("  PUT    /api/ratings/{id}/confirm     - Confirm rating comment");
            System.out.println("  POST   /api/ratings/{id}/like        - Like a rating");
            System.out.println("  DELETE /api/ratings/{id}/unlike      - Unlike a rating");
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