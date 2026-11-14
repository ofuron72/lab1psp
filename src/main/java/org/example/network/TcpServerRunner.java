package org.example.network;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.example.exception.CatalogFileCorruptedException;
import org.example.service.CatalogFileService;
import org.example.service.CatalogService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.stereotype.Component;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.List;

@Component
@ConditionalOnExpression("'${catalog.mode:console}' == 'server-single' or '${catalog.mode:console}' == 'server-multi'")
public class TcpServerRunner implements CommandLineRunner {
    private final CatalogService catalog;
    private final CatalogFileService files;
    private final CatalogCommandHandler commands;
    private final ObjectMapper mapper;
    private final String mode;
    private final String host;
    private final int port;

    public TcpServerRunner(CatalogService catalog, CatalogFileService files, CatalogCommandHandler commands,
                           ObjectMapper mapper, @Value("${catalog.mode}") String mode,
                           @Value("${catalog.server.host:0.0.0.0}") String host,
                           @Value("${catalog.port:8888}") int port) {
        this.catalog = catalog;
        this.files = files;
        this.commands = commands;
        this.mapper = mapper;
        this.mode = mode;
        this.host = host;
        this.port = port;
    }

    @Override
    public void run(String... args) {
        try {
            catalog.replaceAll(files.load());
            System.out.println("Загружено товаров: " + catalog.getAll().size());
        } catch (FileNotFoundException | CatalogFileCorruptedException e) {
            catalog.replaceAll(List.of());
            System.out.println("Каталог не загружен, начинаем с пустого: " + e.getMessage());
        }
        try (var server = new TcpCatalogServer(host, port, mode.equals("server-multi"), mapper, commands)) {
            Thread shutdown = new Thread(() -> {
                try { server.close(); } catch (IOException e) { System.out.println(e.getMessage()); }
            }, "tcp-shutdown");
            Runtime.getRuntime().addShutdownHook(shutdown);
            try {
                server.serve();
            } finally {
                try { Runtime.getRuntime().removeShutdownHook(shutdown); }
                catch (IllegalStateException ignored) { /* JVM уже завершается. */ }
            }
        } catch (IOException e) {
            System.out.println("Ошибка TCP-сервера: " + e.getMessage());
        }
    }
}
