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

import java.util.List;

/**
 * {@code wkf_supply_request} 上「按业务单号聚合」的各类单据列表过滤入参（同号多行共享头字段，列表按 {@code bus_no} 去重分页）。
 *
 * <p>继承 {@link AbstractPageQuery}：分页 {@code pageNo}/{@code pageSize}（1-based）、{@code keyword}、
 * {@code startDate}/{@code endDate} 等与全局列表约定一致（B.10.11）；申请时刻窗由应用层映射到
 * {@link #setStartDate(java.time.LocalDateTime)} / {@link #setEndDate(java.time.LocalDateTime)}。</p>
 *
 * <p>典型用法：进货、订货、退货、请领等应用层在 {@link com.openhis.health.domain.workflow.supplyrequest.service.SupplyRequestQueryService}
 * 中通过 {@code type_enum} 与其它条件收窄后，调用 {@code countDistinctBusNo} / {@code listDistinctBusNoPage} / {@code listByBusNosWithFilter}。</p>
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class SupplyRequestDocumentListQuery extends AbstractPageQuery {

    /** 序列化版本（与父类共同序列化场景下显式声明）。 */
    private static final long serialVersionUID = 1L;

    /**
     * 供应类型持久化码（{@link com.openhis.health.domain.share.enums.SupplyType#getCode()}）；非空时等值过滤。
     */
    private String typeEnum;

    /** 源位置主键；与 {@code source_location_id} 对齐。 */
    private Long sourceLocationId;

    /**
     * 源位置主键多选（IN）；与 {@link #sourceLocationId} 互斥，非空时优先于单值等值过滤。
     * <p>用于「sourceLocationId=0 表示当前用户可见全部药库」等应用层门控后的 id 集合。</p>
     */
    private List<Long> sourceLocationIds;

    /** 可选目的位置主键；与 {@code purpose_location_id} 对齐。 */
    private Long purposeLocationId;

    /**
     * 目的位置主键多选（IN）；与 {@link #purposeLocationId} 互斥，非空时优先于单值等值过滤。
     * <p>用于「purposeLocationId=0 表示当前用户可见全部药房」等应用层门控后的 id 集合。</p>
     */
    private List<Long> purposeLocationIds;

    /** 可选状态；{@link com.openhis.health.domain.share.enums.SupplyStatus#getCode()} 持久化字符串（B.10.8.1）。 */
    private String statusEnum;

    /** 可选租户；与 {@code tenant_id} 对齐。 */
    private Long tenantId;
}
