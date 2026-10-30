package com.billing.invoicehub.exception;

import java.util.Collections;
import java.util.List;

public class InvalidPasswordException extends IllegalArgumentException {

    private final List<String> errorCodes;
    private final List<String> errorMessages;

    public InvalidPasswordException(String message) {
        super(message);
        this.errorCodes = Collections.emptyList();
        this.errorMessages = List.of(message);
    }

    public InvalidPasswordException(String message, List<String> errorCodes, List<String> errorMessages) {
        super(message);
        this.errorCodes = errorCodes != null ? errorCodes : Collections.emptyList();
        this.errorMessages = errorMessages != null ? errorMessages : Collections.emptyList();
    }

    public List<String> getErrorCodes() {
        return errorCodes;
    }

    public List<String> getErrorMessages() {
        return errorMessages;
    }
}

