package server;

import common.Message;
import java.io.IOException;
import java.sql.SQLException;
import java.util.Random;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

public class GameRoom {

    private ClientHandler shooterHandler;
    private ClientHandler goalkeeperHandler;
    private DatabaseManager dbManager;
    private int matchId;
    private int shooterScore;
    private int goalkeeperScore;
    private int currentRound;
    private final int MAX_ROUNDS = 6;
    private final int WIN_SCORE = 3;
    private String shooterDirection;
    private Boolean shooterWantsRematch = null;
    private Boolean goalkeeperWantsRematch = null;
    // Thời gian chờ cho mỗi lượt (ví dụ: 15 giây)
    private final int TURN_TIMEOUT = 15;

    private boolean isShooter = true;
    private boolean isKeeper = true;
    private ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(2);

    // Biến lưu trữ Future của nhiệm vụ chờ
    private ScheduledFuture<?> shooterTimeoutTask;
    private ScheduledFuture<?> goalkeeperTimeoutTask;

    // Biến để kiểm tra xem người chơi đã thực hiện hành động chưa
    private boolean shooterActionReceived = false;
    private boolean goalkeeperActionReceived = false;

    private String goalkeeperDirection;

    public GameRoom(ClientHandler player1, ClientHandler player2, DatabaseManager dbManager) throws SQLException {
        this.dbManager = dbManager;
        this.matchId = dbManager.saveMatch(player1.getUser().getId(), player2.getUser().getId(), 0);
        this.shooterScore = 0;
        this.goalkeeperScore = 0;
        this.currentRound = 1;

        // Random chọn người sút và người bắt
        if (new Random().nextBoolean()) {
            this.shooterHandler = player1;
            this.goalkeeperHandler = player2;
        } else {
            this.shooterHandler = player2;
            this.goalkeeperHandler = player1;
        }
    }

    public void startMatch() {
        try {
            // update ingame status for both player
            shooterHandler.getUser().setStatus("ingame");
            goalkeeperHandler.getUser().setStatus("ingame");

            // to do gui message neu can
            String shooterMessage = "Trận đấu bắt đầu! Bạn là người sút.";
            String goalkeeperMessage = "Trận đấu bắt đầu! Bạn là người bắt.";
            shooterHandler.sendMessage(new Message("match_start", shooterMessage));
            goalkeeperHandler.sendMessage(new Message("match_start", goalkeeperMessage));
            requestNextMove();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void requestNextMove() {
        try {
            if (checkEndGame()) {
                endMatch();
                return;
            }
            if (isShooter) {
                shooterHandler
                        .sendMessage(
                                new Message("your_turn", TURN_TIMEOUT));
                goalkeeperHandler
                        .sendMessage(
                                new Message("opponent_turn", TURN_TIMEOUT));
                isShooter = false;
            } else {
                goalkeeperHandler.sendMessage(
                        new Message("your_turn", TURN_TIMEOUT));
                shooterHandler.sendMessage(
                        new Message("opponent_turn", TURN_TIMEOUT));
                isShooter = true;
            }
            shooterActionReceived = false;
            shooterDirection = null;
            goalkeeperDirection = null; // Đặt lại biến này cho lượt mới

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    // Xử lý hướng sút từ người sút
    public synchronized void handleShot(String shooterDirection, ClientHandler shooter)
            throws SQLException, IOException {
        this.shooterDirection = shooterDirection;
        shooterActionReceived = true; // Đánh dấu đã nhận hành động từ người sút
        if (shooterTimeoutTask != null && !shooterTimeoutTask.isDone()) {
            shooterTimeoutTask.cancel(true);
        }
        // Yêu cầu người bắt chọn hướng chặn
        if (isKeeper) {
            goalkeeperHandler.sendMessage(
                    new Message("goalkeeper_turn", TURN_TIMEOUT));
            shooterHandler.sendMessage(
                    new Message("opponent_turn", TURN_TIMEOUT));
            isKeeper = false;
        } else {
            shooterHandler.sendMessage(
                    new Message("goalkeeper_turn", TURN_TIMEOUT));
            goalkeeperHandler.sendMessage(
                    new Message("opponent_turn", TURN_TIMEOUT));
            isKeeper = true;
        }

        // Bắt đầu đếm thời gian chờ cho người bắt
        goalkeeperActionReceived = false;
        // startGoalkeeperTimeout();
    }

    // Xử lý hướng chặn từ người bắt
    public synchronized void handleGoalkeeper(String goalkeeperDirection, ClientHandler goalkeeper)
            throws SQLException, IOException {
        if (this.shooterDirection == null) {
            // Nếu shooterDirection chưa được thiết lập, không thể xử lý
            shooterHandler.sendMessage(new Message("error", "Hướng sút chưa được thiết lập."));
            goalkeeperHandler.sendMessage(new Message("error", "Hướng sút chưa được thiết lập."));
            return;
        }
        this.goalkeeperDirection = goalkeeperDirection;
        goalkeeperActionReceived = true; // Đánh dấu đã nhận hành động từ người bắt

        // Hủy nhiệm vụ chờ của người bắt nếu còn tồn tại
        if (goalkeeperTimeoutTask != null && !goalkeeperTimeoutTask.isDone()) {
            goalkeeperTimeoutTask.cancel(true);
        }

        // Xử lý kết quả
        boolean goal = !shooterDirection.equalsIgnoreCase(goalkeeperDirection);
        if (goal) {
            if (!isShooter)
                shooterScore++;
            else
                goalkeeperScore++;
        }

        String kick_result = (goal ? "win" : "lose") + "-" + shooterDirection + "-" + goalkeeperDirection;
        shooterHandler.sendMessage(new Message("kick_result", kick_result));
        goalkeeperHandler.sendMessage(new Message("kick_result", kick_result));

        // Lưu chi tiết trận đấu vào database
        dbManager.saveMatchDetails(matchId, currentRound,
                shooterHandler.getUser().getId(),
                goalkeeperHandler.getUser().getId(),
                shooterDirection, goalkeeperDirection, goal ? "win" : "lose");

        // Gửi tỷ số cập nhật cho từng người chơi
        Message scoreMessageToShooter = new Message("update_score",
                new int[] { shooterScore, goalkeeperScore, currentRound });
        Message scoreMessageToGoalkeeper = new Message("update_score",
                new int[] { goalkeeperScore, shooterScore, currentRound });

        shooterHandler.sendMessage(scoreMessageToShooter);
        goalkeeperHandler.sendMessage(scoreMessageToGoalkeeper);

        // Kiểm tra điều kiện thắng
        currentRound++;
        if (checkEndGame()) {
            determineWinner();
        } else {
            // Thông báo lượt tiếp theo
            shooterDirection = null;
            goalkeeperDirection = null;
            shooterActionReceived = false;
            goalkeeperActionReceived = false;
            requestNextMove();
        }
    }

    private void determineWinner() throws SQLException, IOException {
        int winnerId = 0;
        String resultMessage = "";
        String endReason = "normal";

        if (shooterScore > goalkeeperScore) {
            winnerId = shooterHandler.getUser().getId();
            resultMessage = shooterHandler.getUser().getUsername() + " thắng trận đấu!";
        } else if (goalkeeperScore > shooterScore) {
            winnerId = goalkeeperHandler.getUser().getId();
            resultMessage = goalkeeperHandler.getUser().getUsername() + " thắng trận đấu!";
        } else {
            resultMessage = "Trận đấu hòa!";
        }

        if (winnerId != 0) {
            dbManager.updateUserPoints(winnerId, 3);
        }
        dbManager.updateMatchWinner(matchId, winnerId, endReason);

        // Thông báo kết quả trận đấu cho cả hai người chơi
        shooterHandler.sendMessage(new Message("match_result", (shooterScore > goalkeeperScore) ? "win" : "lose"));
        goalkeeperHandler.sendMessage(new Message("match_result", (goalkeeperScore > shooterScore) ? "win" : "lose"));

        // Tạo một ScheduledExecutorService để trì hoãn việc gửi tin nhắn
        ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);
        scheduler.schedule(() -> {
            // Gửi tin nhắn yêu cầu chơi lại sau 5 giây
            shooterHandler.sendMessage(new Message("play_again_request", "Bạn có muốn chơi lại không?"));
            goalkeeperHandler.sendMessage(new Message("play_again_request", "Bạn có muốn chơi lại không?"));
            // Đóng scheduler sau khi hoàn tất
            scheduler.shutdown();
        }, 3, TimeUnit.SECONDS);
    }

    // Xử lý yêu cầu chơi lại
    public synchronized void handlePlayAgainResponse(boolean playAgain, ClientHandler responder)
            throws SQLException, IOException {
        if (responder == shooterHandler) {
            shooterWantsRematch = playAgain;
        } else if (responder == goalkeeperHandler) {
            goalkeeperWantsRematch = playAgain;
        }

        // Kiểm tra nếu một trong hai người chơi đã thoát
        if (shooterHandler == null || goalkeeperHandler == null) {
            return;
        }

        // Kiểm tra nếu cả hai người chơi đã phản hồi
        if (shooterWantsRematch != null && goalkeeperWantsRematch != null) {
            if (shooterWantsRematch && goalkeeperWantsRematch) {
                // Cả hai người chơi đồng ý chơi lại
                resetGameState();
                startMatch();
            } else {
                // cap nhat status "ingame" -> "online"
                shooterHandler.getUser().setStatus("online");
                goalkeeperHandler.getUser().setStatus("online");

                dbManager.updateUserStatus(shooterHandler.getUser().getId(), "online");
                dbManager.updateUserStatus(goalkeeperHandler.getUser().getId(), "online");

                shooterHandler.getServer()
                        .broadcast(new Message("status_update", shooterHandler.getUser().getUsername() + " is online"));
                goalkeeperHandler.getServer().broadcast(
                        new Message("status_update", goalkeeperHandler.getUser().getUsername() + " is online"));
                // ------------------------------------------------------------//

                // Gửi thông báo kết thúc trận đấu
                shooterHandler.sendMessage(new Message("match_end", "Trận đấu kết thúc."));
                goalkeeperHandler.sendMessage(new Message("match_end", "Trận đấu kết thúc."));

                // Đặt lại biến
                shooterWantsRematch = null;
                goalkeeperWantsRematch = null;

                // Đưa cả hai người chơi về màn hình chính
                shooterHandler.clearGameRoom();
                goalkeeperHandler.clearGameRoom();
            }
        }
    }

    private void resetGameState() throws SQLException {
        // Reset game variables
        shooterScore = 0;
        goalkeeperScore = 0;
        currentRound = 1;
        shooterDirection = null;
        shooterWantsRematch = null;
        goalkeeperWantsRematch = null;

        // Swap shooter and goalkeeper roles for fairness
        ClientHandler temp = shooterHandler;
        shooterHandler = goalkeeperHandler;
        goalkeeperHandler = temp;

        // Create a new match in the database
        matchId = dbManager.saveMatch(shooterHandler.getUser().getId(), goalkeeperHandler.getUser().getId(), 0);
    }

    // Đảm bảo rằng phương thức endMatch() tồn tại và được định nghĩa chính xác
    private void endMatch() throws SQLException, IOException {
        determineWinner();

        // Reset in-game status for both players after match
        if (shooterHandler != null) {
            shooterHandler.getUser().setStatus("online");
            // todo gui message neu can
        }
        if (goalkeeperHandler != null) {
            goalkeeperHandler.getUser().setStatus("online");
            // todo gui message neu can
        }
    }

    public void handlePlayerDisconnect(ClientHandler disconnectedPlayer) throws SQLException, IOException {
        String resultMessageToWinner = "Đối thủ đã thoát. Bạn thắng trận đấu!";
        String resultMessageToLoser = "Bạn đã thoát. Bạn thua trận đấu!";
        int winnerId = 0;
        String endReason = "player_quit";
        ClientHandler otherPlayer = null;

        if (disconnectedPlayer == shooterHandler) {
            otherPlayer = goalkeeperHandler;
        } else if (disconnectedPlayer == goalkeeperHandler) {
            otherPlayer = shooterHandler;
        }

        shooterWantsRematch = false;
        goalkeeperWantsRematch = false;
        winnerId = otherPlayer.getUser().getId();

        if (winnerId != 0) {
            dbManager.updateUserPoints(winnerId, 3);
            dbManager.updateMatchWinner(matchId, winnerId, endReason);
        }

        // cap nhat status "ingame" -> "online"
        otherPlayer.getUser().setStatus("online");
        dbManager.updateUserStatus(otherPlayer.getUser().getId(), "online");
        otherPlayer.getServer()
                .broadcast(new Message("status_update", otherPlayer.getUser().getUsername() + " is online"));

        // cap nhat status "ingame" -> "offline"
        disconnectedPlayer.getUser().setStatus("offline");
        dbManager.updateUserStatus(disconnectedPlayer.getUser().getId(), "offline");
        disconnectedPlayer.getServer()
                .broadcast(new Message("status_update", disconnectedPlayer.getUser().getUsername() + " is offline"));
        // -------------------------------------------------------

        // Gửi thông báo kết thúc trận đấu cho cả hai người chơi
        otherPlayer.sendMessage(new Message("match_end", resultMessageToWinner));
        disconnectedPlayer.sendMessage(new Message("match_end", resultMessageToLoser));

        // Đặt lại trạng thái game room
        shooterWantsRematch = null;
        goalkeeperWantsRematch = null;
        shooterDirection = null;

        // Sử dụng phương thức clearGameRoom() để đặt gameRoom thành null
        if (shooterHandler != null) {
            shooterHandler.clearGameRoom();
        }
        if (goalkeeperHandler != null) {
            goalkeeperHandler.clearGameRoom();
        }

    }

    public void handlePlayerQuit(ClientHandler quittingPlayer) throws SQLException, IOException {
        String resultMessageToLoser = "Bạn đã thoát. Bạn thua trận đấu!";
        String resultMessageToWinner = "Đối thủ đã thoát. Bạn thắng trận đấu!";

        int winnerId = 0;
        String endReason = "player_quit";
        ClientHandler otherPlayer = null;

        if (quittingPlayer == shooterHandler) {
            winnerId = goalkeeperHandler.getUser().getId();
            otherPlayer = goalkeeperHandler;
            // Cập nhật trạng thái chơi lại
            shooterWantsRematch = false;
        } else if (quittingPlayer == goalkeeperHandler) {
            winnerId = shooterHandler.getUser().getId();
            otherPlayer = shooterHandler;
            // Cập nhật trạng thái chơi lại
            goalkeeperWantsRematch = false;
        }

        winnerId = otherPlayer.getUser().getId();

        if (winnerId != 0) {
            dbManager.updateUserPoints(winnerId, 3);
            dbManager.updateMatchWinner(matchId, winnerId, endReason);
        }

        // cap nhat status "ingame" -> "online"
        shooterHandler.getUser().setStatus("online");
        goalkeeperHandler.getUser().setStatus("online");

        dbManager.updateUserStatus(shooterHandler.getUser().getId(), "online");
        dbManager.updateUserStatus(goalkeeperHandler.getUser().getId(), "online");

        shooterHandler.getServer()
                .broadcast(new Message("status_update", shooterHandler.getUser().getUsername() + " is online"));
        goalkeeperHandler.getServer()
                .broadcast(new Message("status_update", goalkeeperHandler.getUser().getUsername() + " is online"));
        // ------------------------------------------------------------

        // Gửi thông báo kết thúc trận đấu cho cả hai người chơi
        quittingPlayer.sendMessage(new Message("match_end", resultMessageToLoser));
        if (otherPlayer != null) {
            otherPlayer.sendMessage(new Message("match_end", resultMessageToWinner));
        }

        // Đặt lại trạng thái game room
        shooterWantsRematch = null;
        goalkeeperWantsRematch = null;
        shooterDirection = null;

        // Sử dụng phương thức clearGameRoom() để đặt gameRoom thành null
        if (shooterHandler != null) {
            shooterHandler.clearGameRoom();
        }
        if (goalkeeperHandler != null) {
            goalkeeperHandler.clearGameRoom();
        }

        // Không cần gửi thông báo "return_to_main"
    }

    public void startShooterTimeout() {
        try {
            if (checkEndGame()) {
                endMatch();
                return;
            }
            if (!shooterActionReceived) {
                // Người sút không thực hiện hành động trong thời gian quy định
                shooterDirection = "Middle";
                shooterActionReceived = true;
                shooterHandler.sendMessage(
                        new Message("timeout", "Hết giờ! \nHệ thống tự chọn 'Middle' cho bạn."));
                goalkeeperHandler.sendMessage(new Message("opponent_timeout",
                        "Hết giờ! \nHệ thống tự chọn 'Middle' cho đối thủ."));
                // Yêu cầu người bắt chọn hướng chặn
                handleShot(shooterDirection, shooterHandler);

                // Bắt đầu đếm thời gian chờ cho người bắt
                goalkeeperActionReceived = false;
                // startGoalkeeperTimeout();
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private boolean checkEndGame() {
        int scoreDifference = Math.abs(shooterScore - goalkeeperScore);
        int turnLeftshooter = (shooterScore >= WIN_SCORE) ? 0 : WIN_SCORE - ((currentRound / 2));
        int turnLeftgoalkeeper = (goalkeeperScore >= WIN_SCORE) ? 0 : WIN_SCORE - (((currentRound - 1) / 2));
        System.out.println(turnLeftshooter + " " + turnLeftgoalkeeper + " " + scoreDifference + " " + currentRound);
        return ((turnLeftshooter < scoreDifference) && shooterScore < goalkeeperScore) ||
                ((turnLeftgoalkeeper < scoreDifference) && goalkeeperScore < shooterScore)
                || ((currentRound > MAX_ROUNDS || shooterScore >= WIN_SCORE || goalkeeperScore >= WIN_SCORE)
                        && currentRound % 2 == 0 && shooterScore != goalkeeperScore);
    }

    public void startGoalkeeperTimeout() {
        try {
            if (!goalkeeperActionReceived) {
                // Người bắt không thực hiện hành động trong thời gian quy định
                goalkeeperDirection = "Middle";
                goalkeeperActionReceived = true;

                goalkeeperHandler.sendMessage(
                        new Message("timeout", "Hết giờ! \nHệ thống tự chọn 'Middle' cho bạn."));
                shooterHandler.sendMessage(new Message("opponent_timeout",
                        "Hết giờ! \nHệ thống tự chọn 'Middle' cho đối thủ."));

                // Tiến hành xử lý kết quả
                handleGoalkeeper(goalkeeperDirection, goalkeeperHandler);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
