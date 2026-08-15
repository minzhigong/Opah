package com.opah.infra;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/** SQLite 时间列统一 TEXT 存取，绕开 sqlite-jdbc 的时间戳解析差异（epoch 串不可读回） */
@Converter
public class SqliteTimestampConverter implements AttributeConverter<LocalDateTime, String> {

    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    @Override
    public String convertToDatabaseColumn(LocalDateTime attribute) {
        return attribute == null ? null : attribute.format(FMT);
    }

    @Override
    public LocalDateTime convertToEntityAttribute(String dbData) {
        return dbData == null || dbData.isBlank() ? null : LocalDateTime.parse(dbData, FMT);
    }
}
