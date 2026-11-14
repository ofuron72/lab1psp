# Каталог товаров — ЛР №1–5

Java 21, Spring Boot. Сборка из корня проекта:

```bash
mvn package
```

В IDEA после изменения проекта нажмите **Reload All Maven Projects**. Сборка состоит из `catalog-contract` (Protobuf и заглушки) и `catalog-app` (приложение). Исходники приложения остались в корневой папке `src`. Перед первым запуском выполните сборку для генерации классов.

Запускайте `CatalogApplication` с нужными **Program arguments**:

| Режим | Аргументы |
|---|---|
| ЛР №1, локальный JSON | без аргументов |
| ЛР №2, последовательный TCP-сервер | `--catalog.mode=server-single` |
| ЛР №2, многопоточный TCP-сервер | `--catalog.mode=server-multi` |
| TCP-клиент | `--catalog.mode=client` |
| ЛР №3, UDP-сервер | `--catalog.mode=udp-server` |
| UDP-клиент | `--catalog.mode=udp-client` |
| ЛР №4, меню PostgreSQL | `--spring.profiles.active=db` |
| ЛР №5, gRPC-сервер | `--spring.profiles.active=db --catalog.mode=grpc-server` |
| gRPC-клиент | `--catalog.mode=grpc-client` |

Сервер и клиент запускаются отдельно. Для нескольких экземпляров в IDEA включите **Allow multiple instances**, рабочая папка — корень проекта. Из терминала используйте `java -jar target/lab1psp-1.0-SNAPSHOT.jar` с теми же аргументами.

Для ЛР №4–5 сначала выполните `docker compose up -d`. PostgreSQL 17: `localhost:5432`, база, пользователь и пароль — `postgres`. Compose без настроек volumes. Параметры подключения: `DB_URL`, `DB_USERNAME`, `DB_PASSWORD`. Liquibase создает `products` при запуске сервера/меню с профилем `db`, Hibernate проверяет схему. SQL лежит в `src/main/resources/db/changelog/sql`. `JpaCatalogService` синхронно работает с `JpaItemRepository`, изменения сохраняются после каждой операции. JSON-файл в этих режимах не используется.

gRPC-клиенту профиль `db` не нужен. Контракт: `catalog-contract/src/main/proto/catalog.proto`. Методы: `Add`, `GetById`, `GetAll` (server-side stream), `Update`, `Delete`. Обмен — бинарный Protobuf по HTTP/2; клиент использует blocking stub. Сервер обращается к PostgreSQL из ЛР №4. Неизвестный ID возвращает `NOT_FOUND`. По умолчанию порт 9090, адрес клиента — localhost; настройки: `catalog.grpc.port`, `catalog.grpc.client-host`, `catalog.grpc.server-host`. Выход клиента — пункт 0, остановка сервера — Ctrl+C.

TCP и UDP используют порт 8888. UDP-сервер содержит тестовые товары 1, 2, 3; неизвестный ID — 999, выход — 0. Он не меняет JSON и БД. В последовательном TCP второй клиент ждет отключения первого; в многопоточном клиенты работают одновременно. TCP-клиент выходит через пункт 5.

Тесты: `mvn test` из корня. Проверки JPA и gRPC используют временную H2-базу, Docker не требуется. Для ЛР №5 запустите `GrpcCatalogServiceTest` — шесть тестов с русскими названиями.
