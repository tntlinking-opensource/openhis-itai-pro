/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.domain.share.utils;

import com.openhis.whale.core.application.util.StringUtils;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.Order;
import jakarta.persistence.criteria.Path;
import jakarta.persistence.criteria.Predicate;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

/**
 * 关键字检索「先精确后模糊」排序辅助（B.2.1）。
 *
 * <p>凡用户键入 {@code keyword} 做子串模糊时，命中集合须在 SQL 层将<strong>任一参与检索列
 * 与关键字忽略大小写全等</strong>的行排在模糊命中之前；次级排序由调用方传入。</p>
 *
 * <p>与 Spring Data {@code Pageable} 的交互：若 {@code Pageable} 带 {@code Sort}，会覆盖
 * Specification 内 {@code orderBy}。关键字非空时调用方须传<strong>无 Sort</strong>的 Pageable
 *（或在 QueryService 入口剥掉 Sort），本工具写入的精确优先序才能生效。</p>
 *
 * <p>计数查询（{@code Long}/{@code long} resultType）不写 {@code orderBy}，避免无效 ORDER BY。</p>
 */
public final class KeywordExactFirstOrderSupport {

    /** 精确命中排序键：越小越靠前。 */
    public static final int RANK_EXACT = 0;

    /** 仅模糊命中排序键。 */
    public static final int RANK_FUZZY = 1;

    private KeywordExactFirstOrderSupport() {
    }

    /**
     * 关键字与候选值是否忽略大小写全等（trim 后比较；任一空白视为非精确）。
     *
     * @param trimmedKeyword 已 trim 的关键字；空白返回 false
     * @param candidate      候选展示/编码值；空白返回 false
     * @return 忽略大小写全等时 true
     */
    public static boolean isExactMatchIgnoreCase(String trimmedKeyword, String candidate) {
        if (StringUtils.isEmpty(trimmedKeyword) || StringUtils.isEmpty(candidate)) {
            return false;
        }
        String left = trimmedKeyword.toLowerCase(Locale.ROOT);
        String right = candidate.trim().toLowerCase(Locale.ROOT);
        return left.equals(right);
    }

    /**
     * 内存排序比较：精确命中优先于仅模糊；两侧同档时返回 0（调用方再比次级键）。
     *
     * @param trimmedKeyword 已 trim 关键字
     * @param leftFields     左侧参与精确判定的字段（名称/编码/助记等）
     * @param rightFields    右侧对应字段
     * @return 负=左优先；正=右优先；0=同档
     */
    public static int compareExactFirst(String trimmedKeyword, Iterable<String> leftFields,
        Iterable<String> rightFields) {
        boolean leftExact = anyExact(trimmedKeyword, leftFields);
        boolean rightExact = anyExact(trimmedKeyword, rightFields);
        if (leftExact == rightExact) {
            return 0;
        }
        if (leftExact) {
            return -1;
        }
        return 1;
    }

    /**
     * 在数据查询（非 count）上写入「精确命中升序在前」的 ORDER BY，并追加次级排序。
     *
     * @param criteriaQuery   当前 CriteriaQuery；null 或计数查询时跳过
     * @param cb              CriteriaBuilder
     * @param trimmedKeyword  已 trim 关键字；空白跳过
     * @param secondaryOrders 次级排序（可为 empty，不可为 null）
     * @param matchPaths      参与精确全等判定的列 Path（须与 LIKE 列同集合或其超集）
     */
    @SafeVarargs
    public static void applyExactFirstOrder(
        CriteriaQuery<?> criteriaQuery,
        CriteriaBuilder cb,
        String trimmedKeyword,
        List<Order> secondaryOrders,
        Path<String>... matchPaths) {
        Objects.requireNonNull(cb, "cb");
        Objects.requireNonNull(secondaryOrders, "secondaryOrders");
        if (criteriaQuery == null) {
            return;
        }
        if (StringUtils.isEmpty(trimmedKeyword)) {
            return;
        }
        if (matchPaths == null || matchPaths.length == 0) {
            return;
        }
        // Spring Data count 查询会复用同一 Specification；计数无需 ORDER BY
        Class<?> resultType = criteriaQuery.getResultType();
        if (resultType == Long.class || resultType == long.class) {
            return;
        }
        Expression<Integer> rank = exactMatchRankExpression(cb, trimmedKeyword, matchPaths);
        List<Order> orders = new ArrayList<>(1 + secondaryOrders.size());
        orders.add(cb.asc(rank));
        orders.addAll(secondaryOrders);
        criteriaQuery.orderBy(orders);
    }

    /**
     * 精确命中排序表达式：任一列与关键字忽略大小写全等 → {@link #RANK_EXACT}，否则 {@link #RANK_FUZZY}。
     *
     * @param cb             CriteriaBuilder
     * @param trimmedKeyword 已 trim 关键字
     * @param matchPaths     参与精确判定的字符串列
     * @return 0/1 整型表达式
     */
    @SafeVarargs
    public static Expression<Integer> exactMatchRankExpression(
        CriteriaBuilder cb,
        String trimmedKeyword,
        Path<String>... matchPaths) {
        Objects.requireNonNull(cb, "cb");
        Objects.requireNonNull(trimmedKeyword, "trimmedKeyword");
        Objects.requireNonNull(matchPaths, "matchPaths");
        String exactLower = SearchCodeUtil.toCaseInsensitiveExactValue(trimmedKeyword);
        Predicate anyExact = exactMatchAnyPathPredicate(cb, exactLower, matchPaths);
        return cb.<Integer>selectCase()
            .when(anyExact, RANK_EXACT)
            .otherwise(RANK_FUZZY);
    }

    /**
     * 任一列 lower(列) 等于关键字小写全文。
     *
     * @param cb         CriteriaBuilder
     * @param exactLower {@link SearchCodeUtil#toCaseInsensitiveExactValue(String)}
     * @param matchPaths 字符串列
     * @return OR 谓词；无列时恒 false
     */
    @SafeVarargs
    public static Predicate exactMatchAnyPathPredicate(
        CriteriaBuilder cb,
        String exactLower,
        Path<String>... matchPaths) {
        Objects.requireNonNull(cb, "cb");
        Objects.requireNonNull(exactLower, "exactLower");
        if (matchPaths == null || matchPaths.length == 0) {
            return cb.disjunction();
        }
        List<Predicate> ors = new ArrayList<>(matchPaths.length);
        for (Path<String> path : matchPaths) {
            if (path == null) {
                continue;
            }
            ors.add(cb.equal(cb.lower(path), exactLower));
        }
        if (ors.isEmpty()) {
            return cb.disjunction();
        }
        return cb.or(ors.toArray(Predicate[]::new));
    }

    private static boolean anyExact(String trimmedKeyword, Iterable<String> fields) {
        if (fields == null) {
            return false;
        }
        for (String field : fields) {
            if (isExactMatchIgnoreCase(trimmedKeyword, field)) {
                return true;
            }
        }
        return false;
    }
}
