package com.mindbridgex.assessment.exception;

import org.springframework.http.HttpStatus;

/** Business exception carrying an HTTP status and a user-presentable message. */
public class AppException extends RuntimeException {

    private final HttpStatus status;

    public AppException(HttpStatus status, String message) {
        super(message);
        this.status = status;
    }

    public HttpStatus getStatus() {
        return status;
    }

    public static AppException notFound(String what) {
        return new AppException(HttpStatus.NOT_FOUND, what + " not found");
    }

    public static AppException forbidden(String message) {
        return new AppException(HttpStatus.FORBIDDEN, message);
    }

    public static AppException bad(String message) {
        return new AppException(HttpStatus.BAD_REQUEST, message);
    }

    public static AppException conflict(String message) {
        return new AppException(HttpStatus.CONFLICT, message);
    }

    public static AppException gone(String message) {
        return new AppException(HttpStatus.GONE, message);
    }

    public static AppException unauthorized(String message) {
        return new AppException(HttpStatus.UNAUTHORIZED, message);
    }

    public static AppException tooMany(String message) {
        return new AppException(HttpStatus.TOO_MANY_REQUESTS, message);
    }
}
