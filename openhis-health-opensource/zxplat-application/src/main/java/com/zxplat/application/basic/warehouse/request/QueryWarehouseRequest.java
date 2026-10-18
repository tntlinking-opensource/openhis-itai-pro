/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.application.basic.warehouse.request;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * 库房查询/批量启停请求：支持单 id 与 id 列表；{@link #resolveIds()} 先追加 ids 再追加单 id（不去重）。
 */
@Data
public class QueryWarehouseRequest {
    /** 单条主键（详情、删除、编辑后回查）。 */
    private Long id;
    /** 批量主键（启停等）。 */
    private List<Long> ids;

    /**
     * 合并 {@link #ids} 与 {@link #id} 为单一列表（null 安全）。
     *
     * @return 可能含重复项的 id 列表；两者皆空则返回空列表
     */
    public List<Long> resolveIds() {
        List<Long> result = new ArrayList<>();
        if (ids != null) {
            result.addAll(ids);
        }
        if (id != null) {
            result.add(id);
        }
        return result;
    }
}
