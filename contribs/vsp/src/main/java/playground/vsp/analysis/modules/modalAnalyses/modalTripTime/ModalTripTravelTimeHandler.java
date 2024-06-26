/* *********************************************************************** *
 * project: org.matsim.*
 *                                                                         *
 * *********************************************************************** *
 *                                                                         *
 * copyright       : (C) 2014 by the members listed in the COPYING,        *
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
package playground.vsp.analysis.modules.modalAnalyses.modalTripTime;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.SortedMap;
import java.util.TreeMap;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.TransportMode;
import org.matsim.api.core.v01.events.ActivityStartEvent;
import org.matsim.api.core.v01.events.PersonArrivalEvent;
import org.matsim.api.core.v01.events.PersonDepartureEvent;
import org.matsim.api.core.v01.events.PersonStuckEvent;
import org.matsim.api.core.v01.events.TransitDriverStartsEvent;
import org.matsim.api.core.v01.events.handler.ActivityStartEventHandler;
import org.matsim.api.core.v01.events.handler.PersonArrivalEventHandler;
import org.matsim.api.core.v01.events.handler.PersonDepartureEventHandler;
import org.matsim.api.core.v01.events.handler.PersonStuckEventHandler;
import org.matsim.api.core.v01.events.handler.TransitDriverStartsEventHandler;
import org.matsim.api.core.v01.population.Person;
import org.matsim.core.gbl.Gbl;
import org.matsim.core.router.StageActivityTypeIdentifier;

/**
 * (1) Handles departure and arrival events and store total travel time of a person and 
 * travel time of each trip of a person segregated by leg mode
 * (2) See followings
 * transit_walk - transit_walk --> walk &
 * transit_walk - pt - transit_walk --> pt &
 * transit_walk - pt - transit_walk - pt - transit_walk --> pt 
 * @author amit
 */

public class ModalTripTravelTimeHandler implements PersonDepartureEventHandler, PersonArrivalEventHandler, PersonStuckEventHandler, 
TransitDriverStartsEventHandler, ActivityStartEventHandler {

	private static final Logger LOGGER = LogManager.getLogger(ModalTripTravelTimeHandler.class);
	private static final int MAX_STUCK_AND_ABORT_WARNINGS = 5;
	private final SortedMap<String, Map<Id<Person>, List<Double>>> mode2PersonId2TravelTimes = new TreeMap<>();
	private final Map<Id<Person>, Double> personId2DepartureTime = new HashMap<>();
	private int warnCount = 0;
	private final List<Id<Person>> transitDriverPersons = new ArrayList<>();
	// agents who first departs with transitWalk and their subsequent modes are stored here until it starts a regular act (home/work/leis/shop)
	private final Map<Id<Person>, List<String>> person2Modes = new HashMap<>();
	private final Map<Id<Person>, Double> personId2ArrivalTime = new HashMap<>();

	public ModalTripTravelTimeHandler() {
		LOGGER.warn("Excluding the departure and arrivals of transit drivers.");
	}

	@Override
	public void reset(int iteration) {
		this.mode2PersonId2TravelTimes.clear();
		this.personId2DepartureTime.clear();
		this.transitDriverPersons.clear();
		this.person2Modes.clear();
	}

	@Override
	public void handleEvent(PersonArrivalEvent event) {
		Id<Person> personId = event.getPersonId();
		String legMode = event.getLegMode();

		if(transitDriverPersons.remove(personId)) {
			// exclude arrivals of transit drivers ;
			return;
		}

		// keep updating the arrival time even if exists
		personId2ArrivalTime.put(event.getPersonId(), event.getTime());
	}

	@Override
	public void handleEvent(PersonDepartureEvent event) {
		Id<Person> personId = event.getPersonId();
		double deartureTime = event.getTime();
		String legMode = event.getLegMode();

		if(transitDriverPersons.contains(personId)) {
			// exclude departures of transit drivers and remove them from arrivals
			return;
		}

		//at this point, it could be main leg (e.g. car/bike) or start of a stage activity (e.g. car/pt interaction)
		List<String> usedModes = person2Modes.getOrDefault(personId, new ArrayList<>());
		usedModes.add(legMode);
		person2Modes.put(personId, usedModes);

		personId2DepartureTime.putIfAbsent(personId, deartureTime);
	}

	/**
	 * @return  trip time for each trip of each person segregated w.r.t. travel modes.
	 */
	public SortedMap<String, Map<Id<Person>, List<Double>>> getLegMode2PesonId2TripTimes (){
		return this.mode2PersonId2TravelTimes;
	}

	@Override
	public void handleEvent(PersonStuckEvent event) {
		this.warnCount++;
		if(this.warnCount <= MAX_STUCK_AND_ABORT_WARNINGS){
			LOGGER.warn("'StuckAndAbort' event is thrown for person "+event.getPersonId()+" on link "+event.getLinkId()+" at time "+event.getTime()+
					". \n Correctness of travel time for such persons can not be guaranteed.");
			if(this.warnCount== MAX_STUCK_AND_ABORT_WARNINGS) LOGGER.warn(Gbl.FUTURE_SUPPRESSED);
		}
	}
	@Override
	public void handleEvent(TransitDriverStartsEvent event) {
		transitDriverPersons.add(event.getDriverId());
	}

	@Override
	public void handleEvent(ActivityStartEvent event) {
		if( person2Modes.containsKey(event.getPersonId()) ) {
			if(! StageActivityTypeIdentifier.isStageActivity(event.getActType()) ) {
				List<String> modes = person2Modes.remove(event.getPersonId());
				String legMode = getMainMode(modes);
				double departureTime = personId2DepartureTime.remove(event.getPersonId());
				double arrivalTime = personId2ArrivalTime.remove(event.getPersonId());
				storeData(event.getPersonId(), legMode, arrivalTime - departureTime);
			} else { 
				// else continue
			}
		} else {
			throw new RuntimeException("Person "+event.getPersonId()+" is not registered.");
		}
	}

	private String getMainMode(List<String> modes){
		if (modes.size()==1) return modes.get(0).equals(TransportMode.transit_walk) ? TransportMode.walk: modes.get(0);
		
		if (modes.contains(TransportMode.pt)) return TransportMode.pt;
		if (modes.contains(TransportMode.car)) return TransportMode.car;
		if (modes.contains(TransportMode.bike)) return TransportMode.bike;
		if (modes.contains(TransportMode.walk)) return TransportMode.walk;
		if (modes.contains(TransportMode.ride)) return TransportMode.ride;
		
		if (modes.contains(TransportMode.transit_walk) || modes.contains(TransportMode.access_walk) || modes.contains(TransportMode.egress_walk)) {
			return TransportMode.walk;
		} 
		
		throw new RuntimeException("Unknown mode(s) "+ modes.toString());
	}

	private void storeData(final Id<Person> personId, final String legMode, final double travelTime){
		Map<Id<Person>, List<Double>> personId2TravelTimes = this.mode2PersonId2TravelTimes.getOrDefault(legMode, new HashMap<>());
		List<Double> travelTimes = personId2TravelTimes.getOrDefault(personId, new ArrayList<>());
		travelTimes.add(travelTime);
		personId2TravelTimes.put(personId, travelTimes);
		this.mode2PersonId2TravelTimes.put(legMode, personId2TravelTimes);
	}
}