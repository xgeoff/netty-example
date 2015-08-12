package nettyexample;

import nettyexample.server.WebServer;

public class App {
    public static void main(String[] args) throws Exception {
        new WebServer()
                .get("/hello", (request, response) -> "Hello world")
                .get("/whois", (request, response) -> "Cody Ebberson")
                .get("/boom", (request, response) -> {
                    throw new Exception("asdf");
                })
                .start();
    }
}
