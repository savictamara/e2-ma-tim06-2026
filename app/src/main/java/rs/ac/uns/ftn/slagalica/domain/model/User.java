package rs.ac.uns.ftn.slagalica.domain.model;

import com.google.firebase.Timestamp;

public class User {
    public String uid;
    public String email;
    public String username;
    public String region;
    public String avatar;
    public int tokens;
    public int stars;
    public String league;
    public Timestamp createdAt;
    public boolean online;
    public boolean inGame;
    public String currentGameId;

    public User() {
    }

    public User(String uid, String email, String username, String region) {
        this.uid = uid;
        this.email = email;
        this.username = username;
        this.region = region;
        this.avatar = "";
        this.tokens = 0;
        this.stars = 0;
        this.league = "Bronzana liga";
        this.createdAt = Timestamp.now();
        this.online = true;
        this.inGame = false;
        this.currentGameId = "";
    }
}
