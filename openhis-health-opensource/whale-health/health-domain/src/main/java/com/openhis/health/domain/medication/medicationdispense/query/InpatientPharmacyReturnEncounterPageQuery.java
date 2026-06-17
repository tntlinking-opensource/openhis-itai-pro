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

package com.openhis.health.domain.medication.medicationdispense.query;

import com.openhis.health.domain.share.query.AbstractPageQuery;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

/**
 * 住院药房退药患者队列分页查询（AS-IP-PHA-003 IF-IPR-02）。
 *
 * <p>在院就诊经 {@code resolvedEncounterScope} 收窄；发放事实按住院退药类型子集与
 * {@code PENDING_REFUND}/{@code REFUNDED} 状态收敛；不按汇总单号聚合。</p>
 */
@Getter
@Setter
public class InpatientPharmacyReturnEncounterPageQuery extends AbstractPageQuery {

    private static final long serialVersionUID = 1L;

    /**
     * 发药库房库位主键集合（与 {@code MedicationDispense.location_id} 对齐）；非空时按库房 {@code IN} 收敛。
     */
    private List<Long> locationIds;

    /**
     * 病区主键（与住院就诊上下文病区字段对齐）；为空表示不按病区过滤。
     */
    private Long wardId;

    /**
     * 应用层预解析的在院就诊主键集合（含病区/关键字收窄）；由 {@code PharmacyEncounterQueueLoader} 写入。
     */
    private List<Long> resolvedEncounterScope;
}
