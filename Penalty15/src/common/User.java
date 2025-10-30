package common;

import java.io.Serializable;

public class User implements Serializable {
    private int id;
    private String username;
    private int points;
    private String status;

    public User(int id, String username, int points, String status) {
        this.id = id;
        this.username = username;
        this.points = points;
        this.status = status;
    }

    // Getters và Setters
    public int getId() {
        return id;
    }

    public String getUsername() {
        return username;
    }

    public int getPoints() {
        return points;
    }

    public String getStatus() {
        return status;
    }

    public void setPoints(int points) {
        this.points = points;
    }

    public void setStatus(String status) {
        this.status = status;
    }
}
