package org.example.grpc;

import org.example.dto.CreateBookRequest;
import org.example.dto.CreateJournalRequest;
import org.example.grpc.contract.BookDetails;
import org.example.grpc.contract.ItemMessage;
import org.example.grpc.contract.JournalDetails;
import org.example.model.Book;
import org.example.model.Item;
import org.example.model.Journal;

import java.math.BigDecimal;

/** Преобразует сообщения общей библиотеки в модели и DTO приложения. */
public final class GrpcItemMapper {
    private GrpcItemMapper() { }

    public static ItemMessage toMessage(Item item) {
        var message = ItemMessage.newBuilder().setId(item.getId() == null ? 0 : item.getId())
                .setName(item.getName()).setPrice(item.getPrice().toPlainString());
        if (item instanceof Book book) {
            message.setBook(BookDetails.newBuilder().setAuthor(book.getAuthor()).setPageCount(book.getPageCount()));
        } else if (item instanceof Journal journal) {
            message.setJournal(JournalDetails.newBuilder().setPublisher(journal.getPublisher()).setIssueNumber(journal.getIssueNumber()));
        } else {
            throw new IllegalArgumentException("Неизвестный тип товара.");
        }
        return message.build();
    }

    public static Item toItem(ItemMessage message) {
        return switch (message.getDetailsCase()) {
            case BOOK -> new Book(message.getId(), message.getName(), price(message),
                    message.getBook().getAuthor(), message.getBook().getPageCount());
            case JOURNAL -> new Journal(message.getId(), message.getName(), price(message),
                    message.getJournal().getPublisher(), message.getJournal().getIssueNumber());
            default -> throw new IllegalArgumentException("Не указан тип товара.");
        };
    }

    public static CreateBookRequest bookRequest(ItemMessage message) {
        return new CreateBookRequest(message.getName(), price(message), message.getBook().getAuthor(), message.getBook().getPageCount());
    }

    public static CreateJournalRequest journalRequest(ItemMessage message) {
        return new CreateJournalRequest(message.getName(), price(message), message.getJournal().getPublisher(), message.getJournal().getIssueNumber());
    }

    private static BigDecimal price(ItemMessage message) {
        try {
            return new BigDecimal(message.getPrice());
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Неверный формат цены.");
        }
    }
}
