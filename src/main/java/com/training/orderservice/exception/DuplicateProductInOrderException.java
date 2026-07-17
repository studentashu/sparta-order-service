package com.training.orderservice.exception;

public class DuplicateProductInOrderException extends RuntimeException {
    public DuplicateProductInOrderException(String message) {
        super(message);
    }
}
