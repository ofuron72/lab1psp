package org.example.network;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.example.exception.CatalogFileWriteException;

import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/** В последовательном режиме обработчик работает прямо в потоке accept(). */
public class TcpCatalogServer implements AutoCloseable {
    private final ServerSocket listener;
    private final boolean parallel;
    private final ObjectMapper mapper;
    private final CatalogCommandHandler commands;
    private final ExecutorService workers = Executors.newVirtualThreadPerTaskExecutor();
    private final Set<Socket> clients = ConcurrentHashMap.newKeySet();
    private volatile boolean closed;

    public TcpCatalogServer(String host, int port, boolean parallel,
                            ObjectMapper mapper, CatalogCommandHandler commands) throws IOException {
        this.parallel = parallel;
        this.mapper = mapper;
        this.commands = commands;
        listener = new ServerSocket();
        try {
            listener.setReuseAddress(true);
            listener.bind(new InetSocketAddress(host, port));
        } catch (IOException | RuntimeException e) {
            listener.close();
            throw e;
        }
    }

    public int port() { return listener.getLocalPort(); }

    public void serve() throws IOException {
        System.out.println("TCP-сервер: " + listener.getLocalSocketAddress()
                + (parallel ? " (многопоточный)" : " (последовательный)"));
        while (!closed) {
            Socket client;
            try {
                client = listener.accept();
            } catch (SocketException e) {
                if (closed) return;
                throw e;
            }
            clients.add(client);
            if (parallel) {
                synchronized (this) {
                    if (closed) {
                        client.close();
                        clients.remove(client);
                        return;
                    }
                    workers.submit(() -> handleClient(client));
                }
            } else {
                if (closed) {
                    client.close();
                    clients.remove(client);
                    return;
                }
                handleClient(client);
            }
        }
    }

    private void handleClient(Socket client) {
        System.out.println("Подключён клиент " + client.getRemoteSocketAddress());
        try (client;
             var in = new BufferedReader(new InputStreamReader(client.getInputStream(), StandardCharsets.UTF_8));
             var out = new BufferedWriter(new OutputStreamWriter(client.getOutputStream(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = in.readLine()) != null) {
                CatalogRequest request = null;
                CatalogResponse response;
                try {
                    request = mapper.readValue(line, CatalogRequest.class);
                    response = commands.execute(request);
                } catch (JsonProcessingException e) {
                    response = CatalogResponse.error("Неверный формат JSON.");
                }
                out.write(mapper.writeValueAsString(response));
                out.newLine();
                out.flush();
                if (request != null && "QUIT".equals(request.command()) && response.success()) break;
            }
        } catch (IOException e) {
            if (!closed) System.out.println("Соединение с клиентом прервано: " + e.getMessage());
        } finally {
            clients.remove(client);
            try {
                commands.save();
            } catch (CatalogFileWriteException e) {
                System.out.println("Ошибка сохранения: " + e.getMessage());
            }
            System.out.println("Клиент отключён " + client.getRemoteSocketAddress());
        }
    }

    @Override
    public synchronized void close() throws IOException {
        if (closed) return;
        closed = true;
        listener.close();
        for (Socket client : clients) {
            try { client.close(); } catch (IOException ignored) { }
        }
        workers.shutdown();
        try {
            if (!workers.awaitTermination(5, TimeUnit.SECONDS)) workers.shutdownNow();
        } catch (InterruptedException e) {
            workers.shutdownNow();
            Thread.currentThread().interrupt();
        }
        try {
            commands.save();
        } catch (CatalogFileWriteException e) {
            System.out.println("Ошибка сохранения: " + e.getMessage());
        }
    }
}
