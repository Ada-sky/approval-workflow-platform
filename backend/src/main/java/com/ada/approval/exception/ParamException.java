package com.ada.approval.exception;

/** Application validation exception */
public class ParamException extends RuntimeException {
    public ParamException(String message) {
        super(message);
    }
}
