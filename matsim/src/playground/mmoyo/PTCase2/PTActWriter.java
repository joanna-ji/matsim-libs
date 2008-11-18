package playground.mmoyo.PTCase2;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import org.matsim.basic.v01.IdImpl;
import org.matsim.config.Config;
import org.matsim.gbl.Gbl;
import org.matsim.network.Link;
import org.matsim.population.Act;
import org.matsim.population.Leg;
import org.matsim.population.Person;
import org.matsim.population.Plan;
import org.matsim.population.Population;
import org.matsim.population.PopulationReaderMatsimV4;
import org.matsim.population.PopulationWriter;
import org.matsim.population.Route;
import org.matsim.utils.geometry.Coord;
import org.matsim.utils.geometry.CoordImpl;
import org.matsim.router.Dijkstra;
import playground.mmoyo.PTRouter.PTNode;
import playground.mmoyo.PTRouter.PTNProximity;

public class PTActWriter {
	private final Population population;
	private PTOb pt;
	private PTNProximity ptnProximity;
	private PTTravelCost ptTravelCost;
	private PTTravelTime ptTravelTime;
	private Dijkstra dijkstra;

	private PTNode ptNode1;
	private PTNode ptNode2;
	
	public PTActWriter(PTOb ptOb){
		this.pt = ptOb;
		Config config = new org.matsim.config.Config();
		config = Gbl.createConfig(new String[]{pt.getConfig(), "http://www.matsim.org/files/dtd/plans_v4.dtd"});
		Gbl.setConfig(config);
		Gbl.getWorld().setNetworkLayer(pt.getPtNetworkLayer());

		this.ptTravelTime =new PTTravelTime(pt.getPtTimeTable());
		this.ptnProximity= new PTNProximity(this.pt.getPtNetworkLayer()); 
		this.dijkstra = new Dijkstra(this.pt.getPtNetworkLayer(), ptTravelCost, ptTravelTime);
		this.population = new org.matsim.population.Population(false);
		PopulationReaderMatsimV4 plansReader = new org.matsim.population.PopulationReaderMatsimV4(this.population);
		plansReader.readFile(pt.getPlansFile());
	}
	
	public void writePTActsLegs(){
		Population newPopulation = new org.matsim.population.Population(false);
		
		int x=0;
		for (Person person: this.population.getPersons().values()) {
			//Person person = population.getPerson("1005733");
			System.out.println(x + " id:" + person.getId());
			Plan plan = person.getPlans().get(0);
			
			boolean val =false;
			Act lastAct = null;
			Act thisAct= null;
			int legNum=0;
			
			Plan newPlan = new Plan(person);
			for (Iterator iter= plan.getIteratorAct(); iter.hasNext();) {
		    	thisAct= (Act)iter.next();
				
		    	if (val) {
					Coord lastActCoord = lastAct.getCoord();
		    		Coord actCoord = thisAct.getCoord();

		    		int distToWalk= distToWalk(person.getAge());
		    		Route route = this.pt.getPtRouter2().forceRoute(lastActCoord, actCoord, lastAct.getEndTime(), distToWalk);
		    		if(route!=null){
		    			if (route.getRoute().size()>2){
		    				createWlinks(lastActCoord, route, actCoord);
		    				legNum= insertLegActs(route, lastAct.getEndTime(), legNum, newPlan);
		    				removeWlinks();
		    			}else{     // if router didn't find a PT connection then walk
		    				newPlan.addLeg(walkLeg(legNum++, lastAct,thisAct));
		    			}//legRoute.getRoute().size()>2
		    		}else{
		    			newPlan.addLeg(walkLeg(legNum++, lastAct,thisAct));
		    		}//if(legRoute!=null)

				}//if val
				thisAct.setLink(this.pt.getPtNetworkLayer().getNearestLink(thisAct.getCoord()));
				newPlan.addAct(thisAct);
				lastAct = thisAct;
				val=true;
			}
			
			person.exchangeSelectedPlan(newPlan, true);
			person.removeUnselectedPlans();
			newPopulation.addPerson(person);
			x++;
			
		}//for person
	
		//Write outplan XML
		System.out.println("writing output plan file...");
		Gbl.getConfig().plans().setOutputFile(this.pt.getOutPutFile());
		Gbl.getConfig().plans().setOutputVersion("v4");
		new PopulationWriter(newPopulation).write();
		System.out.println("Done");
		
		/*
		System.out.println("writing pt network...");
		new NetworkWriter(this.network).write();
		System.out.println("done.");
		*/
		
	}//createPTActs

	
	private void createWlinks(Coord coord1, Route route, Coord coord2){
		ptNode1= this.pt.getPtNetworkFactory().CreateWalkingNode(pt.getPtNetworkLayer(), new IdImpl("w1"), coord1);
		ptNode2= this.pt.getPtNetworkFactory().CreateWalkingNode(pt.getPtNetworkLayer(), new IdImpl("w2"), coord2);
		route.getRoute().add(0, ptNode1);
		route.getRoute().add(ptNode2);
		this.pt.getPtNetworkFactory().createWalkingLink(this.pt.getPtNetworkLayer(), "linkW1", ptNode1 , (PTNode)route.getRoute().get(1), "Walking");
		this.pt.getPtNetworkFactory().createWalkingLink(this.pt.getPtNetworkLayer(), "linkW2", (PTNode)route.getRoute().get(route.getRoute().size()-2) , ptNode2, "Walking");
	}
	
	private void removeWlinks(){
		pt.getPtNetworkLayer().removeLink(pt.getPtNetworkLayer().getLink("linkW1"));
		pt.getPtNetworkLayer().removeLink(pt.getPtNetworkLayer().getLink("linkW2"));
		pt.getPtNetworkLayer().removeNode(ptNode1);
		pt.getPtNetworkLayer().removeNode(ptNode2);
	}	
	
	//TODO: Check this
	private int distToWalk(int personAge){
		int distance=0;
		if (personAge>=60)distance=300; 
		if (personAge>=40 || personAge<60)distance=400;
		if (personAge>=18 || personAge<40)distance=800;
		if (personAge<18)distance=300;
		return distance;
	}	

	public Route forceRoute(Coord coord1, Coord coord2, double time, int distToWalk){
		Route route=null;
		while (route==null && distToWalk<1300){
			route= findRoute(coord1, coord2, time, distToWalk);
			distToWalk= distToWalk+50;
		}
		return route;
	}
		
	public Route findRoute(Coord coord1, Coord coord2, double time, int distToWalk){
		PTNode[] NearStops1=  ptnProximity.getNearestBusStops(coord1, distToWalk);
		PTNode[] NearStops2= ptnProximity.getNearestBusStops(coord2, distToWalk);
		ptNode1= this.pt.getPtNetworkFactory().CreateWalkingNode(this.pt.getPtNetworkLayer(), new IdImpl("W1"), coord1);
		ptNode2=this.pt.getPtNetworkFactory().CreateWalkingNode(this.pt.getPtNetworkLayer(), new IdImpl("W2"), coord2);
		List <IdImpl> walkingLinkList1 = this.pt.getPtNetworkFactory().CreateWalkingLinks(this.pt.getPtNetworkLayer(), ptNode1, NearStops1, true);
		List <IdImpl> walkingLinkList2 = this.pt.getPtNetworkFactory().CreateWalkingLinks(this.pt.getPtNetworkLayer(), ptNode2, NearStops2, false);
		
		Route route = dijkstra.calcLeastCostPath(ptNode1, ptNode2, time);

		this.pt.getPtNetworkFactory().removeWalkinkLinks(this.pt.getPtNetworkLayer(), walkingLinkList1);
		this.pt.getPtNetworkFactory().removeWalkinkLinks(this.pt.getPtNetworkLayer(), walkingLinkList2);
		this.pt.getPtNetworkLayer().removeNode(this.pt.getPtNetworkLayer().getNode("W1"));
		this.pt.getPtNetworkLayer().removeNode(this.pt.getPtNetworkLayer().getNode("W2"));
		this.pt.getPtNetworkLayer().removeNode(ptNode1);
		this.pt.getPtNetworkLayer().removeNode(ptNode2);
	
		return route;
	}
	
	private Leg walkLeg(int legNum, Act act1, Act act2){
		double walkDistance = coordDistance(act1.getCoord(), act2.getCoord());
		double walkTravelTime = walkTravelTime(walkDistance); 
		double depTime = act1.getEndTime();
		double arrTime = depTime + walkTravelTime;
		double distance= coordDistance(act1.getCoord(), act2.getCoord());
		return newPTLeg(legNum, Leg.Mode.walk, new ArrayList<Link>(), distance, depTime, walkTravelTime, arrTime);
	}
	
	private double linkDistance(Link link){
		return coordDistance(link.getFromNode().getCoord(), link.getToNode().getCoord());
	}
	
	private double coordDistance(Coord coord1, Coord coord2){
		CoordImpl coordImpl = new CoordImpl(coord1);
		return coordImpl.calcDistance(coord2); //the swiss coordinate system with 6 digit means meters
	}
	
	private double walkTravelTime(double distance){
		final double WALKING_SPEED = 0.9; //      4 km/h  human speed 
		return distance * WALKING_SPEED;
	}
	
	public int insertLegActs(Route route, double depTime, int legNum, Plan newPlan){
		Link[] routeLinks = route.getLinkRoute();
		List<Link> legRouteLinks = new ArrayList<Link>();
		double accumulatedTime=depTime;
		double arrTime;
		double legTravelTime=0;
		double legDistance=0;
		double linkTravelTime=0;
		double linkDistance=0;
		int linkCounter=1;
		boolean first=true;
		String lastLinkType="";
		
		for(Link link: routeLinks){
			linkTravelTime=ptTravelTime.getLinkTravelTime(link,accumulatedTime)*60;
			linkDistance = linkDistance(link);
			
			if (link.getType().equals("Standard")){
				if (first){ //first PTAct: getting on
					newPlan.addAct(newPTAct("Wait PT Vehicle", link.getFromNode().getCoord(), link, accumulatedTime , linkTravelTime, accumulatedTime + linkTravelTime));	
					accumulatedTime =accumulatedTime+ linkTravelTime;
					first=false;
				}
				if (!lastLinkType.equals("Standard")){  //reset to start a new ptLeg
					legRouteLinks.clear();
					depTime=accumulatedTime;
					legTravelTime=0;
					legDistance=0;
				}
				legTravelTime=legTravelTime+(linkTravelTime); 
				legRouteLinks.add(link);
				if(linkCounter == (routeLinks.length-1)){ //Last PTAct: getting off
					arrTime= depTime+ legTravelTime;
					legDistance=legDistance + linkDistance;  
					newPlan.addLeg(newPTLeg(legNum++, Leg.Mode.pt, legRouteLinks, legDistance, arrTime-legTravelTime, legTravelTime, arrTime));	
					newPlan.addAct(newPTAct("Getting off the PT Vehicle", link.getFromNode().getCoord(), link, arrTime, 0, arrTime));
				}
				
			
			}else if(link.getType().equals("Transfer")){  //add the PTleg and a Transfer Act
				if (lastLinkType.equals("Standard")){ 
					arrTime= depTime+ legTravelTime;
					legDistance= legDistance+ linkDistance;
					newPlan.addLeg(newPTLeg(legNum++, Leg.Mode.pt, legRouteLinks, legDistance, depTime, legTravelTime, arrTime));
					newPlan.addAct(newPTAct("Wait PT Vehicle", link.getFromNode().getCoord(), link, accumulatedTime, linkTravelTime, accumulatedTime + linkTravelTime));
					first=false;
				}
				/*
				if (lastLinkType.equals("Transfer")){
					// 2 transfer links togheter???
					Act act = (Act)newPlan.getActsLegs().get(newPlan.getActsLegs().size()-1);
					act.setCoord(link.getFromNode().getCoord());
					act.setStartTime(act.getStartTime()+linkTravelTime);
					act.setDur( act.getDur()+linkTravelTime);
					act.setEndTime(act.getStartTime()+ act.getDur());
					act.setLink(link);
				}
				*/
			
			} else if (link.getType().equals("Walking")){
				legRouteLinks.clear();
				legRouteLinks.add(link);
				linkTravelTime= linkTravelTime/60;
				arrTime= accumulatedTime+ linkTravelTime;
				newPlan.addLeg(newPTLeg(legNum++, Leg.Mode.walk, legRouteLinks, linkDistance, accumulatedTime, linkTravelTime, arrTime));
			}//if link.gettype

			accumulatedTime =accumulatedTime+ linkTravelTime;
			lastLinkType = link.getType();
			linkCounter++;
		}//for Link
		return legNum;
	}//insert
	
	private Act newPTAct(String type, Coord coord, Link link, double startTime, double dur, double endTime){
		Act ptAct= new Act(type, coord);
		ptAct.setStartTime(startTime);
		ptAct.setEndTime(endTime);
		ptAct.setDur(dur);
		ptAct.calculateDuration();
		ptAct.setLink(link);
		//act.setLinkId(link.getId());
		//act.setCoord(coord)
		return ptAct;
	}
		
	private Leg newPTLeg(int num, Leg.Mode mode, List<Link> routeLinks, double distance, double depTime, double travTime, double arrTime){
		Route legRoute = new Route();
		if (mode!=Leg.Mode.walk){
			legRoute.setLinkRoute(routeLinks);
		}
		legRoute.setTravTime(travTime);
		legRoute.setDist(distance);  
	
		Leg leg = new Leg(mode);
		leg.setNum(num);
		leg.setRoute(legRoute);
		leg.setDepTime(depTime);
		leg.setTravTime(travTime);
		leg.setArrTime(arrTime);
		return leg;
	}
	
}
