package rs.ac.uns.ftn.slagalica.domain.model;

import com.google.firebase.Timestamp;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class MyNumberRound {
    public String id;
    public String gameId;
    public int roundIndex;
    public int roundNumber;
    public String type;
    public String activePlayerUid;
    public String opponentUid;
    public String phase;
    public int targetNumber;
    public List<Integer> numbers = new ArrayList<>();
    public Map<String, String> submissionsByPlayer = new HashMap<>();
    public Map<String, Double> resultsByPlayer = new HashMap<>();
    public Map<String, Boolean> validByPlayer = new HashMap<>();
    public String winnerUid;
    public int awardedPoints;
    public boolean finished;
    public Timestamp createdAt;
    public Timestamp updatedAt;
    public Timestamp startedAt;
    public boolean scoreApplied;

    public MyNumberRound() {
    }
}
