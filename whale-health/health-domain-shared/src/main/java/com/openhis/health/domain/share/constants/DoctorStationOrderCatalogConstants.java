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

package com.openhis.health.domain.share.constants;

import com.openhis.health.domain.share.enums.DispenseStatus;

import java.util.List;

/**
 * 门诊医生站医嘱开立目录（IF-DOR-11）与发放/库存读侧对齐的共享常量。
 *
 * <p>口径：目录「还可开」预减时，同就诊下尚未完成发药闭环的发放事实按状态纳入占用统计。</p>
 */
public interface DoctorStationOrderCatalogConstants {

    /**
     * 纳入目录占用统计的发放状态集合（药品侧 JPA 枚举列、耗材侧可与 {@link DispenseStatus#getCode()} 对照）。
     *
     * <p>含：草稿、待配药、已配药、已汇总待发；不含已完成发药及退药终态。</p>
     */
    List<DispenseStatus> DISPENSE_OCCUPANCY_STATUSES_FOR_ORDER_CATALOG = List.of(
        DispenseStatus.DRAFT,
        DispenseStatus.PREPARATION,
        DispenseStatus.PREPARED,
        DispenseStatus.IN_PROGRESS,
        DispenseStatus.SUMMARIZED);
}
