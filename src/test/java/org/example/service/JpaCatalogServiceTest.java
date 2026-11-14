package org.example.service;

import jakarta.persistence.EntityManager;
import org.example.dto.CreateBookRequest;
import org.example.dto.CreateJournalRequest;
import org.example.exception.ItemNotFoundException;
import org.example.model.Book;
import org.example.model.Item;
import org.example.model.Journal;
import org.example.repository.jpa.JpaItemRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.*;

/** Интеграционные проверки JPA и SQL-миграции; каждая операция теста откатывается. */
@SpringBootTest(classes = JpaCatalogServiceTest.Config.class)
@ActiveProfiles("db")
@Transactional
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@DisplayName("Каталог БД: Liquibase, Hibernate и сервис")
class JpaCatalogServiceTest {
    @Configuration
    @EnableAutoConfiguration
    @EntityScan(basePackageClasses = Item.class)
    @EnableJpaRepositories(basePackageClasses = JpaItemRepository.class)
    @Import(JpaCatalogService.class)
    static class Config { }

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry properties) {
        // По умолчанию тесты не требуют Docker. Для PostgreSQL задайте catalog.test.url/user/password.
        properties.add("spring.datasource.url", () -> System.getProperty("catalog.test.url",
                "jdbc:h2:mem:jpa-catalog;MODE=PostgreSQL;DB_CLOSE_DELAY=-1"));
        properties.add("spring.datasource.username", () -> System.getProperty("catalog.test.user", "sa"));
        properties.add("spring.datasource.password", () -> System.getProperty("catalog.test.password", ""));
    }

    @Autowired JpaCatalogService catalog;
    @Autowired JpaItemRepository repository;
    @Autowired EntityManager entityManager;

    /** После сохранения очищает persistence context и проверяет реальные строки и типы в БД. */
    @Test
    @DisplayName("Книга и журнал сохраняются в одной таблице и восстанавливаются по типу")
    void persistsBothTypes() {
        var book = catalog.addBook(book("Книга"));
        var journal = catalog.addJournal(journal("Журнал"));
        long bookId = book.getId();
        long journalId = journal.getId();
        flushAndClear();
        assertNotEquals(bookId, journalId);
        assertEquals(2, catalog.getAll().size());
        assertEquals("Автор", assertInstanceOf(Book.class, catalog.getById(bookId)).getAuthor());
        assertEquals(7, assertInstanceOf(Journal.class, catalog.getById(journalId)).getIssueNumber());
        assertEquals("book", entityManager.createNativeQuery("select type from products where id = :id")
                .setParameter("id", bookId).getSingleResult());
    }

    /** Проверяет редактирование общих и специфичных полей обоих типов товара. */
    @Test
    @DisplayName("Редактирование книги и журнала сохраняется в БД")
    void editsBothTypes() {
        long bookId = catalog.addBook(book("Книга")).getId();
        long journalId = catalog.addJournal(journal("Журнал")).getId();
        catalog.updateBook(bookId, new CreateBookRequest("Новое название", new BigDecimal("250.50"), "Другой автор", 300));
        catalog.updateJournal(journalId, new CreateJournalRequest("Новый журнал", BigDecimal.TEN, "Другой издатель", 8));
        flushAndClear();
        var book = assertInstanceOf(Book.class, catalog.getById(bookId));
        assertEquals("Новое название", book.getName());
        assertEquals(new BigDecimal("250.50"), book.getPrice());
        assertEquals(300, book.getPageCount());
        assertEquals(8, assertInstanceOf(Journal.class, catalog.getById(journalId)).getIssueNumber());
    }

    /** Удаленная запись не находится, а повторное удаление возвращает понятную ошибку. */
    @Test
    @DisplayName("Удаление и поиск отсутствующего товара")
    void deletesAndReportsMissingItem() {
        long id = catalog.addBook(book("Книга")).getId();
        catalog.deleteById(id);
        flushAndClear();
        assertThrows(ItemNotFoundException.class, () -> catalog.getById(id));
        assertThrows(ItemNotFoundException.class, () -> catalog.deleteById(id));
        assertTrue(catalog.getAll().isEmpty());
    }

    /** Некорректные значения отвергаются до записи; тип существующего товара не меняется. */
    @Test
    @DisplayName("Валидация цены, текста, количества и типа товара")
    void rejectsInvalidData() {
        assertThrows(IllegalArgumentException.class, () -> catalog.addBook(new CreateBookRequest("", BigDecimal.TEN, "Автор", 10)));
        assertThrows(IllegalArgumentException.class, () -> catalog.addBook(new CreateBookRequest("Книга", new BigDecimal("1.001"), "Автор", 10)));
        assertThrows(IllegalArgumentException.class, () -> catalog.addJournal(new CreateJournalRequest("Журнал", BigDecimal.TEN, "Издатель", 0)));
        long id = catalog.addBook(book("Книга")).getId();
        assertThrows(IllegalArgumentException.class, () -> catalog.updateJournal(id, journal("Журнал")));
        flushAndClear();
        assertEquals("Книга", catalog.getById(id).getName());
    }

    /** SQL-подобное название хранится как строка и не изменяет структуру таблицы. */
    @Test
    @DisplayName("Параметризация запросов: SQL в названии остается текстом")
    void sqlTextIsStoredAsData() {
        String name = "Книга'; DROP TABLE products; --";
        long id = catalog.addBook(book(name)).getId();
        flushAndClear();
        assertEquals(name, catalog.getById(id).getName());
        assertEquals(1, repository.count());
    }

    /** Ограничения SQL защищают таблицу даже при записи в обход сервисной валидации. */
    @Test
    @DisplayName("Ограничения миграции: отрицательная цена запрещена в БД")
    void databaseRejectsInvalidPrice() {
        var invalid = new Book(null, "Книга", BigDecimal.valueOf(-1), "Автор", 100);
        assertThrows(DataIntegrityViolationException.class, () -> repository.saveAndFlush(invalid));
    }

    private void flushAndClear() {
        entityManager.flush();
        entityManager.clear();
    }

    private CreateBookRequest book(String name) {
        return new CreateBookRequest(name, new BigDecimal("100.00"), "Автор", 100);
    }

    private CreateJournalRequest journal(String name) {
        return new CreateJournalRequest(name, new BigDecimal("50.00"), "Издатель", 7);
    }
}
