package ch.sbb.matsim.analysis.skims;

import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.Scenario;
import org.matsim.core.config.ConfigUtils;
import org.matsim.core.scenario.ScenarioUtils;
import org.matsim.pt.transitSchedule.TransitScheduleFactoryImpl;
import org.matsim.pt.transitSchedule.api.*;
import org.matsim.vehicles.*;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class MergeSchedules {

    public static void main(String[] args) {

        /**
         * initialize n scenarios
         */
        Scenario scenarioOne = ScenarioUtils.createScenario(ConfigUtils.createConfig());
        Scenario scenarioTwo = ScenarioUtils.createScenario(ConfigUtils.createConfig());
//        Scenario scenarioThree = ScenarioUtils.createScenario(ConfigUtils.createConfig());
        //Scenario scenarioFour= ScenarioUtils.createScenario(ConfigUtils.createConfig());

        /*
        Add here the n schedules to merge (if more or less, some minor changes in the code may be required)
         */
        new TransitScheduleReader(scenarioOne).readFile("./output/rb/schedule.xml");
        new TransitScheduleReader(scenarioTwo).readFile("./output/db/schedule.xml");
//        new TransitScheduleReader(scenarioThree).readFile("./output/blablabus/scheduleModeLdBus.xml");
        //new TransitScheduleReader(scenarioFour).readFile("./output/flixbus/schedule.xml");
        TransitSchedule scheduleOne = scenarioOne.getTransitSchedule();
        TransitSchedule scheduleTwo = scenarioTwo.getTransitSchedule();
//        TransitSchedule scheduleThree = scenarioThree.getTransitSchedule();
        //TransitSchedule scheduleFour = scenarioFour.getTransitSchedule();

        final TransitSchedule mergedSchedule = new TransitScheduleFactoryImpl().createTransitSchedule();

        /**
         * Define suffixes to the schedule elements to prevent duplicate ids
         */
        scheduleOne = addSuffixToScheduleElements(scheduleOne, "_rb");
        scheduleTwo = addSuffixToScheduleElements(scheduleTwo, "_ld");
//        scheduleThree = addSuffixToScheduleElements(scheduleThree, "_blablabus");
        //scheduleFour = addSuffixToScheduleElements(scheduleFour, "_flixbus");

        List<TransitSchedule> transitSchedules = new ArrayList<>();

        transitSchedules.add(scheduleOne);
        transitSchedules.add(scheduleTwo);
//        transitSchedules.add(scheduleThree);
        //transitSchedules.add(scheduleFour);

        for (TransitSchedule schedule : transitSchedules){
            for (TransitStopFacility stop : schedule.getFacilities().values()) {
                mergedSchedule.addStopFacility(stop);
            }

            for (TransitLine line : schedule.getTransitLines().values()) {
                mergedSchedule.addTransitLine(line);
            }
        }

        /**
         * Define output files for schedule and vehicles
         */
        new TransitScheduleWriter(mergedSchedule).writeFile("./output/ld_rb/schedule_germany.xml");

        Vehicles vehicles = VehicleUtils.createVehiclesContainer();
        createVehiclesForSchedule(mergedSchedule, vehicles);
        new VehicleWriterV1(vehicles).writeFile("./output/ld_rb/vehicles_germany.xml");


    }

    public static void createVehiclesForSchedule(TransitSchedule schedule, Vehicles vehicles) {
        VehiclesFactory vb = vehicles.getFactory();
        Map<String, VehicleType> vehicleTypeMap = new HashMap<>();

        //todo read from vehicle xml file can be better
        {
            VehicleType vehicleType = vb.createVehicleType(Id.create("default", VehicleType.class));
            //VehicleCapacity capacity = new VehicleCapacityImpl();
            vehicleType.getCapacity().setSeats(Integer.valueOf(50));
            vehicleType.getCapacity().setStandingRoom(Integer.valueOf(50));
            //vehicleType.setCapacity(capacity);
            vehicles.addVehicleType(vehicleType);
            vehicleTypeMap.put(vehicleType.getId().toString(), vehicleType);
        }

        {
            VehicleType vehicleType = vb.createVehicleType(Id.create("bus", VehicleType.class));
            //VehicleCapacity capacity = new VehicleCapacityImpl();
            vehicleType.getCapacity().setSeats(Integer.valueOf(100));
            vehicleType.getCapacity().setStandingRoom(Integer.valueOf(0));
            //vehicleType.setCapacity(capacity);
            vehicles.addVehicleType(vehicleType);
            vehicleTypeMap.put(vehicleType.getId().toString(), vehicleType);
        }

        {
            VehicleType vehicleType = vb.createVehicleType(Id.create("tram", VehicleType.class));
            //VehicleCapacity capacity = new VehicleCapacityImpl();
            vehicleType.getCapacity().setSeats(Integer.valueOf(100));
            vehicleType.getCapacity().setStandingRoom(Integer.valueOf(100));
            //vehicleType.setCapacity(capacity);
            vehicles.addVehicleType(vehicleType);
            vehicleTypeMap.put(vehicleType.getId().toString(), vehicleType);
        }


        {
            VehicleType vehicleType = vb.createVehicleType(Id.create("subway", VehicleType.class));
            //VehicleCapacity capacity = new VehicleCapacityImpl();
            vehicleType.getCapacity().setSeats(Integer.valueOf(300));
            vehicleType.getCapacity().setStandingRoom(Integer.valueOf(300));
           //vehicleType.setCapacity(capacity);
            vehicles.addVehicleType(vehicleType);
            vehicleTypeMap.put(vehicleType.getId().toString(), vehicleType);
        }

        {
            VehicleType vehicleType = vb.createVehicleType(Id.create("rail", VehicleType.class));
            //VehicleCapacity capacity = new VehicleCapacityImpl();
            vehicleType.getCapacity().setSeats(Integer.valueOf(300));
            vehicleType.getCapacity().setStandingRoom(Integer.valueOf(0));
            //vehicleType.setCapacity(capacity);
            vehicles.addVehicleType(vehicleType);
            vehicleTypeMap.put(vehicleType.getId().toString(), vehicleType);
        }

        for (TransitLine line : schedule.getTransitLines().values()) {
            System.out.println(line.getId());
            for (TransitRoute route : line.getRoutes().values()) {
                System.out.println(route.getStops());
                for (Departure departure : route.getDepartures().values()) {
                    System.out.println(route.getDepartures().values().toString());
                    Id<Vehicle> vehicleId = departure.getVehicleId();
                    VehicleType vehicleType = getVehicleTypeFromId(vehicleId, vehicleTypeMap);
                    Vehicle veh = vb.createVehicle(vehicleId, vehicleType);
                    vehicles.addVehicle(veh);
                    departure.setVehicleId(veh.getId());
                }
            }
        }



    }

    private static VehicleType getVehicleTypeFromId(Id<Vehicle> vehicleId, Map<String, VehicleType> vehicleTypeMap) {
        String string = vehicleId.toString().toLowerCase();
        if (string.contains("bus")){
            return vehicleTypeMap.get("bus");
        } else if (string.contains("tram")){
            return vehicleTypeMap.get("tram");
        } else if (string.contains("subway")){
            return vehicleTypeMap.get("subway");
        } else if (string.contains("rail")){
            return vehicleTypeMap.get("rail");
        } else {
            System.out.println("A default vehicle type is selected!");
            return vehicleTypeMap.get("default");
        }
    }

    private static TransitSchedule addSuffixToScheduleElements(TransitSchedule schedule, String suffix) {
        TransitScheduleFactory factory = schedule.getFactory();
        TransitSchedule newSchedule = factory.createTransitSchedule();
        for (TransitStopFacility stop : schedule.getFacilities().values()) {
            Id<TransitStopFacility> newStopId = Id.create(stop.getId() + suffix, TransitStopFacility.class);
            TransitStopFacility newStop = factory.createTransitStopFacility(newStopId, stop.getCoord(), stop.getIsBlockingLane());
            newSchedule.addStopFacility(newStop);
        }

        for (TransitLine line : schedule.getTransitLines().values()) {
            Id<TransitLine> newLineId = Id.create(line.getId() + suffix, TransitLine.class);
            TransitLine newLine = factory.createTransitLine(newLineId);

            for (TransitRoute route : line.getRoutes().values()) {

                List<TransitRouteStop> newStops = new ArrayList<>();

                for (TransitRouteStop routeStop : route.getStops()) {
                    Id<TransitStopFacility> newStopFacilityId = Id.create(routeStop.getStopFacility().getId() + suffix, TransitStopFacility.class);
                    TransitStopFacility newStopFacility = newSchedule.getFacilities().get(newStopFacilityId);
                    TransitRouteStop newRouteStop = factory.createTransitRouteStop(newStopFacility, routeStop.getArrivalOffset(), routeStop.getDepartureOffset());
                    newStops.add(newRouteStop);
                }


                Id<TransitRoute> newRouteId = Id.create(route.getId() + suffix, TransitRoute.class);
                TransitRoute newRoute = factory.createTransitRoute(newRouteId, null, newStops, route.getTransportMode());

                for (Departure departure : route.getDepartures().values()) {
                    departure.getDepartureTime();
                    Id<Departure> newDepartureId = Id.create(departure.getId() + suffix, Departure.class);
                    Id<Vehicle> newVehicleId = Id.create(departure.getVehicleId() + suffix, Vehicle.class);
                    Departure newDeparture = factory.createDeparture(newDepartureId, departure.getDepartureTime());
                    newDeparture.setVehicleId(newVehicleId);
                    newRoute.addDeparture(newDeparture);
                }

                newLine.addRoute(newRoute);
            }
            newSchedule.addTransitLine(newLine);
        }
        return newSchedule;
    }


}

