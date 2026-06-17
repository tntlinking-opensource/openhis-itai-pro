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

package com.openhis.health.module.common.assembler.command;

import com.openhis.health.module.common.converter.TextValueConverter;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Supplier;

/**
 * 应用层共享的批量命令项装配器。
 *
 * <p>用于把网关或页面层的主键列表转换为各类领域批量命令项，避免在多个 MapperImpl 内重复写循环装配代码。</p>
 */
public final class BatchCommandItemAssembler {

    /**
     * 禁止实例化：仅提供静态方法。
     */
    private BatchCommandItemAssembler() {
    }

    /**
     * 把 Long 主键列表映射为批量命令项列表。
     *
     * @param ids 主键列表
     * @param itemSupplier 命令项工厂
     * @param idSetter 主键写入函数
     * @param <T> 命令项类型
     * @return 命令项列表；空输入返回空集合
     */
    public static <T> List<T> fromLongIds(
        List<Long> ids,
        Supplier<T> itemSupplier,
        BiConsumer<T, Long> idSetter) {
        if (ids == null || ids.isEmpty()) {
            return Collections.emptyList();
        }
        List<T> items = new ArrayList<>();
        // Loop：遍历主键列表，逐条装配命令项
        for (Long id : ids) {
            if (id == null) {
                continue;
            }
            T item = itemSupplier.get();
            idSetter.accept(item, id);
            items.add(item);
        }
        return items;
    }

    /**
     * 把字符串主键列表映射为批量命令项列表（非法值自动跳过）。
     *
     * @param ids 字符串主键列表
     * @param itemSupplier 命令项工厂
     * @param idSetter 主键写入函数
     * @param <T> 命令项类型
     * @return 命令项列表；空输入返回空集合
     */
    public static <T> List<T> fromStringIds(
        List<String> ids,
        Supplier<T> itemSupplier,
        BiConsumer<T, Long> idSetter) {
        if (ids == null || ids.isEmpty()) {
            return Collections.emptyList();
        }
        List<T> items = new ArrayList<>();
        // Loop：遍历字符串主键列表，逐条解析并装配
        for (String raw : ids) {
            Long id = TextValueConverter.toNullableLong(raw);
            if (id == null) {
                continue;
            }
            T item = itemSupplier.get();
            idSetter.accept(item, id);
            items.add(item);
        }
        return items;
    }

}
