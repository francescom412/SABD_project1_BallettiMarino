package query3;

import org.apache.commons.math3.stat.regression.SimpleRegression;
import org.apache.spark.SparkConf;
import org.apache.spark.api.java.JavaPairRDD;
import org.apache.spark.api.java.JavaRDD;
import org.apache.spark.api.java.JavaSparkContext;
import scala.Tuple2;
import utility.ClusteringUtility;
import utility.IOUtility;
import utility.QueryUtility;

import java.text.SimpleDateFormat;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

public class Query3Main {

    public static void main(String[] args) {

        SparkConf sparkConf = new SparkConf()
                .setMaster("local")
                .setAppName("Query 3");
        JavaSparkContext sparkContext = new JavaSparkContext(sparkConf);
        sparkContext.setLogLevel("ERROR");

        JavaRDD<String> dataset2 = sparkContext.textFile(IOUtility.getDS2());

        final long startTime = System.currentTimeMillis();

        JavaPairRDD<String, CountryDataQuery3> monthlyData = dataset2.flatMapToPair(
                line -> {
                    List<Tuple2<String, CountryDataQuery3>> result = new ArrayList<>();
                    String[] splitted = line.split(",");

                    // name as country or state when country is not available
                    String name = splitted[0].equals("") ? splitted[1] : splitted[0];

                    // conversion of cumulative data to punctual data for every RDD row
                    List<Double> punctualData = QueryUtility.toPunctualData(Arrays.asList(splitted)
                            .subList(4, splitted.length));

                    Calendar currentDate = QueryUtility.getDataset2StartDate();
                    SimpleDateFormat format = new SimpleDateFormat("MM-yyyy");
                    // for monthly grouping
                    String currentMonth = format.format(currentDate.getTime());
                    List<Double> monthlyPoints = new ArrayList<>();
                    for (Double singlePoint : punctualData) {
                        // add point for current date
                        monthlyPoints.add(singlePoint);
                        // increment date
                        currentDate.add(Calendar.DATE, 1);
                        // switch of month then save previous data
                        if (!(format.format(currentDate.getTime())).equals(currentMonth)) {
                            result.add(new Tuple2<>(currentMonth, new CountryDataQuery3(name,
                                    monthlyPoints)));
                            monthlyPoints = new ArrayList<>();
                            currentMonth = format.format(currentDate.getTime());
                        }
                    }
                    // may have not yet inserted last month data
                    if (!monthlyPoints.isEmpty()) {
                        result.add(new Tuple2<>(currentMonth, new CountryDataQuery3(name, monthlyPoints)));
                    }
                    return result.iterator();
                }
        );

        JavaPairRDD<String, Tuple2<Double, CountryDataQuery3>> monthlySlopes = monthlyData.mapToPair(
                tuple -> {
                    SimpleRegression regression = new SimpleRegression();
                    List<Double> values = tuple._2().getValues();
                    for (int i = 0; i < values.size(); i++) {
                        regression.addData(i, values.get(i));
                    }
                    // compute and save trendline coefficient
                    tuple._2().setSlope(regression.getSlope());
                    return new Tuple2<>(tuple._1(), new Tuple2<>(tuple._2().getSlope(), tuple._2()));
                }
        );

        JavaPairRDD<String, List<Tuple2<Double, CountryDataQuery3>>> topMonthlySlopes = monthlySlopes
                .groupByKey()
                .mapToPair(
                        tuple -> {
                            List<Tuple2<Double, CountryDataQuery3>> data = StreamSupport
                                    .stream(tuple._2().spliterator(), false).sorted((t1, t2) -> {
                                        // must be in descending order so the implementation is the inverse respect to
                                        // the ascending one
                                        double val = t1._1 - t2._1;
                                        if (val > 0) return -1;
                                        else if (val < 0) return 1;
                                        else return 0;
                                    }).collect(Collectors.toList());
                            return new Tuple2<>(tuple._1(), data.subList(0, 49));
                        })
                .cache();

        // list of months
        List<String> keys = topMonthlySlopes.keys().collect();

        // result structure
        List<Tuple2<String, ArrayList<ArrayList<String>>>> result = new ArrayList<>();

        // perform clustering for every month and add result to result structure
        for (String key : keys) {
            result.add(new Tuple2<>(key,
                    // perform clustering filtering the RDD by current month
                    ClusteringUtility.performClustering(topMonthlySlopes.filter(tuple -> tuple._1().equals(key)))));
        }

        IOUtility.printTime(System.currentTimeMillis() - startTime);

        //printResult(result);

        IOUtility.writeLocalStructureToHdfs(IOUtility.getOutputPathQuery3(), result);

        sparkContext.close();
    }

    private static void printResult(List<Tuple2<String, ArrayList<ArrayList<String>>>> result) {

        for (Tuple2<String, ArrayList<ArrayList<String>>> singleResult : result) {
            System.out.println("Results for: " + singleResult._1());
            for (ArrayList<String> singleCluster : singleResult._2()) {
                System.out.println(singleCluster);
            }
            System.out.println("-------------------------------------------------------------------------------------");
        }
    }


}
