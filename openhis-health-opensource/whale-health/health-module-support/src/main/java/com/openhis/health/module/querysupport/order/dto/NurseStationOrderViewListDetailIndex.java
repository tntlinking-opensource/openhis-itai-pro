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

package com.openhis.health.module.querysupport.order.dto;

import com.openhis.health.domain.medication.medicationrequest.queries.model.MedicationRequestListQueryModel;
import com.openhis.health.domain.workflow.servicerequest.queries.model.ServiceRequestListModel;
import lombok.Data;
import lombok.experimental.Accessors;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * 住院护士站医嘱查看：按就诊批量明细的第一遍扫描索引（校对/执行/划价父医嘱共用）。
 */
@Data
@Accessors(chain = true)
public class NurseStationOrderViewListDetailIndex {

    /** 药品申请主键 → 列表明细。 */
    private Map<Long, MedicationRequestListQueryModel> medDetailById = new HashMap<>();

    /** 诊疗申请主键 → 列表明细。 */
    private Map<Long, ServiceRequestListModel> svcDetailById = new HashMap<>();

    /** 药品定义主键集合（预热）。 */
    private Set<Long> medDefIds = new HashSet<>();

    /** 诊疗活动定义主键集合（预热）。 */
    private Set<Long> activityDefIds = new HashSet<>();

    /** 医务人员主键集合（姓名批量解析）。 */
    private Set<Long> practitionerIds = new HashSet<>();

    /** 库位主键集合（名称批量解析）。 */
    private Set<Long> locationIds = new HashSet<>();
}
