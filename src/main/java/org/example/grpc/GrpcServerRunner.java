package org.example.grpc;

import io.grpc.Server;
import io.grpc.netty.shaded.io.grpc.netty.NettyServerBuilder;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.concurrent.TimeUnit;

@Component
@Profile("db")
@ConditionalOnProperty(name = "catalog.mode", havingValue = "grpc-server")
public class GrpcServerRunner implements CommandLineRunner {
    private final GrpcCatalogEndpoint service;
    private final String host;
    private final int port;

    public GrpcServerRunner(GrpcCatalogEndpoint service,
                            @Value("${catalog.grpc.server-host:0.0.0.0}") String host,
                            @Value("${catalog.grpc.port:9090}") int port) {
        this.service = service;
        this.host = host;
        this.port = port;
    }

    @Override
    public void run(String... args) throws IOException, InterruptedException {
        Server server = NettyServerBuilder.forAddress(new InetSocketAddress(host, port)).addService(service).build().start();
        var shutdown = new Thread(() -> stop(server), "grpc-shutdown");
        Runtime.getRuntime().addShutdownHook(shutdown);
        System.out.println("gRPC-сервер: " + host + ":" + server.getPort() + ", каталог PostgreSQL");
        try {
            server.awaitTermination();
        } finally {
            stop(server);
            try { Runtime.getRuntime().removeShutdownHook(shutdown); }
            catch (IllegalStateException ignored) { /* JVM уже завершается. */ }
        }
    }

    private void stop(Server server) {
        server.shutdown();
        try {
            if (!server.awaitTermination(5, TimeUnit.SECONDS)) server.shutdownNow();
        } catch (InterruptedException e) {
            server.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }
}
