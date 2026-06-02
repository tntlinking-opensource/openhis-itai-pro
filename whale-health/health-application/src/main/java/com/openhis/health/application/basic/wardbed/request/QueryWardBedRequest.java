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

package com.openhis.health.application.basic.wardbed.request;

import com.openhis.health.domain.share.constants.FieldNameConstants;
import com.openhis.health.application.common.pagination.PageQueryBinder.AbstractHealthPageRequest;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.ArrayList;
import java.util.List;

/**
 * 病区病房查询请求：{@code id} 在列表接口中表示机构或父位置过滤；基类默认模糊列为拼音/五笔。
 */
@Data
@EqualsAndHashCode(callSuper = true)
/**
 * QueryWardBedRequest。
 */
public class QueryWardBedRequest extends AbstractHealthPageRequest {
    /** 机构主键或父位置主键，依调用接口语义而定。 */
    /**
     * id。
     */
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
