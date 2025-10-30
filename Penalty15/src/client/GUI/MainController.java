package client.GUI;

import client.Client;
import common.Match;
import common.MatchDetails;
import common.Message;
import common.User;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.layout.HBox;
import javafx.scene.paint.Color;
import javafx.scene.shape.Circle;

import java.io.IOException;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import javafx.scene.control.cell.PropertyValueFactory;
import java.sql.Timestamp;
import javafx.application.Platform;

public class MainController {

    @FXML
    private TableColumn<Match, String> matchTimeColumn;

    @FXML
    private TableColumn<MatchDetails, String> timeColumn;

    @FXML
    private TextField searchField;
    @FXML
    private TableView<User> usersTable;
    @FXML
    private TableColumn<User, String> nameColumn;
    @FXML
    private TableColumn<User, Integer> pointsColumn;
    @FXML
    private TableColumn<User, String> statusColumn;
    @FXML
    private TableColumn<User, Void> actionColumn;
    @FXML
    private Label statusLabel;

    private Client client;
    private ObservableList<User> usersList = FXCollections.observableArrayList();

    @FXML
    private TableView<User> leaderboardTable;
    @FXML
    private TableColumn<User, Integer> lbRankColumn;
    @FXML
    private TableColumn<User, String> lbNameColumn;
    @FXML
    private TableColumn<User, Integer> lbPointsColumn;

    @FXML
    private TableView<MatchDetails> historyTable;
    @FXML
    private TableColumn<MatchDetails, Integer> roundColumn;
    @FXML
    private TableColumn<MatchDetails, String> roleColumn;
    @FXML
    private TableColumn<MatchDetails, String> directionColumn;
    @FXML
    private TableColumn<MatchDetails, String> historyResultColumn;

    @FXML
    private TableView<Match> matchesTable;
    @FXML
    private TableColumn<Match, Integer> matchIdColumn;
    @FXML
    private TableColumn<Match, String> opponentColumn;
    @FXML
    private TableColumn<Match, String> matchResultColumn;

    public void setClient(Client client) throws IOException {
        this.client = client;
        loadUsers();
        loadLeaderboard();
        loadUserMatches(); // Tải danh sách trận đấu
    }

    private void loadUserMatches() throws IOException {
        Message request = new Message("get_user_matches", null);
        client.sendMessage(request);
    }

    // Thêm phương thức để cập nhật bảng xếp hạng
    public void updateLeaderboard(List<User> leaderboard) {
        ObservableList<User> leaderboardList = FXCollections.observableArrayList(leaderboard);
        leaderboardTable.setItems(leaderboardList);
    }

    private void loadUsers() throws IOException {
        // Gửi yêu cầu lấy danh sách người chơi
        Message request = new Message("get_users", null);
        client.sendMessage(request);
    }

    @FXML
    private void handleLogout() throws IOException {
        client.getUser().setStatus("offline");
        // Gửi yêu cầu đăng xuất
        if (client.getUser() != null) {
            try {
                Message logoutMessage = new Message("logout", client.getUser().getId());
                client.sendMessage(logoutMessage);
                
                // Đợi một chút để message được gửi đi
                Thread.sleep(100);
            } catch (Exception e) {
                System.out.println("Lỗi khi gửi logout message: " + e.getMessage());
            } finally {
                // Đóng kết nối hiện tại
                client.closeConnection();
                
                // Hiển thị màn hình login (sẽ tự động tái kết nối)
                client.showLoginUI();
            }
        }
    }

    @FXML
    private void handleFilterOnline() {
        ObservableList<User> filtered = FXCollections.observableArrayList();
        for (User user : usersList) {
            if (user.getStatus().equalsIgnoreCase("online")) {
                filtered.add(user);
            }
        }
        usersTable.setItems(filtered);
    }

    @FXML
    private void handleSearch() {
        String keyword = searchField.getText().toLowerCase();
        if (keyword.isEmpty()) {
            usersTable.setItems(usersList);
            return;
        }
        ObservableList<User> filtered = FXCollections.observableArrayList();
        for (User user : usersList) {
            if (user.getUsername().toLowerCase().contains(keyword)) {
                filtered.add(user);
            }
        }
        usersTable.setItems(filtered);
    }

    // Cập nhật danh sách người chơi từ server
    public void updateUsersList(List<User> newUsers) {
        Platform.runLater(() -> {
            usersList.setAll(newUsers);
            usersTable.setItems(usersList);
            usersTable.refresh(); // Buộc bảng cập nhật lại
        });
    }

    // Cập nhật trạng thái người chơi
    public void updateStatus(String statusUpdate) {
        if (statusUpdate == null || statusUpdate.isEmpty()) {
            return;
        }
        String[] parts = statusUpdate.split(" ");
        if (parts.length >= 3) {
            String username = parts[0];
            String status = parts[2].replace(".", "");
            for (User user : usersList) {
                if (user.getUsername().equalsIgnoreCase(username)) {
                    user.setStatus(status);
                    usersTable.refresh();
                    break;
                }
            }
        }
    }

    // Hiển thị yêu cầu trận đấu
    public void showMatchRequest(int requesterId) {
        Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
        alert.setTitle("Yêu Cầu Trận Đấu");
        alert.setHeaderText("Bạn nhận được yêu cầu trận đấu từ người chơi ID: " + requesterId);
        alert.setContentText("Bạn có muốn đồng ý?");

        alert.showAndWait().ifPresent(response -> {
            boolean accepted = response == ButtonType.OK;
            Object[] data = { requesterId, accepted };
            Message responseMessage = new Message("match_response", data);
            try {
                client.sendMessage(responseMessage);
            } catch (IOException ex) {
                Logger.getLogger(MainController.class.getName()).log(Level.SEVERE, null, ex);
            }
        });
    }

    // Xử lý phản hồi trận đấu
    public void handleMatchResponse(String response) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle("Trận Đấu");
        alert.setHeaderText(null);
        alert.setContentText(response);
        alert.showAndWait();
    }

    @FXML
    private void initialize() {

        // Cấu hình bảng người chơi
        nameColumn.setCellValueFactory(new PropertyValueFactory<>("username"));
        
        // Styling cho cột điểm
        pointsColumn.setCellFactory(column -> new TableCell<User, Integer>() {
            @Override
            protected void updateItem(Integer item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                    setStyle("");
                } else {
                    setText(item.toString());
                    setStyle("-fx-font-weight: bold; -fx-text-fill: #0284c7; " +
                           "-fx-font-size: 14px; -fx-alignment: center;");
                }
            }
        });
        pointsColumn.setCellValueFactory(new PropertyValueFactory<>("points"));
        
        statusColumn.setCellValueFactory(new PropertyValueFactory<>("status"));

        // Custom cell factory cho statusColumn
        statusColumn.setCellFactory(column -> new TableCell<User, String>() {
            private final HBox hBox = new HBox(5);
            private final Circle circle = new Circle(5);
            private final Label label = new Label();

            {
                label.getStyleClass().add("status-label");
                label.setStyle("-fx-text-fill: black; -fx-font-weight: bold;");
                hBox.getChildren().addAll(circle, label);
            }

            @Override
            protected void updateItem(String status, boolean empty) {
                super.updateItem(status, empty);
                if (empty || status == null) {
                    setGraphic(null);
                    setText(null);
                } else {
                    Color color;
                    System.out.println(status);
                    switch (status.trim()) {
                        case "online":
                            color = Color.GREEN;
                            label.setStyle("-fx-text-fill: green; -fx-font-weight: bold;");
                            break;
                        case "ingame":
                            color = Color.RED;
                            label.setStyle("-fx-text-fill: red; -fx-font-weight: bold;");
                            break;
                        case "offline":
                            color = Color.GRAY;
                            label.setStyle("-fx-text-fill: gray; -fx-font-weight: bold;");
                            break;
                        default:
                            color = Color.BLACK;
                            label.setStyle("-fx-text-fill: black; -fx-font-weight: bold;");
                            break;
                    }
                    System.out.println(status + " " + color);
                    circle.setFill(color); // Cập nhật màu của Circle
                    label.setText(status); // Cập nhật văn bản của Label
                    setGraphic(hBox); // Đặt HBox chứa Circle và Label làm đồ họa của ô
                    setText(null); // Không cần văn bản mặc định cho ô
                }
            }

        });

        // Cấu hình cột Action với nút "Mời"
        actionColumn.setCellFactory(column -> new TableCell<User, Void>() {
            private final Button inviteButton = new Button("🎮 Mời");
            
            {
                inviteButton.setStyle(
                    "-fx-background-color: #0284c7; " +
                    "-fx-text-fill: white; " +
                    "-fx-font-weight: bold; " +
                    "-fx-font-size: 12px; " +
                    "-fx-padding: 6px 16px; " +
                    "-fx-border-radius: 8; " +
                    "-fx-background-radius: 8; " +
                    "-fx-cursor: hand;"
                );
                
                inviteButton.setOnMouseEntered(e -> 
                    inviteButton.setStyle(
                        "-fx-background-color: #0ea5e9; " +
                        "-fx-text-fill: white; " +
                        "-fx-font-weight: bold; " +
                        "-fx-font-size: 12px; " +
                        "-fx-padding: 6px 16px; " +
                        "-fx-border-radius: 8; " +
                        "-fx-background-radius: 8; " +
                        "-fx-cursor: hand;"
                    )
                );
                
                inviteButton.setOnMouseExited(e -> 
                    inviteButton.setStyle(
                        "-fx-background-color: #0284c7; " +
                        "-fx-text-fill: white; " +
                        "-fx-font-weight: bold; " +
                        "-fx-font-size: 12px; " +
                        "-fx-padding: 6px 16px; " +
                        "-fx-border-radius: 8; " +
                        "-fx-background-radius: 8; " +
                        "-fx-cursor: hand;"
                    )
                );
                
                inviteButton.setOnAction(event -> {
                    User user = getTableView().getItems().get(getIndex());
                    if (user.getId() != client.getUser().getId()) {
                        Message matchRequest = new Message("request_match", user.getId());
                        try {
                            client.sendMessage(matchRequest);
                        } catch (IOException ex) {
                            Logger.getLogger(MainController.class.getName()).log(Level.SEVERE, null, ex);
                        }
                    }
                });
            }
            
            @Override
            protected void updateItem(Void item, boolean empty) {
                super.updateItem(item, empty);
                if (empty) {
                    setGraphic(null);
                } else {
                    User user = getTableView().getItems().get(getIndex());
                    // Disable nút nếu là chính mình hoặc người chơi offline/ingame
                    if (user.getId() == client.getUser().getId()) {
                        inviteButton.setDisable(true);
                        inviteButton.setText("👤 Bạn");
                        inviteButton.setStyle(
                            "-fx-background-color: #e5e7eb; " +
                            "-fx-text-fill: #9ca3af; " +
                            "-fx-font-weight: bold; " +
                            "-fx-font-size: 12px; " +
                            "-fx-padding: 6px 16px; " +
                            "-fx-border-radius: 8; " +
                            "-fx-background-radius: 8;"
                        );
                    } else if (user.getStatus().equalsIgnoreCase("ingame")) {
                        inviteButton.setDisable(true);
                        inviteButton.setText("🎮 Đang chơi");
                        inviteButton.setStyle(
                            "-fx-background-color: #fef2f2; " +
                            "-fx-text-fill: #dc2626; " +
                            "-fx-font-weight: bold; " +
                            "-fx-font-size: 11px; " +
                            "-fx-padding: 6px 12px; " +
                            "-fx-border-radius: 8; " +
                            "-fx-background-radius: 8;"
                        );
                    } else if (user.getStatus().equalsIgnoreCase("offline")) {
                        inviteButton.setDisable(true);
                        inviteButton.setText("📴 Offline");
                        inviteButton.setStyle(
                            "-fx-background-color: #f3f4f6; " +
                            "-fx-text-fill: #6b7280; " +
                            "-fx-font-weight: bold; " +
                            "-fx-font-size: 11px; " +
                            "-fx-padding: 6px 12px; " +
                            "-fx-border-radius: 8; " +
                            "-fx-background-radius: 8;"
                        );
                    } else {
                        inviteButton.setDisable(false);
                        inviteButton.setText("🎮 Mời");
                    }
                    setGraphic(inviteButton);
                    setStyle("-fx-alignment: center;");
                }
            }
        });

        // Cấu hình bảng xếp hạng với styling đặc biệt cho top 3
        lbRankColumn.setCellFactory(column -> new TableCell<User, Integer>() {
            @Override
            protected void updateItem(Integer item, boolean empty) {
                super.updateItem(item, empty);
                if (empty) {
                    setText(null);
                    setGraphic(null);
                    setStyle("");
                } else {
                    int rank = getIndex() + 1;
                    Label rankLabel = new Label();
                    
                    // Styling cho top 3
                    switch (rank) {
                        case 1:
                            rankLabel.setText("🥇 " + rank);
                            rankLabel.setStyle("-fx-font-size: 16px; -fx-font-weight: bold; " +
                                             "-fx-text-fill: #f59e0b; " +
                                             "-fx-padding: 5px;");
                            setStyle("-fx-background-color: #fffbeb;");
                            break;
                        case 2:
                            rankLabel.setText("🥈 " + rank);
                            rankLabel.setStyle("-fx-font-size: 15px; -fx-font-weight: bold; " +
                                             "-fx-text-fill: #94a3b8; " +
                                             "-fx-padding: 5px;");
                            setStyle("-fx-background-color: #f8fafc;");
                            break;
                        case 3:
                            rankLabel.setText("🥉 " + rank);
                            rankLabel.setStyle("-fx-font-size: 15px; -fx-font-weight: bold; " +
                                             "-fx-text-fill: #d97706; " +
                                             "-fx-padding: 5px;");
                            setStyle("-fx-background-color: #fef3c7;");
                            break;
                        default:
                            rankLabel.setText(String.valueOf(rank));
                            rankLabel.setStyle("-fx-font-size: 14px; -fx-font-weight: bold; " +
                                             "-fx-text-fill: #64748b;");
                            setStyle("");
                            break;
                    }
                    setGraphic(rankLabel);
                    setText(null);
                }
            }
        });
        
        // Styling cho tên người chơi trong bảng xếp hạng
        lbNameColumn.setCellFactory(column -> new TableCell<User, String>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                    setStyle("");
                } else {
                    setText(item);
                    int rank = getIndex() + 1;
                    if (rank <= 3) {
                        setStyle("-fx-font-weight: bold; -fx-font-size: 14px;");
                    } else {
                        setStyle("-fx-font-size: 13px;");
                    }
                }
            }
        });
        lbNameColumn.setCellValueFactory(new PropertyValueFactory<>("username"));
        
        // Styling cho điểm trong bảng xếp hạng
        lbPointsColumn.setCellFactory(column -> new TableCell<User, Integer>() {
            @Override
            protected void updateItem(Integer item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                    setStyle("");
                } else {
                    setText(item.toString());
                    int rank = getIndex() + 1;
                    if (rank <= 3) {
                        setStyle("-fx-font-weight: bold; -fx-font-size: 14px; " +
                               "-fx-text-fill: #0284c7; -fx-alignment: center;");
                    } else {
                        setStyle("-fx-font-size: 13px; -fx-alignment: center;");
                    }
                }
            }
        });
        lbPointsColumn.setCellValueFactory(new PropertyValueFactory<>("points"));

        // Cấu hình bảng lịch sử đấu
        roundColumn.setCellValueFactory(new PropertyValueFactory<>("round"));
        
        // Styling cho vai trò (Sút/Bắt)
        roleColumn.setCellFactory(column -> new TableCell<MatchDetails, String>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                    setGraphic(null);
                    setStyle("");
                } else {
                    Label roleLabel = new Label();
                    if (item.equals("Sút")) {
                        roleLabel.setText("⚽ " + item);
                        roleLabel.setStyle("-fx-font-weight: bold; -fx-text-fill: #0ea5e9;");
                    } else {
                        roleLabel.setText("🛡️ " + item);
                        roleLabel.setStyle("-fx-font-weight: bold; -fx-text-fill: #f59e0b;");
                    }
                    setGraphic(roleLabel);
                    setText(null);
                }
            }
        });
        roleColumn.setCellValueFactory(cellData -> {
            MatchDetails md = cellData.getValue();
            String role;
            // Xác định role dựa trên vòng (round) và ID
            // Vòng lẻ (1,3,5,7,9): Player1 sút, Player2 bắt
            // Vòng chẵn (2,4,6,8,10): Player1 bắt, Player2 sút
            if (md.getRound() % 2 == 1) {
                // Vòng lẻ: shooter là người có ID nhỏ hơn (player1)
                role = (md.getShooterId() == client.getUser().getId()) ? "Sút" : "Bắt";
            } else {
                // Vòng chẵn: shooter là người có ID lớn hơn (player2)
                role = (md.getShooterId() == client.getUser().getId()) ? "Sút" : "Bắt";
            }
            return new SimpleStringProperty(role);
        });
        
        directionColumn.setCellValueFactory(cellData -> {
            MatchDetails md = cellData.getValue();
            String direction = (md.getShooterId() == client.getUser().getId()) ? md.getShooterDirection()
                    : md.getGoalkeeperDirection();
            return new SimpleStringProperty(direction);
        });
        
        // Styling cho kết quả (Win/Lose)
        historyResultColumn.setCellFactory(column -> new TableCell<MatchDetails, String>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                    setGraphic(null);
                    setStyle("");
                } else {
                    Label resultLabel = new Label();
                    if (item.equalsIgnoreCase("win")) {
                        resultLabel.setText("✅ THẮNG");
                        resultLabel.setStyle("-fx-font-weight: bold; -fx-text-fill: #16a34a; " +
                                           "-fx-padding: 4px 8px; -fx-background-color: #f0fdf4; " +
                                           "-fx-background-radius: 5;");
                    } else {
                        resultLabel.setText("❌ THUA");
                        resultLabel.setStyle("-fx-font-weight: bold; -fx-text-fill: #dc2626; " +
                                           "-fx-padding: 4px 8px; -fx-background-color: #fef2f2; " +
                                           "-fx-background-radius: 5;");
                    }
                    setGraphic(resultLabel);
                    setText(null);
                }
            }
        });
        historyResultColumn.setCellValueFactory(cellData -> {
            MatchDetails md = cellData.getValue();
            String result = "";
            if (md.getShooterId() == client.getUser().getId()) {
                if (md.getShooterDirection() != null && md.getGoalkeeperDirection() != null
                        && md.getShooterDirection().equalsIgnoreCase(md.getGoalkeeperDirection())) {
                    if (md.getRound() % 2 == 1)
                        result = "lose";
                    else
                        result = "win";
                } else {
                    if (md.getRound() % 2 == 1)
                        result = "win";
                    else
                        result = "lose";
                }

            } else {
                if (md.getShooterDirection() != null && md.getGoalkeeperDirection() != null
                        && md.getShooterDirection().equalsIgnoreCase(md.getGoalkeeperDirection())) {
                    if (md.getRound() % 2 == 1)
                        result = "win";
                    else
                        result = "lose";
                } else {
                    if (md.getRound() % 2 == 1)
                        result = "lose";
                    else
                        result = "win";
                }
            }
            return new SimpleStringProperty(result);
        });

        // Cấu hình bảng danh sách trận đấu
        matchIdColumn.setCellValueFactory(new PropertyValueFactory<>("id"));
        
        opponentColumn.setCellValueFactory(cellData -> {
            Match match = cellData.getValue();
            String opponentName = match.getOpponentName(client.getUser().getId());
            return new SimpleStringProperty(opponentName);
        });
        
        // Styling cho kết quả trận đấu
        matchResultColumn.setCellFactory(column -> new TableCell<Match, String>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                    setGraphic(null);
                    setStyle("");
                } else {
                    Label resultLabel = new Label();
                    if (item.equalsIgnoreCase("win") || item.contains("Thắng")) {
                        resultLabel.setText("🏆 THẮNG");
                        resultLabel.setStyle("-fx-font-weight: bold; -fx-text-fill: white; " +
                                           "-fx-padding: 6px 12px; -fx-background-color: #16a34a; " +
                                           "-fx-background-radius: 8;");
                    } else if (item.equalsIgnoreCase("lose") || item.contains("Thua")) {
                        resultLabel.setText("💔 THUA");
                        resultLabel.setStyle("-fx-font-weight: bold; -fx-text-fill: white; " +
                                           "-fx-padding: 6px 12px; -fx-background-color: #dc2626; " +
                                           "-fx-background-radius: 8;");
                    } else {
                        resultLabel.setText("⚖️ HÒA");
                        resultLabel.setStyle("-fx-font-weight: bold; -fx-text-fill: white; " +
                                           "-fx-padding: 6px 12px; -fx-background-color: #64748b; " +
                                           "-fx-background-radius: 8;");
                    }
                    setGraphic(resultLabel);
                    setText(null);
                }
            }
        });
        matchResultColumn.setCellValueFactory(cellData -> {
            Match match = cellData.getValue();
            String result = match.getResult(client.getUser().getId());
            return new SimpleStringProperty(result);
        });

        // Cấu hình cột thời gian cho matchesTable
        matchTimeColumn.setCellValueFactory(cellData -> {
            Timestamp time = cellData.getValue().getTime();
            return new SimpleStringProperty(time != null ? time.toString() : "");
        });

        // Cấu hình cột thời gian cho historyTable
        timeColumn.setCellValueFactory(cellData -> {
            Timestamp time = cellData.getValue().getTime();
            return new SimpleStringProperty(time != null ? time.toString() : "");
        });

        // Sự kiện click để hiển thị chi tiết trận đấu (sửa đổi để sử dụng listener)
        matchesTable.getSelectionModel().selectedItemProperty().addListener((observable, oldValue, newValue) -> {
            if (newValue != null) {
                Match clickedMatch = newValue;
                try {
                    Message request = new Message("get_match_details", clickedMatch.getId());
                    client.sendMessage(request);
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        });
    }

    public void showMatchDetails(List<MatchDetails> details) {
        ObservableList<MatchDetails> detailsList = FXCollections.observableArrayList(details);
        historyTable.setItems(detailsList);
    }

    public void updateMatchesList(List<Match> matches) {
        ObservableList<Match> matchesList = FXCollections.observableArrayList(matches);
        matchesTable.setItems(matchesList);
    }

    private void loadLeaderboard() throws IOException {
        Message request = new Message("get_leaderboard", null);
        client.sendMessage(request);
    }

    public void updateMatchHistory(List<MatchDetails> history) {
        ObservableList<MatchDetails> historyList = FXCollections.observableArrayList(history);
        historyTable.setItems(historyList);
    }
}
