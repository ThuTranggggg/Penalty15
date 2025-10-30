package common;

import java.io.Serializable;
import java.sql.Timestamp;

public class MatchDetails implements Serializable {
    private int id;
    private int matchId;
    private int round;
    private int shooterId;
    private int goalkeeperId;
    private String shooterDirection;
    private String goalkeeperDirection;
    private String result;
    private Timestamp time;
    
    public MatchDetails(int id, int matchId, int round, int shooterId, int goalkeeperId, String shooterDirection, String goalkeeperDirection, String result, Timestamp time) {
        this.id = id;
        this.matchId = matchId;
        this.round = round;
        this.shooterId = shooterId;
        this.goalkeeperId = goalkeeperId;
        this.shooterDirection = shooterDirection;
        this.goalkeeperDirection = goalkeeperDirection;
        this.result = result;
        this.time = time;
    }

    // Getters và Setters
    public Timestamp getTime() {
        return time;
    }
    
    public int getId() {
        return id;
    }

    public int getMatchId() {
        return matchId;
    }

    public int getRound() {
        return round;
    }

    public int getShooterId() {
        return shooterId;
    }

    public int getGoalkeeperId() {
        return goalkeeperId;
    }

    public String getShooterDirection() {
        return shooterDirection;
    }

    public String getGoalkeeperDirection() {
        return goalkeeperDirection;
    }

    public String getResult() {
        return result;
    }

    public void setId(int id) {
        this.id = id;
    }

    public void setMatchId(int matchId) {
        this.matchId = matchId;
    }

    public void setRound(int round) {
        this.round = round;
    }

    public void setShooterId(int shooterId) {
        this.shooterId = shooterId;
    }

    public void setGoalkeeperId(int goalkeeperId) {
        this.goalkeeperId = goalkeeperId;
    }

    public void setShooterDirection(String shooterDirection) {
        this.shooterDirection = shooterDirection;
    }

    public void setGoalkeeperDirection(String goalkeeperDirection) {
        this.goalkeeperDirection = goalkeeperDirection;
    }

    public void setResult(String result) {
        this.result = result;
    }
}
