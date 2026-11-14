package org.example.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.example.model.Book;
import org.example.model.Item;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;

import java.math.BigDecimal;
import java.nio.file.Path;
import java.util.AbstractCollection;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import static org.junit.jupiter.api.Assertions.*;

/** Проверки согласованной работы потоков с одним файлом каталога. */
@DisplayName("Файл каталога: одновременное чтение и запись")
@Timeout(10)
class CatalogFileServiceTest {
    @TempDir Path directory;

    /**
     * Приостанавливает сохранение после открытия файла, но до записи JSON.
     * Параллельное чтение должно дождаться конца сохранения и вернуть книгу,
     * а не попытаться разобрать еще пустой файл.
     */
    @Test
    @DisplayName("Чтение ожидает завершения записи и получает целый JSON")
    void loadWaitsForConcurrentSave() throws Exception {
        var files = new CatalogFileService(new ObjectMapper(), directory.resolve("catalog.json").toString());
        files.save(List.of());
        List<Item> items = List.of(new Book(1L, "Книга", BigDecimal.TEN, "Автор", 100));
        var savingStarted = new CountDownLatch(1);
        var allowSave = new CountDownLatch(1);
        var readingStarted = new CountDownLatch(1);
        var pausedItems = new AbstractCollection<Item>() {
            @Override
            public Iterator<Item> iterator() {
                savingStarted.countDown();
                try {
                    if (!allowSave.await(3, TimeUnit.SECONDS)) {
                        throw new IllegalStateException("Тест не разрешил завершить сохранение");
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new IllegalStateException(e);
                }
                return items.iterator();
            }

            @Override public int size() { return items.size(); }
        };

        try (var pool = Executors.newFixedThreadPool(2)) {
            var saving = pool.submit(() -> files.save(pausedItems));
            try {
                assertTrue(savingStarted.await(3, TimeUnit.SECONDS));
                var reading = pool.submit(() -> {
                    readingStarted.countDown();
                    return files.load();
                });
                assertTrue(readingStarted.await(3, TimeUnit.SECONDS));
                assertThrows(TimeoutException.class, () -> reading.get(200, TimeUnit.MILLISECONDS),
                        "Чтение должно ожидать завершения записи");
                allowSave.countDown();
                saving.get(3, TimeUnit.SECONDS);
                var loaded = reading.get(3, TimeUnit.SECONDS);
                assertEquals(1, loaded.size());
                assertEquals(items.getFirst().displayInfo(), loaded.getFirst().displayInfo());
            } finally {
                allowSave.countDown();
            }
        }
    }
}
