package org.broadinstitute.pcarr.rest;

@SuppressWarnings("serial")
public final class RestClientException extends java.lang.Exception {
    public RestClientException() {
        super();
    }
    public RestClientException(String message) {
        super(message);
    }
    public RestClientException(String message, java.lang.Exception e) {
        super(message, e);
    }
    public RestClientException(String message, Throwable t) {
        super(message, t);
    }
}