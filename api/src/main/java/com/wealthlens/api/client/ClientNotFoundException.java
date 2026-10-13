package com.wealthlens.api.client;

public class ClientNotFoundException extends RuntimeException {

    public ClientNotFoundException(Long id) {
        super("Client " + id + " not found");
    }
}
