package rs.ac.uns.ftn.slagalica.domain.model;

import com.google.firebase.Timestamp;

public class User {
    public String uid;
    public String email;
    public String username;
    public String usernameLowercase;
    public String region;
    public String regionId;
    public String regionName;
    public float regionPointX;
    public float regionPointY;
    public int monthlyRegionStars;
    public int weeklyStars;
    public int monthlyStars;
    public int weeklyMatchesPlayed;
    public int monthlyMatchesPlayed;
    public boolean weeklyLeaderboardEligible;
    public boolean monthlyLeaderboardEligible;
    public boolean pendingReward;
    public String avatarFrame;
    public String avatar;
    public String avatarId;
    public String avatarFrameColor;
    public String avatarFrameType;
    public int tokens;
    public int stars;
    public int league;
    public String leagueName;
    public String leagueIcon;
    public String leagueIconName;
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
        this.usernameLowercase = username == null ? "" : username.toLowerCase(java.util.Locale.US);
        this.region = region;
        this.regionId = "";
        this.regionName = region;
        this.regionPointX = 0f;
        this.regionPointY = 0f;
        this.monthlyRegionStars = 0;
        this.weeklyStars = 0;
        this.monthlyStars = 0;
        this.weeklyMatchesPlayed = 0;
        this.monthlyMatchesPlayed = 0;
        this.weeklyLeaderboardEligible = false;
        this.monthlyLeaderboardEligible = false;
        this.pendingReward = false;
        this.avatarFrame = "NONE";
        this.avatar = "";
        this.avatarId = "star";
        this.avatarFrameColor = "#8A2BE2";
        this.avatarFrameType = "default";
        this.tokens = 5;
        this.stars = 0;
        this.league = 0;
        this.leagueName = "Pocetna liga";
        this.leagueIcon = "ic_league_0";
        this.leagueIconName = "ic_league_0";
        this.createdAt = Timestamp.now();
        this.online = true;
        this.inGame = false;
        this.currentGameId = "";
    }
}
