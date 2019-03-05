package org.broadinstitute.pcarr.mbta_client;

import java.io.PrintStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.broadinstitute.pcarr.rest.RestClient;

import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.LinkedHashMultiset;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

/**
 * MBTA client demo.
 * 
 * <pre>
   Example curl commands
   ---------------------
   
   curl -X GET "https://api-v3.mbta.com/routes?sort=long_name&filter%5Btype%5D=filter%5Btype%5D%3D0%2C1" 
     -H "accept: application/vnd.api+json" 
     -H "x-api-key: ..."
     
   curl -X GET "https://api-v3.mbta.com/routes?filter%5Btype%5D=0%2C1" -H "accept: application/vnd.api+json" \
     | python -m json.tool \
     | more
     
   curl -X GET "https://api-v3.mbta.com/stops?filter%5Bdirection_id%5D=0&filter%5Broute%5D=Red" -H "accept: application/vnd.api+json"
 
 * </pre>
 */
public class Main 
{
    private static final Logger log = LogManager.getLogger(Main.class);

    // build up the route map with collections
    final static public class Route {
        final String id;
        final JsonObject json;
        
        public Route(final String id, final JsonObject json) {
            this.id=id;
            this.json=json;
        }
        
        public String getId() {
            return id;
        }
        
        public boolean equals(Object obj) {
            return obj instanceof Route && ((Route)obj).getId() == id;
        }
    }
    
    final static public class Stop {
        final String id;
        final JsonObject json;
        
        public Stop(final String id, final JsonObject json) {
            this.id=id;
            this.json=json;
        }
        
        public String getId() {
            return id;
        }
        
        public boolean equals(Object obj) {
            return obj instanceof Stop && ((Stop)obj).getId() == id;
        }
    }
    
    final static public class MyMap {
        protected Map<String,Integer> numStopsLookup;
        protected ArrayListMultimap<Route,Stop> routeStops=ArrayListMultimap.create();
        protected ArrayListMultimap<String,String> stopIdToRouteId=ArrayListMultimap.create();

        protected Map<Stop,Set<Route>> stops;
        
        protected Map<String,Route> routes=new HashMap<String,Route>();
        
        public void addRoute(Route route) {
            routes.put(route.getId(), route);
        }
        
        public void addStop(final String routeId, Stop stop) {
            Route route=routes.get(routeId);
            routeStops.put(route, stop);
            stopIdToRouteId.put(stop.getId(), routeId);
        }
        
        public void scanForStops() {
            //find all the stops that have more than one route
            System.out.println("Q: Which rail routes are connected?");
            System.out.println("A: There are {N} connections at {stop} : {routeIds}...");
            for(final String stopId : stopIdToRouteId.keySet()) {
                List<String> routeIds=stopIdToRouteId.get(stopId);
                if (routeIds != null && routeIds.size() > 1) {
                    //System.out.println("multiple routes for stopId="+stopId+", routes="+routeIds);
                    System.out.println("    There are "+routeIds.size()+" connections at stopId="+stopId+" : "+routeIds);
                }
            }
        }
    }
    
    // api root
    private static final String apiPrefix="https://api-v3.mbta.com";
    // mbta api key, x-api-key: {apiKey}
    private static String apiKey="bb7ea6600a394d98ac5cef4512c6e1be";
    
    protected static JsonObject getJson(final RestClient client, final String endpoint) {
        JsonObject json=null;
        try {
            json=client.getJson(endpoint);
            if (client.isVerbose()) {
                System.out.println("Json response ...");
                System.out.println(client.formatJson(json));
            }
        } 
        catch (Exception e) {
            System.err.println(e.getMessage());
        }
        return json;
    }
    
    /**
     * see: https://github.com/google/transit/blob/master/gtfs/spec/en/reference.md#routestxt
     * Indicates the type of transportation used on a route. Valid options are: 
     * 
     *   0 - Tram, Streetcar, Light rail. Any light rail or street level system within a metropolitan area.
     *   1 - Subway, Metro. Any underground rail system within a metropolitan area.
     *   2 - Rail. Used for intercity or long-distance travel.
     *   3 - Bus. Used for short- and long-distance bus routes.
     *   ...
     *   7
     */
    protected static JsonObject getRoutes(final RestClient client) {
        return getJson(client, apiPrefix+"/routes?filter[type]=0,1");
    }
    protected static JsonObject getRoutes(final RestClient client, final boolean includeRail) {
        final String type;
        if (!includeRail) {
            type="0,1";
        }
        else {
            type="0,1,2";
        }
        
        String typeFilter="filter[type]="+type;
        return getJson(client, apiPrefix+"/routes?"+typeFilter);
    }
    
    public static List<String> getLongNames(final RestClient client) { 
        JsonObject routesJson=getRoutes(client);
        final List<String> longNames=extractLongNames(routesJson);        
        return longNames;
    }

    // data[i]."attributes"."long_name"
    private static String extractLongName(final JsonObject nextJsonObj) {
        try {
            return nextJsonObj
                .get("attributes")
                .getAsJsonObject()
                .get("long_name")
            .getAsString();
        }
        catch (Throwable t) {
            log.error("Unexpected error getting 'long_name' from JSON representation", t);
            return null;
        }
    }

    public static List<String> extractLongNames(final JsonObject routesJson) { 
        JsonArray data=routesJson.get("data").getAsJsonArray();
        List<String> longNames=new ArrayList<String>();
        for(int i=0; i<data.size(); ++i) {
            final JsonObject next=data.get(i).getAsJsonObject();
            // data[i]."attributes"."long_name"
            final String longName = extractLongName(next);
            longNames.add(longName);
        }
        return longNames;
    }

    // curl -X GET "https://api-v3.mbta.com/stops?filter%5Bdirection_id%5D=0&filter%5Broute%5D=Red" -H "accept: application/vnd.api+json"
    protected static JsonObject getStopsByRouteId(final RestClient client, final String routeId) {
        return getJson(client, apiPrefix+"/stops?filter[direction_id]=0&filter[route]="+routeId);
    }

    public static Map<String,Integer> getNumStopsLookup(final RestClient client) { 
        MyMap myMap=new MyMap();
        boolean includeRail=false;
        final JsonObject routesJson=getRoutes(client, includeRail);
        final JsonArray data=routesJson.get("data").getAsJsonArray();
        final List<String> routeIds=new ArrayList<String>();
        for(int i=0; i<data.size(); ++i) {
            final JsonObject next=data.get(i).getAsJsonObject();
            // data[i]."id"
            final String id = next.get("id").getAsString();
            
            Route route=new Route(id, next);
            myMap.addRoute(route);
            routeIds.add(id);
        }
        
        final Map<String,Integer> numStopsLookup=new HashMap<String,Integer>();
        for(final String routeId : routeIds) {
            JsonObject stops=getStopsByRouteId(client, routeId);
            final JsonArray stopsArr=stops.get("data").getAsJsonArray();
            int numStops=stopsArr.size();
            numStopsLookup.put(routeId, numStops); 
            for(int i=0; i<numStops; ++i) {
                final JsonObject next=stopsArr.get(i).getAsJsonObject();
                // data[i]."id"
                final String id = next.get("id").getAsString();
                Stop stop=new Stop(id, next);
                myMap.addStop(routeId, stop);
            }
        }
        
        // find connections
        myMap.scanForStops();
        
        return numStopsLookup;
    }
    
    /** @deprecated scratch pad */
    protected static void listRailRoutes(final RestClient client) {
        Map<String,Integer> numStopsLookup=getNumStopsLookup(client);
        System.out.println("listing all rail routes ({routeId} : {numStops}) ...");
        System.out.println(""+numStopsLookup);
        
        Integer max=Collections.max(numStopsLookup.values());
        Integer min=Collections.min(numStopsLookup.values());
        
        // reverse lookup max
        List<String> longestRoutes=new ArrayList<String>();
        List<String> shortestRoutes=new ArrayList<String>();
        for(final Entry<String,Integer> e : numStopsLookup.entrySet()) {
            if (e.getValue() == min) {
                shortestRoutes.add(e.getKey());
            }
            if (e.getValue() == max) {
                longestRoutes.add(e.getKey());
            }
        }
        
        System.out.println("    most stops: "+max);
        System.out.println("    least stops: "+min);

        int minNumStops=Integer.MAX_VALUE;
        int maxNumStops=0;
        String maxId="";
        String minId="";
        for(final Entry<String,Integer> e : numStopsLookup.entrySet()) {
            String id=e.getKey();
            int numStops=e.getValue();
            if (numStops >= maxNumStops) {
                maxId=id;
                maxNumStops=numStops;
            }
            if (numStops <= minNumStops) {
                minNumStops=numStops;
                minId=id;
            }
        }
        System.out.println("    least stops: "+minId+" : "+minNumStops);
        System.out.println("     most stops: "+maxId+" : "+maxNumStops);
    }

    protected static void listAllRailRoutes() {
        RestClient client=new RestClient()
            .withApiKey(apiKey);
        JsonObject json=getJson(client, apiPrefix+"/routes?filter[type]=0,1,2");
            System.out.println("Json response ...");
            System.out.println(client.formatJson(json)); 
    }

    protected static void debug_localServer() {
        RestClient client=new RestClient()
            .withBasicAuth("local", "local")
            .withVerbose();
        getJson(client, "http://127.0.0.1:8080/gp/rest/v1/jobs/");
    }
    
    /**
     * (1) List the "long name" for all subway routes.
     * 
     *     Endpoint: https://api-v3.mbta.com/routes?filter[type]=0,1
     *     Command:
     *         curl -X GET "https://api-v3.mbta.com/routes?filter%5Btype%5D=0%2C1" -H "accept: application/vnd.api+json"
     */
    protected static void listLongNames(final PrintStream out, final RestClient client) {
        final List<String> longNames=getLongNames(client);
        out.println("Long names ...");
        out.println(longNames);
    }
    
    /**
     * (2) Write a program that writes these questions and their answers to the console:
     *   Which rail route has the most stops? 
     *   Which rail route has the fewest stops?  
     *   Which rail routes are connected? List the stops that connect them.
     */
    protected static void queries(final PrintStream out, final RestClient client) {
        out.println("Computing routes ...");
        
        Map<String,Integer> numStopsLookup=getNumStopsLookup(client);
        Integer max=Collections.max(numStopsLookup.values());
        Integer min=Collections.min(numStopsLookup.values());
        
        // reverse lookup max
        List<String> longestRoutes=new ArrayList<String>();
        List<String> shortestRoutes=new ArrayList<String>();
        for(final Entry<String,Integer> e : numStopsLookup.entrySet()) {
            if (e.getValue() == min) {
                shortestRoutes.add(e.getKey());
            }
            if (e.getValue() == max) {
                longestRoutes.add(e.getKey());
            }
        }

        out.println("Q: Which rail route(s) has the most stops?");
        out.println("A: "+longestRoutes+", with "+max+" stops");
        out.println("Q:  Which rail route has the fewest stops?");
        out.println("A: "+shortestRoutes+", with "+min+" stops");
    }

    public static void main( String[] args )
    {
        System.out.println( "mbta-client demo ..." );
        boolean verbose=true;
        RestClient client=new RestClient()
            .withApiKey(apiKey)
            .withVerbose(verbose);

        
        listLongNames(System.out, client);
        // (debug) listRailRoutes(client);
        queries(System.out, client);
        //listAllRailRoutes();
        // (debug) 
        // debug_localServer();
    }
}
