package com.freightfox.transporter_assignment.exception;

import java.util.List;

public class InvalidInputException extends RuntimeException{

    private final List<Violation> violations;

    public InvalidInputException(String message, List<Violation> violations) {
        super(message);
        this.violations = List.copyOf(violations);
    }

    public List<Violation> getViolations() {
        return violations;
    }

    public record Violation(String field, String message){}
}
