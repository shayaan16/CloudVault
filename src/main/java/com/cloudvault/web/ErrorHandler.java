package com.cloudvault.web;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.dao.DataIntegrityViolationException;
import java.util.Map;
@RestControllerAdvice
public class ErrorHandler {
    @ExceptionHandler(ResponseStatusException.class)
    ResponseEntity<?> status(ResponseStatusException e) { return ResponseEntity.status(e.getStatusCode()).body(Map.of("message",e.getReason()==null?"Request failed":e.getReason())); }
    @ExceptionHandler(MaxUploadSizeExceededException.class)
    ResponseEntity<?> size() { return ResponseEntity.status(413).body(Map.of("message","Files must be at most 50 MB (MiB).")); }
    @ExceptionHandler({IllegalArgumentException.class, org.springframework.http.converter.HttpMessageNotReadableException.class, org.springframework.web.bind.MissingServletRequestParameterException.class, org.springframework.web.multipart.support.MissingServletRequestPartException.class})
    ResponseEntity<?> invalid() { return ResponseEntity.badRequest().body(Map.of("message","Invalid or missing request data.")); }
    @ExceptionHandler(DataIntegrityViolationException.class)
    ResponseEntity<?> duplicate() { return ResponseEntity.status(409).body(Map.of("message","This item already exists or conflicts with another update.")); }
}
