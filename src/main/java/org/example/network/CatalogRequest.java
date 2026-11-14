package org.example.network;

import org.example.dto.CreateBookRequest;
import org.example.dto.CreateJournalRequest;

/** Одна строка JSON — одна команда. */
public record CatalogRequest(String command, CreateBookRequest book,
                             CreateJournalRequest journal, Long id) {
    public static CatalogRequest command(String command) {
        return new CatalogRequest(command, null, null, null);
    }
}
