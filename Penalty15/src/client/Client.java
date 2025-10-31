package client;

import client.GUI.GameRoomController;
import client.GUI.LoginController;
import client.GUI.MainController;
import client.GUI.RegisterController;
import common.Match;
import common.MatchDetails;
import common.Message;
import common.User;
import javafx.application.Platform;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;
import javafx.scene.control.Alert;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.Socket;
import java.net.URL;
import java.util.List;

public class Client {

    private Socket socket;
    private ObjectOutputStream out;
    private ObjectInputStream in;
    private User user;
    private Stage primaryStage;
    private volatile boolean isInGame = false; // Track nếu đang trong game

    // Controllers
    private LoginController loginController;
    private MainController mainController;
    private GameRoomController gameRoomController;
    private RegisterController registerController;

    private volatile boolean isRunning = true;

    public Client(Stage primaryStage) {
        this.primaryStage = primaryStage;
    }

    public void showErrorAlert(String message) {
        Platform.runLater(() -> {
            Alert alert = new Alert(Alert.AlertType.ERROR);
            alert.setTitle("Lỗi");
            alert.setHeaderText(null);
            alert.setContentText(message);
            alert.showAndWait();
        });
    }

    public void startConnection(String address, int port) {
        try {
            socket = new Socket(address, port);
            in = new ObjectInputStream(socket.getInputStream());
            out = new ObjectOutputStream(socket.getOutputStream());
            out.flush();
            isRunning = true; // Đặt lại isRunning thành true
            listenForMessages();
        } catch (IOException e) {
            e.printStackTrace();
            showErrorAlert("Không thể kết nối tới server.");
        }
    }

    private void listenForMessages() {
        new Thread(() -> {
            try {
                while (isRunning) {
                    Message message = (Message) in.readObject();
                    if (message != null) {
                        handleMessage(message);
                    }
                }
            } catch (java.io.EOFException ex) {
                // EOFException xảy ra khi server đóng kết nối
                if (isRunning) {
                    System.out.println("Server đã đóng kết nối.");

                    // Nếu đang trong game, cố gắng xử lý fallback: đưa người chơi về MainUI
                    if (isInGame) {
                        System.out.println("⚠️ Server đóng kết nối trong game - thực hiện fallback về MainUI");
                        isInGame = false;
                        // Đóng kết nối cục bộ và chuyển về MainUI mà KHÔNG gọi sendMessage
                        Platform.runLater(() -> {
                            showErrorAlert("Kết nối tới server đã bị gián đoạn trong trận đấu. Trở về giao diện chính.");
                            try {
                                closeConnection();
                            } catch (IOException e) {
                                e.printStackTrace();
                            }
                            try {
                                showMainUI();
                            } catch (Exception e) {
                                e.printStackTrace();
                            }
                        });
                    } else {
                        // Chỉ logout khi KHÔNG trong game
                        Platform.runLater(() -> {
                            try {
                                closeConnection();
                                showLoginUI();
                            } catch (Exception e) {
                                e.printStackTrace();
                            }
                        });
                    }
                } else {
                    System.out.println("Đã đóng kết nối, dừng luồng lắng nghe.");
                }
            } catch (IOException | ClassNotFoundException ex) {
                if (isRunning) {
                    System.err.println("Lỗi kết nối: " + ex.getMessage());

                    // Nếu đang trong game, xử lý fallback giống EOFException
                    if (isInGame) {
                        System.out.println("⚠️ Mất kết nối trong game - thực hiện fallback về MainUI");
                        isInGame = false;
                        Platform.runLater(() -> {
                            showErrorAlert("Kết nối tới server bị mất trong trận đấu. Trở về giao diện chính.");
                            try {
                                closeConnection();
                            } catch (IOException e) {
                                e.printStackTrace();
                            }
                            try {
                                showMainUI();
                            } catch (Exception e) {
                                e.printStackTrace();
                            }
                        });
                    } else {
                        // Chỉ khi KHÔNG trong game mới cleanup và về login
                        try {
                            closeConnection(); // Đóng kết nối hiện tại
                        } catch (IOException e) {
                            e.printStackTrace();
                        }
                        Platform.runLater(() -> {
                            showErrorAlert("Kết nối tới server bị mất.");
                            try {
                                showLoginUI(); // Hiển thị giao diện đăng nhập và tái kết nối
                            } catch (Exception e) {
                                e.printStackTrace();
                            }
                        });
                    }
                } else {
                    System.out.println("Đã đóng kết nối, dừng luồng lắng nghe.");
                }
            }
        }).start();
    }

    private void handleMessage(Message message) {
        System.out.println("Received message: " + message.getType() + " - " + message.getContent());
        if (message == null) {
            return;
        }
        switch (message.getType()) {
            case "login_success":
                this.user = (User) message.getContent();
                Platform.runLater(() -> showMainUI());
                break;
            case "login_failure":
                Platform.runLater(() -> {
                    if (loginController != null) {
                        loginController.showError((String) message.getContent());
                    }
                });
                break;
            case "register_success":
                Platform.runLater(() -> {
                    if (registerController != null) {
                        registerController.showSuccess((String) message.getContent());
                    }
                });
                break;
            case "register_failure":
                Platform.runLater(() -> {
                    if (registerController != null) {
                        registerController.showError((String) message.getContent());
                    }
                });
                break;
            case "user_list":
                List<User> users = (List<User>) message.getContent();
                Platform.runLater(() -> {
                    if (mainController != null) {
                        mainController.updateUsersList(users);
                    }
                });
                break;
            case "status_update":
                Platform.runLater(() -> {
                    if (mainController != null) {
                        mainController.updateStatus((String) message.getContent());
                    }
                });
                break;
            case "match_request":
                Platform.runLater(() -> {
                    if (mainController != null) {
                        // Parse request info: ID|Username
                        String requestInfo = (String) message.getContent();
                        String[] parts = requestInfo.split("\\|");
                        int requesterId = Integer.parseInt(parts[0]);
                        String requesterName = parts.length > 1 ? parts[1] : "ID: " + requesterId;
                        mainController.showMatchRequest(requesterId, requesterName);
                    }
                });
                break;
            case "match_response":
                Platform.runLater(() -> {
                    if (mainController != null) {
                        mainController.handleMatchResponse((String) message.getContent());
                    }
                });
                break;
            case "chat":
                Platform.runLater(() -> {
                    if (gameRoomController != null) {
                        gameRoomController.updateChat((String) message.getContent());
                    }
                });
                break;
            case "match_start":
                isInGame = true; // Đánh dấu đang trong game
                Platform.runLater(() -> {
                    showGameRoomUI((String) message.getContent());
                });
                break;
            case "kick_result":
                Platform.runLater(() -> {
                    if (gameRoomController != null) {
                        String[] result = ((String) message.getContent()).split("-");
                        if (result[0].equals("win")) {
                            gameRoomController.animateShootVao(result[1], result[2]);
                        } else {
                            gameRoomController.animateShootKhongVao(result[1], result[2]);
                        }
                    }
                });
                break;
            case "round_result":
                Platform.runLater(() -> {
                    if (gameRoomController != null) {
                        gameRoomController.showRoundResult((String) message.getContent());
                    }
                });
                break;
            case "match_end":
                System.out.println("📨 Client nhận message: match_end");
                isInGame = false; // Đánh dấu ra khỏi game
                Platform.runLater(() -> {
                    if (gameRoomController != null) {
                        System.out.println("✅ Gọi endMatch() với content: " + message.getContent());
                        gameRoomController.endMatch((String) message.getContent());
                    } else {
                        System.err.println("❌ gameRoomController is null!");
                    }
                });
                break;
            case "play_again_request":
                System.out.println("📨 Client nhận message: play_again_request");
                Platform.runLater(() -> {
                    if (gameRoomController != null) {
                        System.out.println("✅ Gọi promptPlayAgain()");
                        gameRoomController.promptPlayAgain();
                    } else {
                        System.err.println("❌ gameRoomController is null!");
                    }
                });
                break;
            case "rematch_declined":
                Platform.runLater(() -> {
                    if (gameRoomController != null) {
                        gameRoomController.handleRematchDeclined((String) message.getContent());
                    }
                });
                break;
            case "leaderboard":
                List<User> leaderboard = (List<User>) message.getContent();
                Platform.runLater(() -> {
                    if (mainController != null) {
                        mainController.updateLeaderboard(leaderboard);
                    }
                });
                break;

            case "match_history":
                List<MatchDetails> history = (List<MatchDetails>) message.getContent();
                Platform.runLater(() -> {
                    if (mainController != null) {
                        mainController.updateMatchHistory(history);
                    }
                });
                break;

            case "user_matches":
                List<Match> matches = (List<Match>) message.getContent();
                Platform.runLater(() -> {
                    if (mainController != null) {
                        mainController.updateMatchesList(matches);
                    }
                });
                break;
            case "match_details":
                List<MatchDetails> details = (List<MatchDetails>) message.getContent();
                Platform.runLater(() -> {
                    if (mainController != null) {
                        mainController.showMatchDetails(details);
                    }
                });
                break;

            case "update_score":
                int[] scores = (int[]) message.getContent();
                Platform.runLater(() -> {
                    if (gameRoomController != null) {
                        gameRoomController.updateScore(scores);
                    }
                });
                break;
            case "return_to_main":
                Platform.runLater(() -> {
                    try {
                        showMainUI();
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                });
                break;
            case "match_result":
                System.out.println("📨 Client nhận message: match_result = " + message.getContent());
                Platform.runLater(() -> {
                    if (gameRoomController != null) {
                        System.out.println("✅ Gọi showMatchResult()");
                        gameRoomController.showMatchResult((String) message.getContent());
                    } else {
                        System.err.println("❌ gameRoomController is null!");
                    }
                });
                break;

            case "your_turn":
                int duration = (int) message.getContent();
                Platform.runLater(() -> {
                    if (gameRoomController != null) {
                        gameRoomController.promptYourTurn(duration);
                    }
                });
                break;
            case "goalkeeper_turn":
                int duration1 = (int) message.getContent();
                Platform.runLater(() -> {
                    if (gameRoomController != null) {
                        gameRoomController.promptGoalkeeperTurn(duration1);
                    }
                });
                break;

            case "opponent_turn":
                int duration2 = (int) message.getContent();
                Platform.runLater(() -> {
                    if (gameRoomController != null) {
                        gameRoomController.handleOpponentTurn(duration2);
                    }
                });
                break;
                
            case "timeout":
                Platform.runLater(() -> {
                    if (gameRoomController != null) {
                        gameRoomController.handleTimeout((String) message.getContent());
                    }
                });
                break;
            case "opponent_timeout":
                Platform.runLater(() -> {
                    if (gameRoomController != null) {
                        gameRoomController.handleOpponentTimeout((String) message.getContent());
                    }
                });
                break;
            
            case "force_logout":
                Platform.runLater(() -> {
                    showErrorAlert((String) message.getContent());
                    try {
                        closeConnection();
                        showLoginUI();
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                });
                break;



            // Các loại message khác
            // ...
        }
    }

    public void sendMessage(Message message) throws IOException {
        if (socket != null && !socket.isClosed() && out != null) {
            try {
                out.writeObject(message);
                out.flush();
            } catch (IOException e) {
                if (isRunning) {
                    throw e; // Chỉ throw lỗi nếu đang chạy
                }
                // Bỏ qua lỗi nếu đang đóng kết nối
                System.out.println("Không thể gửi message - kết nối đã đóng: " + e.getMessage());
            }
        } else {
            System.out.println("Không thể gửi message - socket đã đóng");
        }
    }

    public void showMainUI() {
        try {
            isInGame = false; // Reset game state khi về main UI
            
            System.out.println("Loading MainUI.fxml...");
            FXMLLoader loader = new FXMLLoader(MainController.class.getResource("/resources/GUI/MainUI.fxml"));
            Parent root = loader.load();
            mainController = loader.getController();

            if (mainController == null) {
                System.err.println("Controller is null for MainUI.fxml");
                showErrorAlert("Không thể tải controller giao diện chính.");
                return;
            }

            mainController.setClient(this);
            Scene scene = new Scene(root);

            URL cssLocation = MainController.class.getResource("/resources/GUI/style.css");
            if (cssLocation != null) {
                scene.getStylesheets().add(cssLocation.toExternalForm());
                System.out.println("CSS file loaded: " + cssLocation.toExternalForm());
            } else {
                System.err.println("Cannot find CSS file: style.css");
            }
            
            // Configure stage
            primaryStage.setTitle("Penalty Shootout - Main");
            primaryStage.setResizable(true);
            primaryStage.setMinWidth(1200);
            primaryStage.setMinHeight(700);
            primaryStage.setScene(scene);
            
            // Show then maximize
            if (!primaryStage.isShowing()) {
                primaryStage.show();
            }
            Platform.runLater(() -> {
                primaryStage.setMaximized(true);
            });
            
            sendMessage(new Message("get_users", null));
        } catch (IOException e) {
            e.printStackTrace();
            showErrorAlert("Không thể tải giao diện chính.");
        }
    }

    // Utility for controllers to check connection state before attempting send
    public boolean isConnected() {
        return socket != null && !socket.isClosed() && out != null;
    }

    public void showLoginUI() {
        try {
            isInGame = false; // Reset game state khi về login UI
            
            System.out.println("Loading LoginUI.fxml...");
            FXMLLoader loader = new FXMLLoader(LoginController.class.getResource("/resources/GUI/LoginUI.fxml"));
            Parent root = loader.load();
            loginController = loader.getController();

            if (loginController == null) {
                System.err.println("Controller is null for LoginUI.fxml");
                showErrorAlert("Không thể tải controller giao diện đăng nhập.");
                return;
            }

            loginController.setClient(this);
            Scene scene = new Scene(root);

            URL cssLocation = LoginController.class.getResource("/resources/GUI/style.css");
            if (cssLocation != null) {
                scene.getStylesheets().add(cssLocation.toExternalForm());
                System.out.println("CSS file loaded: " + cssLocation.toExternalForm());
            } else {
                System.err.println("Cannot find CSS file: style.css");
            }
            
            // Configure stage
            primaryStage.setTitle("Penalty Shootout - Login");
            primaryStage.setResizable(true);
            primaryStage.setMinWidth(1000);
            primaryStage.setMinHeight(650);
            primaryStage.setScene(scene);
            
            // Show then maximize
            if (!primaryStage.isShowing()) {
                primaryStage.show();
            }
            Platform.runLater(() -> {
                primaryStage.setMaximized(true);
            });

            // Tái kết nối đến server nếu socket đã đóng
            if (socket == null || socket.isClosed()) {
                System.out.println("Đang tái kết nối đến server...");
                startConnection("localhost", 12345);
            }

        } catch (IOException e) {
            e.printStackTrace();
            showErrorAlert("Không thể tải giao diện đăng nhập.");
        }
    }

    public void showRegisterUI() {
        try {
            System.out.println("Loading RegisterUI.fxml...");
            System.out.println("RegisterController class: " + RegisterController.class);
            URL fxmlUrl = RegisterController.class.getResource("/resources/GUI/RegisterUI.fxml");
            System.out.println("FXML URL: " + fxmlUrl);
            
            if (fxmlUrl == null) {
                System.err.println("Cannot find RegisterUI.fxml at /resources/GUI/RegisterUI.fxml");
                showErrorAlert("Không tìm thấy file giao diện đăng ký.");
                return;
            }
            
            FXMLLoader loader = new FXMLLoader(fxmlUrl);
            Parent root = loader.load();
            registerController = loader.getController();

            if (registerController == null) {
                System.err.println("Controller is null for RegisterUI.fxml");
                showErrorAlert("Không thể tải controller giao diện đăng ký.");
                return;
            }

            registerController.setClient(this);
            Scene scene = new Scene(root);

            URL cssLocation = RegisterController.class.getResource("/resources/GUI/style.css");
            if (cssLocation != null) {
                scene.getStylesheets().add(cssLocation.toExternalForm());
                System.out.println("CSS file loaded: " + cssLocation.toExternalForm());
            } else {
                System.err.println("Cannot find CSS file: style.css");
            }
            
            // Configure stage
            primaryStage.setTitle("Penalty Shootout - Register");
            primaryStage.setResizable(true);
            primaryStage.setMinWidth(1000);
            primaryStage.setMinHeight(650);
            primaryStage.setScene(scene);
            
            // Show then maximize
            if (!primaryStage.isShowing()) {
                primaryStage.show();
            }
            Platform.runLater(() -> {
                primaryStage.setMaximized(true);
            });

            // Đảm bảo có kết nối đến server
            if (socket == null || socket.isClosed()) {
                System.out.println("Đang kết nối đến server...");
                startConnection("localhost", 12345);
            }

        } catch (IOException e) {
            e.printStackTrace();
            showErrorAlert("Không thể tải giao diện đăng ký: " + e.getMessage());
        }
    }

    public void showGameRoomUI(String startMessage) {
        try {
            System.out.println("Loading GameRoomUI.fxml...");
            FXMLLoader loader = new FXMLLoader(GameRoomController.class.getResource("/resources/GUI/GameRoomUI.fxml"));
            Parent root = loader.load();
            gameRoomController = loader.getController();

            if (gameRoomController == null) {
                System.err.println("Controller is null for GameRoomUI.fxml");
                showErrorAlert("Không thể tải controller giao diện phòng chơi.");
                return;
            }

            gameRoomController.setClient(this);
            
            // Parse opponent name from startMessage (format: "message|opponentName")
            String opponentName = "Đối thủ";
            if (startMessage.contains("|")) {
                String[] parts = startMessage.split("\\|");
                if (parts.length > 1) {
                    opponentName = parts[1];
                    startMessage = parts[0]; // Keep only the message part
                }
            }
            gameRoomController.setOpponentName(opponentName);
            
            Scene scene = new Scene(root);

            URL cssLocation = GameRoomController.class.getResource("/resources/GUI/style.css");
            if (cssLocation != null) {
                scene.getStylesheets().add(cssLocation.toExternalForm());
                System.out.println("CSS file loaded: " + cssLocation.toExternalForm());
            } else {
                System.err.println("Cannot find CSS file: style.css");
            }
            
            // Configure stage
            primaryStage.setTitle("Penalty Shootout - Game Room");
            primaryStage.setResizable(true);
            primaryStage.setMinWidth(1400);
            primaryStage.setMinHeight(800);
            primaryStage.setScene(scene);
            
            // Show then maximize
            if (!primaryStage.isShowing()) {
                primaryStage.show();
            }
            Platform.runLater(() -> {
                primaryStage.setMaximized(true);
            });

            // Hiển thị thông báo vai trò
            gameRoomController.showStartMessage(startMessage);

        } catch (IOException e) {
            e.printStackTrace();
            showErrorAlert("Không thể tải giao diện phòng chơi.");
        }
    }

    public User getUser() {
        return user;
    }

    public void closeConnection() throws IOException {
        isRunning = false; // Dừng luồng lắng nghe
        try {
            if (in != null) {
                in.close();
            }
        } catch (IOException e) {
            System.out.println("Lỗi khi đóng input stream: " + e.getMessage());
        }
        try {
            if (out != null) {
                out.close();
            }
        } catch (IOException e) {
            System.out.println("Lỗi khi đóng output stream: " + e.getMessage());
        }
        try {
            if (socket != null && !socket.isClosed()) {
                socket.close();
            }
        } catch (IOException e) {
            System.out.println("Lỗi khi đóng socket: " + e.getMessage());
        }
    }

    public static void main(String[] args) {
        javafx.application.Application.launch(ClientApp.class, args);
    }
}
