package ch.sbb.matsim.analysis.skims;

import com.fasterxml.jackson.databind.JsonSerializer;
import org.matsim.api.core.v01.Coord;
import org.matsim.core.config.Config;
import org.matsim.core.config.ConfigUtils;
import org.matsim.core.utils.misc.Time;

import java.io.IOException;
import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public class RunSkimMatrices {

		public static void main(String[] args) throws IOException {
			// Define configuration and input data paths

			String networkFilename = "\\\\nas.ads.mwn.de\\tubv\\mob\\indiv\\joanna\\munich_skim\\muc/studyNetworkDense_inclPT.xml";

			// \\nas.ads.mwn.de\tubv\mob\indiv\joanna\munich_skim\muc/mapped_schedule_deutschland_20240225.xml
			String transitScheduleFilename = "\\\\nas.ads.mwn.de\\tubv\\mob\\indiv\\joanna\\munich_skim\\muc/mapped_schedule_deutschland_20240225.xml";
			String samplePointsFilename = "C:/models/skim_generation/muc/sample_points_reset.csv";
			String zonesShapeFilename = "\\\\nas.ads.mwn.de\\tubv\\mob\\projects\\2021\\tengos\\data\\mitoMunich_shapefiles\\zone/zonesNew.shp";
			String eventsFilename= null;
			String zonesIdAttributeName = "id";

			String outputDirectory = "C:/models/skim_generation/muc/output_folder_test";
			int numberOfThreads = 8;
			int numberOfPointsPerZone = 1;

			String[] timesCarStr ={};
			String[] timesPtStr = {"11:00", "11:10"};

			double[] timesCar = new double[timesCarStr.length];
			for (int i = 0; i < timesCarStr.length; i++) {
				timesCar[i] = Time.parseTime(timesCarStr[i]);
			}

			double[] timesPt = new double[timesPtStr.length];
			for (int i = 0; i < timesPtStr.length; i++) {
				timesPt[i] = Time.parseTime(timesPtStr[i]);
			}

			Config config = ConfigUtils.createConfig();
			Random r = new Random(4711);

			// Initialize CalculateSkimMatrices with the output directory and number of threads
			CalculateSkimMatrices skims = new CalculateSkimMatrices(outputDirectory, zonesIdAttributeName, outputDirectory,numberOfThreads);

   			// Load sampling points from an existing file instead of calculating them

			//skims.loadSamplingPointsFromFile(samplePointsFilename);

			// Example place where Coord objects are used to find the nearest node or link


			skims.calculateSamplingPointsPerZoneFromNetwork(networkFilename, numberOfPointsPerZone,zonesShapeFilename,zonesIdAttributeName, r);

			// Calculate and write network matrices
			if (timesCar.length > 0) {
				skims.calculateAndWriteNetworkMatrices(networkFilename, eventsFilename, timesCar, config, "", link -> true);
			}

			// Calculate and write PT matrices
			if (timesPt.length > 0) {
				skims.calculateAndWritePTMatrices(networkFilename, transitScheduleFilename, timesPt[0], timesPt[1], config, "", (line, route) -> route.getTransportMode().equals("train"));
			}

		}


}
