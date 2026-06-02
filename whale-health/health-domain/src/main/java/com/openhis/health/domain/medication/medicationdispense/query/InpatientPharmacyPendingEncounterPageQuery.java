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
 * 住院发药就诊队列分页查询（存在药品发放事实且在院就诊的主键分页）。
 *
 * <p>对齐 AS-IP-PHA-001 IF-IPP-02：库房集合、发放状态子集、可选病区与关键字；
 * 在院状态与关键字经 {@code hisrm_inpatient_info} 存在性子查询收敛；不按 {@code status_changed_time} 时间窗筛选。</p>
 */
@Getter
@Setter
public class InpatientPharmacyPendingEncounterPageQuery extends AbstractPageQuery {

    private static final long serialVersionUID = 1L;

    /**
     * 发药库房库位主键集合（与 {@code MedicationDispense.location_id} 对齐）；非空时按库房 {@code IN} 收敛。
     */
    private List<Long> locationIds;

    /**
     * 参与收敛的 {@code DispenseStatus#getCode()} 集合；空表示不按发放状态过滤。
     */
    private List<String> dispenseStatusEnumIn;

    /**
     * 病区主键（与 {@code InpatientInfo.ward_id} 对齐）；为空表示不按病区过滤。
     */
    private Long wardId;
}
