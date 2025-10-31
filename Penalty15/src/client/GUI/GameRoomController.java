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
import javafx.animation.RotateTransition;
import javafx.animation.FadeTransition;
import javafx.animation.Interpolator;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.Group;
import javafx.scene.control.*;
import javafx.scene.control.Alert.AlertType;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.Pane;
import javafx.scene.layout.GridPane;
import javafx.geometry.Point2D;
import javafx.scene.media.AudioClip;
import javafx.scene.media.Media;
import javafx.scene.media.MediaPlayer;
import javafx.scene.paint.Color;
import javafx.scene.paint.Paint;
import javafx.scene.shape.*;
import java.util.HashMap;
import java.util.Map;
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
    private GridPane zonesGrid;
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
    private ImageView goalkeeperSprite;
    private Group player;
    private Group imageWinGroup;
    private Group imageLoseGroup;
    private ImageView ballImageView;
    @FXML
    private Label scoreLabel;
    @FXML
    private Label timerLabel;
    @FXML
    private Label roundLabel; // Label hiển thị số vòng
    @FXML
    private Label playerNameLabel; // Label hiển thị tên người chơi
    @FXML
    private Label opponentNameLabel; // Label hiển thị tên đối thủ
    
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

    // Goal / layout computed values (updated on drawField)
    private double goalLeftX = 0;
    private double goalRightX = 0;
    private double goalTop = 0;
    private double goalWidth = 0;
    private double goalHeight = 0;

    // Keeper and ball anchor positions (updated on drawField)
    private double goalkeeperInitialX = 0;
    private double goalkeeperInitialY = 0;
    private double ballStartX = 0;
    private double ballStartY = 0;

    // Absolute overlay zones (aligned to computed goal bounds)
    private Map<Rectangle, String> absZoneMap = new HashMap<>();
    private Rectangle[] absZones = new Rectangle[6];

    // asset paths for keeper and ball
    private static final String GK_STAND = "/assets/ThuMonDung.png";
    private static final String GK_JUMP_LEFT = "/assets/ThuMonNhayTrai.png";
    private static final String GK_JUMP_RIGHT = "/assets/ThuMonNhayPhai.png";
    private static final String GK_JUMP_UP = "/assets/ThuMonNhayLen.png";
    private static final String GK_FALL_LEFT = "/assets/ThuMonNgaTrai.png";
    private static final String GK_FALL_RIGHT = "/assets/ThuMonNgaPhai.png";
    private static final String BALL_IMG = "/assets/QuaBong.jpg";

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

    // Create or update six absolute overlay rectangles positioned exactly over the goal.
    // These rectangles are added directly to gamePane so their layout matches the computed goal bounds.
    private void updateAbsoluteZones() {
        // remove old overlays
        try {
            for (int i = 0; i < absZones.length; i++) {
                if (absZones[i] != null) {
                    gamePane.getChildren().remove(absZones[i]);
                    absZoneMap.remove(absZones[i]);
                    absZones[i] = null;
                }
            }
        } catch (Exception ignore) {}

        // need valid goal geometry
        if (goalWidth <= 0 || goalHeight <= 0) return;

    // make the original GridPane overlay ignore mouse events so our absolute
    // overlays (which are placed directly on gamePane) receive clicks.
    try { if (zonesGrid != null) zonesGrid.setMouseTransparent(true); } catch (Exception ignored) {}

        double zoneW = goalWidth / 3.0;
        double zoneH = goalHeight / 2.0;
        double top = goalTop + 6; // net start offset used elsewhere

        int idx = 0;
        for (int row = 0; row < 2; row++) {
            for (int col = 0; col < 3; col++) {
                double x = goalLeftX + col * zoneW;
                double y = top + row * zoneH;

                Rectangle r = new Rectangle(x, y, zoneW, zoneH);
                r.setFill(Color.TRANSPARENT);
                r.setStroke(Color.TRANSPARENT);
                // keep mouse events enabled
                r.setMouseTransparent(false);
                final String dir = String.valueOf(idx + 1);
                r.setOnMouseClicked(evt -> {
                    // route event through existing handler
                    handleZoneClick((MouseEvent) evt);
                });

                gamePane.getChildren().add(r);
                absZones[idx] = r;
                absZoneMap.put(r, dir);
                idx++;
            }
        }
    }
    
    private String getDirectionFromZone(Rectangle zone) {
        // First check absolute overlay map
        if (zone != null && absZoneMap.containsKey(zone)) {
            return absZoneMap.get(zone);
        }
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
        // Also update absolute overlay zones if present
        try {
            for (Rectangle r : absZoneMap.keySet()) {
                if (r != null) r.setStyle(enable ? enabledStyle : disabledStyle);
            }
        } catch (Exception ignored) {}
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
        
        // Hiển thị tên người chơi
        if (client.getUser() != null && playerNameLabel != null) {
            playerNameLabel.setText("👤 " + client.getUser().getUsername());
        }
    }
    
    public void setOpponentName(String opponentName) {
        if (opponentNameLabel != null) {
            opponentNameLabel.setText("👤 " + opponentName);
        }
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
        
        // Draw field when pane is ready and keep responsive to resizing
        Platform.runLater(() -> {
            drawField();
            // redraw when pane size changes
            gamePane.widthProperty().addListener((obs, oldV, newV) -> drawField());
            gamePane.heightProperty().addListener((obs, oldV, newV) -> drawField());
        });
        
        // Audio disabled due to module access issues
        // playBackgroundMusic();
    }

    private void drawField() {
        // Xóa các phần tử cũ nếu có
        gamePane.getChildren().clear();

        double paneWidth = gamePane.getWidth();
        double paneHeight = gamePane.getHeight();

        // Kiểm tra nếu kích thước chưa được khởi tạo
        if (paneWidth <= 0 || paneHeight <= 0) {
            paneWidth = 650; // Giá trị mặc định
            paneHeight = 550;
        }

        // ========== VẼ SÂN CỎ CHUYÊN NGHIỆP ==========
        // Sân cỏ với gradient từ xanh đậm đến xanh nhạt (tạo chiều sâu)
        for (int i = 0; i < paneHeight; i += 25) {
            Rectangle stripe = new Rectangle(0, i, paneWidth, 25);
            Color grassColor = i % 50 == 0 ? 
                Color.rgb(34, 139, 34, 0.95) : Color.rgb(50, 205, 50, 0.85);
            stripe.setFill(grassColor);
            gamePane.getChildren().add(stripe);
        }

        // Vòng tròn penalty spot - chi tiết hơn
        double centerX = paneWidth / 2;
        double centerY = paneHeight - 150;
        Circle penaltySpot = new Circle(centerX, centerY, 6);
        penaltySpot.setFill(Color.WHITE);
        penaltySpot.setStroke(Color.rgb(200, 200, 200));
        penaltySpot.setStrokeWidth(1.5);
        gamePane.getChildren().add(penaltySpot);

        // ========== VẼ KHUNG THÀNH 3D CHUYÊN NGHIỆP ==========
    double goalWidth = 280;
    double goalHeight = 140;
    double goalTop = 20;
    double goalLeftX = centerX - goalWidth / 2;
    double goalRightX = centerX + goalWidth / 2;

    // Store computed goal bounds into controller fields so other methods
    // (getZoneCenter, keeper/ball animations) use the live values.
    this.goalLeftX = goalLeftX;
    this.goalRightX = goalRightX;
    this.goalTop = goalTop;
    this.goalWidth = goalWidth;
    this.goalHeight = goalHeight;

        // Nền tối cho khung thành (tạo độ sâu 3D)
        Rectangle goalDepth = new Rectangle(goalLeftX - 15, goalTop - 10, goalWidth + 30, goalHeight + 15);
        goalDepth.setFill(Color.rgb(20, 20, 20, 0.4));
        goalDepth.setArcWidth(10);
        goalDepth.setArcHeight(10);
        gamePane.getChildren().add(goalDepth);

        // Cột trái khung thành với gradient
        Rectangle goalPostLeft = new Rectangle(goalLeftX - 6, goalTop, 6, goalHeight);
        goalPostLeft.setFill(Color.WHITE);
        goalPostLeft.setStroke(Color.LIGHTGRAY);
        goalPostLeft.setStrokeWidth(1);
        gamePane.getChildren().add(goalPostLeft);

        // Cột phải khung thành
        Rectangle goalPostRight = new Rectangle(goalRightX, goalTop, 6, goalHeight);
        goalPostRight.setFill(Color.WHITE);
        goalPostRight.setStroke(Color.LIGHTGRAY);
        goalPostRight.setStrokeWidth(1);
        gamePane.getChildren().add(goalPostRight);

        // Xà ngang khung thành
        Rectangle goalCrossbar = new Rectangle(goalLeftX - 6, goalTop, goalWidth + 12, 6);
        goalCrossbar.setFill(Color.WHITE);
        goalCrossbar.setStroke(Color.LIGHTGRAY);
        goalCrossbar.setStrokeWidth(1);
        gamePane.getChildren().add(goalCrossbar);

        // ========== VẼ LƯỚI KHUNG THÀNH ĐẸP MẮT ==========
        // Lưới dọc - dày hơn, rõ ràng hơn
        for (int i = 0; i <= goalWidth; i += 20) {
            Line netVertical = new Line(
                goalLeftX + i, goalTop + 6,
                goalLeftX + i, goalTop + goalHeight
            );
            netVertical.setStroke(Color.rgb(255, 255, 255, 0.5));
            netVertical.setStrokeWidth(1.5);
            gamePane.getChildren().add(netVertical);
        }

        // Lưới ngang
        for (int i = 10; i <= goalHeight; i += 20) {
            Line netHorizontal = new Line(
                goalLeftX, goalTop + i,
                goalRightX, goalTop + i
            );
            netHorizontal.setStroke(Color.rgb(255, 255, 255, 0.5));
            netHorizontal.setStrokeWidth(1.5);
            gamePane.getChildren().add(netHorizontal);
        }

        // Vẽ lưới chéo tạo hiệu ứng 3D cho khung thành
        Line netDiag1 = new Line(goalLeftX, goalTop + 6, goalLeftX + 15, goalTop + goalHeight);
        netDiag1.setStroke(Color.rgb(255, 255, 255, 0.3));
        netDiag1.setStrokeWidth(1);
        gamePane.getChildren().add(netDiag1);

        Line netDiag2 = new Line(goalRightX, goalTop + 6, goalRightX - 15, goalTop + goalHeight);
        netDiag2.setStroke(Color.rgb(255, 255, 255, 0.3));
        netDiag2.setStrokeWidth(1);
        gamePane.getChildren().add(netDiag2);

        // ========== VẼ ZONE INDICATORS (6 ZONES) ==========
        drawZoneIndicators(goalLeftX, goalRightX, goalTop, goalHeight);

        // Position the interactive GridPane (zonesGrid) so the clickable rectangles align with the drawn goal
        try {
            if (zonesGrid != null) {
                // Remove any translate set in FXML and place grid exactly over the goal
                zonesGrid.setTranslateY(0);
                zonesGrid.setTranslateX(0);

                // Set size to match goal
                zonesGrid.setPrefWidth(goalWidth);
                zonesGrid.setPrefHeight(goalHeight);

                // Position relative to the StackPane containing gamePane
                // Slight offset for top stroke (goalTop + 6) to match net start
                zonesGrid.setLayoutX(goalLeftX);
                zonesGrid.setLayoutY(goalTop + 6);

                double zoneW = goalWidth / 3.0;
                double zoneH = goalHeight / 2.0;

                if (zone1 != null) { zone1.setWidth(zoneW); zone1.setHeight(zoneH); }
                if (zone2 != null) { zone2.setWidth(zoneW); zone2.setHeight(zoneH); }
                if (zone3 != null) { zone3.setWidth(zoneW); zone3.setHeight(zoneH); }
                if (zone4 != null) { zone4.setWidth(zoneW); zone4.setHeight(zoneH); }
                if (zone5 != null) { zone5.setWidth(zoneW); zone5.setHeight(zoneH); }
                if (zone6 != null) { zone6.setWidth(zoneW); zone6.setHeight(zoneH); }
            }
        } catch (Exception ex) {
            // ignore if zonesGrid not present or layout fails
        }

        // Also create/update absolute overlay rectangles positioned exactly over the goal
        updateAbsoluteZones();

        // ========== VẼ CẦU THỦ CHI TIẾT ==========
        // Use project player sprite asset
        player = createEnhancedPlayer(centerX, paneHeight - 50, Color.BLUE, "/assets/CauThu.png");
        gamePane.getChildren().add(player);

        // ========== VẼ THỦ MÔN CHI TIẾT ==========
    goalkeeperGroup = createEnhancedGoalkeeper(centerX, goalTop + goalHeight + 30, Color.ORANGE, "/assets/goalkeeper_head.jpg");
        gamePane.getChildren().add(goalkeeperGroup);

    // store initial keeper coords for animation reference
    goalkeeperInitialX = centerX;
    goalkeeperInitialY = goalTop + goalHeight + 30;

        // ========== VẼ BÓNG ĐÁ CHUYÊN NGHIỆP ==========
    ballGroup = createEnhancedBall(centerX, paneHeight - 120, 12);
        gamePane.getChildren().add(ballGroup);

    // store ball start coordinates
    ballStartX = centerX;
    ballStartY = paneHeight - 120;

        // ========== HÌNH ẢNH THẮNG/THUA ==========
        createWinLoseGroups(paneWidth, paneHeight);
    }

    // Vẽ các zone indicator (đường kẻ chia 6 ô)
    private void drawZoneIndicators(double leftX, double rightX, double top, double height) {
        double width = rightX - leftX;
        
        // Đường chia dọc (3 cột)
        for (int i = 1; i < 3; i++) {
            Line divider = new Line(
                leftX + (width / 3) * i, top + 6,
                leftX + (width / 3) * i, top + height
            );
            divider.setStroke(Color.rgb(255, 200, 0, 0.4));
            divider.setStrokeWidth(2);
            divider.getStrokeDashArray().addAll(10.0, 5.0);
            gamePane.getChildren().add(divider);
        }
        
        // Đường chia ngang (2 hàng)
        Line horizontalDivider = new Line(
            leftX, top + height / 2,
            rightX, top + height / 2
        );
        horizontalDivider.setStroke(Color.rgb(255, 200, 0, 0.4));
        horizontalDivider.setStrokeWidth(2);
        horizontalDivider.getStrokeDashArray().addAll(10.0, 5.0);
        gamePane.getChildren().add(horizontalDivider);
        
        // Thêm số zone (1-6)
        addZoneNumbers(leftX, rightX, top, height);
    }

    // Thêm số hiển thị cho từng zone
    private void addZoneNumbers(double leftX, double rightX, double top, double height) {
        double width = rightX - leftX;
        String[] zones = {"1", "2", "3", "4", "5", "6"};
        
        for (int row = 0; row < 2; row++) {
            for (int col = 0; col < 3; col++) {
                int zoneIndex = row * 3 + col;
                double x = leftX + (width / 3) * col + (width / 6);
                double y = top + (height / 2) * row + (height / 4);
                
                Text zoneText = new Text(zones[zoneIndex]);
                zoneText.setFont(Font.font("Arial", FontWeight.BOLD, 20));
                zoneText.setFill(Color.rgb(255, 255, 255, 0.3));
                zoneText.setStroke(Color.rgb(0, 0, 0, 0.3));
                zoneText.setStrokeWidth(1);
                zoneText.setX(x - 7);
                zoneText.setY(y + 7);
                gamePane.getChildren().add(zoneText);
            }
        }
    }

    // Trả về toạ độ trung tâm (absolute) cho một zone (1..6) dựa trên kích thước khung thành hiện tại
    private Point2D getZoneCenter(String zoneStr) {
        try {
            int z = Integer.parseInt(zoneStr);
            int idx = Math.max(0, Math.min(5, z - 1));
            int row = idx / 3; // 0 hoặc 1
            int col = idx % 3; // 0..2

            double x = goalLeftX + (col + 0.5) * (goalWidth / 3.0);
            // grid overlay top was set to goalTop + 6 to match net start
            double y = goalTop + 6 + (row == 0 ? goalHeight * 0.25 : goalHeight * 0.75);
            return new Point2D(x, y);
        } catch (Exception ex) {
            // fallback to center of goal
            return new Point2D((goalLeftX + goalRightX) / 2.0, goalTop + goalHeight / 2.0);
        }
    }

    // Tạo cầu thủ với đồ họa nâng cao
    private Group createEnhancedPlayer(double x, double y, Color color, String headImagePath) {
        Group playerGroup = new Group();

        try {
            // Prefer the provided head/sprite image if available, otherwise try common fallbacks
            java.io.InputStream is = null;
            if (headImagePath != null) {
                try { is = getClass().getResourceAsStream(headImagePath); } catch (Exception ignore) { is = null; }
            }
            if (is == null) {
                // try project-specific sprite names
                is = getClass().getResourceAsStream("/assets/CauThu.png");
            }
            if (is == null) {
                is = getClass().getResourceAsStream("/assets/ronaldo.png");
            }
            Image sprite = is != null ? new Image(is) : null;
            ImageView iv = new ImageView(sprite);
            iv.setPreserveRatio(true);
            iv.setFitHeight(96);
            iv.setFitWidth(48);
            iv.setLayoutX(x - iv.getFitWidth() / 2.0);
            iv.setLayoutY(y - iv.getFitHeight());
            playerGroup.getChildren().add(iv);
        } catch (Exception ex) {
            // fallback to stylized figure if sprite missing
            Line body = new Line(x, y - 25, x, y);
            body.setStroke(color);
            body.setStrokeWidth(7);
            body.setStrokeLineCap(javafx.scene.shape.StrokeLineCap.ROUND);
            Line leftArm = new Line(x, y - 20, x - 15, y - 8);
            leftArm.setStroke(color); leftArm.setStrokeWidth(5);
            Line rightArm = new Line(x, y - 20, x + 15, y - 8);
            rightArm.setStroke(color); rightArm.setStrokeWidth(5);
            Line leftLeg = new Line(x, y, x - 12, y + 20);
            leftLeg.setStroke(color); leftLeg.setStrokeWidth(5);
            Line rightLeg = new Line(x, y, x + 12, y + 20);
            rightLeg.setStroke(color); rightLeg.setStrokeWidth(5);
            Circle head = new Circle(x, y - 35, 17);
            head.setFill(Color.rgb(255, 220, 177));
            head.setStroke(color.darker()); head.setStrokeWidth(2);
            Text jerseyNumber = new Text("10");
            jerseyNumber.setFont(Font.font("Arial", FontWeight.BOLD, 10));
            jerseyNumber.setFill(Color.WHITE); jerseyNumber.setX(x - 8); jerseyNumber.setY(y - 10);
            playerGroup.getChildren().addAll(body, leftArm, rightArm, leftLeg, rightLeg, head, jerseyNumber);
        }

        return playerGroup;
    }

    // Tạo thủ môn với đồ họa nâng cao
    private Group createEnhancedGoalkeeper(double x, double y, Color color, String headImagePath) {
        Group gkGroup = new Group();
        try {
            Image img = new Image(getClass().getResourceAsStream(GK_STAND));
            goalkeeperSprite = new ImageView(img);
            goalkeeperSprite.setPreserveRatio(true);
            // size keeper relative to goal height when available
            double h = goalHeight > 0 ? Math.max(56, goalHeight * 0.7) : 86;
            goalkeeperSprite.setFitHeight(h);
            goalkeeperSprite.setLayoutX(x - goalkeeperSprite.getFitWidth() / 2.0);
            goalkeeperSprite.setLayoutY(y - goalkeeperSprite.getFitHeight());
            gkGroup.getChildren().add(goalkeeperSprite);
        } catch (Exception ex) {
            // fallback - simple drawn keeper
            Line body = new Line(x, y - 25, x, y);
            body.setStroke(color); body.setStrokeWidth(7);
            Line leftArm = new Line(x, y - 20, x - 20, y - 15); leftArm.setStroke(color); leftArm.setStrokeWidth(5);
            Line rightArm = new Line(x, y - 20, x + 20, y - 15); rightArm.setStroke(color); rightArm.setStrokeWidth(5);
            Circle leftGlove = new Circle(x - 20, y - 15, 6); leftGlove.setFill(Color.YELLOW); leftGlove.setStroke(Color.BLACK);
            Circle rightGlove = new Circle(x + 20, y - 15, 6); rightGlove.setFill(Color.YELLOW); rightGlove.setStroke(Color.BLACK);
            Line leftLeg = new Line(x, y, x - 10, y + 20); leftLeg.setStroke(color); leftLeg.setStrokeWidth(5);
            Line rightLeg = new Line(x, y, x + 10, y + 20); rightLeg.setStroke(color); rightLeg.setStrokeWidth(5);
            Circle head = new Circle(x, y - 35, 17); head.setFill(Color.rgb(255, 220, 177)); head.setStroke(color.darker()); head.setStrokeWidth(2);
            Text jerseyNumber = new Text("1"); jerseyNumber.setFont(Font.font("Arial", FontWeight.BOLD, 12)); jerseyNumber.setFill(Color.BLACK); jerseyNumber.setX(x - 5); jerseyNumber.setY(y - 10);
            gkGroup.getChildren().addAll(body, leftArm, rightArm, leftGlove, rightGlove, leftLeg, rightLeg, head, jerseyNumber);
        }
        return gkGroup;
    }

    // Tạo bóng đá với hình ảnh thật
    private Group createEnhancedBall(double x, double y, double radius) {
        Group ball = new Group();
        
        try {
            // Sử dụng hình ảnh quả bóng
            Image ballImage = new Image(getClass().getResourceAsStream(BALL_IMG));
            ballImageView = new ImageView(ballImage);
            ballImageView.setPreserveRatio(true);
            ballImageView.setFitWidth(radius * 2);
            ballImageView.setFitHeight(radius * 2);
            ballImageView.setLayoutX(x - radius);
            ballImageView.setLayoutY(y - radius);
            
            // Tạo một circle ẩn để theo dõi vị trí (cho animation)
            ballCircle = new Circle(x, y, radius);
            ballCircle.setFill(Color.TRANSPARENT);
            ballCircle.setStroke(Color.TRANSPARENT);
            
            ball.getChildren().addAll(ballCircle, ballImageView);
        } catch (Exception ex) {
            // Fallback: vẽ bóng đơn giản nếu không load được hình
            Circle ballCircleMain = new Circle(x, y, radius);
            ballCircleMain.setFill(Color.WHITE);
            ballCircleMain.setStroke(Color.BLACK);
            ballCircleMain.setStrokeWidth(1.5);
            ballCircle = ballCircleMain;
            
            Polygon pentagon1 = createPentagon(x, y, radius * 0.5, 0);
            pentagon1.setFill(Color.BLACK);
            
            ball.getChildren().addAll(ballCircleMain, pentagon1);
        }
        
        return ball;
    }

    // Tạo ngũ giác cho bóng
    private Polygon createPentagon(double centerX, double centerY, double radius, double rotation) {
        Polygon pentagon = new Polygon();
        for (int i = 0; i < 5; i++) {
            double angle = Math.toRadians(i * 72 + rotation - 90);
            pentagon.getPoints().addAll(
                centerX + radius * Math.cos(angle),
                centerY + radius * Math.sin(angle)
            );
        }
        return pentagon;
    }

    // Tạo các group thắng/thua
    private void createWinLoseGroups(double paneWidth, double paneHeight) {
        // Hình ảnh thắng (robustly load resource, fallback if missing)
        ImageView imageView = null;
        try {
            java.net.URL imgUrl = getClass().getResource("/assets/c1cup.png");
            if (imgUrl == null) {
                // try a sensible fallback that exists in the project
                imgUrl = getClass().getResource("/assets/CauThu.png");
            }
            if (imgUrl != null) {
                Image image = new Image(imgUrl.toExternalForm());
                imageView = new ImageView(image);
                imageView.setX(paneWidth / 2 - 80);
                imageView.setY(paneHeight / 2 - 100);
                imageView.setFitWidth(160);
                imageView.setFitHeight(160);
            }
        } catch (Exception ex) {
            // ignore image load errors, we'll show text only
            imageView = null;
        }

        Text winText = new Text("🏆 BẠN ĐÃ THẮNG! 🏆");
        winText.setFill(Color.GOLD);
        winText.setFont(Font.font("Arial", FontWeight.BOLD, 28));
        winText.setStroke(Color.DARKGREEN);
        winText.setStrokeWidth(2);
        winText.setX(paneWidth / 2 - 180);
        winText.setY(paneHeight / 2 + 80);

        Text winText2 = new Text("Glory Man United!");
        winText2.setFill(Color.YELLOW);
        winText2.setFont(Font.font("Arial", FontWeight.BOLD, 22));
        winText2.setStroke(Color.RED);
        winText2.setStrokeWidth(1.5);
        winText2.setX(paneWidth / 2 - 140);
        winText2.setY(paneHeight / 2 + 115);

        if (imageView != null) {
            imageWinGroup = new Group(imageView, winText, winText2);
        } else {
            imageWinGroup = new Group(winText, winText2);
        }
        gamePane.getChildren().add(imageWinGroup);
        enableWinGroup(false);

        // Hình ảnh thua (robust load with fallback)
        ImageView imageLoseView = null;
        try {
            java.net.URL imgLoseUrl = getClass().getResource("/assets/loa.png");
            if (imgLoseUrl == null) {
                imgLoseUrl = getClass().getResource("/assets/QuaBong.jpg");
            }
            if (imgLoseUrl != null) {
                Image imageLose = new Image(imgLoseUrl.toExternalForm());
                imageLoseView = new ImageView(imageLose);
                imageLoseView.setX(paneWidth / 2 - 80);
                imageLoseView.setY(paneHeight / 2 - 100);
                imageLoseView.setFitWidth(160);
                imageLoseView.setFitHeight(160);
            }
        } catch (Exception ex) {
            imageLoseView = null;
        }

        Text loseText = new Text("😢 BẠN ĐÃ THUA! 😢");
        loseText.setFill(Color.rgb(255, 100, 100));
        loseText.setFont(Font.font("Arial", FontWeight.BOLD, 28));
        loseText.setStroke(Color.DARKRED);
        loseText.setStrokeWidth(2);
        loseText.setX(paneWidth / 2 - 170);
        loseText.setY(paneHeight / 2 + 80);

        Text loseText2 = new Text("Tất cả vào hang!");
        loseText2.setFill(Color.ORANGE);
        loseText2.setFont(Font.font("Arial", FontWeight.BOLD, 22));
        loseText2.setStroke(Color.DARKGOLDENROD);
        loseText2.setStrokeWidth(1.5);
        loseText2.setX(paneWidth / 2 - 120);
        loseText2.setY(paneHeight / 2 + 115);

        if (imageLoseView != null) {
            imageLoseGroup = new Group(imageLoseView, loseText, loseText2);
        } else {
            imageLoseGroup = new Group(loseText, loseText2);
        }
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
        Platform.runLater(() -> {
            // Compute target center for the selected zone so it always matches the drawn goal
            Point2D target = getZoneCenter(directShoot);
            double targetX = target.getX();
            double targetY = target.getY();
            
            // ========== ANIMATION BÓNG BAY THẲNG ==========
            // Thay vì đường cong, bóng sẽ bay thẳng đến vị trí
            TranslateTransition ballTransition = new TranslateTransition(Duration.seconds(0.8), ballGroup);
            ballTransition.setToX(targetX - ballStartX);
            ballTransition.setToY(targetY - ballStartY);
            ballTransition.setInterpolator(javafx.animation.Interpolator.LINEAR);
            
            // ========== ANIMATION BÓNG XOAY ==========
            javafx.animation.RotateTransition ballSpin = new javafx.animation.RotateTransition(Duration.seconds(0.8), ballGroup);
            ballSpin.setByAngle(720); // Xoay 2 vòng
            
            // ========== ANIMATION THỦ MÔN BAY NGƯỜI ==========
            // Keeper sprite animation (uses provided images)
            boolean keeperTop = Integer.parseInt(directKeeper) <= 3;
            SequentialTransition keeperAnim = createKeeperDiveAnimation(directKeeper, keeperTop, false);
            
            // ========== HIỆU ỨNG PARTICLES KHI GHI BÀN ==========
            Group goalParticles = createGoalParticles(targetX, targetY);
            gamePane.getChildren().add(goalParticles);
            
            // Kết hợp các animation
            ParallelTransition ballAnim = new ParallelTransition(ballTransition, ballSpin);
            ParallelTransition gameAnim = new ParallelTransition(ballAnim, keeperAnim);
            
            // Hiển thị particles sau khi bóng vào lưới
            gameAnim.setOnFinished(e -> {
                animateGoalParticles(goalParticles);
                
                // Flash màu cho khung thành
                flashGoalNet();
                
                // Reset sau 2 giây
                PauseTransition pause = new PauseTransition(Duration.seconds(2.5));
                pause.setOnFinished(evt -> {
                    ballGroup.setTranslateX(0);
                    ballGroup.setTranslateY(0);
                    ballGroup.setRotate(0);
                    goalkeeperGroup.setTranslateX(0);
                    goalkeeperGroup.setTranslateY(0);
                    goalkeeperGroup.setRotate(0);
                    gamePane.getChildren().remove(goalParticles);
                });
                pause.play();
            });
            
            gameAnim.play();
        });
    }

    public void animateShootKhongVao(String directShoot, String directKeeper) {
        Platform.runLater(() -> {
            // Compute zone-based target center so the miss animation still lands accurately
            Point2D target = getZoneCenter(directShoot);
            double targetX = target.getX();
            double targetY = target.getY();
            
            // ========== ANIMATION BÓNG BAY THẲNG ==========
            TranslateTransition ballToGoal = new TranslateTransition(Duration.seconds(0.7), ballGroup);
            ballToGoal.setToX(targetX - ballStartX);
            ballToGoal.setToY(targetY - ballStartY);
            ballToGoal.setInterpolator(javafx.animation.Interpolator.LINEAR);
            
            // Animation xoay bóng
            javafx.animation.RotateTransition ballSpin = new javafx.animation.RotateTransition(Duration.seconds(0.7), ballGroup);
            ballSpin.setByAngle(720);
            
            // ========== ANIMATION THỦ MÔN BAY NGƯỜI CHẶN ==========
            // Keeper sprite animation
            boolean keeperTop = Integer.parseInt(directKeeper) <= 3;
            SequentialTransition keeperAnim = createKeeperDiveAnimation(directKeeper, keeperTop, directShoot.equals(directKeeper));
            
            // ========== ANIMATION BÓNG BỊ ĐẨY RA (NẾU CHẶN ĐƯỢC) ==========
            TranslateTransition ballBounce = null;
            javafx.animation.RotateTransition ballBounceSpin = null;
            
            if (directShoot.equals(directKeeper)) {
                // Bóng bị đẩy ra ngoài
                double bounceX = (targetX > ballStartX ? 60 : -60);
                double bounceY = -40;
                
                ballBounce = new TranslateTransition(Duration.seconds(0.4), ballGroup);
                ballBounce.setToX((targetX - ballStartX) + bounceX);
                ballBounce.setToY((targetY - ballStartY) + bounceY);
                ballBounce.setInterpolator(javafx.animation.Interpolator.EASE_IN);
                
                ballBounceSpin = new javafx.animation.RotateTransition(Duration.seconds(0.4), ballGroup);
                ballBounceSpin.setByAngle(180);
                
                // Hiệu ứng particles khi chặn bóng
                Group saveParticles = createSaveParticles(targetX, targetY);
                gamePane.getChildren().add(saveParticles);
                
                ballBounce.setOnFinished(e -> {
                    animateSaveParticles(saveParticles);
                    flashSaveEffect();
                    
                    PauseTransition pause = new PauseTransition(Duration.seconds(1.5));
                    pause.setOnFinished(evt -> gamePane.getChildren().remove(saveParticles));
                    pause.play();
                });
            }
            
            // ========== KẾT HỢP CÁC ANIMATION ==========
            ParallelTransition ballAnim = new ParallelTransition(ballToGoal, ballSpin);
            ParallelTransition gkAnim = new ParallelTransition(keeperAnim);
            
            SequentialTransition fullAnim;
            if (ballBounce != null && ballBounceSpin != null) {
                ParallelTransition bounceAnim = new ParallelTransition(ballBounce, ballBounceSpin);
                fullAnim = new SequentialTransition(
                    new ParallelTransition(ballAnim, gkAnim),
                    bounceAnim,
                    new PauseTransition(Duration.seconds(2))
                );
            } else {
                fullAnim = new SequentialTransition(
                    new ParallelTransition(ballAnim, gkAnim),
                    new PauseTransition(Duration.seconds(2))
                );
            }
            
            fullAnim.setOnFinished(e -> {
                ballGroup.setTranslateX(0);
                ballGroup.setTranslateY(0);
                ballGroup.setRotate(0);
                goalkeeperGroup.setTranslateX(0);
                goalkeeperGroup.setTranslateY(0);
                goalkeeperGroup.setRotate(0);
            });
            
            fullAnim.play();
        });
    }

    // ========== CÁC PHƯƠNG THỨC TẠO HIỆU ỨNG PARTICLES ==========
    
    private Group createGoalParticles(double x, double y) {
        Group particles = new Group();
        for (int i = 0; i < 30; i++) {
            Circle particle = new Circle(x, y, Math.random() * 4 + 2);
            particle.setFill(Color.rgb(255, 215, 0, Math.random() * 0.8 + 0.2));
            particles.getChildren().add(particle);
        }
        particles.setVisible(false);
        return particles;
    }

    // Create a keeper dive/fall animation using provided sprites
    private SequentialTransition createKeeperDiveAnimation(String directKeeper, boolean isTopRow, boolean isSave) {
        int zone = 1;
        try { 
            zone = Integer.parseInt(directKeeper); 
        } catch (Exception ex) { 
            zone = 1; 
        }

        Point2D keeperTarget = getZoneCenter(directKeeper);
        double dx = keeperTarget.getX() - goalkeeperInitialX;
        double dy = keeperTarget.getY() - goalkeeperInitialY;

        // Load images safely (must be final to be used inside lambdas)
        final Image stand;
        final Image jumpImage;
        final Image fallImage;
        
        try { 
            stand = new Image(getClass().getResourceAsStream(GK_STAND)); 
        } catch (Exception e) { 
            throw new RuntimeException(e); 
        }
        
        // Chọn hình ảnh dựa trên zone (1-6)
        try {
            switch (zone) {
                case 1: // Góc trên bên trái
                    jumpImage = new Image(getClass().getResourceAsStream(GK_JUMP_LEFT));
                    fallImage = new Image(getClass().getResourceAsStream(GK_FALL_LEFT));
                    break;
                case 2: // Giữa bên trên - nhảy lên
                    jumpImage = new Image(getClass().getResourceAsStream(GK_JUMP_UP));
                    fallImage = new Image(getClass().getResourceAsStream(GK_JUMP_UP)); // Giữ nguyên tư thế nhảy
                    break;
                case 3: // Góc trên bên phải
                    jumpImage = new Image(getClass().getResourceAsStream(GK_JUMP_RIGHT));
                    fallImage = new Image(getClass().getResourceAsStream(GK_FALL_RIGHT));
                    break;
                case 4: // Góc dưới bên trái
                    jumpImage = new Image(getClass().getResourceAsStream(GK_FALL_LEFT)); // Ngã trực tiếp
                    fallImage = new Image(getClass().getResourceAsStream(GK_FALL_LEFT));
                    break;
                case 5: // Giữa bên dưới - đứng yên
                    jumpImage = stand; // Giữ nguyên tư thế đứng
                    fallImage = stand;
                    break;
                case 6: // Góc dưới bên phải
                    jumpImage = new Image(getClass().getResourceAsStream(GK_FALL_RIGHT)); // Ngã trực tiếp
                    fallImage = new Image(getClass().getResourceAsStream(GK_FALL_RIGHT));
                    break;
                default:
                    jumpImage = stand;
                    fallImage = stand;
            }
        } catch (Exception e) { 
            throw new RuntimeException(e); 
        }

        // Ensure sprite exists
        if (goalkeeperSprite == null) {
            goalkeeperSprite = new ImageView();
            goalkeeperSprite.setFitHeight(Math.max(56, goalHeight * 0.7));
            goalkeeperSprite.setPreserveRatio(true);
            goalkeeperGroup.getChildren().add(0, goalkeeperSprite);
        }

        // Xử lý animation dựa trên zone
        SequentialTransition seq;
        
        if (zone == 5) {
            // Zone 5: Giữa dưới - thủ môn chỉ đứng yên hoặc cúi xuống nhẹ
            PauseTransition setStand = new PauseTransition(Duration.millis(5));
            setStand.setOnFinished(e -> goalkeeperSprite.setImage(stand));
            
            TranslateTransition crouch = new TranslateTransition(Duration.seconds(0.4), goalkeeperGroup);
            crouch.setByY(20); // Cúi xuống nhẹ
            crouch.setInterpolator(javafx.animation.Interpolator.EASE_OUT);
            
            PauseTransition holdPause = new PauseTransition(Duration.seconds(isSave ? 0.6 : 0.2));
            
            seq = new SequentialTransition(setStand, crouch, holdPause);
            
        } else if (zone == 2) {
            // Zone 2: Giữa trên - nhảy lên thẳng
            PauseTransition setJump = new PauseTransition(Duration.millis(5));
            setJump.setOnFinished(e -> goalkeeperSprite.setImage(jumpImage));
            
            TranslateTransition jumpUp = new TranslateTransition(Duration.seconds(0.5), goalkeeperGroup);
            jumpUp.setByY(dy); // Nhảy lên
            jumpUp.setInterpolator(javafx.animation.Interpolator.EASE_OUT);
            
            PauseTransition holdPause = new PauseTransition(Duration.seconds(isSave ? 0.6 : 0.2));
            
            seq = new SequentialTransition(setJump, jumpUp, holdPause);
            
        } else if (zone == 4 || zone == 6) {
            // Zone 4 hoặc 6: Góc dưới - ngã nhanh sang bên
            PauseTransition setFall = new PauseTransition(Duration.millis(5));
            setFall.setOnFinished(e -> goalkeeperSprite.setImage(fallImage));
            
            TranslateTransition dive = new TranslateTransition(Duration.seconds(0.5), goalkeeperGroup);
            dive.setByX(dx);
            dive.setByY(dy);
            dive.setInterpolator(javafx.animation.Interpolator.EASE_OUT);
            
            RotateTransition rotate = new RotateTransition(Duration.seconds(0.5), goalkeeperGroup);
            rotate.setByAngle(zone == 4 ? -30 : 30); // Nghiêng theo hướng ngã
            
            PauseTransition holdPause = new PauseTransition(Duration.seconds(isSave ? 0.6 : 0.2));
            
            seq = new SequentialTransition(setFall, new ParallelTransition(dive, rotate), holdPause);
            
        } else {
            // Zone 1 hoặc 3: Góc trên - nhảy rồi ngã
            PauseTransition setJump = new PauseTransition(Duration.millis(5));
            setJump.setOnFinished(e -> goalkeeperSprite.setImage(jumpImage));
            
            TranslateTransition jump = new TranslateTransition(Duration.seconds(0.5), goalkeeperGroup);
            jump.setByX(dx * 0.7);
            jump.setByY(dy - 20); // Nhảy lên cao
            jump.setInterpolator(javafx.animation.Interpolator.EASE_OUT);
            
            RotateTransition jumpRotate = new RotateTransition(Duration.seconds(0.5), goalkeeperGroup);
            jumpRotate.setByAngle(zone == 1 ? -40 : 40);
            
            PauseTransition setFall = new PauseTransition(Duration.millis(5));
            setFall.setOnFinished(e -> goalkeeperSprite.setImage(fallImage));
            
            TranslateTransition fall = new TranslateTransition(Duration.seconds(0.4), goalkeeperGroup);
            fall.setByX(dx * 0.3);
            fall.setByY(25); // Rơi xuống
            fall.setInterpolator(javafx.animation.Interpolator.EASE_IN);
            
            PauseTransition holdPause = new PauseTransition(Duration.seconds(isSave ? 0.6 : 0.2));
            
            seq = new SequentialTransition(
                setJump, 
                new ParallelTransition(jump, jumpRotate), 
                setFall, 
                fall, 
                holdPause
            );
        }
        
        // Reset về tư thế ban đầu
        seq.setOnFinished(e -> {
            if (stand != null) goalkeeperSprite.setImage(stand);
            goalkeeperGroup.setTranslateX(0);
            goalkeeperGroup.setTranslateY(0);
            goalkeeperGroup.setRotate(0);
        });
        
        return seq;
    }

    private void animateGoalParticles(Group particles) {
        particles.setVisible(true);

        for (int i = 0; i < particles.getChildren().size(); i++) {
            Circle particle = (Circle) particles.getChildren().get(i);
            double angle = Math.random() * 2 * Math.PI;
            double distance = Math.random() * 80 + 40;

            TranslateTransition tt = new TranslateTransition(Duration.seconds(1), particle);
            tt.setByX(Math.cos(angle) * distance);
            tt.setByY(Math.sin(angle) * distance);
            tt.setInterpolator(javafx.animation.Interpolator.EASE_OUT);

            javafx.animation.FadeTransition ft = new javafx.animation.FadeTransition(Duration.seconds(1), particle);
            ft.setToValue(0);

            ParallelTransition pt = new ParallelTransition(tt, ft);
            pt.play();
        }
    }
    
    private Group createSaveParticles(double x, double y) {
        Group particles = new Group();
        for (int i = 0; i < 20; i++) {
            Circle particle = new Circle(x, y, Math.random() * 3 + 1);
            particle.setFill(Color.rgb(255, 255, 255, Math.random() * 0.7 + 0.3));
            particles.getChildren().add(particle);
        }
        particles.setVisible(false);
        return particles;
    }
    
    private void animateSaveParticles(Group particles) {
        particles.setVisible(true);
        
        for (int i = 0; i < particles.getChildren().size(); i++) {
            Circle particle = (Circle) particles.getChildren().get(i);
            double angle = Math.random() * 2 * Math.PI;
            double distance = Math.random() * 60 + 30;
            
            TranslateTransition tt = new TranslateTransition(Duration.seconds(0.8), particle);
            tt.setByX(Math.cos(angle) * distance);
            tt.setByY(Math.sin(angle) * distance);
            
            javafx.animation.FadeTransition ft = new javafx.animation.FadeTransition(Duration.seconds(0.8), particle);
            ft.setToValue(0);
            
            ParallelTransition pt = new ParallelTransition(tt, ft);
            pt.play();
        }
    }
    
    private void flashGoalNet() {
        // Tạo flash màu vàng cho khung thành khi ghi bàn
        Rectangle flash = new Rectangle(0, 0, gamePane.getWidth(), gamePane.getHeight());
        flash.setFill(Color.rgb(255, 215, 0, 0.3));
        gamePane.getChildren().add(flash);
        
        javafx.animation.FadeTransition ft = new javafx.animation.FadeTransition(Duration.seconds(0.5), flash);
        ft.setFromValue(0.3);
        ft.setToValue(0);
        ft.setOnFinished(e -> gamePane.getChildren().remove(flash));
        ft.play();
    }
    
    private void flashSaveEffect() {
        // Flash màu xanh khi cản phá
        Rectangle flash = new Rectangle(0, 0, gamePane.getWidth(), gamePane.getHeight());
        flash.setFill(Color.rgb(0, 200, 255, 0.25));
        gamePane.getChildren().add(flash);
        
        javafx.animation.FadeTransition ft = new javafx.animation.FadeTransition(Duration.seconds(0.4), flash);
        ft.setFromValue(0.25);
        ft.setToValue(0);
        ft.setOnFinished(e -> gamePane.getChildren().remove(flash));
        ft.play();
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
            // Dừng countdown nếu đang chạy
            if (countdownTimeline != null) {
                countdownTimeline.stop();
            }
            
            Alert alert = new Alert(AlertType.INFORMATION);
            alert.setTitle("Kết Thúc Trận Đấu");
            alert.setHeaderText(null);
            alert.setContentText(result);
            
            // Sử dụng showAndWait để đảm bảo alert hiển thị trước khi chuyển màn hình
            alert.showAndWait();
            
            // Chuyển về màn hình chính ngay sau khi đóng alert
            try {
                client.showMainUI();
            } catch (Exception e) {
                e.printStackTrace();
                // Nếu lỗi, thử delay 500ms rồi chuyển lại
                PauseTransition delay = new PauseTransition(Duration.millis(500));
                delay.setOnFinished(event -> {
                    try {
                        client.showMainUI();
                    } catch (Exception ex) {
                        System.err.println("❌ Không thể chuyển về MainUI: " + ex.getMessage());
                        ex.printStackTrace();
                    }
                });
                delay.play();
            }
        });
    }

    public void handleRematchDeclined(String message) {
        Platform.runLater(() -> {
            // Dừng countdown nếu đang chạy
            if (countdownTimeline != null) {
                countdownTimeline.stop();
            }
            
            Alert alert = new Alert(AlertType.INFORMATION);
            alert.setTitle("Chơi Lại");
            alert.setHeaderText(null);
            alert.setContentText(message);
            
            // Sử dụng showAndWait
            alert.showAndWait();
            
            // Chuyển về màn hình chính ngay sau khi đóng alert
            try {
                client.showMainUI();
            } catch (Exception e) {
                e.printStackTrace();
                // Nếu lỗi, thử delay 500ms rồi chuyển lại
                PauseTransition delay = new PauseTransition(Duration.millis(500));
                delay.setOnFinished(event -> {
                    try {
                        client.showMainUI();
                    } catch (Exception ex) {
                        System.err.println("❌ Không thể chuyển về MainUI: " + ex.getMessage());
                        ex.printStackTrace();
                    }
                });
                delay.play();
            }
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
                // Try to send quit message only if connection still open. Always
                // attempt to return to MainUI to avoid users getting stuck when
                // the remote peer or server already closed the connection.
                try {
                    if (client != null && client.isConnected()) {
                        try { client.sendMessage(quitMessage); } catch (IOException ignored) { }
                    }
                } finally {
                    try { client.showMainUI(); } catch (Exception ex) { ex.printStackTrace(); }
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
            } else if (result.equals("draw")) {
                // Hòa - không hiển thị win hoặc lose group
                enableWinGroup(false);
                enableLoseGroup(false);
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
