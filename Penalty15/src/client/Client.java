package client;

import client.GUI.GameRoomController;
import client.GUI.LoginController;
import client.GUI.MainController;
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

    // Controllers
    private LoginController loginController;
    private MainController mainController;
    private GameRoomController gameRoomController;

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
            } catch (IOException | ClassNotFoundException ex) {
                if (isRunning) {
                    ex.printStackTrace();
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
                        mainController.showMatchRequest((int) message.getContent());
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
                Platform.runLater(() -> {
                    if (gameRoomController != null) {
                        gameRoomController.endMatch((String) message.getContent());
                    }
                });
                break;
            case "play_again_request":
                Platform.runLater(() -> {
                    if (gameRoomController != null) {
                        gameRoomController.promptPlayAgain();
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
                Platform.runLater(() -> {
                    if (gameRoomController != null) {
                        gameRoomController.showMatchResult((String) message.getContent());
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



            // Các loại message khác
            // ...
        }
    }

    public void sendMessage(Message message) throws IOException {
        out.writeObject(message);
        out.flush();
    }

    public void showMainUI() {
        try {
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

            primaryStage.setScene(scene);
            primaryStage.setTitle("Penalty Shootout - Main");
            primaryStage.setMinWidth(400);
            primaryStage.setMinHeight(300);
            primaryStage.show();
            sendMessage(new Message("get_users", null));
        } catch (IOException e) {
            e.printStackTrace();
            showErrorAlert("Không thể tải giao diện chính.");
        }
    }

    public void showLoginUI() {
        try {
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

            primaryStage.setScene(scene);
            primaryStage.setTitle("Penalty Shootout - Login");
            primaryStage.show();

            // Loại bỏ đoạn mã khởi tạo kết nối ở đây

        } catch (IOException e) {
            e.printStackTrace();
            showErrorAlert("Không thể tải giao diện đăng nhập.");
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
            Scene scene = new Scene(root);

            URL cssLocation = GameRoomController.class.getResource("/resources/GUI/style.css");
            if (cssLocation != null) {
                scene.getStylesheets().add(cssLocation.toExternalForm());
                System.out.println("CSS file loaded: " + cssLocation.toExternalForm());
            } else {
                System.err.println("Cannot find CSS file: style.css");
            }

            primaryStage.setScene(scene);
            primaryStage.setTitle("Penalty Shootout - Game Room");
            primaryStage.show();

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
        if (in != null) {
            in.close();
        }
        if (out != null) {
            out.close();
        }
        if (socket != null && !socket.isClosed()) {
            socket.close();
        }
    }

    public static void main(String[] args) {
        javafx.application.Application.launch(ClientApp.class, args);
    }
}
