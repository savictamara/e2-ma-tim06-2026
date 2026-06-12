package rs.ac.uns.ftn.slagalica.domain.model;

import com.google.firebase.Timestamp;

public class GameSession {
    public String id;
    public String player1Uid;
    public String player2Uid;
    public String currentPlayerUid;
    public String status;
    public String currentMiniGame;
    public int player1Score;
    public int player2Score;
    public Timestamp createdAt;
    public Timestamp updatedAt;

    public GameSession() {
    }
}
