package query2;

import org.apache.spark.SparkConf;
import org.apache.spark.api.java.JavaPairRDD;
import org.apache.spark.api.java.JavaRDD;
import org.apache.spark.api.java.JavaSparkContext;
import scala.Tuple2;
import utility.ContinentDecoder;
import utility.IOUtility;
import utility.QueryUtility;

import java.util.*;

public class Query2Main {

    public static void main(String[] args) {

        SparkConf sparkConf = new SparkConf()
                .setMaster("local")
                .setAppName("Query 2");
        JavaSparkContext sparkContext = new JavaSparkContext(sparkConf);
        sparkContext.setLogLevel("ERROR");

        JavaRDD<String> dataset2 = sparkContext.textFile(IOUtility.getDS2());

        final long startTime = System.currentTimeMillis();

        JavaRDD<Tuple2<Double, CountryDataQuery2>> data = Query2Preprocessing.preprocessData(dataset2);

        JavaPairRDD<String, List<Double>> continents = data.flatMapToPair(
                        tuple -> {
                            ArrayList<Tuple2<String, List<Double>>> result = new ArrayList<>();
                            String keyHeader = ContinentDecoder.detectContinent(tuple._2().getCoordinate()) + " - ";

                            // create an RDD row for every week
                            Calendar currentDate = QueryUtility.getDataset2StartDate();
                            // being sequential and taking care of just two consecutive values works on different years
                            // too
                            int currentWeekNumber = currentDate.get(Calendar.WEEK_OF_YEAR);
                            List<Double> weeklyValues = new ArrayList<>();
                            for (Double value : tuple._2().getCovidConfirmedCases()) {
                                if (currentWeekNumber != currentDate.get(Calendar.WEEK_OF_YEAR)) {
                                    // back to before week switch
                                    currentDate.add(Calendar.WEEK_OF_YEAR, -1);
                                    // add result with current date
                                    result.add(new Tuple2<>(keyHeader +
                                            QueryUtility.getFirstDayOfTheWeek(currentDate.get(Calendar.WEEK_OF_YEAR),
                                                    currentDate.get(Calendar.YEAR)), weeklyValues));
                                    // reswitch week
                                    currentDate.add(Calendar.WEEK_OF_YEAR, 1);
                                    // reinitialize current week structure
                                    weeklyValues = new ArrayList<>();
                                    // update current week number
                                    currentWeekNumber = currentDate.get(Calendar.WEEK_OF_YEAR);
                                }
                                weeklyValues.add(value);
                                currentDate.add(Calendar.DATE, 1);
                            }
                            // end of data, add last batch if present
                            if (!weeklyValues.isEmpty()) {
                                if (weeklyValues.size() == 7) {
                                    // the week is completed so the current date points to the next monday,
                                    // return to the right week
                                    currentDate.add(Calendar.DATE, -1);
                                }
                                result.add(new Tuple2<>(keyHeader +
                                        QueryUtility.getFirstDayOfTheWeek(currentDate.get(Calendar.WEEK_OF_YEAR),
                                                currentDate.get(Calendar.YEAR)), weeklyValues));

                            }
                            return result.iterator();
                        }
                ).reduceByKey(
                        (x, y) -> {
                            // same x and y list length due to dataset update rules
                            // sum day-by-day data for each continent
                            List<Double> sum = new ArrayList<>();
                            for (int i = 0; i < x.size(); i++) {
                                sum.add(x.get(i) + y.get(i));
                            }
                            return sum;
                        }
                );

        JavaPairRDD<String, List<Double>> statistics = continents.mapToPair(
                        tuple -> {
                            double weeklyMean = 0.0;
                            double weeklyStdDev = 0.0;

                            int weekLength = tuple._2().size();

                            // compute max and min
                            double weeklyMax = Collections.max(tuple._2());
                            double weeklyMin = Collections.min(tuple._2());

                            // compute mean
                            for (Double value : tuple._2()) {
                                weeklyMean += value;
                            }
                            weeklyMean = weeklyMean / weekLength;

                            // compute standard deviation
                            for (Double value : tuple._2()) {
                                weeklyStdDev += Math.pow((value - weeklyMean), 2);
                            }
                            weeklyStdDev = Math.sqrt(weeklyStdDev / (weekLength - 1));

                            List<Double> result = Arrays.asList(weeklyMean, weeklyStdDev, weeklyMin, weeklyMax);

                            return new Tuple2<>(tuple._1(), result);
                        }
                );

        JavaPairRDD<String, List<Double>> orderedStatistics = statistics.sortByKey(true).cache();

        // without console printing result this line is not needed, although it was added for benchmark purposes
        List<Tuple2<String, List<Double>>> finalResult = orderedStatistics.collect();
        // uncomment the next line to print result on console
        //printResult(finalResult);

        IOUtility.printTime(System.currentTimeMillis() - startTime);

        IOUtility.writeRDDToHdfs(IOUtility.getOutputPathQuery2(), orderedStatistics);

        sparkContext.close();
    }

    private static void printResult(List<Tuple2<String, List<Double>>> orderedResult) {

        System.out.println("Index\tWeek Start Day\t\t\tMean\tStandard Deviation\tMinimum\tMaximum");
        int i = 1;
        for (Tuple2<String, List<Double>> element : orderedResult) {

            System.out.println("-------------------------------------------------------------------------------------");

            System.out.printf("%2d) %s:\t\t%f\t\t%f\t\t%f\t\t%f\n",
                    i, element._1(), element._2().get(0), element._2().get(1), element._2().get(2), element._2().get(3));

            System.out.println("-------------------------------------------------------------------------------------");
            i++;
        }
    }
}
