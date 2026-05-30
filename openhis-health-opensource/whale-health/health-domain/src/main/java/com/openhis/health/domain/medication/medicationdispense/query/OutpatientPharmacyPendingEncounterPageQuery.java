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
 * 门诊发药就诊队列分页查询（存在门诊药品发放事实的就诊主键分页）。
 *
 * <p>对齐 IF-OPP-02：可按发药库房集合（{@code MedicationDispense.location_id} ∈ 给定列表）收敛，再配合发放状态子集与关键字；
 * 不按 {@code status_changed_time} 时间窗筛选。</p>
 */
@Getter
@Setter
public class OutpatientPharmacyPendingEncounterPageQuery extends AbstractPageQuery {

    private static final long serialVersionUID = 1L;

    /**
     * 发药库房库位主键集合（与 {@code MedicationDispense.location_id} 对齐）；非空时按库房 {@code IN} 收敛；空表示不按库房过滤。
     */
    private List<Long> locationIds;

    /**
     * 参与收敛的 {@code DispenseStatus#getCode()} 集合；
     * 空表示不按发放状态过滤；显式传入须为 {@link com.openhis.health.domain.share.enums.DispenseStatus#getCode()} 字面量并与 {@code EnumUtil} 解析一致（代码生成通用规范 B.10.8、B.10.8.1）。
     */
    private List<String> dispenseStatusEnumIn;
}
