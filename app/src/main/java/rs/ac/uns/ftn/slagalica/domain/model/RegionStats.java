package rs.ac.uns.ftn.slagalica.domain.model;

public class RegionStats {
    public String regionId;
    public String regionName;
    public String iconName;
    public long monthlyStars;
    public long firstPlaces;
    public long secondPlaces;
    public long thirdPlaces;
    public long activePlayers;
    public long totalPlayers;
    public String lastCycleMonth;
    public long previousCyclePlacement;
    public long currentRank;

    public RegionStats(String regionId, String regionName, String iconName) {
        this.regionId = regionId;
        this.regionName = regionName;
        this.iconName = iconName;
    }
}
