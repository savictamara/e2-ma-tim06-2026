package rs.ac.uns.ftn.slagalica.domain.model;

public class LeagueDefinition {
    public final int id;
    public final String name;
    public final long requiredStars;
    public final String iconName;

    private static final LeagueDefinition[] LEAGUES = {
            new LeagueDefinition(0, "Pocetna liga", 0, "ic_league_0"),
            new LeagueDefinition(1, "Bronzana liga", 100, "ic_league_1"),
            new LeagueDefinition(2, "Srebrna liga", 200, "ic_league_2"),
            new LeagueDefinition(3, "Zlatna liga", 400, "ic_league_3"),
            new LeagueDefinition(4, "Dijamantska liga", 800, "ic_league_4"),
            new LeagueDefinition(5, "Sampionska liga", 1600, "ic_league_5")
    };

    private LeagueDefinition(int id, String name, long requiredStars, String iconName) {
        this.id = id;
        this.name = name;
        this.requiredStars = requiredStars;
        this.iconName = iconName;
    }

    public static LeagueDefinition forStars(long stars) {
        long safeStars = Math.max(0, stars);
        LeagueDefinition selected = LEAGUES[0];
        for (LeagueDefinition league : LEAGUES) {
            if (safeStars >= league.requiredStars) {
                selected = league;
            }
        }
        return selected;
    }

    public static LeagueDefinition byId(long id) {
        for (LeagueDefinition league : LEAGUES) {
            if (league.id == id) {
                return league;
            }
        }
        return LEAGUES[0];
    }

    public static LeagueDefinition nextAfter(LeagueDefinition league) {
        if (league == null || league.id >= 5) {
            return null;
        }
        return byId(league.id + 1);
    }

    public static int dailyTokensFor(long leagueId) {
        return 5 + byId(leagueId).id;
    }

    public static String direction(int oldLeague, int newLeague) {
        if (newLeague > oldLeague) return "PROMOTION";
        if (newLeague < oldLeague) return "DEMOTION";
        return "NONE";
    }
}
