package com.cloudvault.web;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
public final class ApiError {
    private ApiError() {}
    public static ResponseStatusException bad(String msg) { return new ResponseStatusException(HttpStatus.BAD_REQUEST,msg); }
    public static ResponseStatusException forbidden(String msg) { return new ResponseStatusException(HttpStatus.FORBIDDEN,msg); }
    public static ResponseStatusException missing() { return new ResponseStatusException(HttpStatus.NOT_FOUND,"Item not found"); }
    public static ResponseStatusException conflict(String msg) { return new ResponseStatusException(HttpStatus.CONFLICT,msg); }
}
