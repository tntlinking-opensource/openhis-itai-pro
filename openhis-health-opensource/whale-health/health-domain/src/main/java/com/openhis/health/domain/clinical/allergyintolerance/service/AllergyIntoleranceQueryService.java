/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.domain.clinical.allergyintolerance.service;

import com.openhis.health.domain.clinical.allergyintolerance.entity.AllergyIntolerance;
import com.openhis.health.domain.clinical.allergyintolerance.queries.model.AllergyHistoryFlagQueryModel;
import com.openhis.health.domain.clinical.allergyintolerance.queries.model.AllergyIntoleranceRecordQueryModel;
import com.openhis.health.domain.clinical.allergyintolerance.repository.AllergyIntoleranceRepository;
import com.openhis.health.domain.share.constants.ErrorCodeConstants;
import com.openhis.whale.core.application.exception.BusinessException;
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
 *
 * <p>聚合边界：仅本聚合 {@link AllergyIntoleranceRepository}。</p>
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
            if (entity == null || Boolean.TRUE.equals(entity.getIsDeleted())) {
                continue;
            }
            models.add(this.toRecordModel(entity));
        }
        return models;
    }

    /**
     * 按主键加载单条过敏记录读模型（皮试删除/更新门控）。
     *
     * @param recordId 过敏记录主键
     * @return 读模型
     * @throws BusinessException 记录不存在或已删除
     */
    public AllergyIntoleranceRecordQueryModel mustGetRecordById(Long recordId) {
        if (recordId == null) {
            throw new BusinessException(ErrorCodeConstants.ALLERGY_INTOLERANCE_INVALID_ARGUMENT, "过敏记录标识不能为空");
        }
        AllergyIntolerance entity = repository.findById(recordId).orElseThrow(
            () -> new BusinessException(ErrorCodeConstants.ALLERGY_INTOLERANCE_NOT_FOUND, "过敏记录不存在"));
        if (Boolean.TRUE.equals(entity.getIsDeleted())) {
            throw new BusinessException(ErrorCodeConstants.ALLERGY_INTOLERANCE_NOT_FOUND, "过敏记录不存在");
        }
        return this.toRecordModel(entity);
    }

    /**
     * 过敏实体 → 记录读模型（字段一一对齐）。
     *
     * @param entity 聚合根
     * @return 读模型
     */
    private AllergyIntoleranceRecordQueryModel toRecordModel(AllergyIntolerance entity) {
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
        model.setDescription(entity.getDescription());
        model.setPractitionerId(entity.getPractitionerId());
        model.setCheckPractitionerId(entity.getCheckPractitionerId());
        return model;
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
