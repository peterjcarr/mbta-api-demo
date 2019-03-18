package org.broadinstitute.pcarr.mbta_client;

import static org.hamcrest.Matchers.is;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import org.broadinstitute.pcarr.rest.RestClient;
import org.jgrapht.GraphPath;
import org.junit.BeforeClass;
import org.junit.Ignore;
import org.junit.Test;

import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;

public class TestSystemGraph {
    private static SystemGraph graph;

    @BeforeClass
    public static void beforeClass() {
        RestClient client=new RestClient()
            .withApiKey(MbtaClient.apiKey)
            .withVerbose(false);
        graph=new SystemGraph()
            .withClient(client)
        .build();
    }
    
    protected static Route route(final String routeId) {
        Route route=graph.routesById.get(routeId);
        assertNotNull("Invalid route, routeId='"+routeId+"'", route);
        return route;
    }
    
    protected static Route routeByName(final String routeName) {
        Route route=graph.routesByName.get(routeName);
        assertNotNull("Invalid route, routeName='"+routeName+"'", route);
        return route;
    } 

    public static Stop stop(final String name) {
        final Stop stop=graph.stop(name);
        assertNotNull("Invalid stop, name='"+name+"'", stop);
        return stop;
    }

    protected static void assertPath(final String fromName, final String toName, final String... expectedRouteIds) {
        final List<Route> expected=new ArrayList<Route>(expectedRouteIds.length);
        for(final String routeId : expectedRouteIds) {
            expected.add(route(routeId));
        }  
        final List<Route> actualPath=graph.listRoutesFrom(stop(fromName), stop(toName));
        assertEquals("getPath from '"+fromName+"' to '"+toName+"'", expected, actualPath);
    }

    @Test
    public void routeLongNames() {
        final Set<String> expected = ImmutableSet.of(
            "Blue Line",
            "Green Line B", "Green Line C", "Green Line D", "Green Line E",
            "Orange Line",
            "Red Line", "Mattapan Trolley");
        assertThat(
            Sets.newHashSet(graph.getLongNames()),
            // expected
            is(expected));
    }
    
    @Test
    public void numStops() {
        assertEquals("total number of stops",
            // expected
            120, 
            // actual
            graph.getNumStops()
        );
    }
    
    //        System.out.println("Q: Which rail route(s) has the most stops?");
    //        System.out.println("A: "+longestRoutes+", with "+max+" stops");
    //        System.out.println("Q:  Which rail route(s) has the fewest stops?");
    //        System.out.println("A: "+shortestRoutes+", with "+min+" stops");    
    @Test
    public void longestRoute() {
        List<Route> l=graph.getLongestRoutes().asList();
        assertEquals("num routes", 1, l.size());
        assertEquals("name", "Green Line B", l.get(0).getLongName());
        assertEquals("num stops", 24, l.get(0).getStops().size());
    }
    
    @Test
    public void shortestRoute() {
        List<Route> l=graph.getShortestRoutes().asList();
        assertEquals("num routes", 1, l.size());
        assertEquals("name", "Mattapan Trolley", l.get(0).getLongName());
        assertEquals("num stops", 8, l.get(0).getStops().size());
    }
    
    @Test
    public void connectingRoutes() {
        graph.printConnectingRoutes(System.out);
    }
    
    /** Which rail routes are connected? List the stops that connect them. */
    @Test public void connectingStops() {
    }
    
    @Test
    public void getPath_oneLine() {
        final String fromName="Aquarium";
        final String toName="Bowdoin";
        assertPath(fromName, toName, "Blue");
    }
    
    @Test
    public void getPath_oneLine_endToEnd() {
        assertPath("Wonderland", "Bowdoin", "Blue");
    }

    @Test
    public void getPath_oneLine_endAtConnection() {
        assertPath("Wonderland", "State", "Blue");
    }

    @Test
    public void getPath_oneLine_cxn_to_cxn() {
        assertPath("State", "Government Center", "Blue");
    }

    @Test
    public void getPath_sameStop() {
        assertPath("Wonderland", "Wonderland");
    }
    
    @Test
    public void getPath_twoLines() {
        assertPath("Aquarium", "Assembly", "Blue", "Orange");
    }
    
    @Test
    public void getPath_Lechmere_to_Airport() {
        assertPath("Lechmere", "Airport", "Green-E", "Blue");
    }

    @Test
    public void getPath_Kendall_to_Airport() {
        assertPath("Kendall/MIT", "Airport", "Red", "Green-D", "Blue");
    }

    
    @Test
    public void fromParkToSouthStation() {
        final String from="Park Street";
        final String to="South Station";
        System.out.println("Shortest path from "+from+" to "+to+":");

        GraphPath<Stop,RouteEdge> path=graph.getGraphPath(
                stop(from), stop(to));
        System.out.println("    stops: "+path.getVertexList());
        
        List<Route> routes=Lists.newArrayList();
        for(RouteEdge edge : path.getEdgeList()) {
            routes.add(edge.getRoute());
        }
        System.out.println("    routes: "+routes);

        List<Route> shortestRoute=graph.listRoutesFrom(stop(from), stop(to));
        System.out.println("    shortestRoute: "+shortestRoute);
    }
    
    // for debugging, remove the '@Ignore' to run this test
    @Ignore @Test
    public void allRoutes() {
        graph.printAllRoutesFromAllStops(System.out);
    }

}
