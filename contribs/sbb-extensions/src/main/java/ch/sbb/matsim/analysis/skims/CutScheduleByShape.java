package ch.sbb.matsim.analysis.skims;

import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.prep.PreparedGeometry;
import org.locationtech.jts.geom.prep.PreparedGeometryFactory;
import org.matsim.api.core.v01.Scenario;
import org.matsim.core.config.ConfigUtils;
import org.matsim.core.scenario.ScenarioUtils;
import org.matsim.core.utils.geometry.GeometryUtils;
import org.matsim.core.utils.gis.ShapeFileReader;
import org.matsim.pt.transitSchedule.TransitScheduleFactoryImpl;
import org.matsim.pt.transitSchedule.TransitScheduleWriterV2;
import org.matsim.pt.transitSchedule.api.*;
import org.matsim.pt.utils.CreateVehiclesForSchedule;
import org.matsim.vehicles.VehicleUtils;
import org.matsim.vehicles.VehicleWriterV1;
import org.matsim.vehicles.Vehicles;
import org.opengis.feature.simple.SimpleFeature;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

public class CutScheduleByShape {

    public static void main(String[] args) {

        String service = args[0];

        final Collection<SimpleFeature> features = ShapeFileReader.getAllFeatures("./input/gis/studyAreaResidentsBoundary.shp");
        final SimpleFeature feature = features.iterator().next();
        final Geometry initialGeometry = (Geometry) feature.getDefaultGeometry();
        PreparedGeometry geometry = PreparedGeometryFactory.prepare(initialGeometry);

        String scheduleFile = "./output/" + service + "/schedule.xml";
        String newScheduleFile = "./output/" + service + "/scheduleMuc.xml";
        String newVehicleFile = "./output/" + service + "/vehiclesMuc.xml";

        Scenario scenario = ScenarioUtils.createScenario(ConfigUtils.createConfig());
        new TransitScheduleReader(scenario).readFile(scheduleFile);
        final TransitSchedule originalSchedule = scenario.getTransitSchedule();
        final TransitSchedule subsetSchedule = new TransitScheduleFactoryImpl().createTransitSchedule();

        List<TransitStopFacility> stopsInStudyArea = new ArrayList<>();

        //add all stops from first schedule if within study area
        for (TransitStopFacility stop : originalSchedule.getFacilities().values()) {
            if (geometry.contains(GeometryUtils.createGeotoolsPoint(stop.getCoord()))) {
                stopsInStudyArea.add(stop);

            }
        }

        for (TransitLine line : originalSchedule.getTransitLines().values()) {
            TransitLine lineCopy = subsetSchedule.getFactory().createTransitLine(line.getId());
            for (TransitRoute route : line.getRoutes().values()) {
                final boolean anyStopInside = route.getStops().stream().anyMatch(stop -> stopsInStudyArea.contains(stop.getStopFacility()));
                if (anyStopInside) {
                    lineCopy.addRoute(route);
                    route.getStops().stream().map(TransitRouteStop::getStopFacility).forEach(stop -> addStofIfNonExistent(stop, subsetSchedule));
                }
            }
            if (!lineCopy.getRoutes().isEmpty()) {
                subsetSchedule.addTransitLine(lineCopy);
            }
        }
        new TransitScheduleWriterV2(subsetSchedule).write(newScheduleFile);


        Vehicles vehicles = VehicleUtils.createVehiclesContainer();
        new CreateVehiclesForSchedule(subsetSchedule, vehicles).run();
        new VehicleWriterV1(vehicles).writeFile(newVehicleFile);


    }

    public static void addStofIfNonExistent(TransitStopFacility stop, TransitSchedule subsetSchedule) {
        if (!subsetSchedule.getFacilities().keySet().contains(stop.getId())) {
            subsetSchedule.addStopFacility(stop);
        }
    }


}
