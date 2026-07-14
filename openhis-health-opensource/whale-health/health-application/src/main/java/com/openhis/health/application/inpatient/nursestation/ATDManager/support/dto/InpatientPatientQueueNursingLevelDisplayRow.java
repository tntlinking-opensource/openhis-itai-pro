/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.inpatient.nursestation.ATDManager.support.dto;

import lombok.Data;
import lombok.experimental.Accessors;

/**
 * 住院患者队列护理级别展示行（AS-IP-NUR-001 IF-IP-NUR-02 / AS-IP-DOR-001 IF-IP-DOR-01）。
 *
 * <p>中性读 enrich 产物；AppService 映射至各场景 PatientQueueItemDto。</p>
 */
@Data
@Accessors(chain = true)
public class InpatientPatientQueueNursingLevelDisplayRow {

    /** 源护理级别医嘱主键。 */
    private Long serviceRequestId;

    /** 护理级别诊疗活动定义主键。 */
    private Long activityId;

    /** 护理级别展示名（如「I级护理」）。 */
    private String activityName;

    /** 源护理级别医嘱状态 code（VERIFIED / ACTIVE / DRAFT / STOPPED）。 */
    private String statusEnum;
}
