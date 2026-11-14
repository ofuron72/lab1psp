package org.example.network;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.example.exception.ItemNotFoundException;
import org.example.model.Book;
import org.example.model.Journal;
import org.example.repository.InMemoryItemRepository;
import org.example.service.CatalogService;
import org.junit.jupiter.api.*;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.math.BigDecimal;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.*;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.*;

/** Проверки UDP-обмена настоящими датаграммами на свободном локальном порту. */
@DisplayName("UDP-каталог: поиск товаров и обмен датаграммами")
@Timeout(10)
class UdpCatalogServerTest {
    private final ObjectMapper mapper = new ObjectMapper();
    private final ExecutorService acceptor = Executors.newSingleThreadExecutor();
    private UdpCatalogServer server;
    private Future<?> serving;

    @BeforeEach
    void start() throws IOException {
        var catalog = new CatalogService(new InMemoryItemRepository());
        catalog.replaceAll(List.of(
                new Book(1L, "Война и мир", new BigDecimal("899.90"), "Лев Толстой", 1274),
                new Journal(2L, "Наука и жизнь", new BigDecimal("320.00"), "Издатель", 9)));
        server = new UdpCatalogServer("127.0.0.1", 0, catalog, mapper);
        serving = acceptor.submit(() -> {
            try { server.serve(); }
            catch (IOException e) { throw new UncheckedIOException(e); }
        });
    }

    @AfterEach
    void stop() throws Exception {
        if (server != null) server.close();
        try {
            if (serving != null) serving.get(3, TimeUnit.SECONDS);
        } finally {
            acceptor.shutdownNow();
        }
    }

    private UdpCatalogClient client() {
        return new UdpCatalogClient("127.0.0.1", server.port(), 2000, mapper);
    }

    /** По GET:1 клиент должен восстановить Book с автором, ценой и количеством страниц. */
    @Test
    @DisplayName("Поиск книги: JSON восстанавливается в Book")
    void findsBookWithAllFields() throws Exception {
        var book = assertInstanceOf(Book.class, client().findById(1));
        assertEquals("Война и мир", book.getName());
        assertEquals("Лев Толстой", book.getAuthor());
        assertEquals(1274, book.getPageCount());
        assertEquals(new BigDecimal("899.90"), book.getPrice());
        assertTrue(book.displayInfo().contains("[Книга]"));
    }

    /** По GET:2 клиент восстанавливает Journal и выводит специфичные для журнала поля. */
    @Test
    @DisplayName("Поиск журнала: JSON восстанавливается в Journal")
    void findsJournalWithAllFields() throws Exception {
        var journal = assertInstanceOf(Journal.class, client().findById(2));
        assertEquals("Наука и жизнь", journal.getName());
        assertEquals("Издатель", journal.getPublisher());
        assertEquals(9, journal.getIssueNumber());
        assertEquals(new BigDecimal("320.00"), journal.getPrice());
        assertTrue(journal.displayInfo().contains("[Журнал]"));
    }

    /** Отсутствующий ID возвращает JSON с Error, который клиент преобразует в понятную ошибку. */
    @Test
    @DisplayName("Неизвестный ID: ответ Error=Not found")
    void missingItemReturnsError() throws Exception {
        assertEquals("Not found", mapper.readTree(raw("GET:999")).path("Error").asText());
        assertThrows(ItemNotFoundException.class, () -> client().findById(999));
        assertInstanceOf(Book.class, client().findById(1));
    }

    /** Неверные команды, нулевой ID и переполнение числа не останавливают цикл приема. */
    @Test
    @DisplayName("Неверные команды: ошибка формата и продолжение работы сервера")
    void badRequestsDoNotStopServer() throws Exception {
        for (String command : List.of("", "LIST", "GET:abc", "GET:0", "GET:-1", "GET:999999999999999999999999")) {
            assertEquals("Bad request", mapper.readTree(raw(command)).path("Error").asText());
        }
        assertInstanceOf(Journal.class, client().findById(2));
    }

    /** Двенадцать независимых клиентов получают ответ на свой ID без смешивания датаграмм. */
    @Test
    @DisplayName("12 UDP-клиентов: каждый получает запрошенный товар")
    void independentClientsReceiveTheirOwnResponses() throws Exception {
        try (var pool = Executors.newVirtualThreadPerTaskExecutor()) {
            List<Callable<Void>> requests = IntStream.range(0, 12).mapToObj(i -> (Callable<Void>) () -> {
                long id = i % 2 + 1;
                assertEquals(id, client().findById(id).getId());
                return null;
            }).toList();
            for (var result : pool.invokeAll(requests)) result.get();
        }
    }

    /** Сокет принимает адресованные ему пакеты, но не отвечает: клиент обязан завершить ожидание. */
    @Test
    @DisplayName("Нет ответа: ожидание завершается по тайм-ауту")
    void unansweredRequestTimesOut() throws Exception {
        try (var silent = new DatagramSocket(new InetSocketAddress("127.0.0.1", 0))) {
            var client = new UdpCatalogClient("127.0.0.1", silent.getLocalPort(), 200, mapper);
            assertThrows(SocketTimeoutException.class, () -> client.findById(1));
        }
    }

    private String raw(String command) throws IOException {
        try (var socket = new DatagramSocket()) {
            socket.setSoTimeout(2000);
            byte[] bytes = command.getBytes(StandardCharsets.UTF_8);
            socket.send(new DatagramPacket(bytes, bytes.length, InetAddress.getByName("127.0.0.1"), server.port()));
            var response = new DatagramPacket(new byte[65507], 65507);
            socket.receive(response);
            return new String(response.getData(), response.getOffset(), response.getLength(), StandardCharsets.UTF_8);
        }
    }
}
