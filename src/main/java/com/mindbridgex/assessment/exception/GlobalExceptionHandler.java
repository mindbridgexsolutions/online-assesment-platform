package com.mindbridgex.assessment.exception;

import com.mindbridgex.assessment.security.AuthUser;
import com.mindbridgex.assessment.util.RequestInfo;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.servlet.ModelAndView;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.util.Map;
import java.util.UUID;

/** One place that turns exceptions into either a JSP error page (pages) or a JSON body (/api/**). */
@ControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(AppException.class)
    public Object handleApp(AppException ex, HttpServletRequest request) {
        return respond(request, ex.getStatus(), ex.getMessage());
    }

    @ExceptionHandler(AccessDeniedException.class)
    public Object handleDenied(AccessDeniedException ex, HttpServletRequest request) {
        return respond(request, HttpStatus.FORBIDDEN, "You are not allowed to access this page.");
    }

    @ExceptionHandler({MethodArgumentNotValidException.class, MissingServletRequestParameterException.class,
            MethodArgumentTypeMismatchException.class, HttpRequestMethodNotSupportedException.class})
    public Object handleBadRequest(Exception ex, HttpServletRequest request) {
        HttpStatus status = ex instanceof HttpRequestMethodNotSupportedException
                ? HttpStatus.METHOD_NOT_ALLOWED : HttpStatus.BAD_REQUEST;
        return respond(request, status, "The request could not be processed. Please check your input.");
    }

    @ExceptionHandler(NoResourceFoundException.class)
    public Object handleNotFound(NoResourceFoundException ex, HttpServletRequest request) {
        return respond(request, HttpStatus.NOT_FOUND, "The page you are looking for does not exist.");
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public Object handleUpload(MaxUploadSizeExceededException ex, HttpServletRequest request) {
        return respond(request, HttpStatus.PAYLOAD_TOO_LARGE, "The uploaded file is too large.");
    }

    @ExceptionHandler(ObjectOptimisticLockingFailureException.class)
    public Object handleLock(ObjectOptimisticLockingFailureException ex, HttpServletRequest request) {
        return respond(request, HttpStatus.CONFLICT,
                "This record was changed by someone else at the same time. Please reload and try again.");
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public Object handleIntegrity(DataIntegrityViolationException ex, HttpServletRequest request) {
        log.warn("Data integrity violation on {}: {}", request.getRequestURI(), ex.getMostSpecificCause().getMessage());
        return respond(request, HttpStatus.CONFLICT,
                "The operation conflicts with existing data (duplicate value or record still in use).");
    }

    @ExceptionHandler(Exception.class)
    public Object handleAny(Exception ex, HttpServletRequest request) {
        String ref = UUID.randomUUID().toString().substring(0, 8);
        log.error("Unhandled error [ref {}] on {} {}", ref, request.getMethod(), request.getRequestURI(), ex);
        return respond(request, HttpStatus.INTERNAL_SERVER_ERROR,
                "Something went wrong on our side. Reference: " + ref);
    }

    private Object respond(HttpServletRequest request, HttpStatus status, String message) {
        if (RequestInfo.isApi(request)) {
            return ResponseEntity.status(status).contentType(MediaType.APPLICATION_JSON)
                    .body(Map.of("error", status.getReasonPhrase(), "message", message));
        }
        ModelAndView mav = new ModelAndView("error");
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof AuthUser user) {
            mav.addObject("currentUser", user);
        }
        mav.setStatus(status);
        mav.addObject("status", status.value());
        mav.addObject("title", status.getReasonPhrase());
        mav.addObject("message", message);
        return mav;
    }
}
