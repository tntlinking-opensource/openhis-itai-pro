/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.domain.share.utils;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.function.Function;

import com.openhis.health.domain.share.constants.GeneralSharedConstants;

/**
 * 主键 {@code IN (...)} 分批加载工具（SKILL §2.1 #9 / {@link GeneralSharedConstants#ID_IN_QUERY_CHUNK_SIZE}）。
 *
 * <p>供 QueryService 共享入口收敛超大 IN，避免单次参数过长导致库超时；语义与一次 IN 等价（结果并集）。</p>
 */
public final class IdInQueryChunkSupport {

    /**
     * 禁止实例化。
     */
    private IdInQueryChunkSupport() {
    }

    /**
     * 去重并去掉 null 的主键列表（保持首次出现顺序）。
     *
     * @param ids 原始主键集合；可空
     * @return 去重后的列表；空入参返回空列表
     */
    public static List<Long> distinctNonNullIds(Collection<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return List.of();
        }
        LinkedHashSet<Long> distinct = new LinkedHashSet<>();
        for (Long id : ids) {
            if (id != null) {
                distinct.add(id);
            }
        }
        if (distinct.isEmpty()) {
            return List.of();
        }
        return new ArrayList<>(distinct);
    }

    /**
     * 按默认分批上限加载并合并结果。
     *
     * @param ids    主键集合
     * @param loader 单批加载器（入参为当批 id 子列表）
     * @param <T>    行类型
     * @return 各批结果并集；空 id 返回空列表
     */
    public static <T> List<T> loadByIdInChunks(
        Collection<Long> ids,
        Function<List<Long>, List<T>> loader) {
        return loadByIdInChunks(ids, GeneralSharedConstants.ID_IN_QUERY_CHUNK_SIZE, loader);
    }

    /**
     * 按指定分批上限加载并合并结果。
     *
     * @param ids       主键集合
     * @param chunkSize 单批上限；≤0 时回落默认值
     * @param loader    单批加载器
     * @param <T>       行类型
     * @return 各批结果并集
     */
    public static <T> List<T> loadByIdInChunks(
        Collection<Long> ids,
        int chunkSize,
        Function<List<Long>, List<T>> loader) {
        List<Long> idList = distinctNonNullIds(ids);
        if (idList.isEmpty()) {
            return List.of();
        }
        if (loader == null) {
            return List.of();
        }
        int resolvedChunkSize = chunkSize;
        if (resolvedChunkSize <= 0) {
            resolvedChunkSize = GeneralSharedConstants.ID_IN_QUERY_CHUNK_SIZE;
        }
        if (idList.size() <= resolvedChunkSize) {
            List<T> oneBatch = loader.apply(idList);
            if (oneBatch == null) {
                return List.of();
            }
            return oneBatch;
        }
        List<T> allRows = new ArrayList<>(idList.size());
        for (int fromIndex = 0; fromIndex < idList.size(); fromIndex += resolvedChunkSize) {
            int toIndex = Math.min(fromIndex + resolvedChunkSize, idList.size());
            List<T> batch = loader.apply(idList.subList(fromIndex, toIndex));
            if (batch == null || batch.isEmpty()) {
                continue;
            }
            allRows.addAll(batch);
        }
        return allRows;
    }
}
