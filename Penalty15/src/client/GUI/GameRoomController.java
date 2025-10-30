package client.GUI;

import client.Client;
import common.Message;
import java.io.File;
import java.io.IOException;
import java.util.Optional;
import java.util.logging.Level;
import java.util.logging.Logger;
import javafx.animation.KeyFrame;
import javafx.animation.ParallelTransition;
import javafx.animation.PathTransition;
import javafx.animation.PauseTransition;
import javafx.animation.SequentialTransition;
import javafx.animation.Timeline;
import javafx.animation.TranslateTransition;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.Group;
import javafx.scene.control.*;
import javafx.scene.control.Alert.AlertType;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.Pane;
import javafx.scene.media.AudioClip;
import javafx.scene.media.Media;
import javafx.scene.media.MediaPlayer;
import javafx.scene.paint.Color;
import javafx.scene.paint.Paint;
import javafx.scene.shape.*;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.scene.text.Text;
import javafx.scene.input.MouseEvent;
import javafx.util.Duration;

public class GameRoomController {
    @FXML
    private TextArea chatArea;
    @FXML
    private TextField chatInput;
    @FXML
    private Button quitButton;
    @FXML
    private Pane gamePane;
    @FXML
    private ToggleButton shootModeButton;
    @FXML
    private ToggleButton goalkeeperModeButton;
    @FXML
    private Label instructionLabel;
    @FXML
    private Rectangle zone1, zone2, zone3, zone4, zone5, zone6; // 6 Click zones
    @FXML
    private Circle goalkeeper;
    @FXML
    private Circle ball;

    private Client client;

    // Game state
    private String currentMode = ""; // "shoot" or "goalkeeper"
    private boolean actionPerformed = false;

    // Các thành phần đồ họa - OLD SYSTEM (keep for compatibility)
    private Group ballGroup; // Changed name to avoid conflict
    private Circle ballCircle;
    private Group goalkeeperGroup; // Changed name
    private Group player;
    private Group imageWinGroup;
    private Group imageLoseGroup;
    @FXML
    private Label scoreLabel;
    @FXML
    private Label timerLabel;
    @FXML
    private Label roundLabel; // Label hiển thị số vòng
    
    // Legacy compatibility - REMOVED, using new zone system

    // Các phần âm thanh - DISABLED DUE TO MODULE ACCESS ISSUE
    // private AudioClip siuuuuuu;
    // private AudioClip mu;
    private Timeline countdownTimeline;
    private int timeRemaining;

    private static final int TURN_TIMEOUT = 15;
    private int lastTurnDuration = 15;
    private String yourRole = "";
    private boolean isMyTurn = false;
    private String waitingForOpponentAction = "";

    // ============ NEW CLICK-BASED ZONE SYSTEM ============
    @FXML
    private void handleZoneClick(MouseEvent event) {
        if (!actionPerformed && !currentMode.isEmpty()) {
            Rectangle clickedZone = (Rectangle) event.getSource();
            String direction = getDirectionFromZone(clickedZone);
            
            // Send action to server
            Message message;
            if (currentMode.equals("shoot")) {
                message = new Message("shoot", direction);
                System.out.println("Shot direction: " + direction);
            } else {
                message = new Message("goalkeeper", direction);
                System.out.println("Goalkeeper direction: " + direction);
            }
            
            try {
                client.sendMessage(message);
                actionPerformed = true;
                disableModes();
                if (countdownTimeline != null) {
                    countdownTimeline.stop();
                }
            } catch (IOException ex) {
                Logger.getLogger(GameRoomController.class.getName()).log(Level.SEVERE, null, ex);
            }
        }
    }
    
    private String getDirectionFromZone(Rectangle zone) {
        if (zone == zone1) return "1"; // Top Left
        if (zone == zone2) return "2"; // Top Center
        if (zone == zone3) return "3"; // Top Right
        if (zone == zone4) return "4"; // Bottom Left
        if (zone == zone5) return "5"; // Bottom Center
        if (zone == zone6) return "6"; // Bottom Right
        return "5"; // Default to center
    }
    
    @FXML
    private void handleShootMode() {
        if (shootModeButton.isSelected()) {
            currentMode = "shoot";
            goalkeeperModeButton.setSelected(false);
            actionPerformed = false;
            enableZones(true);
            instructionLabel.setText("🎯 Nhấp vào khung thành để chọn vị trí sút bóng!");
            instructionLabel.setStyle("-fx-font-size: 13px; -fx-font-weight: bold; -fx-text-fill: #4ecca3; -fx-alignment: center; -fx-padding: 8; -fx-background-color: #e8fff6; -fx-background-radius: 10; -fx-border-radius: 10;");
        } else {
            currentMode = "";
            enableZones(false);
            instructionLabel.setText("👉 Chọn chế độ SÚT BÓNG hoặc CHẶN BÓNG để tiếp tục");
            instructionLabel.setStyle("-fx-font-size: 13px; -fx-font-weight: bold; -fx-text-fill: #ff6b9d; -fx-alignment: center; -fx-padding: 8; -fx-background-color: #ffeef8; -fx-background-radius: 10; -fx-border-radius: 10;");
        }
    }
    
    @FXML
    private void handleGoalkeeperMode() {
        if (goalkeeperModeButton.isSelected()) {
            currentMode = "goalkeeper";
            shootModeButton.setSelected(false);
            actionPerformed = false;
            enableZones(true);
            instructionLabel.setText("🛡️ Nhấp vào khung thành để chọn vị trí chặn bóng!");
            instructionLabel.setStyle("-fx-font-size: 13px; -fx-font-weight: bold; -fx-text-fill: #ffd93d; -fx-alignment: center; -fx-padding: 8; -fx-background-color: #fffbeb; -fx-background-radius: 10; -fx-border-radius: 10;");
        } else {
            currentMode = "";
            enableZones(false);
            instructionLabel.setText("👉 Chọn chế độ SÚT BÓNG hoặc CHẶN BÓNG để tiếp tục");
            instructionLabel.setStyle("-fx-font-size: 13px; -fx-font-weight: bold; -fx-text-fill: #ff6b9d; -fx-alignment: center; -fx-padding: 8; -fx-background-color: #ffeef8; -fx-background-radius: 10; -fx-border-radius: 10;");
        }
    }
    
    private void enableZones(boolean enable) {
        String enabledStyle = "-fx-fill: rgba(76, 204, 163, 0.2); -fx-stroke: rgba(76, 204, 163, 0.6); -fx-stroke-width: 3; -fx-stroke-dash-array: 10 5;";
        String disabledStyle = "-fx-fill: transparent; -fx-stroke: transparent;";
        
        if (zone1 != null) zone1.setStyle(enable ? enabledStyle : disabledStyle);
        if (zone2 != null) zone2.setStyle(enable ? enabledStyle : disabledStyle);
        if (zone3 != null) zone3.setStyle(enable ? enabledStyle : disabledStyle);
        if (zone4 != null) zone4.setStyle(enable ? enabledStyle : disabledStyle);
        if (zone5 != null) zone5.setStyle(enable ? enabledStyle : disabledStyle);
        if (zone6 != null) zone6.setStyle(enable ? enabledStyle : disabledStyle);
    }
    
    private void disableModes() {
        shootModeButton.setDisable(true);
        goalkeeperModeButton.setDisable(true);
        enableZones(false);
        instructionLabel.setText("⏳ Đã gửi lựa chọn! Đang chờ đối thủ...");
        instructionLabel.setStyle("-fx-font-size: 13px; -fx-font-weight: bold; -fx-text-fill: #999; -fx-alignment: center; -fx-padding: 8; -fx-background-color: #f5f5f5; -fx-background-radius: 10; -fx-border-radius: 10;");
    }

    public void updateScore(int[] scores) {
        Platform.runLater(() -> {
            int yourScore = scores[0];
            int opponentScore = scores[1];
            int currentRound = scores[2];
            scoreLabel.setText(yourScore + " - " + opponentScore);
            
            // Cập nhật hiển thị vòng hiện tại
            if (roundLabel != null) {
                roundLabel.setText("Vòng " + currentRound + "/10");
            }
        });
    }

    public void setClient(Client client) {
        this.client = client;
    }

    @FXML
    private void initialize() {
        // Disable mode buttons initially
        shootModeButton.setDisable(true);
        goalkeeperModeButton.setDisable(true);
        enableZones(false);

        // Initial timer label
        if (timerLabel != null) {
            timerLabel.setText("15");
        }

        // Initialize score
        if (scoreLabel != null) {
            scoreLabel.setText("0 - 0");
        }
        
        // Draw field when pane is ready
        Platform.runLater(() -> {
            drawField();
        });
        
        // Audio disabled due to module access issues
        // playBackgroundMusic();
    }

    private void drawField() {
        // Audio disabled - remove duplicate call
        // playBackgroundMusic();
        // Xóa các phần tử cũ nếu có
        gamePane.getChildren().clear();

        double paneWidth = gamePane.getWidth();
        double paneHeight = gamePane.getHeight();

        // Kiểm tra nếu kích thước chưa được khởi tạo
        if (paneWidth <= 0 || paneHeight <= 0) {
            paneWidth = 600; // Giá trị mặc định
            paneHeight = 400;
        }

        // Vẽ sân cỏ với họa tiết sọc ngang
        for (int i = 0; i < paneHeight; i += 20) {
            Rectangle stripe = new Rectangle(0, i, paneWidth, 20);
            stripe.setFill(i % 40 == 0 ? Color.DARKGREEN : Color.GREEN);
            gamePane.getChildren().add(stripe);
        }

        // Vẽ đường viền sân
        Rectangle fieldBorder = new Rectangle(0, 0, paneWidth, paneHeight);
        fieldBorder.setFill(Color.TRANSPARENT);
        fieldBorder.setStroke(Color.WHITE);
        fieldBorder.setStrokeWidth(2);
        gamePane.getChildren().add(fieldBorder);

        // Vẽ khung thành với cột và xà ngang
        Rectangle goal = new Rectangle(paneWidth / 2 - 100, 15, 200, 5);
        goal.setFill(Color.WHITE);
        gamePane.getChildren().add(goal);

        Rectangle goalLeft = new Rectangle(paneWidth / 2 - 100, 15, 5, 80);
        goalLeft.setFill(Color.WHITE);
        gamePane.getChildren().add(goalLeft);

        Rectangle goalRight = new Rectangle(paneWidth / 2 + 95, 15, 5, 80);
        goalRight.setFill(Color.WHITE);
        gamePane.getChildren().add(goalRight);

        // Vẽ lưới khung thành
        for (int i = 0; i <= 200; i += 10) {
            Line verticalLine = new Line(paneWidth / 2 - 100 + i, 20, paneWidth / 2 - 100 + i, 80);
            verticalLine.setStroke(Color.WHITE);
            verticalLine.setStrokeWidth(1);
            gamePane.getChildren().add(verticalLine);
        }
        for (int i = 10; i <= 60; i += 10) {
            Line horizontalLine = new Line(paneWidth / 2 - 95, i + 20, paneWidth / 2 + 95, i + 20);
            horizontalLine.setStroke(Color.WHITE);
            horizontalLine.setStrokeWidth(1);
            gamePane.getChildren().add(horizontalLine);
        }

        // Vẽ cầu thủ chi tiết
        player = createPlayer(paneWidth / 2, paneHeight - 50, Color.BLUE, "/assets/player_head.jpg");
        gamePane.getChildren().add(player);

        // Vẽ thủ môn chi tiết
        goalkeeperGroup = createPlayer(paneWidth / 2, 100, Color.RED, "/assets/goalkeeper_head.jpg");
        gamePane.getChildren().add(goalkeeperGroup);

        // Vẽ bóng với họa tiết đen trắng
        ballGroup = createBall(paneWidth / 2, paneHeight - 120, 10);
        gamePane.getChildren().add(ballGroup);

        // Hình ảnh thắng
        Image image = new Image(getClass().getResource("/assets/c1cup.png").toExternalForm());
        ImageView imageView = new ImageView(image);
        imageView.setX(0); // Center the image at the player's position
        imageView.setY(20);

        imageView.setFitWidth(image.getWidth() / 4);
        imageView.setFitHeight(image.getHeight() / 4);

        // Tạo dòng chữ "Bạn đã thắng!" với màu xanh lá cây và kích thước lớn
        Text winText = new Text("Bạn đã thắng!");
        winText.setFill(Color.YELLOW);
        winText.setFont(Font.font("Arial", FontWeight.BOLD, 24)); // Tăng kích thước phông chữ
        winText.setX(imageView.getX() + 25); // Đặt vị trí ngang giống ImageView
        winText.setY(imageView.getY() + imageView.getFitHeight() + 30); // Đặt vị trí ngay bên dưới hình ảnh

        Text winText2 = new Text("Glory Man United!");
        winText2.setFill(Color.YELLOW);
        winText2.setFont(Font.font("Arial", FontWeight.BOLD, 24)); // Tăng kích thước phông chữ
        winText2.setX(imageView.getX() + 5); // Đặt vị trí ngang giống ImageView
        winText2.setY(imageView.getY() + imageView.getFitHeight() + 60);

        // Thêm ImageView và Text vào Group và sau đó thêm vào gamePane
        imageWinGroup = new Group(imageView, winText, winText2);
        gamePane.getChildren().add(imageWinGroup);
        enableWinGroup(false);

        // Hình ảnh thua
        Image imageLose = new Image(getClass().getResource("/assets/loa.png").toExternalForm());
        ImageView imageLoseView = new ImageView(imageLose);
        imageLoseView.setX(25); // Center the image at the player's position
        imageLoseView.setY(20);

        imageLoseView.setFitWidth(imageLose.getWidth() / 8);
        imageLoseView.setFitHeight(imageLose.getHeight() / 8);

        // Tạo dòng chữ "Bạn đã thua!" với màu trắng và kích thước lớn
        Text loseText = new Text("Bạn đã thua!");
        loseText.setFill(Color.YELLOW);
        loseText.setFont(Font.font("Arial", FontWeight.BOLD, 24)); // Tăng kích thước phông chữ
        loseText.setX(imageLoseView.getX()); // Đặt vị trí ngang giống ImageView
        loseText.setY(imageLoseView.getY() + imageLoseView.getFitHeight() + 20);
        Text loseText2 = new Text("Tất cả vào hang!");
        loseText2.setFill(Color.YELLOW);
        loseText2.setFont(Font.font("Arial", FontWeight.BOLD, 24)); // Tăng kích thước phông chữ
        loseText2.setX(imageLoseView.getX() - 20); // Đặt vị trí ngang giống ImageView
        loseText2.setY(imageLoseView.getY() + imageLoseView.getFitHeight() + 50);// Đặt vị trí ngay bên dưới hình ảnh

        // Thêm ImageView và Text vào Group và sau đó thêm vào gamePane
        imageLoseGroup = new Group(imageLoseView, loseText, loseText2);
        gamePane.getChildren().add(imageLoseGroup);
        enableLoseGroup(false);

    }

    private void enableWinGroup(boolean enable) {
        imageWinGroup.setVisible(enable);
    }

    private void enableLoseGroup(boolean enable) {
        imageLoseGroup.setVisible(enable);
    }

    private Group createPlayer(double x, double y, Color color, String headImagePath) {
        // Đầu
        // đầu người chơi
        Image headImage = new Image(getClass().getResourceAsStream(headImagePath));
        ImageView headImageView = new ImageView(headImage);
        headImageView.setFitWidth(30); // Điều chỉnh kích thước phù hợp
        headImageView.setFitHeight(30);
        headImageView.setLayoutX(x - 15); // Điều chỉnh vị trí
        headImageView.setLayoutY(y - 50);

        Circle clip = new Circle(15, 15, 15); // Bán kính 10 (vì fitWidth và fitHeight là 20)
        headImageView.setClip(clip);

        // Thân
        Line body = new Line(x, y - 20, x, y);
        body.setStroke(color);
        body.setStrokeWidth(5);

        // Tay
        Line leftArm = new Line(x, y - 15, x - 10, y - 5);
        leftArm.setStroke(color);
        leftArm.setStrokeWidth(3);

        Line rightArm = new Line(x, y - 15, x + 10, y - 5);
        rightArm.setStroke(color);
        rightArm.setStrokeWidth(3);

        // Chân
        Line leftLeg = new Line(x, y, x - 10, y + 15);
        leftLeg.setStroke(color);
        leftLeg.setStrokeWidth(3);

        Line rightLeg = new Line(x, y, x + 10, y + 15);
        rightLeg.setStroke(color);
        rightLeg.setStrokeWidth(3);

        return new Group(headImageView, body, leftArm, rightArm, leftLeg, rightLeg);
    }

    private Group createBall(double x, double y, double radius) {
        Circle circle = new Circle(x, y, radius);
        circle.setFill(Color.WHITE);
        circle.setStroke(Color.BLACK);

        // Gán circle cho ballCircle
        ballCircle = circle;

        // Vẽ họa tiết đen trên bóng
        Polygon pentagon = new Polygon();
        double angle = -Math.PI / 2;
        double angleIncrement = 2 * Math.PI / 5;
        for (int i = 0; i < 5; i++) {
            pentagon.getPoints().addAll(
                    x + radius * 0.6 * Math.cos(angle),
                    y + radius * 0.6 * Math.sin(angle));
            angle += angleIncrement;
        }
        pentagon.setFill(Color.BLACK);

        return new Group(circle, pentagon);
    }

    @FXML
    private void handleSendChat() throws IOException {
        String message = chatInput.getText();
        if (!message.isEmpty()) {
            Message chatMessage = new Message("chat", message);
            client.sendMessage(chatMessage);
            chatInput.clear();
        }
    }

    public void updateChat(String message) {
        Platform.runLater(() -> {
            chatArea.appendText(message + "\n");
        });
    }

    // OLD METHODS REMOVED - Now using zone click system
    // handleShoot() and handleGoalkeeper() replaced by handleZoneClick()

    public void animateShootVao(String directShoot, String directKeeper) {
        // Audio disabled - siuuuuuu.play();
        Platform.runLater(() -> {
            // Tạo đường đi cho bóng
            Path path = new Path();
            path.getElements().add(new MoveTo(ballCircle.getCenterX(), ballCircle.getCenterY()));

            double targetX = ballCircle.getCenterX();
            double targetY = ballCircle.getCenterY() - 210;

            // Tính toán vị trí dựa trên 6 zones (1-6)
            // Zone 1,2,3: Hàng trên | Zone 4,5,6: Hàng dưới
            switch (directShoot) {
                case "1": // Top Left
                    targetX -= 90;
                    targetY -= 20;
                    break;
                case "2": // Top Center
                    // targetX không đổi
                    targetY -= 20;
                    break;
                case "3": // Top Right
                    targetX += 90;
                    targetY -= 20;
                    break;
                case "4": // Bottom Left
                    targetX -= 90;
                    break;
                case "5": // Bottom Center
                    // targetX không đổi
                    break;
                case "6": // Bottom Right
                    targetX += 90;
                    break;
                default:
                    // Default center
                    break;
            }

            path.getElements().add(new LineTo(targetX, targetY));

            // Tạo animation cho bóng
            PathTransition pathTransition = new PathTransition();
            pathTransition.setDuration(Duration.seconds(1));
            pathTransition.setPath(path);
            pathTransition.setNode(ballGroup);
            pathTransition.play();

            // Tạo animation cho thủ môn
            double targetKeeperX = 0;
            double targetKeeperY = 0;
            
            switch (directKeeper) {
                case "1": // Top Left
                    targetKeeperX = -90;
                    targetKeeperY = -20;
                    break;
                case "2": // Top Center
                    targetKeeperY = -20;
                    break;
                case "3": // Top Right
                    targetKeeperX = 90;
                    targetKeeperY = -20;
                    break;
                case "4": // Bottom Left
                    targetKeeperX = -90;
                    break;
                case "5": // Bottom Center
                    // Không đổi
                    break;
                case "6": // Bottom Right
                    targetKeeperX = 90;
                    break;
            }

            TranslateTransition translateTransition = new TranslateTransition(Duration.seconds(1), goalkeeperGroup);
            translateTransition.setByX(targetKeeperX);
            translateTransition.setByY(targetKeeperY);
            translateTransition.play();

            // Tạo một khoảng chờ 2 giây trước khi reset vị trí
            PauseTransition pauseTransition = new PauseTransition(Duration.seconds(2));
            pauseTransition.setOnFinished(event -> {
                // Đặt lại vị trí của quả bóng và thủ môn ngay lập tức
                ballGroup.setTranslateX(0);
                ballGroup.setTranslateY(0);
                goalkeeperGroup.setTranslateX(0);
                goalkeeperGroup.setTranslateY(0);
            });

            // Bắt đầu pauseTransition sau khi các animations hoàn thành
            pauseTransition.playFromStart();

        });
    }

    public void animateShootKhongVao(String directShoot, String directKeeper) {
        Platform.runLater(() -> {
            // Tạo đường đi cho bóng
            Path path = new Path();
            path.getElements().add(new MoveTo(ballCircle.getCenterX(), ballCircle.getCenterY()));

            double targetX = ballCircle.getCenterX();
            double targetY = ballCircle.getCenterY() - 210;

            // Tính toán vị trí dựa trên 6 zones
            switch (directShoot) {
                case "1": // Top Left
                    targetX -= 90;
                    targetY -= 20;
                    break;
                case "2": // Top Center
                    targetY -= 20;
                    break;
                case "3": // Top Right
                    targetX += 90;
                    targetY -= 20;
                    break;
                case "4": // Bottom Left
                    targetX -= 90;
                    break;
                case "5": // Bottom Center
                    break;
                case "6": // Bottom Right
                    targetX += 90;
                    break;
            }

            // Bóng đi đến vị trí sút
            path.getElements().add(new LineTo(targetX, targetY));

            // Đường đi ra ngoài nếu bị đẩy ra
            double targetPathOutX = targetX;
            double targetPathOutY = targetY - 25;
            
            switch (directKeeper) {
                case "1": // Top Left
                    targetPathOutX -= 40;
                    targetPathOutY -= 20;
                    break;
                case "2": // Top Center
                    targetPathOutY -= 40;
                    break;
                case "3": // Top Right
                    targetPathOutX += 40;
                    targetPathOutY -= 20;
                    break;
                case "4": // Bottom Left
                    targetPathOutX -= 40;
                    break;
                case "5": // Bottom Center
                    targetPathOutY -= 40;
                    break;
                case "6": // Bottom Right
                    targetPathOutX += 40;
                    break;
            }
            
            Path pathOut = new Path();
            pathOut.getElements().add(new MoveTo(targetX, targetY));
            pathOut.getElements().add(new LineTo(targetPathOutX, targetPathOutY));

            // Tạo animation cho bóng đi đến khung thành
            PathTransition pathTransitionToGoal = new PathTransition(Duration.seconds(0.9), path, ballGroup);

            // Tạo animation cho bóng bị đẩy ra ngoài (chỉ khi chặn được)
            PathTransition pathTransitionOut = new PathTransition(Duration.seconds(0.3), pathOut, ballGroup);

            // Tạo animation cho thủ môn
            double targetKeeperX = 0;
            double targetKeeperY = 0;
            
            switch (directKeeper) {
                case "1": // Top Left
                    targetKeeperX = -90;
                    targetKeeperY = -20;
                    break;
                case "2": // Top Center
                    targetKeeperY = -20;
                    break;
                case "3": // Top Right
                    targetKeeperX = 90;
                    targetKeeperY = -20;
                    break;
                case "4": // Bottom Left
                    targetKeeperX = -90;
                    break;
                case "5": // Bottom Center
                    break;
                case "6": // Bottom Right
                    targetKeeperX = 90;
                    break;
            }

            TranslateTransition goalkeeperMove = new TranslateTransition(Duration.seconds(1), goalkeeperGroup);
            goalkeeperMove.setByX(targetKeeperX);
            goalkeeperMove.setByY(targetKeeperY);
            goalkeeperMove.setAutoReverse(false);

            PauseTransition pause = new PauseTransition(Duration.seconds(2));

            // Kết hợp các animations
            SequentialTransition ballAnimation;
            if (directShoot.equals(directKeeper)) {
                // Nếu thủ môn chặn được, thêm animation bóng bị đẩy ra ngoài
                ballAnimation = new SequentialTransition(pathTransitionToGoal, pathTransitionOut, pause);
            } else {
                // Nếu không bị chặn, chỉ cần di chuyển đến khung thành
                ballAnimation = new SequentialTransition(pathTransitionToGoal, pause);
            }

            // Kết hợp animation của thủ môn và bóng
            ParallelTransition gameAnimation = new ParallelTransition(ballAnimation, goalkeeperMove);

            // Thiết lập hành động khi kết thúc gameAnimation để reset vị trí ngay lập tức
            gameAnimation.setOnFinished(event -> {
                // Đặt lại vị trí của quả bóng và thủ môn ngay lập tức
                ballGroup.setTranslateX(0);
                ballGroup.setTranslateY(0);
                goalkeeperGroup.setTranslateX(0);
                goalkeeperGroup.setTranslateY(0);
            });

            gameAnimation.play();

        });
    }

    // OLD METHODS REMOVED - Now using zone click system
    // handleShoot() and handleGoalkeeper() replaced by handleZoneClick()

    public void promptYourTurn(int durationInSeconds) {
        Platform.runLater(() -> {
            lastTurnDuration = durationInSeconds;
            isMyTurn = true;
            yourRole = "Shooter";
            actionPerformed = false;
            
            // Enable shoot mode
            shootModeButton.setDisable(false);
            goalkeeperModeButton.setDisable(true);
            shootModeButton.setSelected(false);
            goalkeeperModeButton.setSelected(false);
            currentMode = "";
            
            instructionLabel.setText("🎯 LƯỢT CỦA BẠN: Chọn CHẾ ĐỘ SÚT BÓNG!");
            instructionLabel.setStyle("-fx-font-size: 13px; -fx-font-weight: bold; -fx-text-fill: #4ecca3; -fx-alignment: center; -fx-padding: 8; -fx-background-color: #e8fff6; -fx-background-radius: 10; -fx-border-radius: 10;");
            
            startCountdown(durationInSeconds);
        });
    }

    public void promptGoalkeeperTurn(int durationInSeconds) {
        Platform.runLater(() -> {
            lastTurnDuration = durationInSeconds;
            isMyTurn = true;
            yourRole = "Goalkeeper";
            actionPerformed = false;
            
            // Enable goalkeeper mode
            goalkeeperModeButton.setDisable(false);
            shootModeButton.setDisable(true);
            shootModeButton.setSelected(false);
            goalkeeperModeButton.setSelected(false);
            currentMode = "";
            
            instructionLabel.setText("🛡️ LƯỢT CỦA BẠN: Chọn CHẾ ĐỘ CHẶN BÓNG!");
            instructionLabel.setStyle("-fx-font-size: 13px; -fx-font-weight: bold; -fx-text-fill: #ffd93d; -fx-alignment: center; -fx-padding: 8; -fx-background-color: #fffbeb; -fx-background-radius: 10; -fx-border-radius: 10;");
            
            startCountdown(durationInSeconds);
        });
    }

    public void handleOpponentTurn(int durationInSeconds) {
        Platform.runLater(() -> {
            isMyTurn = false;
            shootModeButton.setDisable(true);
            goalkeeperModeButton.setDisable(true);
            enableZones(false);

            if (yourRole.equals("Shooter")) {
                waitingForOpponentAction = "goalkeeper";
            } else if (yourRole.equals("Goalkeeper")) {
                waitingForOpponentAction = "shoot";
            }
            
            instructionLabel.setText("⏳ LƯỢT ĐỐI THỦ - Đang chờ đối thủ...");
            instructionLabel.setStyle("-fx-font-size: 13px; -fx-font-weight: bold; -fx-text-fill: #999; -fx-alignment: center; -fx-padding: 8; -fx-background-color: #f5f5f5; -fx-background-radius: 10; -fx-border-radius: 10;");

            startCountdown(durationInSeconds);
        });
    }

    public void showRoundResult(String roundResult) {
        // Audio disabled - siuuuuuu.play();
        Platform.runLater(() -> {
            Alert alert = new Alert(AlertType.INFORMATION);
            alert.setTitle("Kết Quả Lượt");
            alert.setHeaderText(null);
            alert.setContentText(roundResult);
            alert.showAndWait();
        });
    }

    public void endMatch(String result) {
        // Audio disabled - stop background music
        // if (mu != null) {
        //     mu.stop();
        // }
        Platform.runLater(() -> {
            Alert alert = new Alert(AlertType.INFORMATION);
            alert.setTitle("Kết Thúc Trận Đấu");
            alert.setHeaderText(null);
            alert.setContentText(result);
            alert.show(); // Thay vì showAndWait()
            // Chuyển về màn hình chính sau một khoảng thời gian
            PauseTransition delay = new PauseTransition(Duration.seconds(2));
            delay.setOnFinished(event -> {
                try {
                    client.showMainUI();
                } catch (Exception e) {
                    e.printStackTrace();
                }
            });
            delay.play();
        });
    }

    public void handleRematchDeclined(String message) {
        Platform.runLater(() -> {
            Alert alert = new Alert(AlertType.INFORMATION);
            alert.setTitle("Chơi Lại");
            alert.setHeaderText(null);
            alert.setContentText(message);
            alert.show(); // Thay vì showAndWait()
            // Chuyển về màn hình chính sau một khoảng thời gian
            PauseTransition delay = new PauseTransition(Duration.seconds(2));
            delay.setOnFinished(event -> {
                try {
                    client.showMainUI();
                } catch (Exception e) {
                    e.printStackTrace();
                }
            });
            delay.play();
        });
    }

    public void promptPlayAgain() {
        Platform.runLater(() -> {
            Alert alert = new Alert(AlertType.CONFIRMATION);
            alert.setTitle("Chơi Lại");
            alert.setHeaderText(null);
            alert.setContentText("Bạn có muốn chơi lại không?");
            ButtonType yesButton = new ButtonType("Có", ButtonBar.ButtonData.YES);
            ButtonType noButton = new ButtonType("Không", ButtonBar.ButtonData.NO);
            alert.getButtonTypes().setAll(yesButton, noButton);

            Optional<ButtonType> result = alert.showAndWait();
            if (result.isPresent()) {
                boolean playAgain = result.get() == yesButton;
                Message playAgainResponse = new Message("play_again_response", playAgain);
                try {
                    client.sendMessage(playAgainResponse);
                } catch (IOException e) {
                    e.printStackTrace();
                }

                if (!playAgain) {
                    // Người chơi chọn không chơi lại, trở về màn hình chính
                    try {
                        client.showMainUI();
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }
            }
        });
    }

    @FXML
    private void handleQuitGame() {
        Platform.runLater(() -> {
            Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
            alert.setTitle("Thoát Trò Chơi");
            alert.setHeaderText(null);
            alert.setContentText("Bạn có chắc chắn muốn thoát trò chơi không?");
            ButtonType yesButton = new ButtonType("Có", ButtonBar.ButtonData.YES);
            ButtonType noButton = new ButtonType("Không", ButtonBar.ButtonData.NO);
            alert.getButtonTypes().setAll(yesButton, noButton);

            Optional<ButtonType> result = alert.showAndWait();
            if (result.isPresent() && result.get() == yesButton) {
                Message quitMessage = new Message("quit_game", null);
                try {
                    client.sendMessage(quitMessage);
                    // Quay về màn hình chính
                    client.showMainUI();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        });
    }

    // Thêm phương thức để hiển thị thông báo vai trò khi bắt đầu trận đấu
    public void showStartMessage(String message) {
        Platform.runLater(() -> {
            if (message.contains("người sút")) {
                yourRole = "Shooter";
            } else if (message.contains("người bắt")) {
                yourRole = "Goalkeeper";
            }
        });
    }

    public void showMatchResult(String result) {
        Platform.runLater(() -> {
            if (result.equals("win")) {
                enableWinGroup(true);
                enableLoseGroup(false);
            } else if (result.equals("lose")) {
                enableLoseGroup(true);
                enableWinGroup(false);
            }
            if (countdownTimeline != null) {
                countdownTimeline.stop(); // Dừng đồng hồ đếm ngược
            }
            timerLabel.setText("Kết thúc trận đấu!");
        });
    }

    // Trong GameRoomController.java
    public void handleTimeout(String message) {
        Platform.runLater(() -> {
            isMyTurn = false; // Cập nhật trạng thái lượt chơi
            Alert alert = new Alert(AlertType.WARNING);
            alert.setTitle("Hết giờ");
            alert.setHeaderText(null);
            alert.setContentText(message);
            alert.show(); // Thay vì showAndWait()
            // Vô hiệu hóa các nút hành động - NEW SYSTEM
            shootModeButton.setDisable(true);
            goalkeeperModeButton.setDisable(true);
            enableZones(false);
            // Cập nhật trạng thái chờ đối thủ
            if (yourRole.equals("Shooter")) {
                waitingForOpponentAction = "goalkeeper";
            } else if (yourRole.equals("Goalkeeper")) {
                waitingForOpponentAction = "shoot";
            }
            // Bắt đầu đồng hồ đếm ngược chờ đối thủ
            startCountdown(TURN_TIMEOUT);
        });
    }

    // Audio playback disabled due to JavaFX module access issues
    // To re-enable: add --add-exports javafx.base/com.sun.javafx=ALL-UNNAMED to JVM args
    /*
    private void playBackgroundMusic() {
        siuuuuuu = new AudioClip(getClass().getResource("/sound/siuuu.wav").toExternalForm());
        mu = new AudioClip(getClass().getResource("/sound/mu.wav").toExternalForm());
        mu.setCycleCount(AudioClip.INDEFINITE); // Set to loop indefinitely
        mu.setVolume(0.15f); // Set volume to 50%
        mu.play();// Play the music
    }
    */

    public void handleOpponentTimeout(String message) {
        Platform.runLater(() -> {
            if (countdownTimeline != null) {
                countdownTimeline.stop(); // Dừng đồng hồ đếm ngược
            }
            isMyTurn = true;
            waitingForOpponentAction = "";
            // Kiểm tra vai trò và kích hoạt nút hành động tương ứng - NEW SYSTEM
            if (yourRole.equals("Shooter")) {
                shootModeButton.setDisable(false);
                goalkeeperModeButton.setDisable(true);
            } else if (yourRole.equals("Goalkeeper")) {
                goalkeeperModeButton.setDisable(false);
                shootModeButton.setDisable(true);
            }
            // Bắt đầu đồng hồ đếm ngược cho lượt của bạn
            startCountdown(TURN_TIMEOUT);
        });
    }

    private void startCountdown(int durationInSeconds) {
        timeRemaining = durationInSeconds;

        if (countdownTimeline != null) {
            countdownTimeline.stop();
        }

        countdownTimeline = new Timeline(new KeyFrame(Duration.seconds(1), event -> {
            // Xác định thông báo phù hợp
            final String action;
            if (isMyTurn) {
                if (yourRole.equals("Shooter") && !shootModeButton.isDisabled()) {
                    action = "Thời gian còn lại: ";
                } else if (yourRole.equals("Goalkeeper") && !goalkeeperModeButton.isDisabled()) {
                    action = "Thời gian còn lại: ";
                } else {
                    action = "Thời gian còn lại: ";
                }
            } else {
                if (waitingForOpponentAction.equals("shoot")) {
                    action = "Đang chờ đối thủ: ";
                } else if (waitingForOpponentAction.equals("goalkeeper")) {
                    action = "Đang chờ đối thủ: ";
                } else {
                    action = "Đang chờ đối thủ: ";
                }
            }

            timerLabel.setText(action + timeRemaining + " giây");
            timeRemaining--;

            if (timeRemaining < 0) {
                countdownTimeline.stop();
                // Dialog removed in new click-based system
                timerLabel.setText(action + "0 giây");
                // Vô hiệu hóa các nút hành động và zones khi hết thời gian
                shootModeButton.setDisable(true);
                goalkeeperModeButton.setDisable(true);
                enableZones(false);
                if (yourRole.equals("Shooter")) {
                    try {
                        client.sendMessage(new Message("timeout", "shooter"));
                    } catch (IOException ex) {
                        Logger.getLogger(GameRoomController.class.getName()).log(Level.SEVERE, null, ex);
                    }
                } else if (yourRole.equals("Goalkeeper")) {
                    try {
                        client.sendMessage(new Message("timeout", "goalkeeper"));
                    } catch (IOException ex) {
                        Logger.getLogger(GameRoomController.class.getName()).log(Level.SEVERE, null, ex);
                    }
                }
                isMyTurn = false;
            }
        }));
        countdownTimeline.setCycleCount(durationInSeconds + 1); // Bao gồm cả 0 giây
        countdownTimeline.play();

        // Cập nhật timerLabel lần đầu tiên
        final String action;
        if (isMyTurn) {
            if (yourRole.equals("Shooter") && !shootModeButton.isDisabled()) {
                action = "Thời gian còn lại: ";
            } else if (yourRole.equals("Goalkeeper") && !goalkeeperModeButton.isDisabled()) {
                action = "Thời gian còn lại: ";
            } else {
                action = "Thời gian còn lại: ";
            }
        } else {
            if (waitingForOpponentAction.equals("shoot")) {
                action = "Đang chờ đối thủ: ";
            } else if (waitingForOpponentAction.equals("goalkeeper")) {
                action = "Đang chờ đối thủ: ";
            } else {
                action = "Đang chờ đối thủ: ";
            }
        }

        timerLabel.setText(action + timeRemaining + " giây");
    }

}
