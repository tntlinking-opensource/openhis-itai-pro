package com.openhis.whale.mrms.domain.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.openhis.whale.mrms.domain.enums.FieldFillSource;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

/**
 * field_sources_json 读写辅助。
 */
@Component
public class FieldSourcesJsonHelper {

    private static final TypeReference<Map<String, String>> MAP_TYPE = new TypeReference<>() {};

    private final ObjectMapper objectMapper;

    public FieldSourcesJsonHelper(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public Map<String, String> parse(String json) {
        if (json == null || json.isBlank()) {
            return new HashMap<>();
        }
        try {
            return new HashMap<>(objectMapper.readValue(json, MAP_TYPE));
        } catch (JsonProcessingException e) {
            return new HashMap<>();
        }
    }

    public String serialize(Map<String, String> sources) {
        if (sources == null || sources.isEmpty()) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(sources);
        } catch (JsonProcessingException e) {
            return null;
        }
    }

    public boolean isManual(Map<String, String> sources, String fieldKey) {
        return FieldFillSource.MANUAL.getCode().equals(sources.get(fieldKey));
    }

    public void markAuto(Map<String, String> sources, String fieldKey) {
        sources.put(fieldKey, FieldFillSource.AUTO.getCode());
    }

    public void markManual(Map<String, String> sources, String fieldKey) {
        sources.put(fieldKey, FieldFillSource.MANUAL.getCode());
    }
}
