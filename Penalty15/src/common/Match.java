package common;

import java.io.Serializable;
import java.sql.Timestamp;

public class Match implements Serializable {
    private int id;
    private int player1Id;
    private int player2Id;
    private Integer winnerId; // Có thể NULL
    private String player1Name;
    private String player2Name;
    private Timestamp time; // Thêm thuộc tính thời gian
    private String endReason; // Thêm thuộc tính endReason
    
public Match(int id, int player1Id, int player2Id, Integer winnerId, String player1Name, String player2Name, Timestamp time, String endReason) {
        this.id = id;
        this.player1Id = player1Id;
        this.player2Id = player2Id;
        this.winnerId = winnerId;
        this.player1Name = player1Name;
        this.player2Name = player2Name;
        this.time = time;
        this.endReason = endReason;
    }

    // Getters và Setters

    public Timestamp getTime() {
        return time;
    }

    public int getId() {
        return id;
    }

    public int getPlayer1Id() {
        return player1Id;
    }

    public int getPlayer2Id() {
        return player2Id;
    }

    public Integer getWinnerId() {
        return winnerId;
    }

    public String getPlayer1Name() {
        return player1Name;
    }

    public String getPlayer2Name() {
        return player2Name;
    }

    public String getEndReason() {
        return endReason;
    }

    public String getResult(int userId) {
        if (endReason != null && endReason.equals("player_quit")) {
            if (winnerId == userId) {
                return "Đối thủ đã thoát";
            } else {
                return "Bạn đã thoát";
            }
        } else {
            if (winnerId == null || winnerId == 0) {
                return "Thắng";
            } else if (winnerId == userId) {
                return "Thắng";
            } else {
                return "Thua";
            }
        }
    }

    public String getOpponentName(int userId) {
        return (player1Id == userId) ? player2Name : player1Name;
    }
}
