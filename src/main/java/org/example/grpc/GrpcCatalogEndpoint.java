package org.example.grpc;

import io.grpc.Status;
import io.grpc.stub.ServerCallStreamObserver;
import io.grpc.stub.StreamObserver;
import lombok.RequiredArgsConstructor;
import org.example.exception.ItemNotFoundException;
import org.example.grpc.contract.*;
import org.example.service.JpaCatalogService;
import org.springframework.context.annotation.Profile;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Component;

import java.util.function.Supplier;

/** Удаленный объект: синхронная бизнес-логика поверх сгенерированного gRPC-интерфейса. */
@Component
@Profile("db")
@RequiredArgsConstructor
public class GrpcCatalogEndpoint extends CatalogServiceGrpc.CatalogServiceImplBase {
    private final JpaCatalogService catalog;

    @Override
    public void getAll(Empty request, StreamObserver<ItemMessage> response) {
        try {
            for (var item : catalog.getAll()) {
                if (response instanceof ServerCallStreamObserver<?> stream && stream.isCancelled()) return;
                response.onNext(GrpcItemMapper.toMessage(item));
            }
            response.onCompleted();
        } catch (RuntimeException e) {
            response.onError(status(e).asRuntimeException());
        }
    }

    @Override
    public void getById(IdRequest request, StreamObserver<ItemMessage> response) {
        reply(response, () -> {
            positiveId(request.getId());
            return GrpcItemMapper.toMessage(catalog.getById(request.getId()));
        });
    }

    @Override
    public void add(ItemMessage request, StreamObserver<ItemMessage> response) {
        reply(response, () -> {
            if (request.getId() != 0) throw new IllegalArgumentException("При добавлении ID назначает сервер.");
            return GrpcItemMapper.toMessage(switch (request.getDetailsCase()) {
                case BOOK -> catalog.addBook(GrpcItemMapper.bookRequest(request));
                case JOURNAL -> catalog.addJournal(GrpcItemMapper.journalRequest(request));
                default -> throw new IllegalArgumentException("Не указан тип товара.");
            });
        });
    }

    @Override
    public void update(ItemMessage request, StreamObserver<ItemMessage> response) {
        reply(response, () -> {
            positiveId(request.getId());
            return GrpcItemMapper.toMessage(switch (request.getDetailsCase()) {
                case BOOK -> catalog.updateBook(request.getId(), GrpcItemMapper.bookRequest(request));
                case JOURNAL -> catalog.updateJournal(request.getId(), GrpcItemMapper.journalRequest(request));
                default -> throw new IllegalArgumentException("Не указан тип товара.");
            });
        });
    }

    @Override
    public void delete(IdRequest request, StreamObserver<Empty> response) {
        reply(response, () -> {
            positiveId(request.getId());
            catalog.deleteById(request.getId());
            return Empty.getDefaultInstance();
        });
    }

    private <T> void reply(StreamObserver<T> response, Supplier<T> operation) {
        try {
            response.onNext(operation.get());
            response.onCompleted();
        } catch (RuntimeException e) {
            response.onError(status(e).asRuntimeException());
        }
    }

    private Status status(RuntimeException error) {
        if (error instanceof ItemNotFoundException) return Status.NOT_FOUND.withDescription(error.getMessage());
        if (error instanceof IllegalArgumentException) return Status.INVALID_ARGUMENT.withDescription(error.getMessage());
        if (error instanceof DataAccessException) return Status.INTERNAL.withDescription("Ошибка базы данных.");
        return Status.INTERNAL.withDescription("Ошибка обработки запроса.");
    }

    private void positiveId(long id) {
        if (id <= 0) throw new IllegalArgumentException("ID должен быть больше нуля.");
    }
}
