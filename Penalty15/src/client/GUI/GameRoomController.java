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
import javafx.util.Duration;

public class GameRoomController {
    @FXML
    private TextArea chatArea;
    @FXML
    private TextField chatInput;
    @FXML
    private Button shootButton;
    @FXML
    private Button goalkeeperButton;
    @FXML
    private Button quitButton;
    @FXML
    private Pane gamePane;

    private Client client;
    ChoiceDialog<String> dialog = new ChoiceDialog<>("Middle", "Left", "Middle", "Right");

    // Các thành phần đồ họa
    private Group ball; // Thay đổi từ Circle thành Group
    private Circle ballCircle; // Thêm biến này để tham chiếu đến Circle bên trong ball
    private Group goalkeeper;
    private Group player;
    private Group imageWinGroup;
    private Group imageLoseGroup;
    @FXML
    private Label scoreLabel;
    @FXML
    private Label timerLabel; // Hiển thị thời gian đếm ngược

    // Các phần âm thanh
    private AudioClip siuuuuuu;
    private AudioClip mu;
    private Timeline countdownTimeline;
    private int timeRemaining; // Thời gian còn lại cho lượt

    private static final int TURN_TIMEOUT = 15;

    private int lastTurnDuration = 15; // Giá trị mặc định

    private String yourRole = "";

    public void updateScore(int[] scores) {
        Platform.runLater(() -> {
            int yourScore = scores[0];
            int opponentScore = scores[1];
            int currentRound = scores[2];
            scoreLabel.setText("Round: " + (currentRound + 1) + "         Bạn: " + yourScore
                    + "   -   Đối thủ: " + opponentScore);
        });
    }

    public void setClient(Client client) {
        this.client = client;
    }

    @FXML
    private void initialize() {
        shootButton.setDisable(true);
        goalkeeperButton.setDisable(true);

        // Khởi tạo giá trị ban đầu cho timerLabel
        if (timerLabel != null) {
            timerLabel.setText("Thời gian còn lại: 0 giây");
        } else {
            System.err.println("timerLabel is null!");
        }

        // Trì hoãn việc vẽ sân bóng cho đến khi gamePane có kích thước
        Platform.runLater(() -> {
            drawField();
        });

        if (scoreLabel != null) {
            scoreLabel.setText("Round: " + 1 + "         Bạn: " + 0
                    + "   -   Đối thủ: " + 0);
        } else {
            System.err.println("scoreLabel is null!");
        }
    }

    private void drawField() {
        playBackgroundMusic();
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
        goalkeeper = createPlayer(paneWidth / 2, 100, Color.RED, "/assets/goalkeeper_head.jpg");
        gamePane.getChildren().add(goalkeeper);

        // Vẽ bóng với họa tiết đen trắng
        ball = createBall(paneWidth / 2, paneHeight - 120, 10);
        gamePane.getChildren().add(ball);

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

    @FXML
    private void handleShoot() {
        dialog = new ChoiceDialog<>("Middle", "Left", "Middle", "Right");
        dialog.setTitle("Chọn Hướng Sút");
        dialog.setHeaderText("Chọn hướng sút:");
        dialog.setContentText("Hướng:");

        Optional<String> result = dialog.showAndWait();
        result.ifPresent(direction -> {
            if (timeRemaining < 0) {
                return;
            }
            Message shootMessage = new Message("shoot", direction);
            try {
                client.sendMessage(shootMessage);
                System.out.println("Sent shoot direction: " + direction);
                shootButton.setDisable(true);
                // animateShoot(direction);
            } catch (IOException ex) {
                Logger.getLogger(GameRoomController.class.getName()).log(Level.SEVERE, null, ex);
            }
        });
        if (countdownTimeline != null) {
            countdownTimeline.stop(); // Dừng đếm ngược khi người chơi đã chọn
        }
    }

    public void animateShootVao(String directShoot, String directKeeper) {
        siuuuuuu.play();
        Platform.runLater(() -> {
            // Tạo đường đi cho bóng
            Path path = new Path();
            path.getElements().add(new MoveTo(ballCircle.getCenterX(), ballCircle.getCenterY()));

            double targetX = ballCircle.getCenterX();
            double targetY = ballCircle.getCenterY() - 210;

            if (directShoot.equalsIgnoreCase("Left")) {
                targetX -= 90;
            } else if (directShoot.equalsIgnoreCase("Right")) {
                targetX += 90;
            }

            path.getElements().add(new LineTo(targetX, targetY));

            // Tạo animation cho bóng
            PathTransition pathTransition = new PathTransition();
            pathTransition.setDuration(Duration.seconds(1));
            pathTransition.setPath(path);
            pathTransition.setNode(ball);
            pathTransition.play();

            // Tạo animation cho thủ môn
            double targetKeeperX = 0;
            if (directKeeper.equalsIgnoreCase("Left")) {
                targetKeeperX = -90;
            } else if (directKeeper.equalsIgnoreCase("Right")) {
                targetKeeperX = 90;
            }

            TranslateTransition translateTransition = new TranslateTransition(Duration.seconds(1), goalkeeper);
            translateTransition.setByX(targetKeeperX);
            translateTransition.play();

            // Tạo một khoảng chờ 2 giây trước khi reset vị trí
            PauseTransition pauseTransition = new PauseTransition(Duration.seconds(2));
            pauseTransition.setOnFinished(event -> {
                // Đặt lại vị trí của quả bóng và thủ môn ngay lập tức
                ball.setTranslateX(0);
                ball.setTranslateY(0);
                goalkeeper.setTranslateX(0);
                goalkeeper.setTranslateY(0);
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

            if (directShoot.equalsIgnoreCase("Left")) {
                targetX -= 90;
            } else if (directShoot.equalsIgnoreCase("Right")) {
                targetX += 90;
            }

            // Bóng đi đến vị trí sút
            path.getElements().add(new LineTo(targetX, targetY));

            // Đường đi ra ngoài nếu bị đẩy ra
            double targetPathOutX = targetX;
            double targetPathOutY = targetY - 25;
            if (directKeeper.equalsIgnoreCase("Left")) {
                targetPathOutX -= 40;
            } else if (directKeeper.equalsIgnoreCase("Right")) {
                targetPathOutX += 40;
            } else {
                targetPathOutY -= 40;
            }
            Path pathOut = new Path();
            pathOut.getElements().add(new MoveTo(targetX, targetY));
            pathOut.getElements().add(new LineTo(targetPathOutX, targetPathOutY));

            // Tạo animation cho bóng đi đến khung thành
            PathTransition pathTransitionToGoal = new PathTransition(Duration.seconds(0.9), path, ball);

            // Tạo animation cho bóng bị đẩy ra ngoài (chỉ khi chặn được)
            PathTransition pathTransitionOut = new PathTransition(Duration.seconds(0.3), pathOut, ball);

            // Tạo animation cho thủ môn
            double targetKeeperX = 0;
            if (directKeeper.equalsIgnoreCase("Left")) {
                targetKeeperX = -90;
            } else if (directKeeper.equalsIgnoreCase("Right")) {
                targetKeeperX = 90;
            }

            TranslateTransition goalkeeperMove = new TranslateTransition(Duration.seconds(1), goalkeeper);
            goalkeeperMove.setByX(targetKeeperX);
            goalkeeperMove.setAutoReverse(false);

            PauseTransition pause = new PauseTransition(Duration.seconds(2));

            // Kết hợp các animations
            SequentialTransition ballAnimation;
            if (directShoot.equalsIgnoreCase(directKeeper)) {
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
                ball.setTranslateX(0);
                ball.setTranslateY(0);
                goalkeeper.setTranslateX(0);
                goalkeeper.setTranslateY(0);
            });

            gameAnimation.play();

        });
    }

    @FXML
    private void handleGoalkeeper() {
        dialog = new ChoiceDialog<>("Middle", "Left", "Middle", "Right");
        dialog.setTitle("Chọn Hướng Chặn");
        dialog.setHeaderText("Chọn hướng chặn:");
        dialog.setContentText("Hướng:");

        Optional<String> result = dialog.showAndWait();
        result.ifPresent(direction -> {
            if (timeRemaining < 0) {
                return;
            }
            Message goalkeeperMessage = new Message("goalkeeper", direction);
            try {
                client.sendMessage(goalkeeperMessage);
                System.out.println("Sent goalkeeper direction: " + direction);
                goalkeeperButton.setDisable(true);
                // animateGoalkeeper(direction);
            } catch (IOException ex) {
                Logger.getLogger(GameRoomController.class.getName()).log(Level.SEVERE, null, ex);
            }
        });
        if (countdownTimeline != null) {
            countdownTimeline.stop(); // Dừng đếm ngược khi người chơi đã chọn
        }
    }

    private boolean isMyTurn = false;

    private String waitingForOpponentAction = "";

    public void promptYourTurn(int durationInSeconds) {
        Platform.runLater(() -> {
            lastTurnDuration = durationInSeconds; // Update last turn duration
            isMyTurn = true;
            yourRole = "Shooter"; // You are the Shooter in this turn
            shootButton.setDisable(false); // Enable shoot button
            goalkeeperButton.setDisable(true); // Disable goalkeeper button
            startCountdown(durationInSeconds);
        });
    }

    public void promptGoalkeeperTurn(int durationInSeconds) {
        Platform.runLater(() -> {
            lastTurnDuration = durationInSeconds; // Update last turn duration
            isMyTurn = true;
            yourRole = "Goalkeeper"; // You are the Goalkeeper in this turn
            goalkeeperButton.setDisable(false); // Enable goalkeeper button
            shootButton.setDisable(true); // Disable shoot button
            startCountdown(durationInSeconds);
        });
    }

    public void handleOpponentTurn(int durationInSeconds) {
        Platform.runLater(() -> {
            isMyTurn = false;
            shootButton.setDisable(true);
            goalkeeperButton.setDisable(true);

            // Determine what action the opponent is performing based on your role
            if (yourRole.equals("Shooter")) {
                waitingForOpponentAction = "goalkeeper"; // If you're Shooter, opponent is Goalkeeper
            } else if (yourRole.equals("Goalkeeper")) {
                waitingForOpponentAction = "shoot"; // If you're Goalkeeper, opponent is Shooter
            } else {
                waitingForOpponentAction = ""; // If role is not defined, leave it empty
            }

            startCountdown(durationInSeconds);
        });
    }

    public void showRoundResult(String roundResult) {
        siuuuuuu.play();
        Platform.runLater(() -> {
            Alert alert = new Alert(AlertType.INFORMATION);
            alert.setTitle("Kết Quả Lượt");
            alert.setHeaderText(null);
            alert.setContentText(roundResult);
            alert.showAndWait();
        });
    }

    public void endMatch(String result) {
        if (mu != null) {
            mu.stop(); // Stop the background music
        }
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
            // Vô hiệu hóa các nút hành động
            shootButton.setDisable(true);
            goalkeeperButton.setDisable(true);
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

    private void playBackgroundMusic() {
        siuuuuuu = new AudioClip(getClass().getResource("/sound/siuuu.wav").toExternalForm());
        mu = new AudioClip(getClass().getResource("/sound/mu.wav").toExternalForm());
        mu.setCycleCount(AudioClip.INDEFINITE); // Set to loop indefinitely
        mu.setVolume(0.15f); // Set volume to 50%
        mu.play();// Play the music
    }

    public void handleOpponentTimeout(String message) {
        Platform.runLater(() -> {
            if (countdownTimeline != null) {
                countdownTimeline.stop(); // Dừng đồng hồ đếm ngược
            }
            isMyTurn = true;
            waitingForOpponentAction = "";
            // Kiểm tra vai trò và kích hoạt nút hành động tương ứng
            if (yourRole.equals("Shooter")) {
                shootButton.setDisable(false);
            } else if (yourRole.equals("Goalkeeper")) {
                goalkeeperButton.setDisable(false);
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
                if (yourRole.equals("Shooter") && !shootButton.isDisabled()) {
                    action = "Thời gian còn lại: ";
                } else if (yourRole.equals("Goalkeeper") && !goalkeeperButton.isDisabled()) {
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
                dialog.close();
                timerLabel.setText(action + "0 giây");
                // Vô hiệu hóa các nút hành động khi hết thời gian
                shootButton.setDisable(true);
                goalkeeperButton.setDisable(true);
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
            if (yourRole.equals("Shooter") && !shootButton.isDisabled()) {
                action = "Thời gian còn lại: ";
            } else if (yourRole.equals("Goalkeeper") && !goalkeeperButton.isDisabled()) {
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
