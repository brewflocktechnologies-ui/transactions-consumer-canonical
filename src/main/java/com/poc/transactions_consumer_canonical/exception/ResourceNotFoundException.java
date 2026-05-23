package com.poc.transactions_consumer_canonical.exception;

public class ResourceNotFoundException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public ResourceNotFoundException(String resource, String id) {
        super(resource + " not found with id: " + id);
    }
}
