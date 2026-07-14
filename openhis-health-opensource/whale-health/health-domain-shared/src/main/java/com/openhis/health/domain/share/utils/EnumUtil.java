/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.domain.share.utils;

import com.openhis.health.domain.share.enums.EnumInterface;
import com.openhis.whale.core.application.dto.OptionDto;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/**
 * 业务枚举通用工具：持久化身份以 {@link EnumInterface#getCode()} 字符串为准（B.10.8.1），与 Flyway / 事件载荷对齐。
 *
 * <p>解析类方法不对 {@code code} 做隐式 {@code trim}；HTTP 或外部入参应在调用前归一空白，避免首尾空格导致未命中。</p>
 *
 */
public final class EnumUtil {

    private EnumUtil() {
    }

    /**
     * 将枚举常量转为持久化身份串；{@code value} 为 {@code null} 时返回 {@code null}。
     *
     * @param value 实现 {@link EnumInterface} 的枚举项
     * @return {@link EnumInterface#getCode()}，或 {@code null}
     */
    public static String toCode(EnumInterface value) {
        if (value == null) {
            return null;
        }
        return value.getCode();
    }

    /**
     * 按持久化 {@code code} 解析枚举常量；未命中或入参缺省时返回 {@code null}（调用方须自行门禁或抛业务异常，避免链式 NPE）。
     *
     * @param enumClass 目标枚举类，{@code null} 时返回 {@code null}
     * @param code      与 {@link EnumInterface#getCode()} 精确相等的身份串，{@code null} 时返回 {@code null}（不 trim）
     * @param <E>       枚举类型参数
     * @return 匹配常量，否则 {@code null}
     */
    public static <E extends Enum<E> & EnumInterface> E fromCode(Class<E> enumClass, String code) {
        if (enumClass == null || code == null) {
            return null;
        }
        // —— 线性扫描枚举常量：体量小、调用分散，避免与业务侧平行维护 code→枚举 Map（B.4）。——
        for (E item : enumClass.getEnumConstants()) {
            if (item != null && Objects.equals(code, item.getCode())) {
                return item;
            }
        }
        return null;
    }

    /**
     * 将持久化 code 转换为前端展示整数（优先取枚举 {@link EnumInterface#getSortOrder()}）。
     *
     * <p>兼容规则：</p>
     * <ul>
     *     <li>当 code 可解析为指定枚举时，返回枚举的 sortOrder 整数值；</li>
     *     <li>当 code 无法命中枚举但本身是数字串时，返回该数字；</li>
     *     <li>其余情况返回 {@code null}。</li>
     * </ul>
     *
     * <p>其中 {@code Integer.parseInt} 分支仅服务于<strong>非身份</strong>的展示/排序兼容，不得用于把任意数字串当作枚举持久化
     * {@code code} 再参与业务分支（B.10.8.1）。</p>
     *
     * @param enumClass 枚举类型，{@code null} 时仅走数字串分支（若 {@code code} 可解析为整数则返回，否则 {@code null}）
     * @param code      持久化 code 或兼容用数字串；{@code null} 或全空白时返回 {@code null}
     * @param <E>       枚举泛型
     * @return 展示整数，无法解析返回 {@code null}
     */
    public static <E extends Enum<E> & EnumInterface> Integer toDisplayIntegerFromCodeOrNumber(
        Class<E> enumClass,
        String code) {
        if (code == null) {
            return null;
        }
        String trimmed = code.trim();
        if (trimmed.isEmpty()) {
            return null;
        }
        E enumValue = fromCode(enumClass, trimmed);
        if (enumValue != null) {
            Long sortOrder = enumValue.getSortOrder();
            if (sortOrder == null) {
                return null;
            }
            return sortOrder.intValue();
        }
        try {
            return Integer.parseInt(trimmed);
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    /**
     * 按持久化 {@link EnumInterface#getSortOrder()} 解析枚举常量；未命中或入参缺省时返回 {@code null}。
     *
     * @param enumClass  目标枚举类，{@code null} 时返回 {@code null}
     * @param sortOrder  与枚举项 {@code getSortOrder()} 精确相等的序号，{@code null} 时返回 {@code null}
     * @param <E>        枚举类型参数
     * @return 匹配常量，否则 {@code null}
     */
    public static <E extends Enum<E> & EnumInterface> E fromSortOrder(Class<E> enumClass, Long sortOrder) {
        if (enumClass == null || sortOrder == null) {
            return null;
        }
        for (E item : enumClass.getEnumConstants()) {
            if (item != null && Objects.equals(sortOrder, item.getSortOrder())) {
                return item;
            }
        }
        return null;
    }

    /**
     * 按 {@link EnumInterface#getSortOrder()} 解析枚举；未命中时抛 {@link IllegalArgumentException}。
     *
     * @param enumClass  目标枚举类，不可为 {@code null}
     * @param sortOrder  持久化序号，不可为 {@code null}
     * @param <E>        枚举类型参数
     * @return 非空枚举常量
     * @throws IllegalArgumentException 未命中或 {@code enumClass} 为 {@code null}
     */
    public static <E extends Enum<E> & EnumInterface> E requireFromSortOrder(Class<E> enumClass, Long sortOrder) {
        E value = fromSortOrder(enumClass, sortOrder);
        if (value == null) {
            String enumName;
            if (enumClass == null) {
                enumName = "null";
            } else {
                enumName = enumClass.getName();
            }
            throw new IllegalArgumentException(
                "Unknown enum sortOrder, enumClass=" + enumName + ", sortOrder=" + sortOrder);
        }
        return value;
    }

    /**
     * 将业务枚举全量常量转为下拉 {@link OptionDto} 列表（按 {@code sortOrder} 升序，null 权重排末）。
     *
     * <p>映射规则：{@code OptionDto#sort}←{@link EnumInterface#getSortOrder()}；{@code value}←{@link EnumInterface#getCode()}；
     * {@code label}←{@link EnumInterface#getDisplay()}。</p>
     *
     * @param enumClass 枚举类
     * @param <E>       枚举类型参数
     * @return 选项列表，永不为 {@code null}；{@code enumClass} 为 null 或无常量时为空列表
     */
    public static <E extends Enum<E> & EnumInterface> List<OptionDto> toOptions(Class<E> enumClass) {
        List<OptionDto> options = new ArrayList<>();
        if (enumClass == null) {
            return options;
        }
        E[] items = enumClass.getEnumConstants();
        if (items == null || items.length == 0) {
            return options;
        }
        // —— 先按枚举定义的 sortOrder 排序，再映射为 OptionDto，避免依赖 OptionDto 的 Lombok getter/setter。——
        List<E> ordered = new ArrayList<>();
        for (E item : items) {
            if (item != null) {
                ordered.add(item);
            }
        }
        ordered.sort(Comparator.comparing(EnumInterface::getSortOrder, Comparator.nullsLast(Long::compareTo)));
        for (E item : ordered) {
            options.add(new OptionDto(item.getSortOrder(), item.getCode(), item.getDisplay()));
        }
        return options;
    }

    /**
     * 供 {@link java.util.stream.Stream#sorted(java.util.Comparator)} 使用：按 {@link EnumInterface#getSortOrder()} 升序，
     * {@code null} sortOrder 排在末尾（与 {@link #toOptions(Class)} 一致）。
     *
     * @param a 左侧枚举项
     * @param b 右侧枚举项
     * @return 比较结果
     */
    public static int compareSortOrderNullsLast(EnumInterface a, EnumInterface b) {
        Long left;
        if (a != null) {
            left = a.getSortOrder();
        } else {
            left = null;
        }
        Long right;
        if (b != null) {
            right = b.getSortOrder();
        } else {
            right = null;
        }
        return Comparator.nullsLast(Long::compareTo).compare(left, right);
    }
}
