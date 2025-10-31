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

    // Biến để kiểm tra xem người chơi đã thực hiện hành động chưa
    private boolean shooterActionReceived = false;
    private boolean goalkeeperActionReceived = false;

    private String goalkeeperDirection;

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
            // Vòng lẻ (1,3,5,7,9): Player1 sút, Player2 bắt
            // Vòng chẵn (2,4,6,8,10): Player2 sút, Player1 bắt
            boolean isPlayer1Shooter = (currentRound % 2 == 1);
            
            if (isPlayer1Shooter) {
                // Player1 sút, Player2 bắt
                player1Handler.sendMessage(new Message("your_turn", TURN_TIMEOUT));
                player2Handler.sendMessage(new Message("opponent_turn", TURN_TIMEOUT));
            } else {
                // Player2 sút, Player1 bắt
                player2Handler.sendMessage(new Message("your_turn", TURN_TIMEOUT));
                player1Handler.sendMessage(new Message("opponent_turn", TURN_TIMEOUT));
            }
            
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
        this.shooterDirection = shooterDirection;
        shooterActionReceived = true;
        if (shooterTimeoutTask != null && !shooterTimeoutTask.isDone()) {
            shooterTimeoutTask.cancel(true);
        }
        
        // Xác định ai là người bắt trong vòng này
        boolean isPlayer1Shooter = (currentRound % 2 == 1);
        ClientHandler goalkeeperHandler = isPlayer1Shooter ? player2Handler : player1Handler;
        ClientHandler shooterHandler = isPlayer1Shooter ? player1Handler : player2Handler;
        
        // Yêu cầu người bắt chọn hướng chặn
        goalkeeperHandler.sendMessage(new Message("goalkeeper_turn", TURN_TIMEOUT));
        shooterHandler.sendMessage(new Message("opponent_turn", TURN_TIMEOUT));
        
        goalkeeperActionReceived = false;
    }

    // Xử lý hướng chặn từ người bắt
    public synchronized void handleGoalkeeper(String goalkeeperDirection, ClientHandler goalkeeper)
            throws SQLException, IOException {
        if (this.shooterDirection == null) {
            player1Handler.sendMessage(new Message("error", "Hướng sút chưa được thiết lập."));
            player2Handler.sendMessage(new Message("error", "Hướng sút chưa được thiết lập."));
            return;
        }
        this.goalkeeperDirection = goalkeeperDirection;
        goalkeeperActionReceived = true;

        if (goalkeeperTimeoutTask != null && !goalkeeperTimeoutTask.isDone()) {
            goalkeeperTimeoutTask.cancel(true);
        }

        // Xác định vai trò trong vòng này
        boolean isPlayer1Shooter = (currentRound % 2 == 1);
        ClientHandler shooterHandler = isPlayer1Shooter ? player1Handler : player2Handler;
        ClientHandler goalkeeperHandler = isPlayer1Shooter ? player2Handler : player1Handler;
        
        // Xử lý kết quả
        // LOGIC ĐÚNG: Nếu hướng SÚT ≠ hướng BẮT → Người SÚT ghi bàn (thắng)
        //            Nếu hướng SÚT = hướng BẮT → Người BẮT cản được (người sút 0 điểm)
        boolean goal = !shooterDirection.equalsIgnoreCase(goalkeeperDirection);
        
        if (goal) {
            // Người sút thắng → Cộng điểm cho người sút
            if (isPlayer1Shooter) {
                player1Score++;  // Player1 đang sút → Player1 ghi bàn
            } else {
                player2Score++;  // Player2 đang sút → Player2 ghi bàn
            }
        }
        // Nếu không goal (trùng hướng) → Không ai được điểm

        String kick_result = (goal ? "win" : "lose") + "-" + shooterDirection + "-" + goalkeeperDirection;
        shooterHandler.sendMessage(new Message("kick_result", kick_result));
        goalkeeperHandler.sendMessage(new Message("kick_result", kick_result));

        // Lưu chi tiết trận đấu vào database
        dbManager.saveMatchDetails(matchId, currentRound,
                shooterHandler.getUser().getId(),
                goalkeeperHandler.getUser().getId(),
                shooterDirection, goalkeeperDirection, goal ? "win" : "lose");

        // Gửi tỷ số cập nhật cho từng người chơi
        // Player1 nhận điểm của mình trước
        Message scoreMessageToPlayer1 = new Message("update_score",
                new int[] { player1Score, player2Score, currentRound });
        Message scoreMessageToPlayer2 = new Message("update_score",
                new int[] { player2Score, player1Score, currentRound });

        player1Handler.sendMessage(scoreMessageToPlayer1);
        player2Handler.sendMessage(scoreMessageToPlayer2);

        // Tăng vòng
        currentRound++;
        System.out.println("📊 Sau khi tăng vòng: currentRound = " + currentRound + ", MAX_ROUNDS = " + MAX_ROUNDS);
        
        if (checkEndGame()) {
            System.out.println("🏁 Trận đấu kết thúc! Gọi determineWinner()...");
            determineWinner();
        } else {
            System.out.println("▶️ Chuyển sang vòng tiếp theo...");
            // Chuyển sang vòng tiếp theo
            shooterDirection = null;
            goalkeeperDirection = null;
            shooterActionReceived = false;
            goalkeeperActionReceived = false;
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

        // KHÔNG swap vai trò - giữ nguyên player1 và player2
        // Mỗi vòng sẽ tự động đổi vai trò dựa trên currentRound

        // Create a new match in the database
        matchId = dbManager.saveMatch(player1Handler.getUser().getId(), player2Handler.getUser().getId(), 0);
    }

    // Đảm bảo rằng phương thức endMatch() tồn tại và được định nghĩa chính xác
    private void endMatch() throws SQLException, IOException {
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

    public void startShooterTimeout() {
        try {
            System.out.println("⏱️ startShooterTimeout - Round: " + currentRound);
            
            if (checkEndGame()) {
                System.out.println("🏁 Game đã kết thúc trong startShooterTimeout");
                endMatch();
                return;
            }
            
            if (!shooterActionReceived) {
                // Xác định ai là shooter trong vòng này
                boolean isPlayer1Shooter = (currentRound % 2 == 1);
                ClientHandler shooterHandler = isPlayer1Shooter ? player1Handler : player2Handler;
                ClientHandler goalkeeperHandler = isPlayer1Shooter ? player2Handler : player1Handler;
                
                System.out.println("⏱️ Shooter timeout - tự động chọn vị trí 5");
                
                // Người sút không thực hiện hành động trong thời gian quy định
                shooterDirection = "5"; // Vị trí 5 (center)
                shooterActionReceived = true;
                shooterHandler.sendMessage(
                        new Message("timeout", "Hết giờ! \nHệ thống tự chọn vị trí 5 (giữa) cho bạn."));
                goalkeeperHandler.sendMessage(new Message("opponent_timeout",
                        "Hết giờ! \nHệ thống tự chọn vị trí 5 (giữa) cho đối thủ."));
                // Yêu cầu người bắt chọn hướng chặn
                handleShot(shooterDirection, shooterHandler);

                // Bắt đầu đếm thời gian chờ cho người bắt
                goalkeeperActionReceived = false;
            }
        } catch (Exception e) {
            System.err.println("❌ Lỗi trong startShooterTimeout: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private boolean checkEndGame() {
        // Kết thúc sau đủ 10 lượt (mỗi người 5 lượt)
        // currentRound bắt đầu từ 1, sau mỗi lượt tăng lên
        // currentRound = 11 nghĩa là đã chơi đủ 10 lượt
        return currentRound > MAX_ROUNDS;
    }

    public void startGoalkeeperTimeout() {
        try {
            System.out.println("⏱️ startGoalkeeperTimeout - Round: " + currentRound);
            
            if (!goalkeeperActionReceived) {
                // Xác định ai là goalkeeper trong vòng này
                boolean isPlayer1Shooter = (currentRound % 2 == 1);
                ClientHandler shooterHandler = isPlayer1Shooter ? player1Handler : player2Handler;
                ClientHandler goalkeeperHandler = isPlayer1Shooter ? player2Handler : player1Handler;
                
                System.out.println("⏱️ Goalkeeper timeout - tự động chọn vị trí 5");
                
                // Người bắt không thực hiện hành động trong thời gian quy định
                goalkeeperDirection = "5"; // Vị trí 5 (center)
                goalkeeperActionReceived = true;

                goalkeeperHandler.sendMessage(
                        new Message("timeout", "Hết giờ! \nHệ thống tự chọn vị trí 5 (giữa) cho bạn."));
                shooterHandler.sendMessage(new Message("opponent_timeout",
                        "Hết giờ! \nHệ thống tự chọn vị trí 5 (giữa) cho đối thủ."));

                // Tiến hành xử lý kết quả
                handleGoalkeeper(goalkeeperDirection, goalkeeperHandler);
            }
        } catch (Exception e) {
            System.err.println("❌ Lỗi trong startGoalkeeperTimeout: " + e.getMessage());
            e.printStackTrace();
        }
    }
}
