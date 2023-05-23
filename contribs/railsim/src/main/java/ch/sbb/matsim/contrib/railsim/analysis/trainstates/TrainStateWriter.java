/* *********************************************************************** *
 * project: org.matsim.*
 *                                                                         *
 * *********************************************************************** *
 *                                                                         *
 * copyright       : (C) 2023 by the members listed in the COPYING,        *
 *                   LICENSE and WARRANTY file.                            *
 * email           : info at matsim dot org                                *
 *                                                                         *
 * *********************************************************************** *
 *                                                                         *
 *   This program is free software; you can redistribute it and/or modify  *
 *   it under the terms of the GNU General Public License as published by  *
 *   the Free Software Foundation; either version 2 of the License, or     *
 *   (at your option) any later version.                                   *
 *   See also COPYING, LICENSE and WARRANTY file                           *
 *                                                                         *
 * *********************************************************************** */

package ch.sbb.matsim.contrib.railsim.analysis.trainstates;

import ch.sbb.matsim.contrib.railsim.events.RailsimTrainStateEvent;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVPrinter;
import org.matsim.api.core.v01.Coord;
import org.matsim.api.core.v01.network.Link;
import org.matsim.api.core.v01.network.Network;
import org.matsim.core.utils.io.IOUtils;

import java.io.IOException;
import java.io.UncheckedIOException;

public class TrainStateWriter {

	public static void writeCsv(TrainStateAnalysis analysis, Network network, String filename) throws UncheckedIOException {
		String[] header = {"vehicle", "time", "acceleration", "speed", "targetSpeed", "headLink", "headPosition", "headX", "headY", "tailLink", "tailPosition", "tailX", "tailY"};

		try (CSVPrinter csv = new CSVPrinter(IOUtils.getBufferedWriter(filename), CSVFormat.DEFAULT.builder().setHeader(header).build())) {
			for (RailsimTrainStateEvent event : analysis.events) {
				csv.print(event.getVehicleId().toString());
				csv.print(event.getTime());
				csv.print(event.getAcceleration());
				csv.print(event.getSpeed());
				csv.print(event.getTargetSpeed());

				csv.print(event.getHeadLink().toString());
				csv.print(event.getHeadPosition());
				if (network != null) {
					Link link = network.getLinks().get(event.getHeadLink());
					if (link != null) {
						double fraction = event.getHeadPosition() / link.getLength();
						Coord from = link.getFromNode().getCoord();
						Coord to = link.getToNode().getCoord();
						csv.print(from.getX() + (to.getX() - from.getX()) * fraction);
						csv.print(from.getY() + (to.getY() - from.getY()) * fraction);
					}
				} else {
					csv.print("");
					csv.print("");
				}

				csv.print(event.getTailLink().toString());
				csv.print(event.getTailPosition());
				if (network != null) {
					Link link = network.getLinks().get(event.getTailLink());
					if (link != null) {
						double fraction = event.getTailPosition() / link.getLength();
						Coord from = link.getFromNode().getCoord();
						Coord to = link.getToNode().getCoord();
						csv.print(from.getX() + (to.getX() - from.getX()) * fraction);
						csv.print(from.getY() + (to.getY() - from.getY()) * fraction);
					}
				} else {
					csv.print("");
					csv.print("");
				}

				csv.println();
			}
		} catch (IOException e) {
			throw new UncheckedIOException(e);
		}

	}
}
