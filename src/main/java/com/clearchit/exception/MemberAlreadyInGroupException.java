package com.clearchit.exception;

public class MemberAlreadyInGroupException
        extends RuntimeException {

    public MemberAlreadyInGroupException(String message) {
        super(message);
    }
}