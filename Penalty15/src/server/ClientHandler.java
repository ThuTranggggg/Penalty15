package server;

import common.Match;
import common.MatchDetails;
import common.Message;
import common.User;

import java.io.*;
import java.net.Socket;
import java.sql.SQLException;
import java.util.List;
import javafx.util.Pair;

public class ClientHandler implements Runnable {
    private Socket socket;
    private Server server;
    private DatabaseManager dbManager;
    private ObjectInputStream in;
    private ObjectOutputStream out;
    private User user;
    private GameRoom gameRoom;
    private volatile boolean isRunning = true;

    public ClientHandler(Socket socket, Server server, DatabaseManager dbManager) {
        this.socket = socket;
        this.server = server;
        this.dbManager = dbManager;
        try {
            // Đặt ObjectOutputStream trước ObjectInputStream để tránh deadlock
            out = new ObjectOutputStream(socket.getOutputStream());
            out.flush(); // Đảm bảo ObjectOutputStream được khởi tạo trước
            in = new ObjectInputStream(socket.getInputStream());
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public User getUser() {
        return user;
    }

    // Trong phương thức run()
    @Override
    public void run() {
        try {
            while (isRunning) {
                Message message = (Message) in.readObject();
                if (message != null) {
                    handleMessage(message);
                }
            }
        } catch (IOException | ClassNotFoundException | SQLException e) {
            System.out.println("Kết nối với " + (user != null ? user.getUsername() : "client") + " bị ngắt.");
            isRunning = false; // Dừng vòng lặp
            if (gameRoom != null) {
                try {
                    gameRoom.handlePlayerDisconnect(this);
                } catch (IOException | SQLException ex) {
                    ex.printStackTrace();
                }
            }
        } finally {
            try {
                if (user != null) {
                    dbManager.updateUserStatus(user.getId(), "offline");
                    server.broadcast(new Message("status_update", user.getUsername() + " đã offline."));
                    server.removeClient(this);
                }
                if (socket != null && !socket.isClosed()) {
                    socket.close();
                }
            } catch (IOException | SQLException e) {
                e.printStackTrace();
            }
        }
    }

    private void handleMessage(Message message) throws IOException, SQLException {
        switch (message.getType()) {
            case "login":
                handleLogin(message);
                break;
            case "get_users":
                handleGetUsers();
                break;
            case "request_match":
                handleMatchRequest(message);
                break;
            case "match_response":
                handleMatchResponse(message);
                break;
            case "chat":
                handleChat(message);
                break;
            case "logout":
                handleLogout();
                break;
            case "shoot":
                handleShoot(message);
                break;
            case "goalkeeper":
                handleGoalkeeper(message);
                break;
            case "play_again_response":
                handlePlayAgainResponse(message);
                break;
            case "get_leaderboard":
                handleGetLeaderboard();
                break;
            case "get_match_history":
                handleGetMatchHistory();
                break;
            case "quit_game":
                handleQuitGame();
                break;
            case "get_user_matches":
                handleGetUserMatches();
                break;
            case "get_match_details":
                handleGetMatchDetails(message);
                break;
            case "timeout":
                handleHandleTimeout(message);
                break;
            case "return_to_main":
                // Không cần xử lý gì thêm ở server side cho thông báo này
                break;
            // Các loại message khác
            // ...
        }
    }

    private void handleHandleTimeout(Message message) throws IOException, SQLException {
        if (gameRoom != null) {
            if (message.getContent().equals("shooter")) {
                gameRoom.startShooterTimeout();
            } else if (message.getContent().equals("goalkeeper")) {
                gameRoom.startGoalkeeperTimeout();
            }
        }
    }

    private void handleGetMatchDetails(Message message) throws IOException, SQLException {
        int matchId = (int) message.getContent();
        List<MatchDetails> details = dbManager.getMatchDetails(matchId);
        sendMessage(new Message("match_details", details));
    }

    private void handleGetUserMatches() throws IOException, SQLException {
        List<Match> matches = dbManager.getUserMatches(user.getId());
        sendMessage(new Message("user_matches", matches));
    }

    private void handleQuitGame() throws IOException, SQLException {
        if (gameRoom != null) {
            gameRoom.handlePlayerQuit(this);
        }
    }

    private void handleGetMatchHistory() throws IOException, SQLException {
        List<MatchDetails> history = dbManager.getUserMatchHistory(user.getId());
        sendMessage(new Message("match_history", history));
    }

    private void handleGetLeaderboard() throws IOException, SQLException {
        List<User> leaderboard = dbManager.getLeaderboard();
        sendMessage(new Message("leaderboard", leaderboard));
    }

    private void handlePlayAgainResponse(Message message) throws SQLException, IOException {
        boolean playAgain = (boolean) message.getContent();
        if (gameRoom != null) {
            gameRoom.handlePlayAgainResponse(playAgain, this);
        }
    }

    private void handleLogin(Message message) throws IOException, SQLException {
        String[] credentials = (String[]) message.getContent();
        String username = credentials[0];
        String password = credentials[1];
        Pair<User, Boolean> pairAuthnticatedUser = dbManager.authenticate(username, password);
        User _user = pairAuthnticatedUser.getKey();
        Boolean isOffline = pairAuthnticatedUser.getValue();
        if (_user != null && isOffline == true) {
            this.user = _user;
            dbManager.updateUserStatus(user.getId(), "online");
            user.setStatus("online"); // Cập nhật trạng thái trong đối tượng user
            sendMessage(new Message("login_success", user));
            server.broadcast(new Message("status_update", user.getUsername() + " đã online."));
            server.addClient(user.getId(), this); // Thêm client vào danh sách server
        } else if (_user != null && isOffline == false) {
            sendMessage(new Message("login_failure", "Tài khoản được đăng nhập ở nơi khác"));
        } else {
            sendMessage(new Message("login_failure", "Tài khoản hoặc mật khẩu không đúng"));
        }
    }

    private void handleLogout() throws IOException, SQLException {
        if (user != null) {
            dbManager.updateUserStatus(user.getId(), "offline");
            user.setStatus("offline");
            server.broadcast(new Message("status_update", user.getUsername() + " đã offline."));
            if (socket != null && !socket.isClosed()) {
                sendMessage(new Message("logout_success", "Đăng xuất thành công."));
            }
            isRunning = false; // Dừng vòng lặp
            server.removeClient(this);
            socket.close();
        }
    }

    private void handleGetUsers() throws IOException, SQLException {
        List<User> users = dbManager.getUsers();
        sendMessage(new Message("user_list", users));
    }

    private void handleMatchRequest(Message message) throws IOException, SQLException {
        int opponentId = (int) message.getContent();
        System.out.println("Received match request from user ID: " + user.getId() + " to opponent ID: " + opponentId);
        ClientHandler opponent = server.getClientById(opponentId);
        if (opponent != null) {
            System.out.println("Opponent found: " + opponent.getUser().getUsername() + " - Status: "
                    + opponent.getUser().getStatus());
            if (opponent.getUser().getStatus().equals("online")) {
                opponent.sendMessage(new Message("match_request", user.getId()));
                System.out.println("Match request sent to " + opponent.getUser().getUsername());
            } else {
                sendMessage(new Message("match_response", "Người chơi không sẵn sàng."));
                System.out.println("Opponent is not online.");
            }
        } else {
            sendMessage(new Message("match_response", "Người chơi không tồn tại hoặc không online."));
            System.out.println("Opponent not found.");
        }
    }

    private void handleMatchResponse(Message message) throws IOException, SQLException {
        Object[] data = (Object[]) message.getContent();
        int requesterId = (int) data[0];
        boolean accepted = (boolean) data[1];
        ClientHandler requester = server.getClientById(requesterId);
        if (requester != null) {
            if (accepted) {
                // Tạo phòng chơi giữa this và requester
                GameRoom newGameRoom = new GameRoom(this, requester, dbManager);
                this.gameRoom = newGameRoom;
                requester.gameRoom = newGameRoom;

                // update ingame status and broadcast all client --VIETHUNG--
                this.user.setStatus("ingame");
                requester.user.setStatus("ingame");

                dbManager.updateUserStatus(user.getId(), "ingame");
                requester.dbManager.updateUserStatus(user.getId(), "ingame");

                server.broadcast(new Message("status_update", user.getUsername() + " is ingame"));
                server.broadcast(new Message("status_update", requester.user.getUsername() + " is ingame"));

                newGameRoom.startMatch();
            } else {
                requester.sendMessage(new Message("match_response", "Yêu cầu trận đấu của bạn đã bị từ chối."));
            }
        }
    }

    private void handleChat(Message message) {
        // Gửi lại tin nhắn tới tất cả client
        server.broadcast(new Message("chat", user.getUsername() + ": " + message.getContent()));
    }

    private void handleShoot(Message message) throws SQLException, IOException {
        if (gameRoom != null) {
            String shooterDir = (String) message.getContent();
            gameRoom.handleShot(shooterDir, this);
        }
    }

    private void handleGoalkeeper(Message message) throws SQLException, IOException {
        if (gameRoom != null) {
            String goalkeeperDir = (String) message.getContent();
            gameRoom.handleGoalkeeper(goalkeeperDir, this);
        }
    }

    public void sendMessage(Message message) {
        try {
            if (socket != null && !socket.isClosed()) {
                out.writeObject(message);
                out.flush();
            } else {
                System.out.println(
                        "Socket đã đóng, không thể gửi tin nhắn tới " + (user != null ? user.getUsername() : "client"));
            }
        } catch (IOException e) {
            System.out.println("Lỗi khi gửi tin nhắn tới " + (user != null ? user.getUsername() : "client") + ": "
                    + e.getMessage());
            // Không gọi lại handleLogout() ở đây để tránh đệ quy
            // Đánh dấu client là đã ngắt kết nối
            try {
                socket.close();
            } catch (IOException ex) {
                ex.printStackTrace();
            }
        }
    }

    public void clearGameRoom() {
        this.gameRoom = null;
    }

    public Server getServer() {
        return server;
    }

}
