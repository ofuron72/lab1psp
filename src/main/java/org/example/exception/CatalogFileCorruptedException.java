package org.example.exception;

public class CatalogFileCorruptedException extends RuntimeException {

    public CatalogFileCorruptedException(String message, Throwable cause) {
        super(message, cause);
    }
}
