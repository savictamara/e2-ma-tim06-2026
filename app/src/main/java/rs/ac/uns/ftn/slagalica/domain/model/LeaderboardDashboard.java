package rs.ac.uns.ftn.slagalica.domain.model;

import java.util.ArrayList;
import java.util.List;

public class LeaderboardDashboard {
    public String cycleType;
    public String cycleId;
    public String dateRange;
    public List<LeaderboardEntry> entries = new ArrayList<>();
}
