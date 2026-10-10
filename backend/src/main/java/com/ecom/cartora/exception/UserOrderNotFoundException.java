package com.ecom.cartora.exception;

public class UserOrderNotFoundException extends RuntimeException{
    public UserOrderNotFoundException(String message){
        super(message);
    }
}
