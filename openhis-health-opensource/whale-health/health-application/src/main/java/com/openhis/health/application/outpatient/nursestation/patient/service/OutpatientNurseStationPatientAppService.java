/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.outpatient.nursestation.patient.service;

import com.openhis.health.application.outpatient.nursestation.patient.mapper.OutpatientNurseStationPatientMapper;
import com.openhis.health.application.outpatient.nursestation.patient.request.PageNurseStationPatientQueueRequest;
import com.openhis.health.application.outpatient.nursestation.patient.response.PageNurseStationPatientQueueResponse;
import com.openhis.health.application.shared.encounter.EncounterContextReader;
import com.openhis.health.domain.administration.encounter.queries.model.OutpatientEncounterContextQueryModel;
import com.openhis.health.domain.share.constants.ErrorCodeConstants;
import com.openhis.whale.core.application.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Service;

/**
 * 门诊护士站患者队列应用编排（IF-NUR-01）。
 *
 * <p>接入说明：由 AppServiceControllerRegistrar 动态注册为
 * {@code POST /api/app/outpatientNurseStationPatient/{methodName}}。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OutpatientNurseStationPatientAppService {

    private final EncounterContextReader EncounterContextReader;
    private final OutpatientNurseStationPatientMapper outpatientNurseStationPatientMapper;

    /**
     * IF-NUR-01：分页查询门诊护士站患者队列。
     *
     * @param request 队列筛选与分页条件
     * @return 当前页队列与总数
     */
    public PageNurseStationPatientQueueResponse pageNurseStationPatientQueue(PageNurseStationPatientQueueRequest request) {
        // Gate：请求体非空
        if (request == null) {
            throw new BusinessException(ErrorCodeConstants.ENCOUNTER_INVALID_ARGUMENT, "请求体不能为空");
        }
        // Pre：映射护士站门诊队列分页查询
        com.openhis.health.domain.share.query.AbstractPageQuery queueQuery =
            this.outpatientNurseStationPatientMapper.toNurseStationOutpatientQueueQuery(request);
        Page<OutpatientEncounterContextQueryModel> page =
            this.EncounterContextReader.pageNurseStationOutpatientQueue(queueQuery);        return this.outpatientNurseStationPatientMapper.toPageNurseStationPatientQueueResponse(page);
    }
}
