/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

import com.google.common.io.Closeables;

import org.apache.log4j.Level;
import org.apache.log4j.Logger;
import org.apache.spark.SparkConf;
import org.apache.spark.api.java.JavaPairRDD;
import org.apache.spark.api.java.function.FlatMapFunction;
import org.apache.spark.api.java.function.Function2;
import org.apache.spark.api.java.function.PairFunction;
import org.apache.spark.storage.StorageLevel;
import org.apache.spark.streaming.Duration;
import org.apache.spark.streaming.Time;
import org.apache.spark.streaming.api.java.JavaDStream;
import org.apache.spark.streaming.api.java.JavaPairDStream;
import org.apache.spark.streaming.api.java.JavaReceiverInputDStream;
import org.apache.spark.streaming.api.java.JavaStreamingContext;
import org.apache.spark.streaming.receiver.Receiver;
import scala.Tuple2;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.ConnectException;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Iterator;
import java.util.regex.Pattern;

/**
 * Custom Receiver that receives data over a socket. Received bytes is
 * interpreted as text and \n delimited lines are considered as records. They
 * are then counted and printed.
 *
 * Usage: JavaCustomReceiver <master> <hostname> <port> <master> is the Spark
 * master URL. In local mode, <master> should be 'local[n]' with n > 1.
 * <hostname> and <port> of the TCP server that Spark Streaming would connect to
 * receive data.
 *
 * To run this on your local machine, you need to first run a Netcat server `$
 * nc -lk 9999` and then run the example `$ bin/run-example
 * org.apache.spark.examples.streaming.JavaCustomReceiver localhost 9999`
 */

public class JavaCustomReceiver extends Receiver<String> {
	private static final Pattern SPACE = Pattern.compile(" ");
	static final String SPARK_HOME = "/home/nb/spark/spark-1.6.2-bin-hadoop2.6";
	static final String SPARK_MASTER = "spark://nb-virtual-machine:7077";
	private static Logger logger = Logger.getLogger(JavaCustomReceiver.class);

	public static void main(String[] args) throws Exception {
		args = new String[2];
		args[0] = "localhost";
		args[1] = "12344";
		// StreamingExamples.setStreamingLogLevels();
		logger.error("hi error");
		System.out.println("logging started atleast");
		// Create the context with a 1 second batch size
		SparkConf sparkConf = new SparkConf().setAppName("JavaCustomReceiver").setMaster(SPARK_MASTER)
				.setSparkHome("local")
				.setJars(new String[] { "target/SparkProject.0.0.1-SNAPSHOT.jar" });
		JavaStreamingContext ssc = new JavaStreamingContext(sparkConf, new Duration(1000));

		JavaReceiverInputDStream<String> lines = ssc.receiverStream(new JavaCustomReceiver("localhost", 12344));
		System.out.println("Received Lines: " + lines.toString());

		JavaDStream<String> words = lines.flatMap(new FlatMapFunction<String, String>() {
			public Iterable<String> call(String x) {
				System.out.println("Flat map processsing " + x);
				return Arrays.asList(x.split(" "));
			}
		});
		JavaPairDStream<String, Integer> pairs = words.mapToPair(new PairFunction<String, String, Integer>() {
			public Tuple2<String, Integer> call(String s) {
				return new Tuple2<String, Integer>(s, 1);
			}
		});

		
		JavaPairDStream<String, Integer> wordCounts = pairs.reduceByKey(new Function2<Integer, Integer, Integer>() {
			public Integer call(Integer i1, Integer i2) {
				return i1 + i2;
			}
		});

		wordCounts.print(1000);
		ssc.start();
		ssc.awaitTermination(1000*10);

	}

	// ============= Receiver code that receives data over a socket
	// ==============

	String host = null;
	int port = -1;

	public JavaCustomReceiver(String host_, int port_) {
		super(StorageLevel.MEMORY_AND_DISK_2());
		host = host_;
		port = port_;
	}

	public void onStart() {
		// Start the thread that receives data over a connection
		new Thread() {
			@Override
			public void run() {
				receive();
			}
		}.start();
	}

	public void onStop() {
		// There is nothing much to do as the thread calling receive()
		// is designed to stop by itself isStopped() returns false
	}

	/** Create a socket connection and receive data until receiver is stopped */
	private void receive() {
		try {
			Socket socket = null;
			BufferedReader reader = null;
			String userInput = null;
			try {
				// connect to the server
				socket = new Socket(host, port);
				reader = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
				// Until stopped or connection broken continue reading
				while (!isStopped() && (userInput = reader.readLine()) != null) {
					System.out.println("Received data '" + userInput + "'");
					store(userInput);
				}
			} finally {
				Closeables.close(reader, /* swallowIOException = */ true);
				Closeables.close(socket, /* swallowIOException = */ true);
			}
			// Restart in an attempt to connect again when server is active
			// again
			restart("Trying to connect again");
		} catch (ConnectException ce) {
			// restart if could not connect to server
			restart("Could not connect", ce);
		} catch (Throwable t) {
			restart("Error receiving data", t);
		}
	}
}