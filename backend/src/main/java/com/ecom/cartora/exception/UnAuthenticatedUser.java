package com.ecom.cartora.exception;

public class UnAuthenticatedUser extends RuntimeException{
    public UnAuthenticatedUser(String message){
        super(message);
    }
}
