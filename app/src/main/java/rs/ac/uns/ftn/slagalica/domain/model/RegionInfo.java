package rs.ac.uns.ftn.slagalica.domain.model;

public class RegionInfo {
    public final String id;
    public final String name;
    public final String iconName;
    public final double minLatitude;
    public final double maxLatitude;
    public final double minLongitude;
    public final double maxLongitude;

    public RegionInfo(String id, String name, String iconName,
                      double minLatitude, double maxLatitude,
                      double minLongitude, double maxLongitude) {
        this.id = id;
        this.name = name;
        this.iconName = iconName;
        this.minLatitude = minLatitude;
        this.maxLatitude = maxLatitude;
        this.minLongitude = minLongitude;
        this.maxLongitude = maxLongitude;
    }

    public double centerLatitude() {
        return (minLatitude + maxLatitude) / 2.0;
    }

    public double centerLongitude() {
        return (minLongitude + maxLongitude) / 2.0;
    }
}
