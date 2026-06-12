package rs.ac.uns.ftn.slagalica.domain.model;

import com.google.firebase.Timestamp;
import java.util.ArrayList;
import java.util.List;

public class StepByStepRound {
    public String id;
    public String gameId;
    public int roundIndex;
    public int roundNumber;
    public String type;
    public String activePlayerUid;
    public String opponentUid;
    public int openedStepIndex;
    public List<String> steps = new ArrayList<>();
    public String answer;
    public String phase;
    public String winnerUid;
    public int awardedPoints;
    public boolean finished;
    public Timestamp createdAt;
    public Timestamp updatedAt;
    public Timestamp phaseStartedAt;
    public boolean scoreApplied;

    public StepByStepRound() {
    }
}
