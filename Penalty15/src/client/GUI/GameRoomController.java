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
import javafx.scene.media.MediaView;
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
    
    // Celebration video for winner
    private MediaView celebrationMediaView;
    private MediaPlayer celebrationMediaPlayer;

    private static final int TURN_TIMEOUT = 15;
    private int lastTurnDuration = 15;
    private String yourRole = "";
    private boolean isMyTurn = false;
    private String waitingForOpponentAction = "";
    private int clientCurrentRound = 1;

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
    private static final String BALL_IMG = "/assets/QuaBong.png";

    // ============ NEW CLICK-BASED ZONE SYSTEM ============
    @FXML
    private void handleZoneClick(MouseEvent event) {
        if (!actionPerformed && !currentMode.isEmpty()) {
            Rectangle clickedZone = (Rectangle) event.getSource();
            String direction = getDirectionFromZone(clickedZone);
            if (direction == null) {
                // Ignore clicks that cannot be mapped to a direction
                return;
            }
            
            // Send action to server; include current client round id to avoid stale processing on server
            if (clientCurrentRound <= 0) {
                System.out.println("⚠️ Không có thông tin vòng hiện tại trên client, bỏ qua gửi hành động.");
                return;
            }

            Message message;
            if (currentMode.equals("shoot")) {
                // New payload: Object[]{ direction:String, round: Integer }
                message = new Message("shoot", new Object[]{ direction, clientCurrentRound });
                System.out.println("Shot direction: " + direction + ", round=" + clientCurrentRound);
            } else {
                message = new Message("goalkeeper", new Object[]{ direction, clientCurrentRound });
                System.out.println("Goalkeeper direction: " + direction + ", round=" + clientCurrentRound);
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
        // No default selection: return null so caller won't auto-send a choice
        return null;
    }
    
    @FXML
    private void handleShootMode() {
        // Auto-select shoot mode when button is clicked - no toggle off
        currentMode = "shoot";
        shootModeButton.setSelected(true);
        goalkeeperModeButton.setSelected(false);
        actionPerformed = false;
        enableZones(true);
        instructionLabel.setText("🎯 Nhấp vào khung thành để chọn vị trí sút bóng!");
        instructionLabel.setStyle("-fx-font-size: 13px; -fx-font-weight: bold; -fx-text-fill: #4ecca3; -fx-alignment: center; -fx-padding: 8; -fx-background-color: #e8fff6; -fx-background-radius: 10; -fx-border-radius: 10;");
    }
    
    @FXML
    private void handleGoalkeeperMode() {
        // Auto-select goalkeeper mode when button is clicked - no toggle off
        currentMode = "goalkeeper";
        goalkeeperModeButton.setSelected(true);
        shootModeButton.setSelected(false);
        actionPerformed = false;
        enableZones(true);
        instructionLabel.setText("🛡️ Nhấp vào khung thành để chọn vị trí chặn bóng!");
        instructionLabel.setStyle("-fx-font-size: 13px; -fx-font-weight: bold; -fx-text-fill: #ffd93d; -fx-alignment: center; -fx-padding: 8; -fx-background-color: #fffbeb; -fx-background-radius: 10; -fx-border-radius: 10;");
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
        // Only show the "sent choice" message for the player who acted.
        // Avoid overwriting observer's instruction label (e.g., "Đối thủ đang sút...")
        if (isMyTurn) {
            instructionLabel.setText("⏳ Đã gửi lựa chọn! Đang chờ đối thủ...");
            instructionLabel.setStyle("-fx-font-size: 13px; -fx-font-weight: bold; -fx-text-fill: #999; -fx-alignment: center; -fx-padding: 8; -fx-background-color: #f5f5f5; -fx-background-radius: 10; -fx-border-radius: 10;");
        }
    }

    public void updateScore(int[] scores) {
        Platform.runLater(() -> {
            int yourScore = scores[0];
            int opponentScore = scores[1];
            int currentRound = scores[2];
            // keep client-side copy of current round for timeout messages
            clientCurrentRound = currentRound;
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

    // Tạo thủ môn với đồ họa nâng cao (removed circle indicator above goalkeeper)
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
            // Circle indicator removed - no longer adding circle above goalkeeper
        } catch (Exception ex) {
            // fallback - simple drawn keeper (without circle)
            Line body = new Line(x, y - 25, x, y);
            body.setStroke(color); body.setStrokeWidth(7);
            Line leftArm = new Line(x, y - 20, x - 20, y - 15); leftArm.setStroke(color); leftArm.setStrokeWidth(5);
            Line rightArm = new Line(x, y - 20, x + 20, y - 15); rightArm.setStroke(color); rightArm.setStrokeWidth(5);
            Circle leftGlove = new Circle(x - 20, y - 15, 6); leftGlove.setFill(Color.YELLOW); leftGlove.setStroke(Color.BLACK);
            Circle rightGlove = new Circle(x + 20, y - 15, 6); rightGlove.setFill(Color.YELLOW); rightGlove.setStroke(Color.BLACK);
            Line leftLeg = new Line(x, y, x - 10, y + 20); leftLeg.setStroke(color); leftLeg.setStrokeWidth(5);
            Line rightLeg = new Line(x, y, x + 10, y + 20); rightLeg.setStroke(color); rightLeg.setStrokeWidth(5);
            // Circle head removed - no longer adding circle above goalkeeper
            Text jerseyNumber = new Text("1"); jerseyNumber.setFont(Font.font("Arial", FontWeight.BOLD, 12)); jerseyNumber.setFill(Color.BLACK); jerseyNumber.setX(x - 5); jerseyNumber.setY(y - 10);
            gkGroup.getChildren().addAll(body, leftArm, rightArm, leftGlove, rightGlove, leftLeg, rightLeg, jerseyNumber);
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
                imgLoseUrl = getClass().getResource("/assets/QuaBong.png");
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
        if (enable) {
            playCelebrationVideo();
        }
    }

    private void enableLoseGroup(boolean enable) {
        imageLoseGroup.setVisible(enable);
    }
    
    // Play celebration video with confetti effect for winner
    private void playCelebrationVideo() {
        try {
            // Try to load celebration video from resources
            String videoPath = getClass().getResource("/assets/celebration.mp4").toExternalForm();
            
            if (videoPath != null) {
                Media media = new Media(videoPath);
                celebrationMediaPlayer = new MediaPlayer(media);
                celebrationMediaView = new MediaView(celebrationMediaPlayer);
                
                // Set video size and position
                double paneWidth = gamePane.getWidth() > 0 ? gamePane.getWidth() : 650;
                double paneHeight = gamePane.getHeight() > 0 ? gamePane.getHeight() : 550;
                
                celebrationMediaView.setFitWidth(paneWidth);
                celebrationMediaView.setFitHeight(paneHeight);
                celebrationMediaView.setPreserveRatio(false);
                celebrationMediaView.setX(0);
                celebrationMediaView.setY(0);
                
                // Make video semi-transparent for overlay effect
                celebrationMediaView.setOpacity(0.85);
                
                // Add to gamePane
                gamePane.getChildren().add(celebrationMediaView);
                
                // Play video
                celebrationMediaPlayer.play();
                
                // Auto-remove and cleanup after video ends
                celebrationMediaPlayer.setOnEndOfMedia(() -> {
                    celebrationMediaPlayer.stop();
                    celebrationMediaPlayer.dispose();
                    gamePane.getChildren().remove(celebrationMediaView);
                });
                
                System.out.println("✅ Celebration video playing!");
            }
        } catch (Exception e) {
            // Video not found or error - silently ignore
            System.out.println("⚠️ Celebration video not found: " + e.getMessage());
        }
    }

    // ============ GAME FLOW METHODS ============
    
    public void updateChat(String message) {
        Platform.runLater(() -> {
            chatArea.appendText(message + "\n");
        });
    }
    
    public void animateShootVao(String shootDirection, String gkDirection) {
        Platform.runLater(() -> {
            Point2D targetPos = getZoneCenter(shootDirection);
            animateBallAndKeeper(targetPos, gkDirection, true);
        });
    }
    
    public void animateShootKhongVao(String shootDirection, String gkDirection) {
        Platform.runLater(() -> {
            Point2D targetPos = getZoneCenter(shootDirection);
            animateBallAndKeeper(targetPos, gkDirection, false);
        });
    }
    
    private void animateBallAndKeeper(Point2D target, String gkDirection, boolean isGoal) {
        // Animate ball
        if (ballGroup != null) {
            TranslateTransition ballMove = new TranslateTransition(Duration.millis(800), ballGroup);
            ballMove.setToX(target.getX() - ballStartX);
            ballMove.setToY(target.getY() - ballStartY);
            ballMove.setInterpolator(Interpolator.EASE_IN);
            
            // Animate goalkeeper
            if (goalkeeperGroup != null) {
                Point2D gkTarget = getZoneCenter(gkDirection);
                TranslateTransition gkMove = new TranslateTransition(Duration.millis(600), goalkeeperGroup);
                gkMove.setToX(gkTarget.getX() - goalkeeperInitialX);
                gkMove.setToY(gkTarget.getY() - goalkeeperInitialY);
                
                // Change goalkeeper sprite based on direction
                changeGoalkeeperSprite(gkDirection);
                
                ParallelTransition parallel = new ParallelTransition(ballMove, gkMove);
                parallel.setOnFinished(e -> {
                    // Reset positions after animation
                    PauseTransition pause = new PauseTransition(Duration.millis(1000));
                    pause.setOnFinished(ev -> resetPositions());
                    pause.play();
                });
                parallel.play();
            } else {
                ballMove.play();
            }
        }
    }
    
    private void changeGoalkeeperSprite(String direction) {
        if (goalkeeperSprite == null) return;
        
        try {
            String spritePath = GK_STAND;
            int zone = Integer.parseInt(direction);
            
            // Choose sprite based on zone
            if (zone == 1 || zone == 4) {
                spritePath = GK_JUMP_LEFT;
            } else if (zone == 3 || zone == 6) {
                spritePath = GK_JUMP_RIGHT;
            } else if (zone == 2) {
                spritePath = GK_JUMP_UP;
            }
            
            Image img = new Image(getClass().getResourceAsStream(spritePath));
            goalkeeperSprite.setImage(img);
        } catch (Exception ex) {
            // Ignore sprite change errors
        }
    }
    
    private void resetPositions() {
        if (ballGroup != null) {
            ballGroup.setTranslateX(0);
            ballGroup.setTranslateY(0);
        }
        if (goalkeeperGroup != null) {
            goalkeeperGroup.setTranslateX(0);
            goalkeeperGroup.setTranslateY(0);
            try {
                Image img = new Image(getClass().getResourceAsStream(GK_STAND));
                goalkeeperSprite.setImage(img);
            } catch (Exception ex) {
                // Ignore
            }
        }
    }
    
    public void showRoundResult(String result) {
        Platform.runLater(() -> {
            Alert alert = new Alert(AlertType.INFORMATION);
            alert.setTitle("Kết quả vòng đấu");
            alert.setHeaderText(null);
            alert.setContentText(result);
            alert.showAndWait();
        });
    }
    
    public void endMatch(String result) {
        Platform.runLater(() -> {
            // Stop any running timers
            if (countdownTimeline != null) {
                countdownTimeline.stop();
            }
            
            // Show result
            if (result.contains("THẮNG")) {
                enableWinGroup(true);
            } else if (result.contains("THUA")) {
                enableLoseGroup(true);
            } else {
                // Draw case
                showAlert("Trận đấu kết thúc", result, AlertType.INFORMATION);
            }
            
            // Disable all controls
            shootModeButton.setDisable(true);
            goalkeeperModeButton.setDisable(true);
            enableZones(false);
            
            instructionLabel.setText("🏁 Trận đấu đã kết thúc!");
            instructionLabel.setStyle("-fx-font-size: 13px; -fx-font-weight: bold; -fx-text-fill: #ff6b9d; -fx-alignment: center; -fx-padding: 8; -fx-background-color: #ffeef8; -fx-background-radius: 10; -fx-border-radius: 10;");
        });
    }
    
    public void handleMatchEnd(String finalResult) {
        Platform.runLater(() -> {
            // Stop countdown
            if (countdownTimeline != null) {
                countdownTimeline.stop();
            }
            
            // Parse final result (e.g., "5-5" or "6-4")
            String[] parts = finalResult.split("-");
            if (parts.length == 2) {
                try {
                    int yourScore = Integer.parseInt(parts[0].trim());
                    int opponentScore = Integer.parseInt(parts[1].trim());
                    
                    // Update final score
                    scoreLabel.setText(yourScore + " - " + opponentScore);
                    
                    // Show result
                    String message;
                    if (yourScore > opponentScore) {
                        message = "🎉 CHIẾN THẮNG!\n\nTỷ số cuối: " + yourScore + " - " + opponentScore;
                        enableWinGroup(true);
                    } else if (yourScore < opponentScore) {
                        message = "😢 THUA CUỘC!\n\nTỷ số cuối: " + yourScore + " - " + opponentScore;
                        enableLoseGroup(true);
                    } else {
                        message = "⚖️ HÒA!\n\nTỷ số cuối: " + yourScore + " - " + opponentScore;
                    }
                    
                    // Show result alert - NO auto return to main screen
                    // Server will send play_again_request after this
                    Alert alert = new Alert(AlertType.INFORMATION);
                    alert.setTitle("Kết quả trận đấu");
                    alert.setHeaderText(null);
                    alert.setContentText(message);
                    alert.showAndWait();
                    
                    // Wait for server to send play_again_request
                    // Do NOT auto return to main screen
                    
                } catch (NumberFormatException e) {
                    showAlert("Kết quả trận đấu", finalResult, AlertType.INFORMATION);
                }
            } else {
                showAlert("Kết quả trận đấu", finalResult, AlertType.INFORMATION);
            }
            
            // Disable all game controls
            shootModeButton.setDisable(true);
            goalkeeperModeButton.setDisable(true);
            enableZones(false);
            currentMode = "";
            actionPerformed = true;
            
            instructionLabel.setText("⏳ Đang chờ quyết định chơi lại...");
            instructionLabel.setStyle("-fx-font-size: 13px; -fx-font-weight: bold; -fx-text-fill: #0ea5e9; -fx-alignment: center; -fx-padding: 8; -fx-background-color: #f0f9ff; -fx-background-radius: 10; -fx-border-radius: 10;");
        });
    }
    
    public void promptPlayAgain() {
        Platform.runLater(() -> {
            Alert alert = new Alert(AlertType.CONFIRMATION);
            alert.setTitle("Chơi lại");
            alert.setHeaderText("Trận đấu đã kết thúc!");
            alert.setContentText("Bạn có muốn chơi lại không?");
            
            ButtonType yesButton = new ButtonType("Có");
            ButtonType noButton = new ButtonType("Không");
            alert.getButtonTypes().setAll(yesButton, noButton);
            
            alert.showAndWait().ifPresent(response -> {
                try {
                    if (response == yesButton) {
                        System.out.println("✅ Người chơi chọn: CÓ chơi lại");
                        client.sendMessage(new Message("play_again_response", true));
                    } else {
                        System.out.println("✅ Người chơi chọn: KHÔNG chơi lại");
                        client.sendMessage(new Message("play_again_response", false));
                    }
                } catch (IOException e) {
                    showAlert("Lỗi", "Không thể gửi phản hồi", AlertType.ERROR);
                }
            });
        });
    }
    
    public void handleRematchDeclined(String message) {
        Platform.runLater(() -> {
            showAlert("Thông báo", message, AlertType.INFORMATION);
            // Return to main screen after rematch declined
            PauseTransition delay = new PauseTransition(Duration.millis(500));
            delay.setOnFinished(e -> {
                try {
                    client.showMainUI();
                } catch (Exception ex) {
                    ex.printStackTrace();
                }
            });
            delay.play();
        });
    }
    
    public void showMatchResult(String result) {
        Platform.runLater(() -> {
            showAlert("Kết quả trận đấu", result, AlertType.INFORMATION);
        });
    }
    
    public void promptYourTurn(int duration) {
        // Backwards-compatible: treat as shooter turn
        promptYourTurn(duration, "shooter", clientCurrentRound);
    }

    // New API: promptYourTurn with role and round so UI can show accurate role and validate round
    public void promptYourTurn(int duration, String role, int round) {
        Platform.runLater(() -> {
            System.out.println("📨 promptYourTurn received: role=" + role + ", round=" + round + ", clientCurrentRound(before)=" + clientCurrentRound);
            // Ignore out-of-order/old messages
            if (round < clientCurrentRound) {
                System.out.println("⚠️ Ignoring your_turn for old round=" + round + " currentClientRound=" + clientCurrentRound);
                return;
            }
            // Update client-side current round
            clientCurrentRound = round;
            this.yourRole = role;
            // Disable both buttons - auto-selected, no need to click
            shootModeButton.setDisable(true);
            goalkeeperModeButton.setDisable(true);
            actionPerformed = false;
            startCountdown(duration);
            // Set currentMode and select appropriate toggle based on role
            if ("shooter".equalsIgnoreCase(role)) {
                currentMode = "shoot";
                shootModeButton.setSelected(true);
                goalkeeperModeButton.setSelected(false);
                instructionLabel.setText("🎯 ĐẾN LƯỢT BẠN SÚT! Nhấp vào khung thành để chọn vị trí");
                instructionLabel.setStyle("-fx-font-size: 13px; -fx-font-weight: bold; -fx-text-fill: #4ecca3; -fx-alignment: center; -fx-padding: 8; -fx-background-color: #e8fff6; -fx-background-radius: 10; -fx-border-radius: 10;");
            } else {
                currentMode = "goalkeeper";
                goalkeeperModeButton.setSelected(true);
                shootModeButton.setSelected(false);
                instructionLabel.setText("🛡️ ĐẾN LƯỢT BẠN CHẶN! Nhấp vào khung thành để chọn vị trí");
                instructionLabel.setStyle("-fx-font-size: 13px; -fx-font-weight: bold; -fx-text-fill: #ffd93d; -fx-alignment: center; -fx-padding: 8; -fx-background-color: #fffbeb; -fx-background-radius: 10; -fx-border-radius: 10;");
            }
            enableZones(true);
            isMyTurn = true;
        });
    }
    
    public void promptGoalkeeperTurn(int duration) {
        Platform.runLater(() -> {
            // Disable both buttons - auto-selected, no need to click
            shootModeButton.setDisable(true);
            goalkeeperModeButton.setDisable(true);
            actionPerformed = false;
            startCountdown(duration);
            // Auto-select goalkeeper mode
            currentMode = "goalkeeper";
            goalkeeperModeButton.setSelected(true);
            shootModeButton.setSelected(false);
            enableZones(true);
            instructionLabel.setText("🛡️ ĐẾN LƯỢT BẠN CHẶN! Nhấp vào khung thành để chọn vị trí");
            instructionLabel.setStyle("-fx-font-size: 13px; -fx-font-weight: bold; -fx-text-fill: #ffd93d; -fx-alignment: center; -fx-padding: 8; -fx-background-color: #fffbeb; -fx-background-radius: 10; -fx-border-radius: 10;");
        });
    }
    
    public void handleOpponentTurn(int duration) {
        // Backwards-compatible: no role/round info
        handleOpponentTurn(duration, "opponent", clientCurrentRound);
    }

    // New API: handle opponent's turn with role and round info
    public void handleOpponentTurn(int duration, String role, int round) {
        Platform.runLater(() -> {
            System.out.println("📨 handleOpponentTurn received: role=" + role + ", round=" + round + ", clientCurrentRound(before)=" + clientCurrentRound);
            shootModeButton.setDisable(true);
            goalkeeperModeButton.setDisable(true);
            // Ignore out-of-order/old messages
            if (round < clientCurrentRound) {
                System.out.println("⚠️ Ignoring opponent_turn for old round=" + round + " currentClientRound=" + clientCurrentRound + " role=" + role);
                return;
            }
            // Update current round so client knows which round is active
            clientCurrentRound = round;
            // Clear currentMode so the client does NOT auto-send a timeout while observing opponent
            currentMode = "";
            startCountdown(duration);
            // Show who is performing the action for better UX
            if ("shooter".equalsIgnoreCase(role)) {
                instructionLabel.setText("⏳ Đối thủ đang sút...");
            } else if ("goalkeeper".equalsIgnoreCase(role)) {
                instructionLabel.setText("⏳ Đối thủ đang chặn...");
            } else {
                instructionLabel.setText("⏳ Đối thủ đang thực hiện...");
            }
            instructionLabel.setStyle("-fx-font-size: 13px; -fx-font-weight: bold; -fx-text-fill: #999; -fx-alignment: center; -fx-padding: 8; -fx-background-color: #f5f5f5; -fx-background-radius: 10; -fx-border-radius: 10;");
            isMyTurn = false;
        });
    }
    
    public void handleTimeout(String message) {
        Platform.runLater(() -> {
            // Show a transient alert but DO NOT overwrite the opponent/turn instruction label.
            // The server will send the next round's `your_turn`/`opponent_turn` messages
            // which will correctly update the UI. Here we only stop timers and mark
            // that the local player is no longer on-turn.
            if (countdownTimeline != null) {
                countdownTimeline.stop();
            }
            actionPerformed = true;
            isMyTurn = false;
            showAlert("Hết giờ", message, AlertType.WARNING);
        });
    }
    
    public void handleOpponentTimeout(String message) {
        Platform.runLater(() -> {
            // Informational only. Do not change the instruction label here.
            if (countdownTimeline != null) {
                countdownTimeline.stop();
            }
            isMyTurn = false;
            actionPerformed = true;
            showAlert("Thông báo", message, AlertType.INFORMATION);
        });
    }
    
    public void showStartMessage(String message) {
        Platform.runLater(() -> {
            showAlert("Bắt đầu trận đấu", message, AlertType.INFORMATION);
        });
    }
    
// client.GUI.GameRoomController.startCountdown()
    private void startCountdown(int seconds) {
        if (countdownTimeline != null) {
            countdownTimeline.stop();
        }

        timeRemaining = seconds;
        if (timerLabel != null) {
            timerLabel.setText(String.valueOf(timeRemaining));
        }

        countdownTimeline = new Timeline(
            new KeyFrame(Duration.seconds(1), e -> {
                timeRemaining--;
                if (timerLabel != null) {
                    timerLabel.setText(String.valueOf(timeRemaining));
                }
                if (timeRemaining <= 0) {
                    countdownTimeline.stop();

                    // === LOGIC ĐÃ ĐƯỢC BỎ: KHÔNG GỬI MESSAGE "timeout" LÊN SERVER ===
                    if (!actionPerformed) {
                        // Cập nhật UI cục bộ để hiển thị trạng thái chờ Server xử lý
                        disableModes();
                        actionPerformed = true;
                    }
                }
            })
        );
        countdownTimeline.setCycleCount(seconds);
        countdownTimeline.play();
    } 

    private void showAlert(String title, String content, AlertType type) {
        Alert alert = new Alert(type);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(content);
        alert.showAndWait();
    }

    @FXML
    private void handleSendChat() {
        try {
            String text = chatInput.getText();
            if (text == null || text.trim().isEmpty()) return;
            Message message = new Message("chat", text.trim());
            client.sendMessage(message);
            chatInput.clear();
        } catch (IOException e) {
            e.printStackTrace();
            showAlert("Lỗi", "Không thể gửi tin nhắn. Vui lòng kiểm tra kết nối.", AlertType.ERROR);
        }
    }

    @FXML
    private void handleQuitGame() {
        // Gửi yêu cầu thoát trận đấu tới server và về giao diện chính
        try {
            client.sendMessage(new Message("quit_game", null));
        } catch (IOException e) {
            System.err.println("Lỗi khi gửi quit_game: " + e.getMessage());
        }
        // // Đóng connection cục bộ cho an toàn và về main UI
        // try {
        //     client.closeConnection();
        // } catch (IOException e) {
        //     // ignore
        // }
        try {
            client.showMainUI();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}