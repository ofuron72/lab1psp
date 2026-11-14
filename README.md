# Каталог товаров — ЛР №1 и №2

Каталог книг и журналов на Java 21 и Spring Boot. Есть обычное консольное меню и TCP-клиент с двумя вариантами сервера.

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

Проверки: `mvn test`. Сетевой обмен — JSON в UTF-8, одна команда и один ответ на строку. Команды: `LIST`, `ADD_BOOK`, `ADD_JOURNAL`, `DELETE`, `SAVE`, `QUIT`.
