package nettyexample;

import nettyexample.server.WebServer;

public class App {
    public static void main(final String[] args) throws Exception {
        new WebServer()

                // Simple GET request
                .get("/hello", (request, response) -> "Hello world")

                // Simple POST request
                .post("/hello", (request, response) -> {
                    return "Hello world: " + request.body();
                })

                // Error handling
                .get("/boom", (request, response) -> {
                    throw new Exception("asdf");
                })

                // GET body?
                .get("/getbody", (request, response) -> {
                    return "What is this? " + request.body();
                })

                // Start the server
                .start();
    }
}
