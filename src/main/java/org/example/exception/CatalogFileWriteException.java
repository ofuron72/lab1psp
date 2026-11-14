package org.example.exception;

public class CatalogFileWriteException extends RuntimeException {

    public CatalogFileWriteException(String message, Throwable cause) {
        super(message, cause);
    }
}
