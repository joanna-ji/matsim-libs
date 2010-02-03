package org.matsim.pt;

import java.util.List;

import org.matsim.api.core.v01.TransportMode;
import org.matsim.api.core.v01.network.Link;
import org.matsim.api.core.v01.network.Network;
import org.matsim.api.core.v01.network.Node;
import org.matsim.core.config.groups.CharyparNagelScoringConfigGroup;
import org.matsim.core.network.NetworkFactoryImpl;
import org.matsim.core.population.routes.NetworkRouteWRefs;
import org.matsim.core.router.Dijkstra;
import org.matsim.core.router.costcalculators.FreespeedTravelTimeCost;
import org.matsim.core.router.util.LeastCostPathCalculator;
import org.matsim.core.router.util.LeastCostPathCalculator.Path;

public class UmlaufInterpolator {

	private final Network network;
	private final CharyparNagelScoringConfigGroup config;

	public UmlaufInterpolator(Network network, final CharyparNagelScoringConfigGroup config) {
		super();
		this.network = network;
		this.config = config;
	}

	public void addUmlaufStueckToUmlauf(UmlaufStueck umlaufStueck, Umlauf umlauf) {
		List<UmlaufStueckI> umlaufStueckeOfThisUmlauf = umlauf.getUmlaufStuecke();
		if (!umlaufStueckeOfThisUmlauf.isEmpty()) {
			UmlaufStueckI previousUmlaufStueck = umlaufStueckeOfThisUmlauf.get(umlaufStueckeOfThisUmlauf.size() - 1);
			NetworkRouteWRefs previousCarRoute = previousUmlaufStueck.getCarRoute();
			Link fromLink = previousCarRoute.getEndLink();
			Link toLink = umlaufStueck.getCarRoute().getStartLink();
			if (fromLink != toLink) {
				insertWenden(fromLink, toLink, umlauf);
			}
		}
		umlaufStueckeOfThisUmlauf.add(umlaufStueck);
	}

	private void insertWenden(Link fromLink, Link toLink, Umlauf umlauf) {
		FreespeedTravelTimeCost calculator = new FreespeedTravelTimeCost(this.config);
		LeastCostPathCalculator routingAlgo = new Dijkstra(network, calculator, calculator);

		Node startNode = fromLink.getToNode();
		Node endNode = toLink.getFromNode();

		double depTime = 0.0;

		Path wendenPath = routingAlgo.calcLeastCostPath(startNode, endNode, depTime);
		wendenPath = routingAlgo.calcLeastCostPath(startNode, endNode, depTime);
		if (wendenPath == null) {
			throw new RuntimeException("No route found from node "
					+ startNode.getId() + " to node " + endNode.getId() + ".");
		}
		NetworkRouteWRefs route = (NetworkRouteWRefs) ((NetworkFactoryImpl) this.network.getFactory())
				.createRoute(TransportMode.car, fromLink, toLink);
		route.setLinks(fromLink, wendenPath.links, toLink);
		umlauf.getUmlaufStuecke().add(new Wenden(route));
	}

}
