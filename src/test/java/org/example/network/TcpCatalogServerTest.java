package org.example.network;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.example.dto.CreateBookRequest;
import org.example.dto.CreateJournalRequest;
import org.example.repository.InMemoryItemRepository;
import org.example.service.CatalogFileService;
import org.example.service.CatalogService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;

import java.io.*;
import java.math.BigDecimal;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.*;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Проверки TCP-сервера с настоящими соединениями на локальном адресе.
 * Каждый тест использует свободный порт и отдельный временный файл каталога.
 */
@DisplayName("TCP-каталог: последовательная и многопоточная обработка")
@Timeout(15)
class TcpCatalogServerTest {
    @TempDir Path directory;
    private final ObjectMapper mapper = new ObjectMapper();
    private final ExecutorService acceptor = Executors.newSingleThreadExecutor();
    private TcpCatalogServer server;
    private CatalogFileService files;
    private Future<?> serving;

    private void start(boolean parallel) throws IOException {
        files = new CatalogFileService(mapper, directory.resolve("catalog.json").toString());
        var catalog = new CatalogService(new InMemoryItemRepository());
        server = new TcpCatalogServer("127.0.0.1", 0, parallel, mapper,
                new CatalogCommandHandler(catalog, files));
        serving = acceptor.submit(() -> {
            try { server.serve(); }
            catch (IOException e) { throw new UncheckedIOException(e); }
        });
    }

    @AfterEach
    void stop() throws Exception {
        if (server != null) server.close();
        if (serving != null) serving.get(3, TimeUnit.SECONDS);
        acceptor.shutdownNow();
    }

    /**
     * Первый клиент удерживает соединение, второй отправляет LIST и не получает ответ.
     * После QUIT первого клиента второй должен получить ответ на уже отправленную команду.
     */
    @Test
    @DisplayName("Последовательный сервер: второй клиент ждет отключения первого")
    void singleServerDoesNotAnswerSecondClientUntilFirstDisconnects() throws Exception {
        start(false);
        try (var first = new Client(); var second = new Client()) {
            assertTrue(first.call(CatalogRequest.command("LIST")).success());
            second.send(CatalogRequest.command("LIST"));
            second.socket.setSoTimeout(300);
            assertThrows(SocketTimeoutException.class, second::receive);
            assertTrue(first.call(CatalogRequest.command("QUIT")).success());
            second.socket.setSoTimeout(3000);
            assertTrue(second.receive().success());
        }
    }

    /**
     * Оба клиента выполняют LIST, не закрывая соединения.
     * Ожидание новых команд первым клиентом не должно блокировать второго.
     */
    @Test
    @DisplayName("Многопоточный сервер: оба клиента получают ответы при открытых соединениях")
    void parallelServerAnswersBothWhileFirstConnectionStaysOpen() throws Exception {
        start(true);
        try (var first = new Client(); var second = new Client()) {
            assertTrue(first.call(CatalogRequest.command("LIST")).success());
            assertTrue(second.call(CatalogRequest.command("LIST")).success());
            assertTrue(first.call(CatalogRequest.command("LIST")).success());
        }
    }

    /**
     * Разные клиенты добавляют книгу и журнал в общий каталог.
     * Проверяются восстановление обоих типов из JSON, видимость удаления
     * другому клиенту и сохранение оставшейся записи после QUIT.
     */
    @Test
    @DisplayName("Общий каталог: добавление, удаление и сохранение книг и журналов")
    void catalogIsSharedAndSavedWithBothItemTypes() throws Exception {
        start(true);
        try (var first = new Client(); var second = new Client()) {
            assertTrue(first.call(book("Война и мир")).success());
            assertTrue(second.call(new CatalogRequest("ADD_JOURNAL", null,
                    new CreateJournalRequest("Наука", new BigDecimal("100.50"), "Издатель", 2), null)).success());
            var items = first.call(CatalogRequest.command("LIST")).items();
            assertEquals(2, items.size());
            assertEquals("Война и мир", items.getFirst().getName());
            assertTrue(second.call(CatalogRequest.command("SAVE")).success());
            assertEquals(2, files.load().size());
            assertEquals(items.get(1).displayInfo(), files.load().get(1).displayInfo());
            assertTrue(first.call(new CatalogRequest("DELETE", null, null, items.getFirst().getId())).success());
            assertEquals(1, second.call(CatalogRequest.command("LIST")).items().size());
            assertTrue(second.call(CatalogRequest.command("QUIT")).success());
            assertEquals(1, files.load().size());
        }
    }

    /**
     * Отправляет неверный JSON, null, неизвестную команду и некорректные данные.
     * Сервер должен отвечать ошибкой и продолжать обработку того же соединения.
     */
    @Test
    @DisplayName("Ошибки запросов: соединение остается доступным")
    void invalidRequestsDoNotBreakConnection() throws Exception {
        start(true);
        try (var client = new Client()) {
            client.raw("not json");
            assertFalse(client.receive().success());
            client.raw("null");
            assertFalse(client.receive().success());
            assertFalse(client.call(CatalogRequest.command("UNKNOWN")).success());
            assertFalse(client.call(CatalogRequest.command("ADD_BOOK")).success());
            assertFalse(client.call(new CatalogRequest("ADD_BOOK",
                    new CreateBookRequest("Книга", BigDecimal.TEN, "Автор", -1), null, null)).success());
            assertFalse(client.call(new CatalogRequest("DELETE", null, null, 999L)).success());
            assertTrue(client.call(CatalogRequest.command("LIST")).items().isEmpty());
        }
    }

    /**
     * Двенадцать клиентов параллельно добавляют товары и сохраняют каталог.
     * Проверяется отсутствие потерянных записей и повторяющихся ID,
     * а также возможность прочитать все товары из сохраненного файла.
     */
    @Test
    @DisplayName("12 клиентов: параллельные добавления и сохранения без потери товаров")
    void simultaneousAddsAndSavesDoNotLoseItemsOrDuplicateIds() throws Exception {
        start(true);
        try (var pool = Executors.newVirtualThreadPerTaskExecutor()) {
            List<Callable<Void>> tasks = IntStream.range(0, 12).mapToObj(i -> (Callable<Void>) () -> {
                try (var client = new Client()) {
                    assertTrue(client.call(book("Книга " + i)).success());
                    assertTrue(client.call(CatalogRequest.command("QUIT")).success());
                }
                return null;
            }).toList();
            for (var future : pool.invokeAll(tasks)) future.get();
        }
        try (var client = new Client()) {
            var items = client.call(CatalogRequest.command("LIST")).items();
            assertEquals(12, items.size());
            assertEquals(12, items.stream().map(item -> item.getId()).distinct().count());
            assertTrue(client.call(CatalogRequest.command("SAVE")).success());
            assertEquals(12, files.load().size());
        }
    }

    /**
     * Первый клиент добавляет книгу и принудительно сбрасывает TCP-соединение.
     * Последовательный сервер должен принять следующего клиента и вернуть каталог.
     */
    @Test
    @DisplayName("Обрыв соединения: сервер продолжает обслуживать клиентов")
    void abruptDisconnectDoesNotStopServer() throws Exception {
        start(false);
        try (var first = new Client()) {
            assertTrue(first.call(book("Книга")).success());
            first.socket.setSoLinger(true, 0);
        }
        try (var second = new Client()) {
            assertEquals(1, second.call(CatalogRequest.command("LIST")).items().size());
        }
    }

    /**
     * Вместо пути к файлу задается папка, чтобы вызвать ошибку записи.
     * QUIT должен вернуть ошибку, после которой клиент может выполнить LIST.
     */
    @Test
    @DisplayName("Ошибка сохранения: клиент получает сообщение и может продолжить работу")
    void failedSaveReturnsErrorAndKeepsConnectionOpen() throws Exception {
        var catalog = new CatalogService(new InMemoryItemRepository());
        files = new CatalogFileService(mapper, directory.toString()); // Каталог вместо файла.
        server = new TcpCatalogServer("127.0.0.1", 0, true, mapper,
                new CatalogCommandHandler(catalog, files));
        serving = acceptor.submit(() -> {
            try { server.serve(); } catch (IOException e) { throw new UncheckedIOException(e); }
        });
        try (var client = new Client()) {
            assertFalse(client.call(CatalogRequest.command("QUIT")).success());
            assertTrue(client.call(CatalogRequest.command("LIST")).success());
        }
    }

    private CatalogRequest book(String name) {
        return new CatalogRequest("ADD_BOOK",
                new CreateBookRequest(name, new BigDecimal("199.99"), "Автор", 100), null, null);
    }

    private class Client implements AutoCloseable {
        final Socket socket;
        final BufferedReader input;
        final BufferedWriter output;

        Client() throws IOException {
            socket = new Socket("127.0.0.1", server.port());
            socket.setSoTimeout(3000);
            input = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
            output = new BufferedWriter(new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8));
        }

        void raw(String line) throws IOException {
            output.write(line);
            output.newLine();
            output.flush();
        }

        void send(CatalogRequest request) throws IOException { raw(mapper.writeValueAsString(request)); }

        CatalogResponse receive() throws IOException {
            String line = input.readLine();
            assertNotNull(line, "Сервер закрыл соединение без ответа");
            return mapper.readValue(line, CatalogResponse.class);
        }

        CatalogResponse call(CatalogRequest request) throws IOException {
            send(request);
            return receive();
        }

        @Override public void close() throws IOException { socket.close(); }
    }
}
