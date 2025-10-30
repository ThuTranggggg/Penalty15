package server;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.sql.SQLException;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import common.Message;
import common.User;

public class Server {
    private static final int PORT = 12345;
    private ServerSocket serverSocket;
    private DatabaseManager dbManager;
    private ConcurrentHashMap<Integer, ClientHandler> clientMap = new ConcurrentHashMap<>();

    public Server() {
        try {
            serverSocket = new ServerSocket(PORT);
            dbManager = new DatabaseManager();
            System.out.println("Server đã khởi động trên cổng " + PORT);
            listenForClients();
        } catch (IOException | SQLException e) {
            e.printStackTrace();
        }
    }

    // Thêm client vào bản đồ
    public synchronized void addClient(int userId, ClientHandler clientHandler) {
        clientMap.put(userId, clientHandler);
    }

    // Lấy client theo ID
    public synchronized ClientHandler getClientById(int userId) {
        return clientMap.get(userId);
    }

    // Loại bỏ client khỏi bản đồ
    public synchronized void removeClient(ClientHandler clientHandler) {
        if (clientHandler.getUser() != null) {
            clientMap.remove(clientHandler.getUser().getId());
        }
    }

    // Gửi tin nhắn tới tất cả client
    public synchronized void broadcast(Message message) {
        for (ClientHandler client : clientMap.values()) {
            client.sendMessage(message);
        }
    }

    // Lắng nghe kết nối từ client
    private void listenForClients() {
        while (true) {
            try {
                Socket socket = serverSocket.accept();
                System.out.println("Đã có kết nối từ " + socket.getInetAddress());
                ClientHandler clientHandler = new ClientHandler(socket, this, dbManager);
                new Thread(clientHandler).start();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    public static void main(String[] args) {
        new Server();
    }
}
