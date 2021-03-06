package output_and_metrics.graphics;

import org.influxdb.InfluxDB;
import org.influxdb.InfluxDBFactory;
import org.influxdb.dto.BatchPoints;
import org.influxdb.dto.Point;
import org.influxdb.dto.Pong;

import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * An influxDB client.
 * In order to use this class make sure to have in /etc/hosts the line "127.0.0.1  influxdb".
 */

public class InfluxDBClient {

    private static final String DB_URL = "http://influxdb:8086";
    private static final String USERNAME = "admin";
    private static final String PASSWORD = "password";

    private InfluxDB connection = null;

    /**
     * Returns an instance of connection. If the current one is null (has never been crested or has been closed) it
     * creates a new one, tests the database reachability and returns it.
     * @return Connection to influxDB
     */
    public InfluxDB getConnection() {

        if (this.connection == null) {
            this.connection = InfluxDBFactory.connect(DB_URL, USERNAME, PASSWORD);
            this.connection.setLogLevel(InfluxDB.LogLevel.BASIC);
            Pong response = this.connection.ping();
            if (response.getVersion().equalsIgnoreCase("unknown")) {
                // error pinging server
                System.err.println("Error pinging influx db");
            }
        }
        return this.connection;
    }

    /**
     * Closes the current influxDB connection, it must be called when no other operation has to be performed on the
     * time series database.
     */
    public void closeConnection() {

        if (!(this.connection == null)) {
            this.connection.close();
            this.connection = null;
        }
    }

    /**
     * Creates a new influxDB database and sets the retention time policy to the specified time interval.
     * @param dbName name of the database to create
     * @param retentionTime data persistence duration
     */
    public void createDatabase(String dbName, String retentionTime) {
        InfluxDB connection = getConnection();
        if (connection.databaseExists(dbName)) {
            connection.deleteDatabase(dbName);
        }
        connection.createDatabase(dbName);
        // maintains time series data for specified time interval
        connection.createRetentionPolicy("defaultPolicy", dbName, retentionTime, 1, true);
        connection.disableBatch();
    }

    /**
     * Scope: Query 1 results
     * Inserts mean of cured people and performed swabs as measurement point in influxDB.
     * @param dbName name of the DB to which data must to be added
     * @param startDay week start day, month and year corresponding to weekly measurements
     * @param cured value of mean cured people
     * @param swabs value of mean performed swabs
     * @return true if insertion has been completed, false elsewhere
     */
    public boolean insertPoints(String dbName, String startDay, Double cured, Double swabs) {
        try {
            DateFormat formatter = new SimpleDateFormat("yyyy-MM-dd");
            long time = TimeUnit.MILLISECONDS.toDays(formatter.parse(startDay).getTime());

            // to insert two points at a time
            BatchPoints batch = BatchPoints
                    .database(dbName)
                    .retentionPolicy("defaultPolicy")
                    .build();

            Point curedPoint = Point.measurement("query1_mean_cured")
                    .time(time, TimeUnit.DAYS)
                    .addField("cured", cured)
                    .build();
            Point swabsPoint = Point.measurement("query1_mean_swabs")
                    .time(time, TimeUnit.DAYS)
                    .addField("swabs", swabs)
                    .build();

            // add points to batch
            batch.point(curedPoint);
            batch.point(swabsPoint);
            // write batch
            getConnection().write(batch);
            return true;
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }

    /**
     * Scope: Query 2 results
     * Inserts mean of cured people and performed swabs as measurement point in influxDB.
     * @param dbName name of the DB to which data must to be added
     * @param week week start day, month and year corresponding to weekly measurements
     * @param continent name of the continent
     * @param mean value of positive case's mean
     * @param stddev value of positive case's standard deviation
     * @param min value of positive case's maximum value
     * @param max value of positive case's minumum value
     * @return true if insertion has been completed, false elsewhere
     */
    public boolean insertPoints(String dbName, String week, String continent, Double mean, Double stddev,
                             Double min, Double max) {
        try {
            DateFormat formatter = new SimpleDateFormat("yyyy-MM-dd");
            long time = TimeUnit.MILLISECONDS.toDays(formatter.parse(week).getTime());

            // to insert multiple points at a time
            BatchPoints batch = BatchPoints
                    .database(dbName)
                    .retentionPolicy("defaultPolicy")
                    .build();

            Point meanPoint = Point.measurement("query2_mean_" + continent)
                    .time(time, TimeUnit.DAYS)
                    .addField("mean", mean)
                    .build();
            Point stddevPoint = Point.measurement("query2_stddev_" + continent)
                    .time(time, TimeUnit.DAYS)
                    .addField("stddev", stddev)
                    .build();
            Point minPoint = Point.measurement("query2_min_" + continent)
                    .time(time, TimeUnit.DAYS)
                    .addField("min", min)
                    .build();
            Point maxPoint = Point.measurement("query2_max_" + continent)
                    .time(time, TimeUnit.DAYS)
                    .addField("max", max)
                    .build();

            // add points to batch
            batch.point(meanPoint);
            batch.point(stddevPoint);
            batch.point(minPoint);
            batch.point(maxPoint);
            // write batch
            getConnection().write(batch);
            return true;
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }

    /**
     * Scope: Query 3 results
     * Inserts centroid position and countries list as measurement point in influxDB.
     * @param dbName name of the DB to which data must to be added
     * @param centroid current cluster centroid position
     * @param countries list of countries belonging to the cluster
     * @param clusterAssignement cluster index
     * @param month month and year corresponding to the cluster composition
     * @return true if insertion has been completed, false elsewhere
     */
    public boolean insertPoints(String dbName, Double centroid, List<String> countries,
                             int clusterAssignement, String month) {
        try {
            DateFormat formatter = new SimpleDateFormat("MM-yyyy");
            long time = TimeUnit.MILLISECONDS.toDays(formatter.parse(month).getTime());
            // insert clustering info as a single point
            Point newPoint = Point.measurement("query3_cluster"+clusterAssignement)
                    .time(time, TimeUnit.DAYS)
                    .addField("centroid", centroid)
                    .addField("countries", countries.toString())
                    .build();
            // perform insertion
            getConnection().setRetentionPolicy("defaultPolicy").setDatabase(dbName).write(newPoint);
            return true;
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }
}
