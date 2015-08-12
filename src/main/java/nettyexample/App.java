package nettyexample;

public class App {
    public static void main(String[] args) throws Exception {
        new WebServer(8081).run();
    }
}
