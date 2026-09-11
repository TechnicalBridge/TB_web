package com.digitalbot.domain;

import com.digitalbot.dto.ExtraLine;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

import java.util.List;

@Converter
public class ExtraListConverter implements AttributeConverter<List<ExtraLine>, String> {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final TypeReference<List<ExtraLine>> TYPE = new TypeReference<>() {};

    @Override
    public String convertToDatabaseColumn(List<ExtraLine> attribute) {
        try {
            return MAPPER.writeValueAsString(attribute == null ? List.of() : attribute);
        } catch (Exception e) {
            throw new IllegalStateException("No se pudo serializar extras", e);
        }
    }

    @Override
    public List<ExtraLine> convertToEntityAttribute(String dbData) {
        try {
            if (dbData == null || dbData.isBlank()) {
                return List.of();
            }
            return MAPPER.readValue(dbData, TYPE);
        } catch (Exception e) {
            return List.of();
        }
    }
}
