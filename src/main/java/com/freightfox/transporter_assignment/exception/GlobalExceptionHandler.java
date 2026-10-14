package com.freightfox.transporter_assignment.exception;

import com.freightfox.transporter_assignment.dto.response.ApiErrorResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(InvalidInputException.class)
    public ResponseEntity<ApiErrorResponse> handleInvalidInput(
            InvalidInputException ex) {

        var details = ex.getViolations().stream()
                .map(v -> new ApiErrorResponse.FieldErrorDetail(
                        v.field(), v.message()))
                .toList();

        ApiErrorResponse response = ApiErrorResponse.of(
                HttpStatus.BAD_REQUEST.value(),
                "INVALID_INPUT",
                ex.getMessage(),
                details
        );

        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
    }

    @ExceptionHandler(DatasetNotFoundException.class)
    public ResponseEntity<ApiErrorResponse> handleDatasetNotFound(
            DatasetNotFoundException ex) {

        ApiErrorResponse response = ApiErrorResponse.of(
                HttpStatus.NOT_FOUND.value(),
                "DATASET_NOT_FOUND",
                ex.getMessage()
        );

        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(response);
    }

    @ExceptionHandler(NoFeasibleAssignmentException.class)
    public ResponseEntity<ApiErrorResponse> handleNoFeasibleAssignment(
            NoFeasibleAssignmentException ex) {

        ApiErrorResponse response = ApiErrorResponse.of(
                HttpStatus.UNPROCESSABLE_ENTITY.value(),
                "NO_FEASIBLE_ASSIGNMENT",
                ex.getMessage()
        );

        return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY)
                .body(response);
    }
}
