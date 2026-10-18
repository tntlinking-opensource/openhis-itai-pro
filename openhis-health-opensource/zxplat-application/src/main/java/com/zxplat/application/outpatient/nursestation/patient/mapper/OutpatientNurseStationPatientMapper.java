/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.application.outpatient.nursestation.patient.mapper;

import com.zxplat.application.outpatient.nursestation.patient.request.PageNurseStationPatientQueueRequest;
import com.zxplat.application.outpatient.nursestation.patient.response.PageNurseStationPatientQueueResponse;
import com.zxplat.domain.administration.encounter.queries.NurseStationOutpatientQueueQuery;
import com.zxplat.domain.administration.encounter.queries.model.OutpatientEncounterContextQueryModel;
import org.springframework.data.domain.Page;

/**
 * 门诊护士站患者队列应用层装配（IF-NUR-01）。
 */
public interface OutpatientNurseStationPatientMapper {

    /**
     * 将护士站队列分页请求映射为读模型查询入参（分页窗、可选开立科室、仅待发耗材）。
     *
     * @param request HTTP 请求
     * @return 护士站队列分页查询入参
     */
    NurseStationOutpatientQueueQuery toNurseStationOutpatientQueueQuery(
        PageNurseStationPatientQueueRequest request);

    /**
     * 将门诊就诊上下文读模型分页映射为护士站队列响应。
     *
     * @param page 读模型分页
     * @return 队列响应
     */
    PageNurseStationPatientQueueResponse toPageNurseStationPatientQueueResponse(
        Page<OutpatientEncounterContextQueryModel> page);
}
