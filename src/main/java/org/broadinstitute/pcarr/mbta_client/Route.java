package org.broadinstitute.pcarr.mbta_client;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.google.common.collect.ImmutableList;
import com.google.gson.JsonObject;

/** a route (aka a line) */
public class Route {
    private static final Logger log = LogManager.getLogger(Route.class);

    /** to sort routes by name */
    public static final Comparator<Route> nameComparator=new Comparator<Route>() {
        public int compare(Route o1, Route o2) {
            return o1.getLongName().compareTo(o2.getLongName());
        }
    };
    
    // data[i]."attributes"."long_name"
    private static String extractLongName(final JsonObject jsonObj) {
        try {
            return jsonObj
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
    
    final String id;
    final JsonObject json;
    final String longName;
    final List<Stop> stops;
    
    public Route(final String id, final JsonObject json) {
        this.id=id;
        this.json=json;
        this.longName=extractLongName(json);
        this.stops=new ArrayList<Stop>();
    }
    
    public void addStop(final Stop stop) {
        this.stops.add(stop);
    }
    
    public String getId() {
        return id;
    }
    
    public String getLongName() {
        return longName;
    }
    
    public ImmutableList<Stop> getStops() {
        return ImmutableList.copyOf(stops);
    }
    
    public String toString() {
        return longName+" ("+id+")";
    }
    
    public boolean equals(Object obj) {
        return obj instanceof Route && ((Route)obj).getId() == id;
    }

    public int hashCode() {
        return id.hashCode();
    }

}