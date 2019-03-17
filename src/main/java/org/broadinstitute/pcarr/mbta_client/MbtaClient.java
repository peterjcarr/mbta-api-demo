package org.broadinstitute.pcarr.mbta_client;

import java.util.Collection;
import java.util.SortedSet;

import org.broadinstitute.pcarr.rest.RestClient;

import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.Multimap;
import com.google.common.collect.Sets;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

public class MbtaClient {
    // api root
    private static final String apiPrefix="https://api-v3.mbta.com";

    // mbta api key, x-api-key: {apiKey}
    public static String apiKey="bb7ea6600a394d98ac5cef4512c6e1be";

    /**
     * Get the list of routes from the MBTA service.
     * 
     *   GET /routes?filter[type]=0,1
     *   GET /routes?filter[type]=0,1,2
     * 
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
    protected static JsonObject getRoutesJson(final RestClient client) {
        return getRoutes(client, false);
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
        return RestClient.getJson(client, apiPrefix+"/routes?"+typeFilter);
    }

    /**
     * Get the list of stops for a particular route from the MBTA service.
     * 
     *   GET /stops?filter[direction_id]=0&filter[route]={routeId}
     * 
     */
    protected static JsonObject getStopsJson(final RestClient client, final Route route) {
        return RestClient.getJson(client, apiPrefix+"/stops?filter[direction_id]=0&filter[route]="+route.getId());
    }

    // curl -X GET "https://api-v3.mbta.com/stops?filter%5Bdirection_id%5D=0&filter%5Broute%5D=Red" -H "accept: application/vnd.api+json"
    protected static JsonObject getStopsByRouteId(final RestClient client, final String routeId) {
        return RestClient.getJson(client, apiPrefix+"/stops?filter[direction_id]=0&filter[route]="+routeId);
    }

    /**
     * Get the list of routes by making API calls to the MBTA service.
     * (1) List the "long name" for all subway routes.
     *   Endpoint: https://api-v3.mbta.com/routes?filter[type]=0,1
     *   Command:
     *     curl -X GET "https://api-v3.mbta.com/routes?filter%5Btype%5D=0%2C1" -H "accept: application/vnd.api+json"
     */
    public static SortedSet<Route> initRoutes(final RestClient client, final boolean withStops, final boolean withConnections) {
        //List<Route> routes=Lists.newArrayList();
        SortedSet<Route> routes=Sets.newTreeSet(Route.nameComparator);
        boolean includeRail=false;

        final JsonObject routesJson=getRoutes(client, includeRail);
        final JsonArray data=routesJson.get("data").getAsJsonArray();
        for(int i=0; i<data.size(); ++i) {
            final JsonObject jsonObj=data.get(i).getAsJsonObject();
            // data[i]."id"
            final String id = jsonObj.get("id").getAsString();
            Route route=new Route(id, jsonObj);
            routes.add(route);
        }
        
        if (withStops) {
            for(final Route route : routes) {
                appendStops(client, route);
            }
        }
        
        if (withConnections) {
            for(final Route route : routes) {
                for(final Stop stop : route.getStops()) {
                    stop.addRoute(route);
                }
            }
        }
        return routes;
    }
    
    protected static Route appendStops(final RestClient client, final Route route) {
        final JsonObject stopsJson=MbtaClient.getStopsJson(client, route);
        final JsonArray stopsArr=stopsJson.get("data").getAsJsonArray();
        for(int i=0; i<stopsArr.size(); ++i) {
            final JsonObject jsonObj=stopsArr.get(i).getAsJsonObject();
            // data[i]."id"
            final String id = jsonObj.get("id").getAsString();
            final Stop stop=new Stop(id, jsonObj);
            route.addStop(stop);
        }
        return route;
    }
    
    public static Multimap<Route,Stop> initStops(final RestClient client, final Collection<Route> routes) {
        ArrayListMultimap<Route,Stop> routeIdStops=ArrayListMultimap.create(); 
        for(final Route route : routes) {
            final JsonObject stopsJson=MbtaClient.getStopsJson(client, route);
            final JsonArray stopsArr=stopsJson.get("data").getAsJsonArray();
            for(int i=0; i<stopsArr.size(); ++i) {
                final JsonObject jsonObj=stopsArr.get(i).getAsJsonObject();
                // data[i]."id"
                final String id = jsonObj.get("id").getAsString();
                final Stop to=new Stop(id, jsonObj);
                to.addRoute(route);
                routeIdStops.put(route, to);
            }
        }
        return routeIdStops;
    }

}
