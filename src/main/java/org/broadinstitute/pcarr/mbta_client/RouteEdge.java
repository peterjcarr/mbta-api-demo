package org.broadinstitute.pcarr.mbta_client;

import org.jgrapht.graph.DefaultEdge;

@SuppressWarnings("serial")
public class RouteEdge extends DefaultEdge {
    private final Route route;
    public RouteEdge(final Route route) {
        this.route=route;
    }
    
    public Route getRoute() {
        return route;
    }

}
