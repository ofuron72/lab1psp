# Каталог товаров — ЛР №1–3

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
