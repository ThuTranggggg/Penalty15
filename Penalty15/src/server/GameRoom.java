package server;

import common.Message;
import java.io.IOException;
import java.sql.SQLException;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

public class GameRoom {

    private ClientHandler player1Handler;  // Người chơi 1 (cố định)
    private ClientHandler player2Handler;  // Người chơi 2 (cố định)
    private DatabaseManager dbManager;
    private int matchId;
    private int player1Score;  // Điểm của player1
    private int player2Score;  // Điểm của player2
    private int currentRound;
    private final int MAX_ROUNDS = 10; // 10 vòng đấu (mỗi người 5 lượt)
    private String shooterDirection;
    private Boolean player1WantsRematch = null;
    private Boolean player2WantsRematch = null;
    // Thời gian chờ cho mỗi lượt (ví dụ: 15 giây)
    private final int TURN_TIMEOUT = 15;

    // Biến lưu trữ Future của nhiệm vụ chờ
    private ScheduledFuture<?> shooterTimeoutTask;
    private ScheduledFuture<?> goalkeeperTimeoutTask;
    // Scheduler cho timeout server-side
    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();

    // Biến để kiểm tra xem người chơi đã thực hiện hành động chưa
    private boolean shooterActionReceived = false;
    private boolean goalkeeperActionReceived = false;

    private String goalkeeperDirection;
    // Track which round has already been processed (to avoid double-processing timeouts)
    private int lastProcessedRound = 0;
    // Monotonic sequence id for turn messages to help clients detect stale messages
    private int turnSeq = 0;

    public GameRoom(ClientHandler player1, ClientHandler player2, DatabaseManager dbManager) throws SQLException {
        this.dbManager = dbManager;
        this.matchId = dbManager.saveMatch(player1.getUser().getId(), player2.getUser().getId(), 0);
        this.player1Score = 0;
        this.player2Score = 0;
        this.currentRound = 1;

        // Gán cố định player1 và player2 (không random)
        this.player1Handler = player1;
        this.player2Handler = player2;
    }

    public void startMatch() {
        try {
            // update ingame status for both player
            player1Handler.getUser().setStatus("ingame");
            player2Handler.getUser().setStatus("ingame");

            // Xác định vai trò cho vòng đầu tiên
            // Vòng lẻ (1,3,5,7,9): Player1 sút, Player2 bắt
            String player1Message = "Trận đấu bắt đầu! Bạn là người sút vòng đầu.|" + player2Handler.getUser().getUsername();
            String player2Message = "Trận đấu bắt đầu! Bạn là người bắt vòng đầu.|" + player1Handler.getUser().getUsername();
            
            player1Handler.sendMessage(new Message("match_start", player1Message));
            player2Handler.sendMessage(new Message("match_start", player2Message));
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

            // Xác định ai sút, ai bắt dựa trên vòng hiện tại
            boolean isPlayer1Shooter = (currentRound % 2 == 1);
            
            // Advance turn sequence for the new turn and send notifications
            turnSeq++;

            // Gửi thông báo cho client (bao gồm role, timeout và round hiện tại, turnSeq)
            if (isPlayer1Shooter) {
                // Player1 sút, Player2 bắt
                player1Handler.sendMessage(new Message("your_turn", new Object[]{"shooter", TURN_TIMEOUT, currentRound, turnSeq}));
                // Inform observer that opponent is the shooter
                player2Handler.sendMessage(new Message("opponent_turn", new Object[]{"shooter", TURN_TIMEOUT, currentRound, turnSeq}));
            } else {
                // Player2 sút, Player1 bắt
                player2Handler.sendMessage(new Message("your_turn", new Object[]{"shooter", TURN_TIMEOUT, currentRound, turnSeq}));
                // Inform observer that opponent is the shooter
                player1Handler.sendMessage(new Message("opponent_turn", new Object[]{"shooter", TURN_TIMEOUT, currentRound, turnSeq}));
            }

            // Debug log: announce which player is shooter/goalkeeper for this round
            System.out.println("📨 requestNextMove: round=" + currentRound + ", shooter=" +
                    (isPlayer1Shooter ? (player1Handler.getUser()!=null?player1Handler.getUser().getUsername():"p1") : (player2Handler.getUser()!=null?player2Handler.getUser().getUsername():"p2")) +
                    ", goalkeeper=" +
                    (isPlayer1Shooter ? (player2Handler.getUser()!=null?player2Handler.getUser().getUsername():"p2") : (player1Handler.getUser()!=null?player1Handler.getUser().getUsername():"p1")));

            // Cancel any previous scheduled tasks (Đảm bảo dọn dẹp triệt để)
            if (shooterTimeoutTask != null && !shooterTimeoutTask.isDone()) shooterTimeoutTask.cancel(true);
            if (goalkeeperTimeoutTask != null && !goalkeeperTimeoutTask.isDone()) goalkeeperTimeoutTask.cancel(true);

            // Schedule server-side shooter timeout enforcement
            shooterTimeoutTask = scheduler.schedule(() -> {
                try {
                    startShooterTimeout();
                } catch (Exception e) {
                    System.err.println("❌ Lỗi khi thực thi shooter timeout scheduler: " + e.getMessage());
                }
            }, TURN_TIMEOUT, TimeUnit.SECONDS);

            shooterActionReceived = false;
            shooterDirection = null;
            goalkeeperDirection = null;

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    // Xử lý hướng sút từ người sút
    public synchronized void handleShot(String shooterDirection, ClientHandler shooter)
            throws SQLException, IOException {
        // Guard: ignore late actions for processed rounds
        if (lastProcessedRound >= currentRound) {
            System.out.println("⚠️ handleShot: round " + currentRound + " already processed, ignoring late shot.");
            return;
        }
        
        System.out.println("📨 handleShot: received shot from " + (shooter!=null && shooter.getUser()!=null?shooter.getUser().getUsername():"unknown") +
            ", direction=" + shooterDirection + ", serverRound=" + currentRound);
        this.shooterDirection = shooterDirection;
        shooterActionReceived = true;

        // Hủy Timer của Shooter (Đã nhận action)
        if (shooterTimeoutTask != null && !shooterTimeoutTask.isDone()) {
            shooterTimeoutTask.cancel(true);
        }
        // Hủy Goalkeeper Timer (cho an toàn)
        if (goalkeeperTimeoutTask != null && !goalkeeperTimeoutTask.isDone()) {
            goalkeeperTimeoutTask.cancel(true);
        }

        // Xác định ai là người bắt trong vòng này
        boolean isPlayer1Shooter = (currentRound % 2 == 1);
        ClientHandler goalkeeperHandler = isPlayer1Shooter ? player2Handler : player1Handler;
        ClientHandler shooterHandler = isPlayer1Shooter ? player1Handler : player2Handler;

        // Yêu cầu người bắt chọn hướng chặn
        // include the turnSeq for this round so clients can ignore stale messages
        goalkeeperHandler.sendMessage(new Message("goalkeeper_turn", new Object[]{"goalkeeper", TURN_TIMEOUT, currentRound, turnSeq}));
            shooterHandler.sendMessage(new Message("opponent_turn", new Object[]{"goalkeeper", TURN_TIMEOUT, currentRound, turnSeq}));

                System.out.println("📨 Sent goalkeeper_turn to " + (goalkeeperHandler.getUser() != null ? goalkeeperHandler.getUser().getUsername() : "unknown") +
                        " and opponent_turn(goalkeeper) to " + (shooterHandler.getUser() != null ? shooterHandler.getUser().getUsername() : "unknown") +
                        " for round " + currentRound);
        goalkeeperActionReceived = false;
        // Schedule goalkeeper timeout enforcement on server side
        if (goalkeeperTimeoutTask != null && !goalkeeperTimeoutTask.isDone()) goalkeeperTimeoutTask.cancel(true);
        goalkeeperTimeoutTask = scheduler.schedule(() -> {
            try {
                startGoalkeeperTimeout();
            } catch (Exception e) {
                System.err.println("❌ Lỗi khi thực thi goalkeeper timeout scheduler: " + e.getMessage());
            }
        }, TURN_TIMEOUT, TimeUnit.SECONDS);
    }

    // Xử lý hướng chặn từ người bắt
    public synchronized void handleGoalkeeper(String goalkeeperDirection, ClientHandler goalkeeper)
            throws SQLException, IOException {
        // Guard: ignore late actions for processed rounds
        if (lastProcessedRound >= currentRound) {
            System.out.println("⚠️ handleGoalkeeper: round " + currentRound + " already processed, ignoring late goalkeeper action.");
            return;
        }
        
        // Kiểm tra shooterDirection đã được đặt chưa
        if (this.shooterDirection == null) {
            if (player1Handler != null) player1Handler.sendMessage(new Message("error", "Hướng sút chưa được thiết lập."));
            if (player2Handler != null) player2Handler.sendMessage(new Message("error", "Hướng sút chưa được thiết lập."));
            return;
        }

        this.goalkeeperDirection = goalkeeperDirection;
        goalkeeperActionReceived = true;

        // Hủy các timeout còn tồn tại
        if (goalkeeperTimeoutTask != null && !goalkeeperTimeoutTask.isDone()) {
            goalkeeperTimeoutTask.cancel(true);
        }
        if (shooterTimeoutTask != null && !shooterTimeoutTask.isDone()) shooterTimeoutTask.cancel(true);

        // Xác định vai trò trong vòng này
        boolean isPlayer1Shooter = (currentRound % 2 == 1);
        ClientHandler shooterHandler = isPlayer1Shooter ? player1Handler : player2Handler;
        ClientHandler goalkeeperHandler = isPlayer1Shooter ? player2Handler : player1Handler;

        // Xử lý kết quả
        boolean goal = !shooterDirection.equalsIgnoreCase(goalkeeperDirection);
        if (goal) {
            if (isPlayer1Shooter) {
                player1Score++;
            } else {
                player2Score++;
            }
        }

        // Gửi kết quả vòng cho cả hai
        String kick_result = (goal ? "win" : "lose") + "-" + shooterDirection + "-" + goalkeeperDirection;
        try {
            shooterHandler.sendMessage(new Message("kick_result", kick_result));
            goalkeeperHandler.sendMessage(new Message("kick_result", kick_result));
        } catch (Exception e) {
            System.err.println("❌ Lỗi gửi kick_result: " + e.getMessage());
        }

        // Lưu chi tiết trận đấu vào database
        try {
            dbManager.saveMatchDetails(matchId, currentRound,
                    shooterHandler.getUser().getId(),
                    goalkeeperHandler.getUser().getId(),
                    shooterDirection, goalkeeperDirection, goal ? "win" : "lose");
        } catch (SQLException se) {
            System.err.println("❌ Lỗi lưu chi tiết trận đấu: " + se.getMessage());
        }

        // Mark this round processed to avoid races with timeouts
        lastProcessedRound = currentRound;

        // Tăng vòng ngay lập tức
        currentRound++;

        // Gửi cập nhật điểm (và vòng mới)
        Message scoreMessageToPlayer1 = new Message("update_score",
                new int[] { player1Score, player2Score, currentRound });
        Message scoreMessageToPlayer2 = new Message("update_score",
                new int[] { player2Score, player1Score, currentRound });

        try {
            System.out.println("📨 Sending update_score for round=" + currentRound + " scores=" + player1Score + "-" + player2Score);
            player1Handler.sendMessage(scoreMessageToPlayer1);
            player2Handler.sendMessage(scoreMessageToPlayer2);
        } catch (Exception e) {
            System.err.println("❌ Lỗi gửi update_score sau goalkeeper action: " + e.getMessage());
        }

        // Reset state cho vòng tiếp theo
        shooterDirection = null;
        goalkeeperDirection = null;
        shooterActionReceived = false;
        goalkeeperActionReceived = false;

        if (checkEndGame()) {
            determineWinner();
        } else {
            requestNextMove();
        }
    }

    private void determineWinner() throws SQLException, IOException {
        System.out.println("🏆 determineWinner được gọi - Round: " + currentRound + ", Score: " + player1Score + "-" + player2Score);
        
        int winnerId = 0;
        String endReason = "normal";

        if (player1Score > player2Score) {
            winnerId = player1Handler.getUser().getId();
            dbManager.updateUserPoints(winnerId, 3); // +3 điểm
            dbManager.updateUserWins(winnerId); // +1 trận thắng
            System.out.println("✅ Player1 thắng: " + player1Handler.getUser().getUsername());
        } else if (player2Score > player1Score) {
            winnerId = player2Handler.getUser().getId();
            dbManager.updateUserPoints(winnerId, 3); // +3 điểm
            dbManager.updateUserWins(winnerId); // +1 trận thắng
            System.out.println("✅ Player2 thắng: " + player2Handler.getUser().getUsername());
        } else {
            // Hòa: cả hai +1 điểm, không tăng wins
            dbManager.updateUserPoints(player1Handler.getUser().getId(), 1);
            dbManager.updateUserPoints(player2Handler.getUser().getId(), 1);
            System.out.println("✅ Hòa - cả hai được 1 điểm");
        }

        dbManager.updateMatchWinner(matchId, winnerId, endReason);
        System.out.println("✅ Đã cập nhật database");

        // Thông báo kết quả cho cả hai người chơi
        try {
            player1Handler.sendMessage(new Message("match_result", (player1Score > player2Score) ? "win" : (player1Score < player2Score ? "lose" : "draw")));
            System.out.println("✅ Đã gửi match_result cho Player1");
        } catch (Exception e) {
            System.err.println("❌ Lỗi gửi match_result cho Player1: " + e.getMessage());
            e.printStackTrace();
        }
        
        try {
            player2Handler.sendMessage(new Message("match_result", (player2Score > player1Score) ? "win" : (player2Score < player1Score ? "lose" : "draw")));
            System.out.println("✅ Đã gửi match_result cho Player2");
        } catch (Exception e) {
            System.err.println("❌ Lỗi gửi match_result cho Player2: " + e.getMessage());
            e.printStackTrace();
        }

        // Gửi message match_end với kết quả chi tiết
        try {
            String player1EndMessage = (player1Score > player2Score) ? 
                "Chúc mừng! Bạn thắng với tỷ số " + player1Score + "-" + player2Score :
                (player1Score < player2Score ? 
                    "Bạn thua với tỷ số " + player1Score + "-" + player2Score :
                    "Hòa với tỷ số " + player1Score + "-" + player2Score);
            player1Handler.sendMessage(new Message("match_end", player1EndMessage));
            System.out.println("✅ Đã gửi match_end cho Player1");
        } catch (Exception e) {
            System.err.println("❌ Lỗi gửi match_end cho Player1: " + e.getMessage());
            e.printStackTrace();
        }
        
        try {
            String player2EndMessage = (player2Score > player1Score) ? 
                "Chúc mừng! Bạn thắng với tỷ số " + player2Score + "-" + player1Score :
                (player2Score < player1Score ? 
                    "Bạn thua với tỷ số " + player2Score + "-" + player1Score :
                    "Hòa với tỷ số " + player2Score + "-" + player1Score);
            player2Handler.sendMessage(new Message("match_end", player2EndMessage));
            System.out.println("✅ Đã gửi match_end cho Player2");
        } catch (Exception e) {
            System.err.println("❌ Lỗi gửi match_end cho Player2: " + e.getMessage());
            e.printStackTrace();
        }

        // Tạo một ScheduledExecutorService để trì hoãn việc gửi tin nhắn play again
        ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);
        scheduler.schedule(() -> {
            try {
                System.out.println("⏰ Gửi play_again_request sau 3 giây...");
                player1Handler.sendMessage(new Message("play_again_request", "Bạn có muốn chơi lại không?"));
                System.out.println("✅ Đã gửi play_again_request cho Player1");
            } catch (Exception e) {
                System.err.println("❌ Lỗi gửi play_again_request cho Player1: " + e.getMessage());
                e.printStackTrace();
            }
            
            try {
                player2Handler.sendMessage(new Message("play_again_request", "Bạn có muốn chơi lại không?"));
                System.out.println("✅ Đã gửi play_again_request cho Player2");
            } catch (Exception e) {
                System.err.println("❌ Lỗi gửi play_again_request cho Player2: " + e.getMessage());
                e.printStackTrace();
            }
            
            scheduler.shutdown();
        }, 3, TimeUnit.SECONDS);
    }

    // Xử lý yêu cầu chơi lại
    public synchronized void handlePlayAgainResponse(boolean playAgain, ClientHandler responder)
            throws SQLException, IOException {
        if (responder == player1Handler) {
            player1WantsRematch = playAgain;
        } else if (responder == player2Handler) {
            player2WantsRematch = playAgain;
        }

        // Kiểm tra nếu một trong hai người chơi đã thoát
        if (player1Handler == null || player2Handler == null) {
            return;
        }

        // Kiểm tra nếu cả hai người chơi đã phản hồi
        if (player1WantsRematch != null && player2WantsRematch != null) {
            if (player1WantsRematch && player2WantsRematch) {
                // Cả hai người chơi đồng ý chơi lại
                resetGameState();
                startMatch();
            } else {
                // Cập nhật status "ingame" -> "online"
                player1Handler.getUser().setStatus("online");
                player2Handler.getUser().setStatus("online");

                dbManager.updateUserStatus(player1Handler.getUser().getId(), "online");
                dbManager.updateUserStatus(player2Handler.getUser().getId(), "online");

                player1Handler.getServer()
                        .broadcast(new Message("status_update", player1Handler.getUser().getUsername() + " is online"));
                player2Handler.getServer().broadcast(
                        new Message("status_update", player2Handler.getUser().getUsername() + " is online"));

                // Gửi thông báo kết thúc trận đấu
                player1Handler.sendMessage(new Message("match_end", "Trận đấu kết thúc."));
                player2Handler.sendMessage(new Message("match_end", "Trận đấu kết thúc."));

                // Đặt lại biến
                player1WantsRematch = null;
                player2WantsRematch = null;

                // Đưa cả hai người chơi về màn hình chính
                player1Handler.clearGameRoom();
                player2Handler.clearGameRoom();
            }
        }
    }

    private void resetGameState() throws SQLException {
        // Reset game variables
        player1Score = 0;
        player2Score = 0;
        currentRound = 1;
        shooterDirection = null;
        player1WantsRematch = null;
        player2WantsRematch = null;

        // Create a new match in the database
        matchId = dbManager.saveMatch(player1Handler.getUser().getId(), player2Handler.getUser().getId(), 0);
    }

    // Đảm bảo rằng phương thức endMatch() tồn tại và được định nghĩa chính xác
    private void endMatch() throws SQLException, IOException {
        // Dọn dẹp timer trước khi kết thúc
        try {
            if (shooterTimeoutTask != null && !shooterTimeoutTask.isDone()) shooterTimeoutTask.cancel(true);
        } catch (Exception e) {
            System.err.println("❌ Lỗi khi hủy shooterTimeoutTask trong endMatch: " + e.getMessage());
        }
        try {
            if (goalkeeperTimeoutTask != null && !goalkeeperTimeoutTask.isDone()) goalkeeperTimeoutTask.cancel(true);
        } catch (Exception e) {
            System.err.println("❌ Lỗi khi hủy goalkeeperTimeoutTask trong endMatch: " + e.getMessage());
        }
        
        determineWinner();

        // Reset in-game status for both players after match
        if (player1Handler != null) {
            player1Handler.getUser().setStatus("online");
        }
        if (player2Handler != null) {
            player2Handler.getUser().setStatus("online");
        }
    }

    public void handlePlayerDisconnect(ClientHandler disconnectedPlayer) throws SQLException, IOException {
        System.out.println("🔌 handlePlayerDisconnect được gọi cho: " + 
            (disconnectedPlayer != null && disconnectedPlayer.getUser() != null ? 
                disconnectedPlayer.getUser().getUsername() : "Unknown"));
        // Cancel any pending scheduled timeout tasks to avoid orphaned timeouts
        try {
            if (shooterTimeoutTask != null && !shooterTimeoutTask.isDone()) shooterTimeoutTask.cancel(true);
        } catch (Exception e) {
            System.err.println("❌ Lỗi khi hủy shooterTimeoutTask: " + e.getMessage());
        }
        try {
            if (goalkeeperTimeoutTask != null && !goalkeeperTimeoutTask.isDone()) goalkeeperTimeoutTask.cancel(true);
        } catch (Exception e) {
            System.err.println("❌ Lỗi khi hủy goalkeeperTimeoutTask: " + e.getMessage());
        }
        
        String resultMessageToWinner = "Đối thủ đã ngắt kết nối. Bạn thắng trận đấu!";
        String endReason = "player_disconnect";
        ClientHandler otherPlayer = null;

        // Xác định người chơi còn lại
        if (disconnectedPlayer == player1Handler) {
            otherPlayer = player2Handler;
        } else if (disconnectedPlayer == player2Handler) {
            otherPlayer = player1Handler;
        }

        // Nếu không tìm thấy người chơi còn lại, thoát
        if (otherPlayer == null) {
            System.out.println("⚠️ Không tìm thấy người chơi còn lại trong GameRoom");
            return;
        }

        System.out.println("✅ Người chơi còn lại: " + otherPlayer.getUser().getUsername());

        int winnerId = otherPlayer.getUser().getId();
        
        // Cập nhật điểm và kết quả trận đấu
        try {
            dbManager.updateUserPoints(winnerId, 3); // Người thắng +3 điểm
            dbManager.updateUserWins(winnerId); // Người thắng +1 trận thắng
            dbManager.updateMatchWinner(matchId, winnerId, endReason);
            System.out.println("✅ Đã cập nhật database - Người thắng: " + otherPlayer.getUser().getUsername());
        } catch (SQLException e) {
            System.err.println("❌ Lỗi cập nhật database: " + e.getMessage());
            e.printStackTrace();
        }

        // Cập nhật status người chơi còn lại: "ingame" -> "online"
        try {
            otherPlayer.getUser().setStatus("online");
            dbManager.updateUserStatus(otherPlayer.getUser().getId(), "online");
            otherPlayer.getServer()
                    .broadcast(new Message("status_update", otherPlayer.getUser().getUsername() + " is online"));
            System.out.println("✅ Đã cập nhật status người thắng -> online");
        } catch (Exception e) {
            System.err.println("❌ Lỗi cập nhật status người thắng: " + e.getMessage());
        }

        // Cập nhật status người bị disconnect: "ingame" -> "offline"
        if (disconnectedPlayer.getUser() != null) {
            try {
                disconnectedPlayer.getUser().setStatus("offline");
                dbManager.updateUserStatus(disconnectedPlayer.getUser().getId(), "offline");
                disconnectedPlayer.getServer()
                        .broadcast(new Message("status_update", disconnectedPlayer.getUser().getUsername() + " is offline"));
                System.out.println("✅ Đã cập nhật status người disconnect -> offline");
            } catch (Exception e) {
                System.err.println("❌ Lỗi cập nhật status người disconnect: " + e.getMessage());
            }
        }

        // Gửi thông báo cho người chơi còn lại
        try {
            otherPlayer.sendMessage(new Message("match_result", "win"));
            System.out.println("✅ Đã gửi match_result=win");
            
            otherPlayer.sendMessage(new Message("match_end", resultMessageToWinner));
            System.out.println("✅ Đã gửi match_end");
        } catch (Exception e) {
            System.err.println("❌ Lỗi gửi message cho người thắng: " + e.getMessage());
            e.printStackTrace();
        }

        // Đặt lại trạng thái game room
        player1WantsRematch = null;
        player2WantsRematch = null;
        shooterDirection = null;

        // Clear game room cho cả hai người chơi
        if (player1Handler != null) {
            player1Handler.clearGameRoom();
        }
        if (player2Handler != null) {
            player2Handler.clearGameRoom();
        }
        
        System.out.println("🏁 handlePlayerDisconnect hoàn tất");
    }

    public void handlePlayerQuit(ClientHandler quittingPlayer) throws SQLException, IOException {
        System.out.println("🚪 handlePlayerQuit được gọi cho: " + 
            (quittingPlayer != null && quittingPlayer.getUser() != null ? 
                quittingPlayer.getUser().getUsername() : "Unknown"));
        // Cancel any pending scheduled timeout tasks to avoid orphaned timeouts
        try {
            if (shooterTimeoutTask != null && !shooterTimeoutTask.isDone()) shooterTimeoutTask.cancel(true);
        } catch (Exception e) {
            System.err.println("❌ Lỗi khi hủy shooterTimeoutTask: " + e.getMessage());
        }
        try {
            if (goalkeeperTimeoutTask != null && !goalkeeperTimeoutTask.isDone()) goalkeeperTimeoutTask.cancel(true);
        } catch (Exception e) {
            System.err.println("❌ Lỗi khi hủy goalkeeperTimeoutTask: " + e.getMessage());
        }
        
        String resultMessageToLoser = "Bạn đã thoát. Bạn thua trận đấu!";
        String resultMessageToWinner = "Đối thủ đã thoát. Bạn thắng trận đấu!";

        String endReason = "player_quit";
        ClientHandler otherPlayer = null;

        // Xác định người chơi còn lại
        if (quittingPlayer == player1Handler) {
            otherPlayer = player2Handler;
        } else if (quittingPlayer == player2Handler) {
            otherPlayer = player1Handler;
        }

        // Nếu không tìm thấy người chơi còn lại, thoát
        if (otherPlayer == null) {
            System.out.println("⚠️ Không tìm thấy người chơi còn lại");
            return;
        }

        System.out.println("✅ Người chơi còn lại: " + otherPlayer.getUser().getUsername());

        int winnerId = otherPlayer.getUser().getId();

        // Cập nhật điểm và kết quả trận đấu
        try {
            dbManager.updateUserPoints(winnerId, 3); // Người thắng +3 điểm
            dbManager.updateUserWins(winnerId); // Người thắng +1 trận thắng
            dbManager.updateMatchWinner(matchId, winnerId, endReason);
            System.out.println("✅ Đã cập nhật database");
        } catch (SQLException e) {
            System.err.println("❌ Lỗi cập nhật database: " + e.getMessage());
            e.printStackTrace();
        }

        // Cập nhật status cả hai người chơi: "ingame" -> "online"
        try {
            if (player1Handler != null && player1Handler.getUser() != null) {
                player1Handler.getUser().setStatus("online");
                dbManager.updateUserStatus(player1Handler.getUser().getId(), "online");
                player1Handler.getServer()
                        .broadcast(new Message("status_update", player1Handler.getUser().getUsername() + " is online"));
            }
            
            if (player2Handler != null && player2Handler.getUser() != null) {
                player2Handler.getUser().setStatus("online");
                dbManager.updateUserStatus(player2Handler.getUser().getId(), "online");
                player2Handler.getServer()
                        .broadcast(new Message("status_update", player2Handler.getUser().getUsername() + " is online"));
            }
            System.out.println("✅ Đã cập nhật status -> online");
        } catch (Exception e) {
            System.err.println("❌ Lỗi cập nhật status: " + e.getMessage());
        }

        // Gửi thông báo kết thúc trận đấu cho người thoát
        try {
            quittingPlayer.sendMessage(new Message("match_result", "lose"));
            System.out.println("✅ Đã gửi match_result=lose cho người thoát");
            
            quittingPlayer.sendMessage(new Message("match_end", resultMessageToLoser));
            System.out.println("✅ Đã gửi match_end cho người thoát");
        } catch (Exception e) {
            System.err.println("❌ Lỗi gửi message cho người thoát: " + e.getMessage());
        }
        
        // Gửi thông báo cho người còn lại
        try {
            otherPlayer.sendMessage(new Message("match_result", "win"));
            System.out.println("✅ Đã gửi match_result=win cho người thắng");
            
            otherPlayer.sendMessage(new Message("match_end", resultMessageToWinner));
            System.out.println("✅ Đã gửi match_end cho người thắng");
        } catch (Exception e) {
            System.err.println("❌ Lỗi gửi message cho người thắng: " + e.getMessage());
            e.printStackTrace();
        }

        // Đặt lại trạng thái game room
        player1WantsRematch = null;
        player2WantsRematch = null;
        shooterDirection = null;

        // Clear game room cho cả hai người chơi
        if (player1Handler != null) {
            player1Handler.clearGameRoom();
        }
        if (player2Handler != null) {
            player2Handler.clearGameRoom();
        }
        
        System.out.println("🏁 handlePlayerQuit hoàn tất");
    }

    // Xử lý Shooter Timeout
    public synchronized void startShooterTimeout() {
        try {
            System.out.println("⏱️ startShooterTimeout - Round: " + currentRound);
            
            if (checkEndGame()) {
                System.out.println("🏁 Game đã kết thúc trong startShooterTimeout");
                endMatch();
                return;
            }
            
            // Guard: if this round was already processed, skip
            if (lastProcessedRound >= currentRound) {
                System.out.println("⚠️ startShooterTimeout: round " + currentRound + " already processed, skipping");
                return;
            }

            if (!shooterActionReceived) {
                System.out.println("⏱️ Shooter timeout - người sút không phản hồi, đối thủ được +1 điểm. (round=" + currentRound + ")");

                // Mark and cancel any pending tasks
                shooterActionReceived = true;
                lastProcessedRound = currentRound;

                // HỦY TẤT CẢ TÁC VỤ ĐANG CHỜ
                if (goalkeeperTimeoutTask != null && !goalkeeperTimeoutTask.isDone()) {
                    goalkeeperTimeoutTask.cancel(true);
                    System.out.println("✅ Đã hủy goalkeeperTimeoutTask sau Shooter Timeout.");
                }
                if (shooterTimeoutTask != null && !shooterTimeoutTask.isDone()) {
                    shooterTimeoutTask.cancel(true);
                }

                // Xác định ai là shooter/goalkeeper
                boolean isPlayer1Shooter = (currentRound % 2 == 1);
                ClientHandler shooterHandler = isPlayer1Shooter ? player1Handler : player2Handler;
                ClientHandler goalkeeperHandler = isPlayer1Shooter ? player2Handler : player1Handler;

                // Người sút không thực hiện -> đối thủ được +1 điểm
                if (isPlayer1Shooter) {
                    player2Score++;
                } else {
                    player1Score++;
                }

                // Thông báo timeout và kết quả vòng cho cả hai
                try {
                    shooterHandler.sendMessage(new Message("timeout", "Hết giờ! Bạn đã không chọn hướng. Đối thủ được +1 điểm."));
                    goalkeeperHandler.sendMessage(new Message("opponent_timeout", "Đối thủ hết giờ. Bạn được +1 điểm."));
                } catch (Exception e) {
                    System.err.println("❌ Lỗi gửi thông báo timeout: " + e.getMessage());
                }

                // Lưu chi tiết trận đấu (ghi chú timeout)
                try {
                    dbManager.saveMatchDetails(matchId, currentRound,
                            shooterHandler.getUser().getId(),
                            goalkeeperHandler.getUser().getId(),
                            "timeout", "timeout", "timeout");
                } catch (SQLException se) {
                    System.err.println("❌ Lỗi lưu chi tiết trận đấu sau shooter timeout: " + se.getMessage());
                }

                // Persist per-round point: opponent gets +1 point permanently
                try {
                    int winnerId = goalkeeperHandler.getUser().getId();
                    dbManager.updateUserPoints(winnerId, 1);
                    System.out.println("✅ Đã cập nhật database: +1 điểm cho user id=" + winnerId + " do timeout (shooter)");
                } catch (SQLException se) {
                    System.err.println("❌ Lỗi cập nhật điểm sau timeout shooter: " + se.getMessage());
                }

                // Debug: log scores before sending update
                System.out.println("📨 ShooterTimeout awarding point. scores now=" + player1Score + "-" + player2Score + ", currentRound(before increment)=" + currentRound);

                // Tăng vòng ngay lập tức
                currentRound++;

                // Gửi cập nhật điểm (và vòng mới)
                Message scoreMessageToPlayer1 = new Message("update_score",
                        new int[] { player1Score, player2Score, currentRound });
                Message scoreMessageToPlayer2 = new Message("update_score",
                        new int[] { player2Score, player1Score, currentRound });

                try {
                    player1Handler.sendMessage(scoreMessageToPlayer1);
                    player2Handler.sendMessage(scoreMessageToPlayer2);
                } catch (Exception e) {
                    System.err.println("❌ Lỗi gửi update_score sau shooter timeout: " + e.getMessage());
                }

                // Reset state
                shooterDirection = null;
                goalkeeperDirection = null;
                shooterActionReceived = false;
                goalkeeperActionReceived = false;

                if (checkEndGame()) {
                    determineWinner();
                } else {
                    requestNextMove();
                }
            }
        } catch (Exception e) {
            System.err.println("❌ Lỗi trong startShooterTimeout: " + e.getMessage());
            e.printStackTrace();
        }
    }

    // Xử lý Goalkeeper Timeout
    public synchronized void startGoalkeeperTimeout() {
        try {
            System.out.println("⏱️ startGoalkeeperTimeout - Round: " + currentRound);

            // Guard: avoid double-processing the same round
            if (lastProcessedRound >= currentRound) {
                System.out.println("⚠️ startGoalkeeperTimeout: round " + currentRound + " already processed, skipping");
                return;
            }

            if (!goalkeeperActionReceived) {
                System.out.println("⏱️ Goalkeeper timeout - người bắt không phản hồi, người sút được +1 điểm ngay. (round=" + currentRound + ")");

                // Mark and cancel pending tasks
                goalkeeperActionReceived = true;
                lastProcessedRound = currentRound;

                // HỦY TẤT CẢ TÁC VỤ ĐANG CHỜ
                if (shooterTimeoutTask != null && !shooterTimeoutTask.isDone()) {
                    shooterTimeoutTask.cancel(true);
                    System.out.println("✅ Đã hủy shooterTimeoutTask sau Goalkeeper Timeout.");
                }
                if (goalkeeperTimeoutTask != null && !goalkeeperTimeoutTask.isDone()) {
                    goalkeeperTimeoutTask.cancel(true);
                }

                // Xác định ai là shooter và người được +1
                boolean isPlayer1Shooter = (currentRound % 2 == 1);
                ClientHandler shooterHandler = isPlayer1Shooter ? player1Handler : player2Handler;
                ClientHandler goalkeeperHandler = isPlayer1Shooter ? player2Handler : player1Handler;

                // Người bắt không phản hồi -> người sút được +1
                if (isPlayer1Shooter) {
                    player1Score++;
                } else {
                    player2Score++;
                }

                // Thông báo timeout và kết quả vòng cho cả hai
                try {
                    shooterHandler.sendMessage(new Message("opponent_timeout", "Đối thủ hết giờ. Bạn được +1 điểm."));
                    goalkeeperHandler.sendMessage(new Message("timeout", "Hết giờ! Bạn đã không chọn hướng. Đối thủ được +1 điểm."));
                } catch (Exception e) {
                    System.err.println("❌ Lỗi gửi thông báo timeout (goalkeeper): " + e.getMessage());
                }

                // Lưu chi tiết trận đấu: ghi chú timeout
                try {
                    dbManager.saveMatchDetails(matchId, currentRound,
                            shooterHandler.getUser().getId(),
                            goalkeeperHandler.getUser().getId(),
                            "timeout", "timeout", "timeout");
                } catch (SQLException se) {
                    System.err.println("❌ Lỗi lưu chi tiết trận đấu sau goalkeeper timeout: " + se.getMessage());
                }

                // Persist per-round point: shooter gets +1 point permanently
                try {
                    int winnerId = shooterHandler.getUser().getId();
                    dbManager.updateUserPoints(winnerId, 1);
                    System.out.println("✅ Đã cập nhật database: +1 điểm cho user id=" + winnerId + " do timeout (goalkeeper)");
                } catch (SQLException se) {
                    System.err.println("❌ Lỗi cập nhật điểm sau goalkeeper timeout: " + se.getMessage());
                }

                // Debug: log scores before sending update
                System.out.println("📨 GoalkeeperTimeout awarding point. scores now=" + player1Score + "-" + player2Score + ", currentRound(before increment)=" + currentRound);

                // Tăng vòng ngay lập tức
                currentRound++;

                // Gửi cập nhật điểm (và vòng mới)
                Message scoreMessageToPlayer1 = new Message("update_score",
                        new int[] { player1Score, player2Score, currentRound });
                Message scoreMessageToPlayer2 = new Message("update_score",
                        new int[] { player2Score, player1Score, currentRound });

                try {
                    player1Handler.sendMessage(scoreMessageToPlayer1);
                    player2Handler.sendMessage(scoreMessageToPlayer2);
                } catch (Exception e) {
                    System.err.println("❌ Lỗi gửi update_score sau goalkeeper timeout: " + e.getMessage());
                }

                // Reset state
                shooterDirection = null;
                goalkeeperDirection = null;
                shooterActionReceived = false;
                goalkeeperActionReceived = false;

                if (checkEndGame()) {
                    determineWinner();
                } else {
                    requestNextMove();
                }
            }
        } catch (Exception e) {
            System.err.println("❌ Lỗi trong startGoalkeeperTimeout: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private boolean checkEndGame() {
        // Kết thúc sau đủ 10 lượt (mỗi người 5 lượt)
        // currentRound bắt đầu từ 1, sau mỗi lượt tăng lên
        // currentRound = 11 nghĩa là đã chơi đủ 10 lượt
        return currentRound > MAX_ROUNDS;
    }

    // Thread-safe getter for currentRound so other classes can validate client-sent round IDs
    public synchronized int getCurrentRound() {
        return currentRound;
    }
}