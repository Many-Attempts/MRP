package org.example.utils;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import org.example.handlers.*;

import java.io.IOException;

public class Router implements HttpHandler {
    private final AuthHandler authHandler = new AuthHandler();

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        String path = exchange.getRequestURI().getPath(); // zb "/login/

        try {
            // Route to appropriate handler based on path
            if (path.startsWith("/api/auth/") || path.startsWith("/api/users/login")) {
                authHandler.handle(exchange);
            }
            else if (path.equals("/") || path.equals("/api") || path.equals("/api/")) {
                // Health check endpoint
                JsonHelper.sendResponse(exchange, 200,
                    java.util.Map.of(
                        "status", "ok",
                        "service", "Media Ratings Platform",
                        "version", "1.0.0"
                    )
                );
            }
            else {
                JsonHelper.sendError(exchange, 404, "Endpoint not found: " + path);
            }
        } catch (Exception e) {
            e.printStackTrace();
            JsonHelper.sendError(exchange, 500, "Internal server error: " + e.getMessage());
        }
    }
}