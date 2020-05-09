package utility;

public class Boundary {
    Double[] latPoints;
    Double[] lonPoints;
    int npoints;

    public Boundary(Double[] latPoints, Double[] lonPoints) {
        this.npoints = latPoints.length;
        this.latPoints = latPoints;
        this.lonPoints = lonPoints;
    }

    private boolean checkCrossing(int i, int j, double testLat, double testLon) {
        return ((lonPoints[i] > testLon) != (lonPoints[j] > testLon) &&
                (testLat < (latPoints[j] - latPoints[i]) *
                        (testLon - lonPoints[i]) / (lonPoints[j]-lonPoints[i]) + latPoints[i]));
    }

    public boolean contains(GeoCoordinate geoCoordinate) throws Exception {
        if (this.npoints != lonPoints.length) {
            throw new Exception("Arrays must have same length");
        }
        boolean result = false;
        int i, j;
        for (i = 0, j = this.npoints - 1; i < this.npoints; j = i++) {
            if (checkCrossing(i, j, geoCoordinate.getLatitude(), geoCoordinate.getLongitude())) {
                result = !result;
            }
        }
        return result;
    }
}