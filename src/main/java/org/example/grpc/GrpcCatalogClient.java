package org.example.grpc;

import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import org.example.grpc.contract.CatalogServiceGrpc;
import org.example.grpc.contract.Empty;
import org.example.grpc.contract.IdRequest;
import org.example.model.Item;

import java.util.Iterator;
import java.util.concurrent.TimeUnit;

/** Клиентская заглушка вызывает удаленные методы синхронно. БД клиенту не нужна. */
public class GrpcCatalogClient implements AutoCloseable {
    private final ManagedChannel channel;

    public GrpcCatalogClient(String host, int port) {
        channel = ManagedChannelBuilder.forAddress(host, port).usePlaintext().build();
    }

    private CatalogServiceGrpc.CatalogServiceBlockingStub stub() {
        return CatalogServiceGrpc.newBlockingStub(channel).withDeadlineAfter(5, TimeUnit.SECONDS);
    }

    public Item getById(long id) {
        return GrpcItemMapper.toItem(stub().getById(IdRequest.newBuilder().setId(id).build()));
    }

    /** Читает товары по мере получения сообщений server-side stream. */
    public Iterator<Item> getAll() {
        var messages = stub().getAll(Empty.getDefaultInstance());
        return new Iterator<>() {
            @Override public boolean hasNext() { return messages.hasNext(); }
            @Override public Item next() { return GrpcItemMapper.toItem(messages.next()); }
        };
    }

    public Item add(Item item) {
        return GrpcItemMapper.toItem(stub().add(GrpcItemMapper.toMessage(item)));
    }

    public Item update(Item item) {
        return GrpcItemMapper.toItem(stub().update(GrpcItemMapper.toMessage(item)));
    }

    public void delete(long id) {
        stub().delete(IdRequest.newBuilder().setId(id).build());
    }

    @Override
    public void close() {
        channel.shutdown();
        try {
            if (!channel.awaitTermination(3, TimeUnit.SECONDS)) channel.shutdownNow();
        } catch (InterruptedException e) {
            channel.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }
}
