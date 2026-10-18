/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.framework.core.application.util;

import java.util.Collection;
import java.util.stream.Collectors;

/**
 * 集合工具类
 */
public class CollectionUtils {

    /**
     * 判断集合是否为空
     * @param collection 集合
     * @return true表示为空
     */
    public static boolean isEmpty(Collection<?> collection) {
        return collection == null || collection.isEmpty();
    }

    /**
     * 判断集合是否不为空
     * @param collection 集合
     * @return true表示不为空
     */
    public static boolean isNotEmpty(Collection<?> collection) {
        return !isEmpty(collection);
    }

    /**
     * 获取集合大小（安全获取，null返回0）
     * @param collection 集合
     * @return 集合大小
     */
    public static int size(Collection<?> collection) {
        return collection == null ? 0 : collection.size();
    }

    /**
     * 将集合转换为逗号分隔的字符串
     * @param collection 集合
     * @return 逗号分隔的字符串
     */
    public static String join(Collection<?> collection) {
        if (isEmpty(collection)) {
            return "";
        }
        return collection.stream()
                .map(Object::toString)
                .collect(Collectors.joining(","));
    }

    /**
     * 将集合转换为指定分隔符的字符串
     * @param collection 集合
     * @param separator 分隔符
     * @return 分隔符分隔的字符串
     */
    public static String join(Collection<?> collection, String separator) {
        if (isEmpty(collection)) {
            return "";
        }
        return collection.stream()
                .map(Object::toString)
                .collect(Collectors.joining(separator));
    }
}
