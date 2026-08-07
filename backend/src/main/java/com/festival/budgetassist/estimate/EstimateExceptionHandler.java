package com.festival.budgetassist.estimate;

import java.time.Instant;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * 잘못된 코드 값(regionCode 등)이나 데이터 없음 상태를 사람이 읽을 수 있는 400 응답으로 바꾼다.
 * estimate 패키지 컨트롤러에만 적용한다.
 */
@RestControllerAdvice(basePackageClasses = { BudgetEstimateController.class })
class EstimateExceptionHandler {

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, Object>> handleValidation(MethodArgumentNotValidException e) {
        String message = e.getBindingResult().getFieldErrors().stream()
                .map(err -> "%s: %s".formatted(err.getField(), err.getDefaultMessage()))
                .collect(Collectors.joining(", "));
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(errorBody(message));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, Object>> handleBadRequest(IllegalArgumentException e) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(errorBody(e.getMessage()));
    }

    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<Map<String, Object>> handleNoData(IllegalStateException e) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(errorBody(e.getMessage()));
    }

    private Map<String, Object> errorBody(String message) {
        return Map.of(
                "timestamp", Instant.now().toString(),
                "message", message
        );
    }
}