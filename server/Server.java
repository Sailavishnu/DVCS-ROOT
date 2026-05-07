import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import org.bson.Document;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;

public class Server {

    private static final int PORT = 5000;
    private static final String MONGO_URI = "mongodb://localhost:27017";
    private static final String DATABASE_NAME = "dvcs";
    private static final String COLLECTION_NAME = "commits";

    public static void main(String[] args) {
        try (MongoClient mongoClient = MongoClients.create(MONGO_URI);
             ServerSocket serverSocket = new ServerSocket(PORT)) {

            MongoDatabase database = mongoClient.getDatabase(DATABASE_NAME);
            MongoCollection<Document> collection = database.getCollection(COLLECTION_NAME);

            System.out.println("Server running on port " + PORT);

            while (true) {
                Socket clientSocket = serverSocket.accept();
                System.out.println("Client connected from " + clientSocket.getInetAddress());
                new Thread(new ClientHandler(clientSocket, collection)).start();
            }

        } catch (IOException e) {
            System.err.println("Server exception: " + e.getMessage());
        }
    }

    private static class ClientHandler implements Runnable {
        private final Socket socket;
        private final MongoCollection<Document> collection;

        public ClientHandler(Socket socket, MongoCollection<Document> collection) {
            this.socket = socket;
            this.collection = collection;
        }

        @Override
        public void run() {
            try (DataInputStream in = new DataInputStream(socket.getInputStream());
                 DataOutputStream out = new DataOutputStream(socket.getOutputStream())) {

                while (true) {
                    String command = in.readUTF();
                    if (command.startsWith("COMMIT:")) {
                        handleCommit(command, out);
                    } else if (command.equals("REFRESH")) {
                        handleRefresh(out);
                    } else {
                        out.writeUTF("ERROR: Unknown command.");
                    }
                }
            } catch (IOException e) {
                System.out.println("Client disconnected: " + socket.getInetAddress());
            } finally {
                try { socket.close(); } catch (IOException ignored) {}
            }
        }

        private void handleCommit(String command, DataOutputStream out) throws IOException {
            String[] parts = command.split(":", 3);
            if (parts.length != 3) {
                out.writeUTF("ERROR: Invalid COMMIT format. Use COMMIT:fileName:content");
                return;
            }
            String fileName = parts[1];
            String content = parts[2];
            collection.insertOne(new Document("fileName", fileName)
                    .append("content", content)
                    .append("timestamp", System.currentTimeMillis()));
            System.out.println("Committed file: " + fileName);
            out.writeUTF("SUCCESS: Commit saved for " + fileName);
        }

        private void handleRefresh(DataOutputStream out) throws IOException {
            StringBuilder sb = new StringBuilder("--- Commits ---\n");
            for (Document doc : collection.find()) {
                sb.append(String.format("File: %s\nContent: %s\n------\n",
                        doc.getString("fileName"), doc.getString("content")));
            }
            String response = sb.toString().equals("--- Commits ---\n") ? "No commits found." : sb.toString();
            out.writeUTF(response);
        }
    }
}
