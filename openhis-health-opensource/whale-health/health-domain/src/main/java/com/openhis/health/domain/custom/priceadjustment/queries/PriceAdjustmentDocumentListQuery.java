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

package com.openhis.health.domain.custom.priceadjustment.queries;

import com.openhis.health.domain.share.query.AbstractPageQuery;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 调价单列表过滤入参：按 {@code bus_no} 聚合分页（对齐库房 {@link com.openhis.health.domain.workflow.supplyrequest.queries.SupplyRequestDocumentListQuery}）。
 *
 * <p>时刻窗映射 {@link #setStartDate} / {@link #setEndDate}；排序键为
 * {@code COALESCE(submit_time, adjustment_time)}，草稿无提交时间时回落制单时间。</p>
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class PriceAdjustmentDocumentListQuery extends AbstractPageQuery {

    private static final long serialVersionUID = 1L;

    /** 机构主键；非空时等值过滤 {@code org_id}。 */
    private Long orgId;

    /**
     * 可选审批状态；{@link com.openhis.health.domain.share.enums.PriceAdjustmentStatus#getCode()} 持久化串。
     */
    private String statusEnum;
}
