package org.broadinstitute.pcarr.mbta_client;

import java.io.PrintStream;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.SortedSet;

import org.broadinstitute.pcarr.rest.RestClient;

import org.jgrapht.Graph;
import org.jgrapht.GraphPath;
import org.jgrapht.alg.connectivity.KosarajuStrongConnectivityInspector;
import org.jgrapht.alg.interfaces.StrongConnectivityAlgorithm;
import org.jgrapht.alg.interfaces.ShortestPathAlgorithm.SingleSourcePaths;
import org.jgrapht.alg.shortestpath.DijkstraShortestPath;
import org.jgrapht.graph.DefaultDirectedGraph;

import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import com.google.common.collect.TreeMultimap;

/**
 * a graph of the MBTA system implemented with the org.jgrapht library.
 */
public class SystemGraph {
    // the set of all routes
    protected SortedSet<Route> routes;
    // all routes, by 'id'
    protected Map<String,Route> routesById=Maps.newHashMap();
    // all routes, by 'name'
    protected Map<String,Route> routesByName=Maps.newHashMap();
    // the set of all stops, indexed by 'id'
    protected Map<String,Stop> stops=Maps.newHashMap();
    // secondary, set of all stops, indexed by 'name'
    protected Map<String,Stop> nameToStop=Maps.newHashMap();
    // each route is connected to one or more other routes
    // map of route -> list<route>, connections
    protected TreeMultimap<Route,Route> routeConnections=TreeMultimap.create(Route.nameComparator, Route.nameComparator);
    protected Set<Route> longestRoutes;
    protected Set<Route> shortestRoutes;

    private Graph<Stop, RouteEdge> graph;

    public SystemGraph() {
    }

    // builder pattern
    private RestClient client;
    public SystemGraph withClient(final RestClient client) {
        this.client=client;
        return this;
    }

    private void addStop(final Route route, final Stop stop) {
        Stop existing = stops.get(stop.getId());
        if (existing == null) {
            stop.addRoute(route);
            stops.put(stop.id, stop);
            nameToStop.put(stop.name, stop);
        }
        else {
            existing.addRoute(route);
        }
    }

    private void addConnection(final Route from, final Route to, final Stop at) {
        if (from.equals(to)) {
            return;
        }
        routeConnections.put(from, to);
        routeConnections.put(to, from);
    }

    /**
     * Initialize a local data structure (SystemGraph) by making API calls to the MBTA service.
     */
    public SystemGraph build() {
        final boolean withStops=true;
        final boolean withConnections=true;
        this.routes=MbtaClient.initRoutes(client, withStops, withConnections);

        // init route graph
        this.graph =
            new DefaultDirectedGraph<Stop, RouteEdge>(RouteEdge.class); 
        for(final Route route : routes) {
            // init route lookup
            this.routesById.put(route.getId(), route);
            this.routesByName.put(route.getLongName(), route);
            Stop from=null;
            for(final Stop to : route.getStops()) {
                graph.addVertex(to);
                if (from != null) {
                    graph.addEdge(from, to, new RouteEdge(route));
                    graph.addEdge(to, from, new RouteEdge(route));
                }
                else {
                    from=to;
                }
            }
        }

        // record longest and shortest routes
        int max=0;
        int min=-1;
        for(final Route route : routes) {
            int numStops=route.getStops().size();
            if (min==-1 || numStops<min) {
                min=numStops;
            }
            if (numStops>max) {
                max=numStops;
            }
        }
        longestRoutes=Sets.newHashSet();
        shortestRoutes=Sets.newHashSet();
        for(final Route route : routes) {
            int numStops=route.getStops().size();
            if (numStops == min) {
                shortestRoutes.add(route);
            }
            if (numStops == max) {
                longestRoutes.add(route);
            }
        }
        
        // record route connections
        for(final Route route : routes) {
            for(final Stop stop : route.getStops()) {
                addStop(route, stop);
            }
        }

        for(final Stop stop : stops.values()) {
            final Route first=stop.getRoutes().first();
            for(final Route route : stop.getRoutes()) {
                addConnection(first, route, stop);
            }
        }

        return this;
    }
    
    /**
     * get the total number of stops in the system
     */
    public int getNumStops() {
        return graph.vertexSet().size();
    }
    
    /**
     * get all of the routes in the system
     */
    public ImmutableSet<Route> getRoutes() {
        return ImmutableSet.copyOf(routes);
    }
    
    /**
     * get all of the stops in the system
     */
    public ImmutableSet<Stop> getStops() {
        return ImmutableSet.copyOf(graph.vertexSet());
    }

    /** get the Stop by name, or null if no matching stop is found */
    public Stop stop(final String name) {
        return nameToStop.get(name);
    }

    /**
     * get the long names of all of the routes
     */
    public List<String> getLongNames() {
        List<String> longNames=new ArrayList<String>();
        for(final Route route : getRoutes()) {
            longNames.add(route.getLongName());
        }
        return longNames;
    } 
    
    /**
     * which rail route(s) has the most stops?
     */
    public ImmutableSet<Route> getLongestRoutes() {
        return ImmutableSet.copyOf(longestRoutes);
    }
    
    /**
     * which rail route(s) has the fewest stops?
     */
    public ImmutableSet<Route> getShortestRoutes() {
        return ImmutableSet.copyOf(shortestRoutes);
    }

    /**
     * Get a map of connecting routes. Each route (the key) is
     * connected to one or more other routes (the value).
     *   route -> list of connected routes
     */
    public Map<Route,Collection<Route>> getRouteConnections() {
        return routeConnections.asMap();
    }

    protected GraphPath<Stop, RouteEdge> getGraphPath(final Stop from, final Stop to) {
        DijkstraShortestPath<Stop, RouteEdge> dijkstraAlg =
            new DijkstraShortestPath<Stop, RouteEdge>(graph);
        SingleSourcePaths<Stop, RouteEdge> iPaths = dijkstraAlg.getPaths(from);
        return iPaths.getPath(to);
    }

    /**
     * Given any two stations by name, 
     * list the stops you would travel to get from one to the other.
     * Computed as the shortest path from point a to point b.
     */    
    public List<Stop> listStopsFrom(final String fromStation, final String toStation) {
        GraphPath<Stop, RouteEdge> path=getGraphPath(stop(fromStation), stop(toStation));
        return path.getVertexList();
    }

    /**
     * Given any two stops, 
     * list the stops you would travel to get from one to the other.
     * Computed as the shortest path from point a to point b.
     */    
    public List<Stop> listStopsFrom(final Stop from, final Stop to) {
        GraphPath<Stop, RouteEdge> path=getGraphPath(from, to);
        return path.getVertexList();
    }
    
    /**
     * Given any two stations, 
     * list the rail routes you would travel to get from one to the other.
     * Examples: 
     *     Davis to Kendall -> Redline
     *     Ashmont to Arlington -> Redline, Greenline 
     */
    public List<Route> listRoutesFrom(final String fromStation, final String toStation) {
        return listRoutesFrom(stop(fromStation), stop(toStation));
    }
    
    /**
     * Given any two stops, 
     * list the rail routes you would travel to get from one to the other.
     */
    public List<Route> listRoutesFrom(final Stop from, final Stop to) {
        final GraphPath<Stop, RouteEdge> path=getGraphPath(from, to);
        final List<Route> routes=Lists.newArrayList();
        Route prev=null;
        for(RouteEdge edge : path.getEdgeList()) {
            Route next=edge.getRoute();
            if (prev != next) {
                routes.add(next);
            }
            prev=next;                
        }
        return routes;
    }
    
    public void printRoutesFrom(final PrintStream out, final String fromStation, final String toStation) {
        Stop from=stop(fromStation);
        Stop to=stop(toStation);
        if (from==null) {
            out.println("    Input error, station not found, fromStation="+fromStation);
        }
        if (to==null) {
            out.println("    Input error, station not found, toStation="+toStation);
        }
        if (from==null || to==null) {
            return;
        }
        printRoutesFrom(out, from, to);
    }

    public void printRoutesFrom(final PrintStream out, final Stop from, final Stop to) {
        out.println("listing routes from "+from+" to "+to+" ... ");
        if (!from.equals(to)) {
            List<Route> path=listRoutesFrom(from, to);
            out.println("    "+path);
        }
    }

    public void printConnectingRoutes(final PrintStream out) {
        //find all the stops that have more than one route
        out.println("Q: Which rail routes are connected?");
        out.println("A: {routeId} is connected to [{routeId_1}, ... , {routeId_N}]");
        for(final Entry<Route,Collection<Route>> e : getRouteConnections().entrySet()) {
            out.println("    "+e.getKey()+" is connected to "+e.getValue());
        }
        
        out.println("A: There are {N} connections at {stop} : [{routeIds}]...");
        for(final Stop stop : stops.values()) {
            final Set<Route> routes=stop.getRoutes();
            if (stop.getRoutes().size()>1) {
                out.println("    There are "+routes.size()+" connections at stopId="+stop.getId()+", "+stop.getName()+" : "+routes);
            }
        }
    }
    
    /**
     * Walk through all pairs of stops (A to B).
     * Print the routes to take to get from A to B.
     */
    public void printAllRoutesFromAllStops(final PrintStream out) { 
        for(final Stop from : getStops()) {
            for(final Stop to : getStops()) {
                printRoutesFrom(out, from, to);
            }
        } 
    }

    /**
     * for debugging
     */
    protected void debugGraph(final PrintStream out) {
        // computes all the strongly connected components of the directed graph
        KosarajuStrongConnectivityInspector<Stop, RouteEdge> kosarajuStrongConnectivityInspector = 
                new KosarajuStrongConnectivityInspector<Stop, RouteEdge>(graph);
        StrongConnectivityAlgorithm<Stop, RouteEdge> scAlg =
            kosarajuStrongConnectivityInspector;
        List<Graph<Stop, RouteEdge>> stronglyConnectedSubgraphs =
            scAlg.getStronglyConnectedComponents();

        // prints the strongly connected components
        out.println("Strongly connected components:");
        for (int i = 0; i < stronglyConnectedSubgraphs.size(); i++) {
            out.println(stronglyConnectedSubgraphs.get(i));
        }
        out.println();
    }

}
