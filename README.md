# Каталог товаров — ЛР №1–4

Каталог книг и журналов на Java 21 и Spring Boot. Есть консольное меню, TCP-клиент с двумя вариантами сервера и поиск товаров через UDP.

Для запуска нужны JDK 21 и Maven:

```bash
mvn package
```

Запуск обычного меню (ЛР №1):

```bash
java -jar target/lab1psp-1.0-SNAPSHOT.jar
```

Для ЛР №2 запустите один из серверов:

```bash
java -jar target/lab1psp-1.0-SNAPSHOT.jar --catalog.mode=server-single
java -jar target/lab1psp-1.0-SNAPSHOT.jar --catalog.mode=server-multi
```

Затем в двух отдельных терминалах запустите клиентов:

```bash
java -jar target/lab1psp-1.0-SNAPSHOT.jar --catalog.mode=client
```

В `server-single` второй клиент ждет ответа, пока первый не отключится. В `server-multi` оба работают одновременно: каждое подключение обрабатывается отдельным виртуальным потоком Java 21. Сервер останавливается через Ctrl+C; пункт «Сохранить и выйти» отключает только клиента.

По умолчанию сервер слушает `0.0.0.0:8888`, клиент подключается к `localhost:8888`. Другой адрес клиента: `--catalog.client.host=192.168.1.10`, другой порт: `--catalog.port=8889` (укажите у сервера и клиентов).

Каталог общий для всех клиентов. `catalog.json` читает и сохраняет сервер: при выходе или отключении клиента и при штатной остановке сервера. Путь можно изменить через `--catalog.file-path=other.json`. Одновременно запускайте только один сервер с одним файлом каталога.

В IntelliJ IDEA запустите `CatalogApplication`, указав нужный `--catalog.mode=...` в Program arguments. Для нескольких клиентов включите Allow multiple instances в конфигурации запуска.

Проверки: `mvn test`. Обмен по TCP — JSON в UTF-8, одна команда и один ответ на строку. Команды: `LIST`, `ADD_BOOK`, `ADD_JOURNAL`, `DELETE`, `SAVE`, `QUIT`.

ЛР №3 — поиск по ID через UDP. Запустите сервер и клиент отдельно:

```bash
java -jar target/lab1psp-1.0-SNAPSHOT.jar --catalog.mode=udp-server
java -jar target/lab1psp-1.0-SNAPSHOT.jar --catalog.mode=udp-client
```

В IDEA те же параметры указываются в Program arguments у `CatalogApplication`. UDP-сервер хранит тестовые товары с ID 1, 2, 3 в памяти, не меняет `catalog.json` и слушает порт 8888. Клиент отправляет `GET:ID`, принимает JSON и выводит книгу или журнал. Введите 999 для проверки отсутствующего товара, 0 — для выхода. Ожидание ответа — 3 секунды (`--catalog.udp.timeout-ms=3000`). Для просмотра пакетов в Wireshark: интерфейс loopback, фильтр `udp.port == 8888`.

ЛР №4 — PostgreSQL 17, Hibernate и Spring Data JPA:

```bash
docker compose up -d
mvn package
java -jar target/lab1psp-1.0-SNAPSHOT.jar --spring.profiles.active=db
```

В IDEA запустите `CatalogApplication` с Program arguments `--spring.profiles.active=db`, убрав аргументы сетевых режимов. База, пользователь и пароль — `postgres`, адрес — `localhost:5432`. Compose не содержит настроек volumes. Подключение можно изменить переменными `DB_URL`, `DB_USERNAME`, `DB_PASSWORD`.

`Item`, `Book`, `Journal` — JPA entity. Наследование `SINGLE_TABLE`: все товары хранятся в таблице `products`, столбец `type` определяет книгу или журнал. `JpaItemRepository` наследует `JpaRepository<Item, Long>`. Доступны добавление, просмотр, поиск по ID, редактирование и удаление.

Меню вызывает `AsyncJpaCatalogService`: методы с `@Async` возвращают `CompletableFuture` и выполняются в пуле `catalog-db-`. Внутри рабочего потока `JpaCatalogService` проверяет данные и выполняет транзакцию через репозиторий. Future завершается после commit или с ошибкой. Сам Hibernate/JDBC остается блокирующим; консольное меню ожидает результат через `join()`. Размер пула по умолчанию — 4 потока (`catalog.async.pool-size`).

Таблицу создает Liquibase при первом запуске профиля `db`. Changelog: `src/main/resources/db/changelog/db.changelog-master.xml`, SQL: `src/main/resources/db/changelog/sql/001-create-products.sql`. Hibernate использует `ddl-auto=validate`. В режиме БД файл JSON не используется, каждая операция сохраняется сразу. Без профиля `db` предыдущие лабораторные по-прежнему работают без PostgreSQL.

`JpaCatalogServiceTest` проверяет миграцию и CRUD, `AsyncJpaCatalogServiceTest` — выполнение в рабочем потоке, commit и передачу ошибок через future. Тесты используют временные H2-базы в режиме совместимости с PostgreSQL; Docker для обычного `mvn test` не требуется.
