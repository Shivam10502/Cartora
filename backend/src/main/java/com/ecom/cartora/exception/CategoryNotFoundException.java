package com.ecom.cartora.exception;

public class CategoryNotFoundException extends RuntimeException{
    
    public CategoryNotFoundException(String message){
        super(message);
    }
}
