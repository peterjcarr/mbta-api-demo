package org.broadinstitute.pcarr.mbta_client;

import java.util.List;

import org.broadinstitute.pcarr.rest.RestClient;

/**
 * MBTA client demo.
 * 
 * (1) List the "long name" for all subway routes.
 * 
 * (2) Write a program that writes these questions and their answers to the console:
 *   Which rail route has the most stops? 
 *   Which rail route has the fewest stops?  
 *   Which rail routes are connected? List the stops that connect them.
 * 
 * (3) Given any two stops on the rail routes you listed for question 1, list the rail routes you would travel to get from one to the other
 *   Examples: 
 *      Davis to Kendall -> Redline
 *      Ashmont to Arlington -> Redline, Greenline 
 *   How you handle input, represent train routes and present output is your choice.
 * 
 * Example curl commands:
 <pre>
   curl -X GET "https://api-v3.mbta.com/routes?sort=long_name&filter%5Btype%5D=filter%5Btype%5D%3D0%2C1" 
     -H "accept: application/vnd.api+json" 
     -H "x-api-key: ..."
     
   curl -X GET "https://api-v3.mbta.com/routes?filter%5Btype%5D=0%2C1" -H "accept: application/vnd.api+json" \
     | python -m json.tool \
     | more
     
   curl -X GET "https://api-v3.mbta.com/stops?filter%5Bdirection_id%5D=0&filter%5Broute%5D=Red" -H "accept: application/vnd.api+json"
 
 </pre>
 */
public class Main {

    public static void main( String[] args ) {
        System.out.println( "--------------------" );
        System.out.println( "  mbta-client demo" );
        System.out.println( "--------------------" );
        boolean verbose=false;
        RestClient client=new RestClient()
            .withApiKey(MbtaClient.apiKey)
            .withVerbose(verbose);

        System.out.print("Initializing route graph mbta service ... ");
        final SystemGraph graph=new SystemGraph()
            .withClient(client)
        .build();

        System.out.println("");
        final List<String> longNames=graph.getLongNames();
        System.out.println("Listing all subway routes by 'long_name' ...");
        System.out.println("    "+longNames);
        System.out.println("");
        System.out.println("Counting routes, stops, and connections ...");
        System.out.println("Q: Which rail route(s) has the most stops?");
        Route longestRoute=graph.getLongestRoutes().asList().get(0);
        System.out.println("A: "+longestRoute+", with "+longestRoute.getStops().size()+" stops");
        Route shortestRoute=graph.getShortestRoutes().asList().get(0);
        System.out.println("Q:  Which rail route has the fewest stops?");
        System.out.println("A: "+shortestRoute+", with "+shortestRoute.getStops().size()+" stops");
        
        graph.printConnectingRoutes(System.out);
        System.out.println("");
        System.out.println("Done");
        
        // list the rail routes you would travel to get from one to the other ...
        System.out.println();
        System.out.println("List routes from stop {A} to stop {B} ...");
        graph.printRoutesFrom(System.out, "Davis", "Kendall/MIT");
        graph.printRoutesFrom(System.out, "Ashmont", "Arlington");
    }
}
