package rs.ac.uns.ftn.slagalica.domain.model;

public class RegionPoint {
    public String uid;
    public String username;
    public String regionId;
    public float x;
    public float y;
    public double latitude;
    public double longitude;

    public RegionPoint(String uid, String username, String regionId, double latitude, double longitude) {
        this.uid = uid;
        this.username = username;
        this.regionId = regionId;
        this.latitude = latitude;
        this.longitude = longitude;
        this.x = (float) longitude;
        this.y = (float) latitude;
    }

    public RegionPoint(String uid, String username, String regionId, float x, float y) {
        this.uid = uid;
        this.username = username;
        this.regionId = regionId;
        this.x = x;
        this.y = y;
        this.longitude = x;
        this.latitude = y;
    }
}
