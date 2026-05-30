/*
 * Copyright © 2026 OpenHIS Contributors
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 3 of the License, or (at your option) any later version.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library.  If not, see <https://www.gnu.org/licenses/>.
 */

package com.openhis.health.domain.share.utils;

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
