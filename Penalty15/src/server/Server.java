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
            System.out.println("✅ Server đã khởi động thành công trên cổng " + PORT);
            System.out.println("🎮 Đang chờ kết nối từ clients...");
            listenForClients();
        } catch (IOException e) {
            System.err.println("❌ LỖI: Không thể khởi động server trên cổng " + PORT);
            System.err.println("💡 Nguyên nhân: Cổng " + PORT + " đã được sử dụng bởi tiến trình khác");
            System.err.println("🔧 Giải pháp:");
            System.err.println("   1. Tắt tiến trình server cũ đang chạy");
            System.err.println("   2. Hoặc thay đổi số cổng PORT trong Server.java");
            System.err.println("\n📋 Chi tiết lỗi:");
            e.printStackTrace();
        } catch (SQLException e) {
            System.err.println("❌ LỖI: Không thể kết nối đến database");
            System.err.println("💡 Kiểm tra:");
            System.err.println("   1. Database đã được khởi động chưa");
            System.err.println("   2. Thông tin kết nối trong DatabaseManager có đúng không");
            System.err.println("\n📋 Chi tiết lỗi:");
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
                System.out.println("✅ Đã có kết nối từ " + socket.getInetAddress());
                ClientHandler clientHandler = new ClientHandler(socket, this, dbManager);
                new Thread(clientHandler).start();
            } catch (IOException e) {
                if (!serverSocket.isClosed()) {
                    System.err.println("❌ Lỗi khi chấp nhận kết nối:");
                    e.printStackTrace();
                }
                break;
            }
        }
    }

    // Phương thức tắt server đúng cách
    public void shutdown() {
        try {
            System.out.println("🛑 Đang tắt server...");
            
            // Ngắt kết nối tất cả clients
            for (ClientHandler client : clientMap.values()) {
                client.disconnect();
            }
            
            // Đóng server socket
            if (serverSocket != null && !serverSocket.isClosed()) {
                serverSocket.close();
            }
            
            System.out.println("✅ Server đã tắt thành công");
        } catch (IOException e) {
            System.err.println("❌ Lỗi khi tắt server:");
            e.printStackTrace();
        }
    }

    public static void main(String[] args) {
        new Server();
    }
}
