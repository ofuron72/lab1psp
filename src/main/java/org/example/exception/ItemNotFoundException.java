package org.example.exception;

public class ItemNotFoundException extends RuntimeException {

    public ItemNotFoundException(long id) {
        super("Товар с ID " + id + " не найден");
    }
}
