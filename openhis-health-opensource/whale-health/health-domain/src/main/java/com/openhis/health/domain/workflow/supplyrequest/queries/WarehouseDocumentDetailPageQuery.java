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

package com.openhis.health.domain.workflow.supplyrequest.queries;

import com.openhis.health.domain.share.query.AbstractPageQuery;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 库房单据明细分页查询入参（查 {@code wkf_supply_request} 明细行；替代 RM {@code WarehouseDocumentDetailPageQuery}）。
 *
 * <p>分页与通用关键字见 {@link AbstractPageQuery}；业务申请时刻窗使用 {@link #applyTimeStart}/{@link #applyTimeEnd}。</p>
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class WarehouseDocumentDetailPageQuery extends AbstractPageQuery {

    private static final long serialVersionUID = 1L;

    /** 单据号精确匹配（可选）。 */
    private String busNo;

    /** 单据类型筛选（可选）。 */
    private String typeEnum;

    /** 单据状态筛选（可选）。 */
    private String statusEnum;

    /** 源位置 ID 筛选（可选）。 */
    private Long sourceLocationId;

    /**
     * 源位置 ID 多选（IN），与 {@link #sourceLocationId} 互斥。
     */
    private List<Long> sourceLocationIds;

    /** 租户筛选（可选）。 */
    private Long tenantId;

    /** 目标位置 ID 筛选（可选）。 */
    private Long purposeLocationId;

    /**
     * 目标位置 ID 多选（IN），与 {@link #purposeLocationId} 互斥。
     */
    private List<Long> purposeLocationIds;

    /** 物品 ID 筛选（可选）。 */
    private Long itemId;

    /** 批号筛选（可选）。 */
    private String lotNumber;

    /** 发放状态筛选（可选）；主表查询暂不填充发放侧字段，保留入参兼容 RM 契约。 */
    private String deliveryStatusEnum;

    /** 申请时间起（可选）。 */
    private LocalDateTime applyTimeStart;

    /** 申请时间止（可选）。 */
    private LocalDateTime applyTimeEnd;
}
