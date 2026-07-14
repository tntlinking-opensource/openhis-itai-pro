/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.whale.core.infrastructure.util;

import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.lang.reflect.Field;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;

public class SearchUtils<T> {

    /**
     * @param jsonObject 实体类形成的jsonObject
     */
    public Example<T> generatePageExample(JSONObject jsonObject, T t) {

        String searchKey = jsonObject.getString("searchKey"); // 这是用于检索的关键字
        JSONArray wheres = jsonObject.getJSONArray("where"); // 这是需要检索的字段
        JSONObject tJsonobject = JSONObject.parseObject(JSONObject.toJSONString(t));
        ExampleMatcher exampleMatcher = ExampleMatcher.matching();// Or连接
        for (Object where : wheres) {
            where.toString().replace("[", "").replace("]", "").replace("\"", "").trim();
            tJsonobject.put(where.toString(), searchKey);
            exampleMatcher =
                exampleMatcher.withMatcher(where.toString(), ExampleMatcher.GenericPropertyMatcher::contains);
        }
        t = tJsonobject.toJavaObject((Class<T>)t.getClass());
        Example<T> example = Example.of(t, exampleMatcher);
        return example;
    }

    /**
     * 完整的JPA分页查询实现（新增：t中非空字段默认AND查询 + 时间范围查询）
     *
     * @param jsonObject 包含：searchKey(检索关键字)、where(检索字段)、pageNo(页码)、pageSize(页大小)、dateRange(时间范围)
     * @param t 实体类实例（非空属性会作为AND条件加入查询）
     * @param repository JPA的Repository（需继承JpaSpecificationExecutor）
     * @param <T> 实体类泛型
     * @return JPA分页结果Page<T>
     */
    public <T> Page<T> generateJpaPageQuery(JSONObject jsonObject, T t, JpaRepository<T, ?> repository) {
        // ====================== 1. 解析分页参数 ======================
        int pageNum = jsonObject.getInteger("pageNo") == null ? 0 : jsonObject.getInteger("pageNo");
        int pageSize = jsonObject.getInteger("pageSize") == null ? 10 : jsonObject.getInteger("pageSize");
        Sort sort = Sort.by(Sort.Direction.DESC, jsonObject.get("sortBy").toString());
        Pageable pageable = PageRequest.of(pageNum, pageSize, sort);

        // ====================== 2. 解析查询条件参数 ======================
        String searchKey = jsonObject.getString("searchKey");
        JSONArray wheres = jsonObject.getJSONArray("where");
        JSONObject dateRange = new JSONObject();
        if (jsonObject.containsKey("dateRange")) {
            dateRange = jsonObject.getJSONObject("dateRange");
        } else {
            dateRange = null;
        }

        // ====================== 3. 构建JPA动态查询条件（核心：新增t非空字段AND逻辑 + 时间范围） ======================
        JSONObject finalDateRange = dateRange;
        Specification<T> spec = (Root<T> root, CriteriaQuery<?> query, CriteriaBuilder cb) -> {
            // 3.1 构建原有搜索条件（searchKey + where字段的OR/AND）
            Predicate searchPredicate = buildSearchPredicate(root, cb, searchKey, wheres);

            // 3.2 构建t非空字段的AND条件（新增核心逻辑）
            Predicate entityPredicate = buildEntityNonEmptyPredicate(root, cb, t);

            // 3.3 构建时间范围查询条件（新增）
            Predicate dateRangePredicate = buildDateRangePredicate(root, cb, finalDateRange);

            // 3.4 组合最终条件：(搜索条件) AND (t非空字段条件) AND (时间范围条件)
            Predicate finalPredicate = null;
            if (searchPredicate != null && entityPredicate != null && dateRangePredicate != null) {
                finalPredicate = cb.and(searchPredicate, entityPredicate, dateRangePredicate);
            } else if (searchPredicate != null && entityPredicate != null) {
                finalPredicate = cb.and(searchPredicate, entityPredicate);
            } else if (searchPredicate != null && dateRangePredicate != null) {
                finalPredicate = cb.and(searchPredicate, dateRangePredicate);
            } else if (entityPredicate != null && dateRangePredicate != null) {
                finalPredicate = cb.and(entityPredicate, dateRangePredicate);
            } else if (searchPredicate != null) {
                finalPredicate = searchPredicate;
            } else if (entityPredicate != null) {
                finalPredicate = entityPredicate;
            } else if (dateRangePredicate != null) {
                finalPredicate = dateRangePredicate;
            }

            return finalPredicate;
        };

        // ====================== 4. 执行JPA分页查询 ======================
        JpaSpecificationExecutor<T> specExecutor = (JpaSpecificationExecutor<T>)repository;
        return specExecutor.findAll(spec, pageable);
    }


    /**
     * 完整的JPA分页查询实现（新增：t中非空字段默认AND查询 + 时间范围查询）
     *
     * @param jsonObject 包含：searchKey(检索关键字)、where(检索字段)、pageNo(页码)、pageSize(页大小)、dateRange(时间范围)
     * @param t 实体类实例（非空属性会作为AND条件加入查询）
     * @return JPA分页结果Page<T>
     */
    public Specification<T> generateJpaPageQuery(JSONObject jsonObject, T t) {
        // ====================== 1. 解析分页参数 ======================


        // ====================== 2. 解析查询条件参数 ======================
        String searchKey = jsonObject.getString("searchKey");
        JSONArray wheres = jsonObject.getJSONArray("where");
        JSONObject dateRange = new JSONObject();
        if (jsonObject.containsKey("dateRange")) {
            dateRange = jsonObject.getJSONObject("dateRange");
        } else {
            dateRange = null;
        }

        // ====================== 3. 构建JPA动态查询条件（核心：新增t非空字段AND逻辑 + 时间范围） ======================
        JSONObject finalDateRange = dateRange;
        Specification<T> spec = (Root<T> root, CriteriaQuery<?> query, CriteriaBuilder cb) -> {
            // 3.1 构建原有搜索条件（searchKey + where字段的OR/AND）
            Predicate searchPredicate = buildSearchPredicate(root, cb, searchKey, wheres);

            // 3.2 构建t非空字段的AND条件（新增核心逻辑）
            Predicate entityPredicate = buildEntityNonEmptyPredicate(root, cb, t);

            // 3.3 构建时间范围查询条件（新增）
            Predicate dateRangePredicate = buildDateRangePredicate(root, cb, finalDateRange);

            // 3.4 组合最终条件：(搜索条件) AND (t非空字段条件) AND (时间范围条件)
            Predicate finalPredicate = null;
            if (searchPredicate != null && entityPredicate != null && dateRangePredicate != null) {
                finalPredicate = cb.and(searchPredicate, entityPredicate, dateRangePredicate);
            } else if (searchPredicate != null && entityPredicate != null) {
                finalPredicate = cb.and(searchPredicate, entityPredicate);
            } else if (searchPredicate != null && dateRangePredicate != null) {
                finalPredicate = cb.and(searchPredicate, dateRangePredicate);
            } else if (entityPredicate != null && dateRangePredicate != null) {
                finalPredicate = cb.and(entityPredicate, dateRangePredicate);
            } else if (searchPredicate != null) {
                finalPredicate = searchPredicate;
            } else if (entityPredicate != null) {
                finalPredicate = entityPredicate;
            } else if (dateRangePredicate != null) {
                finalPredicate = dateRangePredicate;
            }

            return finalPredicate;
        };
        return spec;
    }

    /**
     * 构建原有搜索条件（searchKey + where字段的OR/AND）
     */
    private <T> Predicate buildSearchPredicate(Root<T> root, CriteriaBuilder cb, String searchKey, JSONArray wheres) {
        Predicate orPredicate = null;
        Predicate andPredicate = null;

        if (wheres != null && !wheres.isEmpty() && searchKey != null && !searchKey.trim().isEmpty()) {
            for (Object whereObj : wheres) {
                String fieldName = whereObj.toString().replace("[", "").replace("]", "").replace("\"", "").trim();
                if (fieldName.isEmpty())
                    continue;

                // 构建模糊查询条件
                Predicate singlePredicate = cb.like(root.get(fieldName), "%" + searchKey.trim() + "%");
                orPredicate = (orPredicate == null) ? singlePredicate : cb.or(orPredicate, singlePredicate);
            }
        }

        // 组合搜索条件
        Predicate searchPredicate = null;
        if (orPredicate != null && andPredicate != null) {
            searchPredicate = cb.and(orPredicate, andPredicate);
        } else if (orPredicate != null) {
            searchPredicate = orPredicate;
        } else if (andPredicate != null) {
            searchPredicate = andPredicate;
        }
        return searchPredicate;
    }

    /**
     * 构建实体类t非空字段的AND条件（新增核心方法）
     */
    private <T> Predicate buildEntityNonEmptyPredicate(Root<T> root, CriteriaBuilder cb, T t) {
        if (t == null)
            return null;

        Predicate entityPredicate = null;
        Class<?> clazz = t.getClass();

        // 遍历实体类所有字段（通过反射获取）
        for (Field field : clazz.getDeclaredFields()) {
            // 跳过序列化字段、静态字段
            if ("serialVersionUID".equals(field.getName())
                || java.lang.reflect.Modifier.isStatic(field.getModifiers())) {
                continue;
            }

            // 允许访问私有字段
            field.setAccessible(true);
            Object fieldValue = null;
            try {
                // 获取字段值
                fieldValue = field.get(t);
            } catch (IllegalAccessException e) {
                continue; // 字段访问失败则跳过
            }

            // 判断字段值是否非空（跳过null、空字符串、空集合等）
            if (isFieldValueEmpty(fieldValue)) {
                continue;
            }

            // 构建该字段的AND条件（默认等于查询，可根据需求改为like等）
            Predicate singleEntityPredicate = cb.equal(root.get(field.getName()), fieldValue);

            // 多个非空字段之间用AND连接
            entityPredicate =
                (entityPredicate == null) ? singleEntityPredicate : cb.and(entityPredicate, singleEntityPredicate);
        }

        return entityPredicate;
    }

    /**
     * 构建时间范围查询条件（新增方法） 支持单个或多个时间字段的范围查询
     *
     * @param root JPA Root对象
     * @param cb CriteriaBuilder
     * @param dateRange 时间范围JSON对象，支持两种格式：
     *            格式1（单个时间字段）：{"fieldName":"createTime","startTime":"2026-01-01","endTime":"2026-01-19"}
     *            格式2（多个时间字段）：[{"fieldName":"createTime","startTime":"2026-01-01","endTime":"2026-01-19"},...]
     * @return 时间范围Predicate
     */
    private <T> Predicate buildDateRangePredicate(Root<T> root, CriteriaBuilder cb, JSONObject dateRange) {
        if (dateRange == null || dateRange.isEmpty()) {
            return null;
        }

        Predicate dateRangePredicate = null;

        // 支持单个时间范围查询
        String fieldName = dateRange.getString("fieldName");
        Object startTime = dateRange.get("startTime");
        Object endTime = dateRange.get("endTime");

        if (fieldName != null && !fieldName.trim().isEmpty()) {
            Predicate singleDatePredicate = buildSingleDateRangePredicate(root, cb, fieldName, startTime, endTime);
            if (singleDatePredicate != null) {
                dateRangePredicate = singleDatePredicate;
            }
        }

        // 支持多个时间范围查询（可选扩展）
        JSONArray dateRanges = dateRange.getJSONArray("ranges");
        if (dateRanges != null && !dateRanges.isEmpty()) {
            for (Object rangeObj : dateRanges) {
                if (rangeObj instanceof JSONObject) {
                    JSONObject range = (JSONObject)rangeObj;
                    String field = range.getString("fieldName");
                    Object start = range.get("startTime");
                    Object end = range.get("endTime");

                    Predicate singlePredicate = buildSingleDateRangePredicate(root, cb, field, start, end);
                    if (singlePredicate != null) {
                        dateRangePredicate = (dateRangePredicate == null) ? singlePredicate
                            : cb.and(dateRangePredicate, singlePredicate);
                    }
                }
            }
        }

        return dateRangePredicate;
    }

    /**
     * 构建单个时间字段的BETWEEN查询
     */
    /**
     * 构建单个时间字段的BETWEEN查询
     */
    private <T> Predicate buildSingleDateRangePredicate(Root<T> root, CriteriaBuilder cb, String fieldName,
        Object startTime, Object endTime) {
        if (fieldName == null || fieldName.trim().isEmpty()) {
            return null;
        }

        Predicate predicate = null;

        try {
            // 转换时间参数为合适的类型
            Comparable comparableStart = convertToComparable(startTime);
            Comparable comparableEnd = convertToComparable(endTime);

            // 只有开始时间：>= startTime
            if (comparableStart != null && comparableEnd == null) {
                predicate = cb.greaterThanOrEqualTo(root.get(fieldName), comparableStart);
            }
            // 只有结束时间：<= endTime
            else if (comparableStart == null && comparableEnd != null) {
                predicate = cb.lessThanOrEqualTo(root.get(fieldName), comparableEnd);
            }
            // 同时有开始和结束时间：BETWEEN
            else if (comparableStart != null && comparableEnd != null) {
                predicate = cb.between(root.get(fieldName), comparableStart, comparableEnd);
            }
        } catch (Exception e) {
            System.err.println("时间范围查询构建失败，字段: " + fieldName + ", 错误: " + e.getMessage());
            return null;
        }

        return predicate;
    }

    /**
     * 将时间参数转换为Comparable类型 支持String、Date、LocalDateTime、OffsetDateTime等类型
     */
    private Comparable convertToComparable(Object timeValue) {
        if (timeValue == null) {
            return null;
        }

        // 如果已经是Comparable类型，直接返回
        if (timeValue instanceof Comparable && !(timeValue instanceof String)) {
            return (Comparable)timeValue;
        }

        // 如果是字符串，尝试转换为时间类型
        if (timeValue instanceof String) {
            String timeStr = ((String)timeValue).trim();
            if (timeStr.isEmpty()) {
                return null;
            }

            // 尝试多种时间格式解析
            try {
                // 尝试解析为 OffsetDateTime (带时区)
                // 格式: "2026-01-19T14:30:00+08:00" 或 "2026-01-19 14:30:00"
                if (timeStr.contains("T") || timeStr.contains("+")) {
                    return OffsetDateTime.parse(timeStr);
                }

                // 尝试解析为 LocalDateTime
                // 支持格式: "2026-01-19 14:30:00" 或 "2026-01-19"
                DateTimeFormatter formatter;
                if (timeStr.length() > 10) {
                    // 包含时间部分
                    formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
                } else {
                    // 只有日期部分，补充时间
                    if (timeStr.length() == 10) {
                        timeStr = timeStr + " 00:00:00";
                    }
                    formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
                }
                return LocalDateTime.parse(timeStr, formatter);

            } catch (DateTimeParseException e1) {
                try {
                    // 尝试其他格式
                    DateTimeFormatter altFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss");
                    return LocalDateTime.parse(timeStr, altFormatter);
                } catch (DateTimeParseException e2) {
                    System.err.println("无法解析时间字符串: " + timeStr + ", 错误: " + e2.getMessage());
                    return null;
                }
            }
        }

        // 其他类型尝试直接转换
        if (timeValue instanceof Comparable) {
            return (Comparable)timeValue;
        }

        return null;
    }

    /**
     * 辅助方法：判断字段值是否为空
     */
    private boolean isFieldValueEmpty(Object value) {
        if (value == null)
            return true;
        // 处理字符串：空字符串视为空
        if (value instanceof String) {
            return ((String)value).trim().isEmpty();
        }
        // 处理集合：空集合视为空（可选，根据你的业务调整）
        if (value instanceof java.util.Collection) {
            return ((java.util.Collection<?>)value).isEmpty();
        }
        // 处理数组：空数组视为空（可选）
        if (value.getClass().isArray()) {
            return java.lang.reflect.Array.getLength(value) == 0;
        }
        // 其他类型（数字、布尔等）：非null即视为非空
        return false;
    }
}
