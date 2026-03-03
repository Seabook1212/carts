package works.weave.socks.cart.controllers;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import org.slf4j.Logger;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.BindException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.ServletWebRequest;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.HandlerMapping;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;
import works.weave.socks.cart.item.ItemNotFoundException;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.slf4j.LoggerFactory.getLogger;

@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {
    private final Logger log = getLogger(getClass());

    @Override
    protected ResponseEntity<Object> handleHttpMessageNotReadable(
            HttpMessageNotReadableException ex,
            HttpHeaders headers,
            HttpStatusCode status,
            WebRequest request) {
        HttpServletRequest servletRequest = ((ServletWebRequest) request).getRequest();
        logRequestFailure("request.invalid_json", HttpStatus.BAD_REQUEST, servletRequest, ex);
        return handleExceptionInternal(
                ex,
                Map.of("message", "Request body is invalid"),
                headers,
                HttpStatus.BAD_REQUEST,
                request);
    }

    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(
            MethodArgumentNotValidException ex,
            HttpHeaders headers,
            HttpStatusCode status,
            WebRequest request) {
        HttpServletRequest servletRequest = ((ServletWebRequest) request).getRequest();
        String message = ex.getBindingResult().getFieldErrors().stream()
                .findFirst()
                .map(error -> error.getField() + " " + error.getDefaultMessage())
                .orElse("Request validation failed");
        logRequestFailure("request.validation", HttpStatus.BAD_REQUEST, servletRequest, ex, message);
        return handleExceptionInternal(
                ex,
                Map.of("message", message),
                headers,
                HttpStatus.BAD_REQUEST,
                request);
    }

    @ExceptionHandler(ItemNotFoundException.class)
    public ResponseEntity<Map<String, Object>> handleItemNotFound(ItemNotFoundException ex, HttpServletRequest request) {
        logRequestFailure("request.not_found", HttpStatus.NOT_FOUND, request, ex);
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("message", ex.getMessage()));
    }

    @ExceptionHandler({IllegalArgumentException.class, ConstraintViolationException.class})
    public ResponseEntity<Map<String, Object>> handleBadRequest(RuntimeException ex, HttpServletRequest request) {
        logRequestFailure("request.validation", HttpStatus.BAD_REQUEST, request, ex);
        return ResponseEntity.badRequest().body(Map.of("message", ex.getMessage()));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, Object>> handleUnhandled(Exception ex, HttpServletRequest request) {
        logRequestFailure("request.unhandled", HttpStatus.INTERNAL_SERVER_ERROR, request, ex);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Map.of("message", "Internal server error"));
    }

    @Override
    protected ResponseEntity<Object> handleExceptionInternal(
            Exception ex,
            Object body,
            HttpHeaders headers,
            HttpStatusCode statusCode,
            WebRequest request) {
        if (ex instanceof BindException bindException && request instanceof ServletWebRequest servletWebRequest) {
            HttpServletRequest servletRequest = servletWebRequest.getRequest();
            String message = bindException.getFieldErrors().stream()
                    .findFirst()
                    .map(error -> error.getField() + " " + error.getDefaultMessage())
                    .orElse("Request binding failed");
            logRequestFailure("request.validation", HttpStatus.BAD_REQUEST, servletRequest, bindException, message);
            Object responseBody = body != null ? body : Map.of("message", message);
            return super.handleExceptionInternal(bindException, responseBody, headers, HttpStatus.BAD_REQUEST, request);
        }

        return super.handleExceptionInternal(ex, body, headers, statusCode, request);
    }

    private void logRequestFailure(
            String classification,
            HttpStatus status,
            HttpServletRequest request,
            Exception ex) {
        logRequestFailure(classification, status, request, ex, ex.getMessage());
    }

    private void logRequestFailure(
            String classification,
            HttpStatus status,
            HttpServletRequest request,
            Exception ex,
            String message) {
        Map<String, String> pathVariables = pathVariables(request);
        if (status.is4xxClientError()) {
            log.warn(
                    "event=request_failed classification={} status={} method={} path={} customerId={} itemId={} sessionId={} message={}",
                    classification,
                    status.value(),
                    request.getMethod(),
                    request.getRequestURI(),
                    pathVariables.get("customerId"),
                    pathVariables.get("itemId"),
                    request.getParameter("sessionId"),
                    message,
                    ex);
            return;
        }

        log.error(
                "event=request_failed classification={} status={} method={} path={} customerId={} itemId={} sessionId={}",
                classification,
                status.value(),
                request.getMethod(),
                request.getRequestURI(),
                pathVariables.get("customerId"),
                pathVariables.get("itemId"),
                request.getParameter("sessionId"),
                ex);
    }

    @SuppressWarnings("unchecked")
    private Map<String, String> pathVariables(HttpServletRequest request) {
        Object attribute = request.getAttribute(HandlerMapping.URI_TEMPLATE_VARIABLES_ATTRIBUTE);
        if (attribute instanceof Map<?, ?> map) {
            Map<String, String> values = new LinkedHashMap<>();
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                if (entry.getKey() != null && entry.getValue() != null) {
                    values.put(entry.getKey().toString(), entry.getValue().toString());
                }
            }
            return values;
        }
        return Map.of();
    }
}
