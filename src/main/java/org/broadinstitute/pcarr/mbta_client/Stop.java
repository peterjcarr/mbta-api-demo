package org.broadinstitute.pcarr.mbta_client;

import java.util.SortedSet;
import java.util.TreeSet;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.google.gson.JsonObject;

/**
 * Example Stop
<pre>
  "data": [
    {
      "id": "place-alfcl",
      "attributes": {
        ...
        "name": "Alewife",
        ...
      },
      "type": "stop"
    },
    ...
   ]
</pre>
 */

public class Stop {
    private static final Logger log = LogManager.getLogger(Stop.class);
    
    final String id;
    final String name;
    final JsonObject json;
    final SortedSet<Route> routes;
    
    public Stop(final String id, final JsonObject json) {
        this.id=id;
        this.json=json;
        this.name=extractName(json);
        this.routes=new TreeSet<Route>(Route.nameComparator);
    }
    
    public void addRoute(final Route route) {
        routes.add(route);
    }
    
    public String getId() {
        return id;
    }
    
    public String getName() {
        return name;
    }
    
    public SortedSet<Route> getRoutes() {
        return routes;
    }
    
    public String extractName(final JsonObject nextJsonObj) {
        try {
            return nextJsonObj
                .get("attributes")
                .getAsJsonObject()
                .get("name")
            .getAsString();
        }
        catch (Throwable t) {
            log.error("Unexpected error getting 'long_name' from JSON representation", t);
            return null;
        }
    }
 
    public String toString() {
        return name+" ("+id+")";
    }
    
    public int hashCode() {
        return toString().hashCode();
    }
    
    public boolean equals(Object o) {
        return (this == o) || 
               ( (o instanceof Stop) && (toString().equals(o.toString())) );
    }

}