/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.whale.core.application.util;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.type.CollectionType;
import com.fasterxml.jackson.databind.type.MapType;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

import java.util.List;
import java.util.Map;

/**
 * JSON工具类
 */
public class JsonUtils {

    private static final ObjectMapper objectMapper = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

    /**
     * 对象转JSON字符串
     * @param obj 对象
     * @return JSON字符串
     */
    public static String toJson(Object obj) {
        if (obj == null) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(obj);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("JSON序列化失败", e);
        }
    }

    /**
     * JSON字符串转对象
     * @param json JSON字符串
     * @param clazz 目标类型
     * @return 对象
     */
    public static <T> T fromJson(String json, Class<T> clazz) {
        if (StringUtils.isEmpty(json)) {
            return null;
        }
        try {
            return objectMapper.readValue(json, clazz);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("JSON反序列化失败", e);
        }
    }

    /**
     * JSON字符串转List
     * @param json JSON字符串
     * @param elementClass 元素类型
     * @return List对象
     */
    public static <T> List<T> fromJsonList(String json, Class<T> elementClass) {
        if (StringUtils.isEmpty(json)) {
            return null;
        }
        try {
            CollectionType listType = objectMapper.getTypeFactory()
                    .constructCollectionType(List.class, elementClass);
            return objectMapper.readValue(json, listType);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("JSON反序列化失败", e);
        }
    }

    /**
     * JSON字符串转Map
     * @param json JSON字符串
     * @param keyClass Key类型
     * @param valueClass Value类型
     * @return Map对象
     */
    public static <K, V> Map<K, V> fromJsonMap(String json, Class<K> keyClass, Class<V> valueClass) {
        if (StringUtils.isEmpty(json)) {
            return null;
        }
        try {
            MapType mapType = objectMapper.getTypeFactory()
                    .constructMapType(Map.class, keyClass, valueClass);
            return objectMapper.readValue(json, mapType);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("JSON反序列化失败", e);
        }
    }

    /**
     * 格式化JSON字符串（美化输出）
     * @param json JSON字符串
     * @return 格式化后的JSON字符串
     */
    public static String formatJson(String json) {
        if (StringUtils.isEmpty(json)) {
            return null;
        }
        try {
            Object obj = objectMapper.readValue(json, Object.class);
            return objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(obj);
        } catch (JsonProcessingException e) {
            return json; // 格式化失败，返回原字符串
        }
    }
}
