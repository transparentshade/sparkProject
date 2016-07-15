import org.apache.kafka.log4jappender.KafkaLog4jAppender;
import org.apache.spark.api.java.JavaRDD;
import org.apache.spark.api.java.JavaSparkContext;
import org.apache.spark.api.java.function.Function;

public class SimpleApp {
	static final String SPARK_HOME = "/home/nb/spark/spark-1.6.2-bin-hadoop2.6";
	public static void main(String[] args) {
		String logFile = "read.md";
		JavaSparkContext sparkContext = new JavaSparkContext("local", "simple app", SPARK_HOME,
				new String[]{"target/SparkProject.0.0.1-SNAPSHOT.jar"});
		JavaRDD<String> logData = sparkContext.textFile(logFile);
		
		long numAs = logData.filter(new Function<String, Boolean>() {
			public Boolean call(String v1) throws Exception {
				System.out.println(v1);
				return v1.contains("a");
			}
		}).count();
		System.out.println("FOund lines with a's " + numAs);
	}
}
