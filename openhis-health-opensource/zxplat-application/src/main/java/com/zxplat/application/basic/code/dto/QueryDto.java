/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.application.basic.code.dto;

import lombok.Data;

import java.util.List;

/**
 * 通用查询条件片段：where 字段名列表 + 关键字 + 排序字段。
 */
@Data
public class QueryDto {

    /** 参与检索的字段名列表（语义由调用方解释）。 */
    private List<String> where;
    /** 模糊检索关键字。 */
    private String keyword;
    /** 排序属性名。 */
    private String sortBy;
}
