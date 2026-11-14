package org.example.network;

import org.example.model.Item;
import java.util.List;

public record CatalogResponse(boolean success, String message, List<Item> items) {
    public static CatalogResponse ok(String message) {
        return new CatalogResponse(true, message, List.of());
    }

    public static CatalogResponse error(String message) {
        return new CatalogResponse(false, message, List.of());
    }
}
