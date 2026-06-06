package com.funfriday.exception;

public class InvalidGameMoveException extends RuntimeException {

    // An optional error code enum or string to help the frontend identify the exact failure profile
    private final String errorCode;

    public InvalidGameMoveException(String message, String errorCode) {
        super(message);
        this.errorCode = errorCode;
    }

    public String getErrorCode() {
        return errorCode;
    }
}