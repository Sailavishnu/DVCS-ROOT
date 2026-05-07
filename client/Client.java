import java.io.IOException;
import java.net.Socket;

public class Client {
    public static void main(String[] args) {
        String serverIp = "localhost";
        int port = 5000;

        if (args.length > 0) {
            serverIp = args[0];
        }

        try (Socket socket = new Socket(serverIp, port)) {
            System.out.println("Client connected to port " + port);
            socket.getInputStream().read(); // block until server closes
        } catch (IOException e) {
            System.err.println("Connection failed: " + e.getMessage());
        }
    }
}
