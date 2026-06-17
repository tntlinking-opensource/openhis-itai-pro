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

package com.openhis.health.application.basic.specimendefinition.request;

import com.openhis.health.domain.share.constants.FieldNameConstants;
import com.openhis.health.module.common.pagination.PageQueryBinder.AbstractHealthPageRequest;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.ArrayList;
import java.util.List;

/**
 * 样本定义分页查询请求：支持关键字、发布状态与标本种类筛选。
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class PageSpecimenDefinitionRequest extends AbstractHealthPageRequest {

    /** 发布状态过滤，存 {@code PublicationStatus.getCode()}。 */
    private String statusEnum;

    /** 标本种类过滤，存 {@code SpecimenType.getCode()}。 */
    private String typeEnum;

    {
        List<String> where = new ArrayList<>();
        where.add(FieldNameConstants.NAME);
        where.add(FieldNameConstants.BUS_NO);
        where.add(FieldNameConstants.PY_STR);
        where.add(FieldNameConstants.WB_STR);
        this.setWhere(where);
        this.setSortBy(FieldNameConstants.UPDATED_AT);
    }
}
