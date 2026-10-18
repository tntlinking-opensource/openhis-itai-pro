/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.application.basic.warehouse.request;

import lombok.Data;

/**
 * 库房/药房列表查询请求：支持关键词模糊检索。
 */
@Data
public class ListWarehouseRequest {
    /** 关键词，匹配名称、业务编号、拼音、五笔、类型、状态与上级科室展示文案。 */
    private String keyword;
}
