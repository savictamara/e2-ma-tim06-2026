package rs.ac.uns.ftn.slagalica.domain.model;

public class RegionInfo {
    public final String id;
    public final String name;
    public final String iconName;
    public final int color;
    public final float[] polygon;
    public final double minLatitude;
    public final double maxLatitude;
    public final double minLongitude;
    public final double maxLongitude;

    public RegionInfo(String id, String name, String iconName,
                      double minLatitude, double maxLatitude,
                      double minLongitude, double maxLongitude) {
        this(id, name, iconName, 0xFFDCCEFF, minLatitude, maxLatitude, minLongitude, maxLongitude, new float[]{
                (float) minLongitude, (float) minLatitude,
                (float) maxLongitude, (float) minLatitude,
                (float) maxLongitude, (float) maxLatitude,
                (float) minLongitude, (float) maxLatitude
        });
    }

    public RegionInfo(String id, String name, String iconName, int color,
                      double minLatitude, double maxLatitude,
                      double minLongitude, double maxLongitude,
                      float[] polygon) {
        this.id = id;
        this.name = name;
        this.iconName = iconName;
        this.color = color;
        this.polygon = polygon;
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

    public float centerX() {
        float sum = 0;
        int count = 0;
        for (int i = 0; i + 1 < polygon.length; i += 2) {
            sum += polygon[i];
            count++;
        }
        return count == 0 ? 0.5f : sum / count;
    }

    public float centerY() {
        float sum = 0;
        int count = 0;
        for (int i = 1; i < polygon.length; i += 2) {
            sum += polygon[i];
            count++;
        }
        return count == 0 ? 0.5f : sum / count;
    }

    public boolean contains(float x, float y) {
        boolean inside = false;
        int count = polygon.length / 2;
        for (int i = 0, j = count - 1; i < count; j = i++) {
            float xi = polygon[i * 2];
            float yi = polygon[i * 2 + 1];
            float xj = polygon[j * 2];
            float yj = polygon[j * 2 + 1];
            boolean intersect = ((yi > y) != (yj > y))
                    && (x < (xj - xi) * (y - yi) / ((yj - yi) == 0 ? 0.0001f : (yj - yi)) + xi);
            if (intersect) {
                inside = !inside;
            }
        }
        return inside;
    }
}
