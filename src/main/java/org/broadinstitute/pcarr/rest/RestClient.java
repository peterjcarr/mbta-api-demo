package org.broadinstitute.pcarr.rest;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.LinkedHashMap;
import java.util.Map.Entry;

import org.apache.commons.codec.binary.Base64;
import org.apache.http.Header;
import org.apache.http.HttpEntity;
import org.apache.http.HttpMessage;
import org.apache.http.HttpResponse;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.HttpClients;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.google.common.base.Strings;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

/**
 * quick and dirty HTTP client cobbled together from code in GpUnit.
 */
public class RestClient {
    private static final Logger log = LogManager.getLogger(RestClient.class);
    
    /**
     * Set Header for HTTP Basic Authentication.
     * Usage:
     *   call this method on an HttpMessage object before making the request.
     <pre>
         HttpGet get = new HttpGet(uri);
         get = setHeaders(get);
         ...
         client.execute(get);
     </pre>
     */
    public static final <T extends HttpMessage> T setBasicAuthHeader(final T message, final String user, final String pass) {
        //for basic auth, use a header like this
        //Authorization: Basic QWxhZGRpbjpvcGVuIHNlc2FtZQ==
        final String orig = user+":"+pass;
        //encoding  byte array into base 64
        byte[] encoded = Base64.encodeBase64(orig.getBytes());
        final String basicAuth="Basic "+new String(encoded);
        message.setHeader("Authorization", basicAuth);
        return message;
    }
    
    public static final <T extends HttpMessage> T setApiKeyHeader(final T message, final String apiKey) {
        if (!Strings.isNullOrEmpty(apiKey)) {
            message.setHeader("x-api-key", apiKey);
        }
        return message;
    }

    private boolean verbose=false;
    private final Gson gson;
    private final LinkedHashMap<String,String> withHeaders=new LinkedHashMap<String,String>();

    public RestClient() {
        this.gson = new GsonBuilder().setPrettyPrinting().create();
    }
    
    public RestClient withHeader(final String key, final String value) {
        withHeaders.put(key, value);
        return this;
    }
    
    public RestClient withApiKey(final String apiKey) {
        return withHeader("x-api-key", apiKey);
    }

    public RestClient withBasicAuth(final String user, final String pass) {
        //for basic auth, use a header like this
        //Authorization: Basic QWxhZGRpbjpvcGVuIHNlc2FtZQ==
        final String orig = user+":"+pass;
        //encoding  byte array into base 64
        byte[] encoded = Base64.encodeBase64(orig.getBytes());
        final String basicAuth="Basic "+new String(encoded);
        return withHeader("Authorization", basicAuth);
    }
    
    public RestClient withVerbose() {
        return withVerbose(true);
    }

    public RestClient withVerbose(final boolean verbose) {
        this.verbose=verbose;
        return this;
    }
    
    public boolean isVerbose() {
        return verbose;
    }
    
    protected <T extends HttpMessage> T setHeaders(final T message) {
        for(final Entry<String,String> header : withHeaders.entrySet()) {
            message.setHeader(header.getKey(), header.getValue());
        }
        message.setHeader("Content-type", "application/json");
        message.setHeader("Accept", "application/json");
        return message;
    }

    /**
     * GET the JSON representation of the given fully-qualified endpoint.
     */
    public JsonObject getJson(final String endpoint) throws URISyntaxException, IOException, RestClientException {
        final URI uri=new URI(endpoint);

        final HttpClient client = HttpClients.createDefault();
        HttpGet get = new HttpGet(uri);
        get = setHeaders(get);
        
        final HttpResponse response;
        try {
            response=client.execute(get);
        }
        catch (IOException e) {
            throw e;
        }
        catch (Throwable t) {
            throw new RestClientException("Unexpected error getting resource from endpoint="+uri, t);
        }
        final int statusCode=response.getStatusLine().getStatusCode();
        final boolean success;
        if (statusCode >= 200 && statusCode < 300) {
            success=true;
        }
        else {
            success=false;
        }
        if (!success) {
            final String message="GET "+uri.toString()+" failed! "+statusCode+": "+response.getStatusLine().getReasonPhrase();
            // for debugging
            log.debug(message);
            log.debug("Headers ...");
            for(final Header header : response.getAllHeaders()) {
                log.debug("    "+header.toString());
            }
            throw new RestClientException(message);
        }
        
        HttpEntity entity = response.getEntity();
        if (entity == null) {
            final String message="GET "+uri.toString()+" failed! The response should contain an entity";
            throw new RestClientException(message);
        }

        BufferedReader reader=null;
        try {
            reader=new BufferedReader(
                    new InputStreamReader( response.getEntity().getContent() )); 
            JsonObject jsonObject=readJsonObject(reader);
            return jsonObject;
        }
        catch (IOException e) {
            final String message="GET "+uri.toString()+", I/O error handling response";
            throw new RestClientException(message, e);
        }
        catch (RestClientException e) {
            final String message="GET "+uri.toString()+", Error parsing JSON response";
            throw new RestClientException(message, e);
        }
        catch (Throwable t) {
            final String message="GET "+uri.toString()+", Unexpected error reading response";
            throw new RestClientException(message, t);
        }
        finally {
            if (reader != null) {
                try {
                    reader.close();
                }
                catch (IOException e) {
                    final String message="GET "+uri.toString()+", I/O error closing reader";
                    throw new RestClientException(message, e);
                }
            }
        }
    }

    /**
     * Create a JsonObject by parsing the contents from the
     * given Reader.
     * 
     * @param reader, an open and initialized reader, for example from an HTTP response.
     *     The calling method must close the reader.
     * @return JsonObject
     * @throws RestClientException
     */
    protected JsonObject readJsonObject(final Reader reader) throws RestClientException {
        JsonParser parser = new JsonParser();
        JsonElement jsonElement=parser.parse(reader);
        if (jsonElement == null) {
            throw new RestClientException("JsonParser returned null JsonElement");
        }
        return jsonElement.getAsJsonObject();
    }

    /** pretty print the json object */
    public String formatJson(final JsonObject json) {
        return gson.toJson(json);
    }

}
