package org.example.service;

import lombok.RequiredArgsConstructor;
import org.example.dto.CreateBookRequest;
import org.example.dto.CreateJournalRequest;
import org.example.model.Book;
import org.example.model.Item;
import org.example.model.Journal;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * Асинхронный вход в операции каталога. Spring запускает методы в catalogExecutor.
 * Отдельный JpaCatalogService открывает транзакцию уже в рабочем потоке:
 * future завершается только после возврата из транзакционного сервиса и commit.
 */
@Service
@Profile("db")
@RequiredArgsConstructor
@Async("catalogExecutor")
public class AsyncJpaCatalogService {
    private final JpaCatalogService catalog;

    public CompletableFuture<List<Item>> getAll() {
        return CompletableFuture.completedFuture(catalog.getAll());
    }

    public CompletableFuture<Item> getById(long id) {
        return CompletableFuture.completedFuture(catalog.getById(id));
    }

    public CompletableFuture<Book> addBook(CreateBookRequest request) {
        return CompletableFuture.completedFuture(catalog.addBook(request));
    }

    public CompletableFuture<Journal> addJournal(CreateJournalRequest request) {
        return CompletableFuture.completedFuture(catalog.addJournal(request));
    }

    public CompletableFuture<Book> updateBook(long id, CreateBookRequest request) {
        return CompletableFuture.completedFuture(catalog.updateBook(id, request));
    }

    public CompletableFuture<Journal> updateJournal(long id, CreateJournalRequest request) {
        return CompletableFuture.completedFuture(catalog.updateJournal(id, request));
    }

    public CompletableFuture<Void> deleteById(long id) {
        catalog.deleteById(id);
        return CompletableFuture.completedFuture(null);
    }
}
