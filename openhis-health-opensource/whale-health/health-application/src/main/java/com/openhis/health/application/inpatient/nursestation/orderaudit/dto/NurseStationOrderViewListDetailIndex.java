/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.inpatient.nursestation.orderaudit.dto;

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
