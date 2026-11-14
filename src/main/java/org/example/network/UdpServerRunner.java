package org.example.network;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.example.model.Book;
import org.example.model.Journal;
import org.example.service.CatalogService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.math.BigDecimal;
import java.util.List;

@Component
@ConditionalOnProperty(name = "catalog.mode", havingValue = "udp-server")
public class UdpServerRunner implements CommandLineRunner {
    private final CatalogService catalog;
    private final ObjectMapper mapper;
    private final String host;
    private final int port;

    public UdpServerRunner(CatalogService catalog, ObjectMapper mapper,
                           @Value("${catalog.server.host:0.0.0.0}") String host,
                           @Value("${catalog.port:8888}") int port) {
        this.catalog = catalog;
        this.mapper = mapper;
        this.host = host;
        this.port = port;
    }

    @Override public void run(String... args) {
        catalog.replaceAll(List.of(
                new Book(1L, "Война и мир", new BigDecimal("899.90"), "Лев Толстой", 1274),
                new Journal(2L, "Наука и жизнь", new BigDecimal("320.00"), "АНО Наука и жизнь", 9),
                new Book(3L, "Мастер и Маргарита", new BigDecimal("650.00"), "Михаил Булгаков", 480)));
        System.out.println("Тестовые товары UDP: ID 1, 2, 3. Файл каталога не используется.");
        try (var server = new UdpCatalogServer(host, port, catalog, mapper)) {
            var shutdown = new Thread(server::close, "udp-shutdown");
            Runtime.getRuntime().addShutdownHook(shutdown);
            try {
                server.serve();
            } finally {
                try { Runtime.getRuntime().removeShutdownHook(shutdown); }
                catch (IllegalStateException ignored) { /* JVM уже завершается. */ }
            }
        } catch (IOException e) {
            System.out.println("Ошибка UDP-сервера: " + e.getMessage());
        }
    }
}
