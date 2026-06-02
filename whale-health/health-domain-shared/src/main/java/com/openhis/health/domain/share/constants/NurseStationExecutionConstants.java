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
 * 住院护士站医嘱执行（AS-IP-NUR-004）域查询共享常量。
 */
public interface NurseStationExecutionConstants {

    /**
     * 执行前待发预占统计发药状态（与 IF-DOR-11 目录占用口径一致；**不限就诊**，全局按药房+药品汇总）。
     *
     * <p>FIFO 分配与 Gate 编排归属 IF-IP-EXE-02 应用层，本常量仅约束发药聚合读侧预占范围。</p>
     */
    List<DispenseStatus> DISPENSE_RESERVE_STATUSES_FOR_EXECUTION_GATE =
        DoctorStationOrderCatalogConstants.DISPENSE_OCCUPANCY_STATUSES_FOR_ORDER_CATALOG;
}
