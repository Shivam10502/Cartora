package com.ecom.cartora.exception;

public class CartIsEmptyException extends RuntimeException{
    public CartIsEmptyException(String message){
        super(message);
    }
}
