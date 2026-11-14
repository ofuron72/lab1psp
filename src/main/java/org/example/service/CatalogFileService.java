package org.example.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.example.exception.CatalogFileCorruptedException;
import org.example.exception.CatalogFileWriteException;
import org.example.model.Item;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * Чтение/запись каталога в JSON-файл и преобразование JSON <-> объекты через ObjectMapper.
 */
@Service
public class CatalogFileService {

    private static final TypeReference<List<Item>> ITEM_LIST = new TypeReference<>() {
    };

    private final ObjectMapper objectMapper;
    private final File file;

    public CatalogFileService(ObjectMapper objectMapper,
                              @Value("${catalog.file-path:catalog.json}") String filePath) {
        this.objectMapper = objectMapper;
        this.file = new File(filePath);
    }

    /**
     * @return список товаров из файла
     * @throws FileNotFoundException         если файла нет
     * @throws CatalogFileCorruptedException если файл не удалось разобрать
     */
    public List<Item> load() throws FileNotFoundException {
        try (InputStream in = new FileInputStream(file)) {
            List<Item> items = objectMapper.readValue(in, ITEM_LIST);
            return items != null ? items : new ArrayList<>();
        } catch (FileNotFoundException e) {
            throw e;
        } catch (JsonProcessingException e) {
            throw new CatalogFileCorruptedException("файл " + file.getName() + " повреждён: " + e.getOriginalMessage(), e);
        } catch (IOException e) {
            throw new CatalogFileCorruptedException("не удалось прочитать файл " + file.getName() + ": " + e.getMessage(), e);
        }
    }

    /**
     * Сохраняет товары в файл в читаемом виде (с отступами).
     *
     * @throws CatalogFileWriteException при ошибке записи (IOException)
     */
    public void save(Collection<? extends Item> items) {
        try (OutputStream out = new FileOutputStream(file)) {
            objectMapper.writerFor(ITEM_LIST)
                    .withDefaultPrettyPrinter()
                    .writeValue(out, new ArrayList<>(items));
        } catch (IOException e) {
            throw new CatalogFileWriteException("ошибка записи в файл " + file.getName() + ": " + e.getMessage(), e);
        }
    }

    /** Имя файла каталога (для сообщений пользователю). */
    public String getFileName() {
        return file.getName();
    }
}
