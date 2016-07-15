import org.apache.kafka.log4jappender.KafkaLog4jAppender;
import org.apache.log4j.Logger;

public class KafkaLogProducerApp {
	static int i = 0;
	public static void main(String[] args) throws InterruptedException {
		Logger logger = Logger.getLogger(KafkaLogProducerApp.class);
		for(i=0; true; i++){
			logger.info("I am writing to kafka topic and produced " + i);
			Thread.sleep(1000);
		}
	}
}
