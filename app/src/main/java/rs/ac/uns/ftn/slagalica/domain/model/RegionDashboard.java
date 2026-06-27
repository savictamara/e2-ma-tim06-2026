package rs.ac.uns.ftn.slagalica.domain.model;

import java.util.ArrayList;
import java.util.List;

public class RegionDashboard {
    public String currentUserRegionId = "";
    public String currentUserRegionName = "";
    public List<RegionStats> regions = new ArrayList<>();
    public List<RegionPoint> points = new ArrayList<>();
}
