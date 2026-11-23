package com.clearchit.exception;

public class MaxMembersReachedException extends RuntimeException {

    public MaxMembersReachedException(String message) {
        super(message);
    }
}