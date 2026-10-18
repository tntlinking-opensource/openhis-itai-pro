/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.application.basic.wardbed.request;

import com.zxplat.domain.share.constants.FieldNameConstants;
import com.zxplat.application.shared.pagination.PageQueryBinder.AbstractHealthPageRequest;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.ArrayList;
import java.util.List;

/**
 * 病区病房查询请求：{@code id} 在列表接口中表示机构或父位置过滤；基类默认模糊列为拼音/五笔。
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class QueryWardBedRequest extends AbstractHealthPageRequest {
    /** 机构主键或父位置主键，依调用接口语义而定。 */
    private Long id;

    /** 默认模糊列为拼音、五笔；排序按创建时间。 */
    {
        List<String> where = new ArrayList<>();
        where.add("pyStr");
        where.add("wbStr");
        this.setWhere(where);
        this.setSortBy(FieldNameConstants.CREATED_AT);
    }
}
