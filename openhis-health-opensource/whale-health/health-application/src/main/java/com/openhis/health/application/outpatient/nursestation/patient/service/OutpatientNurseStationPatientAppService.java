/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.outpatient.nursestation.patient.service;

import com.openhis.health.application.outpatient.nursestation.patient.dto.NurseStationPatientQueueItemDto;
import com.openhis.health.application.outpatient.nursestation.patient.mapper.OutpatientNurseStationPatientMapper;
import com.openhis.health.application.outpatient.nursestation.patient.request.PageNurseStationPatientQueueRequest;
import com.openhis.health.application.outpatient.nursestation.patient.response.PageNurseStationPatientQueueResponse;
import com.openhis.health.application.shared.encounter.EncounterContextReader;
import com.openhis.health.application.shared.financial.EncounterContractDisplayAssembler;
import com.openhis.health.domain.administration.encounter.queries.NurseStationOutpatientQueueQuery;
import com.openhis.health.domain.administration.encounter.queries.model.OutpatientEncounterContextQueryModel;
import com.openhis.health.domain.share.constants.ErrorCodeConstants;
import com.openhis.whale.core.application.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Service;

import java.util.LinkedHashSet;
import java.util.Map;

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
    /** 费用性质展示：有医保账户优先合同名，供患者卡片区分医保/自费。 */
    private final EncounterContractDisplayAssembler encounterContractDisplayAssembler;

    /**
     * IF-NUR-01：分页查询门诊护士站患者队列（可选开立科室 {@code orgId}，空=全部）。
     *
     * @param request 队列筛选与分页条件
     * @return 当前页队列与总数（行内带 {@code contractDisplay}）
     */
    public PageNurseStationPatientQueueResponse pageNurseStationPatientQueue(PageNurseStationPatientQueueRequest request) {
        // Gate：请求体非空
        if (request == null) {
            throw new BusinessException(ErrorCodeConstants.ENCOUNTER_INVALID_ARGUMENT, "请求体不能为空");
        }
        // Pre：映射护士站门诊队列分页查询（含可选开立科室）
        NurseStationOutpatientQueueQuery queueQuery =
            this.outpatientNurseStationPatientMapper.toNurseStationOutpatientQueueQuery(request);
        // Pre：域侧 BR-NUR-001 准入 + 可选 organization_id 收窄
        Page<OutpatientEncounterContextQueryModel> page =
            this.EncounterContextReader.pageNurseStationOutpatientQueue(queueQuery);
        // Asm：读模型 → 队列 Response
        PageNurseStationPatientQueueResponse resp =
            this.outpatientNurseStationPatientMapper.toPageNurseStationPatientQueueResponse(page);
        // Asm：本页就诊批量回填费用性质（有医保账户优先，禁止前端写死自费）
        this.applyContractDisplayLabels(resp);
        return resp;
    }

    /**
     * 门诊护士站队列：按就诊优先合同批量回填 {@code contractDisplay}。
     *
     * @param resp 分页响应
     */
    private void applyContractDisplayLabels(PageNurseStationPatientQueueResponse resp) {
        if (resp == null || resp.getContent() == null || resp.getContent().isEmpty()) {
            return;
        }
        LinkedHashSet<Long> encounterIds = new LinkedHashSet<>();
        for (NurseStationPatientQueueItemDto row : resp.getContent()) {
            if (row != null && row.getEncounterId() != null) {
                encounterIds.add(row.getEncounterId());
            }
        }
        if (encounterIds.isEmpty()) {
            return;
        }
        Map<Long, String> contractDisplayByEncounterId =
            encounterContractDisplayAssembler.mapContractDisplayByEncounterIds(encounterIds);
        for (NurseStationPatientQueueItemDto row : resp.getContent()) {
            if (row == null || row.getEncounterId() == null) {
                continue;
            }
            row.setContractDisplay(contractDisplayByEncounterId.get(row.getEncounterId()));
        }
    }
}
