package rs.ac.uns.ftn.slagalica.domain.model;

public class LeaderboardEntry {
    public String uid;
    public String username;
    public String leagueIcon;
    public long stars;
    public long rank;
    public boolean currentUser;

    public LeaderboardEntry(String uid, String username, String leagueIcon, long stars) {
        this.uid = uid;
        this.username = username;
        this.leagueIcon = leagueIcon;
        this.stars = stars;
    }
}
