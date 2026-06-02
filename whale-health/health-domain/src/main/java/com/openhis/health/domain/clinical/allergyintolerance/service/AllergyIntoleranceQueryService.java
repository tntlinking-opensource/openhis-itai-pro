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

package com.openhis.health.domain.clinical.allergyintolerance.service;

import com.openhis.health.domain.clinical.allergyintolerance.entity.AllergyIntolerance;
import com.openhis.health.domain.clinical.allergyintolerance.queries.model.AllergyHistoryFlagQueryModel;
import com.openhis.health.domain.clinical.allergyintolerance.queries.model.AllergyIntoleranceRecordQueryModel;
import com.openhis.health.domain.clinical.allergyintolerance.repository.AllergyIntoleranceRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * 过敏不耐受读侧查询服务。
 *
 * <p>职责：按就诊加载过敏记录列表，以及按患者+就诊派生「是否有过敏史」类标记。</p>
 *
 * <p>边界：只读；{@code encounterId}/{@code patientId} 缺失时返回空结果或 false 标记，不抛业务异常（读侧闭包）。</p>
 */
@Service
@RequiredArgsConstructor
public class AllergyIntoleranceQueryService {
    private final AllergyIntoleranceRepository repository;

    /**
     * 按就诊查询过敏记录并映射为读模型。
     *
     * @param encounterId 就诊标识；{@code null} 时返回空列表
     * @return 记录视图列表
     */
    public List<AllergyIntoleranceRecordQueryModel> getRecordInfo(Long encounterId) {
        // 无就诊上下文时不查库，避免误扫全表。
        if (encounterId == null) {
            return new ArrayList<>();
        }
        List<AllergyIntolerance> entities = repository.findByEncounterId(encounterId);
        List<AllergyIntoleranceRecordQueryModel> models = new ArrayList<>();
        for (AllergyIntolerance entity : entities) {
            AllergyIntoleranceRecordQueryModel model = new AllergyIntoleranceRecordQueryModel();
            model.setId(entity.getId());
            model.setRequestId(entity.getRequestId());
            model.setEncounterId(entity.getEncounterId());
            model.setPatientId(entity.getPatientId());
            model.setMedicationId(entity.getMedicationId());
            model.setLotNumber(entity.getLotNumber());
            model.setClinicalStatusEnum(entity.getClinicalStatusEnum());
            model.setVerificationStatusEnum(entity.getVerificationStatusEnum());
            model.setCriticalityEnum(entity.getCriticalityEnum());
            model.setSeverityEnum(entity.getSeverityEnum());
            model.setRecordedDate(entity.getRecordedDate());
            models.add(model);
        }
        return models;
    }

    /**
     * 派生当前就诊下是否存在过敏史（记录条数大于 0）。
     *
     * @param patientId   患者标识
     * @param encounterId 就诊标识
     * @return 标记模型；主键缺失时 {@code historyFlag=false} 且 {@code recordCount=0}
     */
    public AllergyHistoryFlagQueryModel getAllergyHistoryFlag(Long patientId, Long encounterId) {
        AllergyHistoryFlagQueryModel model = new AllergyHistoryFlagQueryModel();
        model.setPatientId(patientId);
        model.setEncounterId(encounterId);
        // 无法唯一定位患者+就诊时，不查库，避免展示误导性「有/无过敏」。
        if (patientId == null || encounterId == null) {
            model.setHistoryFlag(Boolean.FALSE);
            model.setRecordCount(0L);
            return model;
        }
        long count = repository.findByPatientIdAndEncounterId(patientId, encounterId).size();
        model.setHistoryFlag(count > 0);
        model.setRecordCount(count);
        return model;
    }
}
