/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.domain.share.utils;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

/**
 * 批量命令解析工具。
 */
public final class CommandBatchUtil {

    /**
     * 私有构造函数，禁止实例化工具类。
     */
    private CommandBatchUtil() {
    }

    /**
     * 从批量子命令中提取标识集合。
     *
     * @param items 子命令列表
     * @param idExtractor 标识提取函数
     * @param <T> 子命令类型
     * @return 标识列表
     */
    public static <T> List<Long> extractIds(List<T> items, Function<T, Long> idExtractor) {
        List<Long> ids = new ArrayList<>();
        for (T item : items) {
            ids.add(idExtractor.apply(item));
        }
        return ids;
    }
}
