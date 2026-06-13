package rs.ac.uns.ftn.slagalica.domain.model;

import com.google.firebase.Timestamp;

public class User {
    public String uid;
    public String email;
    public String username;
    public String region;
    public String avatar;
    public String avatarId;
    public String avatarFrameColor;
    public String avatarFrameType;
    public int tokens;
    public int stars;
    public String league;
    public String leagueName;
    public String leagueIcon;
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
        this.avatarId = "star";
        this.avatarFrameColor = "#8A2BE2";
        this.avatarFrameType = "default";
        this.tokens = 5;
        this.stars = 0;
        this.league = "Početna liga";
        this.leagueName = "Početna liga";
        this.leagueIcon = "star";
        this.createdAt = Timestamp.now();
        this.online = true;
        this.inGame = false;
        this.currentGameId = "";
    }
}
