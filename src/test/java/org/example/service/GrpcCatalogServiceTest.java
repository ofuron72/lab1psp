package org.example.service;

import io.grpc.Server;
import io.grpc.ServerBuilder;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import io.grpc.MethodDescriptor;
import org.example.grpc.GrpcCatalogClient;
import org.example.grpc.GrpcCatalogEndpoint;
import org.example.grpc.GrpcItemMapper;
import org.example.grpc.contract.CatalogServiceGrpc;
import org.example.grpc.contract.ItemMessage;
import org.example.model.Book;
import org.example.model.Item;
import org.example.model.Journal;
import org.example.repository.jpa.JpaItemRepository;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

/** Настоящий gRPC по HTTP/2 на свободном порту; сервер читает и изменяет тестовую БД. */
@SpringBootTest(classes = {JpaCatalogServiceTest.Config.class, GrpcCatalogEndpoint.class})
@ActiveProfiles("db")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@Timeout(15)
@DisplayName("gRPC-каталог: контракт, streaming, CRUD и ошибки")
class GrpcCatalogServiceTest {
    @DynamicPropertySource
    static void database(DynamicPropertyRegistry properties) {
        properties.add("spring.datasource.url", () -> System.getProperty("catalog.test.url",
                "jdbc:h2:mem:grpc-catalog;MODE=PostgreSQL;DB_CLOSE_DELAY=-1"));
        properties.add("spring.datasource.username", () -> System.getProperty("catalog.test.user", "sa"));
        properties.add("spring.datasource.password", () -> System.getProperty("catalog.test.password", ""));
    }

    @Autowired GrpcCatalogEndpoint endpoint;
    @Autowired JpaItemRepository repository;
    private Server server;
    private GrpcCatalogClient client;

    @BeforeEach
    void start() throws Exception {
        server = ServerBuilder.forPort(0).addService(endpoint).build().start();
        client = new GrpcCatalogClient("127.0.0.1", server.getPort());
    }

    @AfterEach
    void stop() throws Exception {
        if (client != null) client.close();
        if (server != null) {
            server.shutdownNow();
            assertTrue(server.awaitTermination(5, TimeUnit.SECONDS));
        }
        repository.deleteAll();
    }

    /** Два типа товара проходят через Protobuf, сервис и JPA без потери полей и точности цены. */
    @Test
    @DisplayName("Добавление и поиск книги и журнала через сгенерированную заглушку")
    void createsAndFindsBothTypes() {
        Item book = client.add(book("Книга"));
        Item journal = client.add(new Journal(null, "Журнал", new BigDecimal("150.50"), "Издатель", 7));
        var loadedBook = assertInstanceOf(Book.class, client.getById(book.getId()));
        var loadedJournal = assertInstanceOf(Journal.class, client.getById(journal.getId()));
        assertEquals("Автор", loadedBook.getAuthor());
        assertEquals(100, loadedBook.getPageCount());
        assertEquals(new BigDecimal("199.99"), loadedBook.getPrice());
        assertEquals("Издатель", loadedJournal.getPublisher());
        assertEquals(7, loadedJournal.getIssueNumber());
        assertEquals(2, repository.count());
    }

    /** GetAll имеет тип SERVER_STREAMING и передает пустой либо последовательный поток товаров. */
    @Test
    @DisplayName("GetAll возвращает server-side stream, включая пустой каталог")
    void streamsAllItems() {
        assertEquals(MethodDescriptor.MethodType.SERVER_STREAMING, CatalogServiceGrpc.getGetAllMethod().getType());
        assertFalse(client.getAll().hasNext());
        var first = client.add(book("Первая"));
        var second = client.add(book("Вторая"));
        var third = client.add(book("Третья"));
        List<Long> ids = new ArrayList<>();
        client.getAll().forEachRemaining(item -> ids.add(item.getId()));
        assertEquals(List.of(first.getId(), second.getId(), third.getId()), ids);
    }

    /** Удаленные изменения фиксируются в БД и видны следующему RPC и прямому запросу репозитория. */
    @Test
    @DisplayName("Редактирование и удаление фиксируются в транзакциях PostgreSQL/JPA")
    void updatesAndDeletes() {
        var item = assertInstanceOf(Book.class, client.add(book("Книга")));
        item.setName("Новая книга");
        item.setPrice(new BigDecimal("250.50"));
        item.setPageCount(200);
        client.update(item);
        assertEquals("Новая книга", repository.findById(item.getId()).orElseThrow().getName());
        assertEquals(200, ((Book) client.getById(item.getId())).getPageCount());
        client.delete(item.getId());
        assertFalse(repository.existsById(item.getId()));
    }

    /** Отсутствующий товар возвращает NOT_FOUND, а сервер после ошибки остается доступным. */
    @Test
    @DisplayName("Неизвестный ID возвращает gRPC NOT_FOUND")
    void reportsNotFound() {
        assertStatus(Status.Code.NOT_FOUND, () -> client.getById(Long.MAX_VALUE));
        assertStatus(Status.Code.NOT_FOUND, () -> client.delete(Long.MAX_VALUE));
        var missing = book("Книга");
        missing.setId(Long.MAX_VALUE);
        assertStatus(Status.Code.NOT_FOUND, () -> client.update(missing));
        assertFalse(client.getAll().hasNext());
    }

    /** Сервер валидирует Protobuf-сообщения независимо от консольного интерфейса клиента. */
    @Test
    @DisplayName("Неверный ID, цена и тип возвращают INVALID_ARGUMENT")
    void validatesRequests() throws Exception {
        assertStatus(Status.Code.INVALID_ARGUMENT, () -> client.getById(0));
        assertStatus(Status.Code.INVALID_ARGUMENT, () -> client.add(new Book(null, "Книга", BigDecimal.valueOf(-1), "Автор", 10)));
        var channel = io.grpc.ManagedChannelBuilder.forAddress("127.0.0.1", server.getPort()).usePlaintext().build();
        try {
            var stub = CatalogServiceGrpc.newBlockingStub(channel).withDeadlineAfter(5, TimeUnit.SECONDS);
            assertStatus(Status.Code.INVALID_ARGUMENT, () -> stub.add(ItemMessage.getDefaultInstance()));
            var invalidPrice = GrpcItemMapper.toMessage(book("Книга")).toBuilder().setPrice("abc").build();
            assertStatus(Status.Code.INVALID_ARGUMENT, () -> stub.add(invalidPrice));
        } finally {
            channel.shutdownNow();
            assertTrue(channel.awaitTermination(3, TimeUnit.SECONDS));
        }
        var existing = client.add(book("Книга"));
        assertStatus(Status.Code.INVALID_ARGUMENT, () -> client.update(new Journal(existing.getId(), "Журнал", BigDecimal.TEN, "Издатель", 1)));
        assertInstanceOf(Book.class, client.getById(existing.getId()));
    }

    /** Независимые клиенты используют одну БД; их новые товары получают разные ID. */
    @Test
    @DisplayName("Несколько gRPC-клиентов работают с общей базой")
    void supportsIndependentClients() throws Exception {
        try (var pool = Executors.newVirtualThreadPerTaskExecutor()) {
            List<Callable<Long>> calls = new ArrayList<>();
            for (int i = 0; i < 6; i++) {
                int number = i;
                calls.add(() -> {
                    try (var other = new GrpcCatalogClient("127.0.0.1", server.getPort())) {
                        return other.add(book("Книга " + number)).getId();
                    }
                });
            }
            var ids = new ArrayList<Long>();
            for (var future : pool.invokeAll(calls)) ids.add(future.get());
            assertEquals(6, ids.stream().distinct().count());
            assertEquals(6, repository.count());
        }
    }

    private void assertStatus(Status.Code code, org.junit.jupiter.api.function.Executable call) {
        assertEquals(code, assertThrows(StatusRuntimeException.class, call).getStatus().getCode());
    }

    private Book book(String name) {
        return new Book(null, name, new BigDecimal("199.99"), "Автор", 100);
    }
}
