/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.domain.clinical.allergyintolerance.service;

import com.zxplat.domain.clinical.allergyintolerance.entity.AllergyIntolerance;
import com.zxplat.domain.clinical.allergyintolerance.queries.model.AllergyHistoryFlagQueryModel;
import com.zxplat.domain.clinical.allergyintolerance.queries.model.AllergyIntoleranceRecordQueryModel;
import com.zxplat.domain.clinical.allergyintolerance.repository.AllergyIntoleranceRepository;
import com.zxplat.domain.share.constants.ErrorCodeConstants;
import com.zxplat.framework.core.application.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import com.zxplat.framework.core.application.util.StringUtils;

import java.util.ArrayList;
import java.util.List;

/**
 * 过敏不耐受读侧查询服务。
 *
 * <p>职责：按就诊加载皮试记录；按患者加载过敏史列表与「有过敏 / 无已知过敏 / 未登记」标记。</p>
 *
 * <p>边界：只读；仅本聚合 {@link AllergyIntoleranceRepository}。</p>
 */
@Service
@RequiredArgsConstructor
public class AllergyIntoleranceQueryService {
    private final AllergyIntoleranceRepository repository;

    /**
     * 按就诊查询过敏记录并映射为读模型（皮试列表用，含阴性）。
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
     * 按患者加载过敏史可见记录：主诉、NKDA、阳性皮试。
     *
     * @param patientId 患者标识；{@code null} 时返回空列表
     * @return 过敏史读模型，按记录时间倒序
     */
    public List<AllergyIntoleranceRecordQueryModel> listPatientAllergyHistory(Long patientId) {
        List<AllergyIntoleranceRecordQueryModel> models = new ArrayList<>();
        if (patientId == null) {
            return models;
        }
        List<AllergyIntolerance> entities = repository.findByPatientId(patientId);
        for (AllergyIntolerance entity : entities) {
            if (entity == null || Boolean.TRUE.equals(entity.getIsDeleted())) {
                continue;
            }
            if (!this.isHistoryVisible(entity)) {
                continue;
            }
            models.add(this.toRecordModel(entity));
        }
        models.sort((left, right) -> {
            if (left.getRecordedDate() == null && right.getRecordedDate() == null) {
                return 0;
            }
            if (left.getRecordedDate() == null) {
                return 1;
            }
            if (right.getRecordedDate() == null) {
                return -1;
            }
            return right.getRecordedDate().compareTo(left.getRecordedDate());
        });
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
        model.setTypeEnum(entity.getTypeEnum());
        model.setCategoryCode(entity.getCategoryCode());
        model.setCode(entity.getCode());
        model.setRecordedDate(entity.getRecordedDate());
        model.setDescription(entity.getDescription());
        model.setNote(entity.getNote());
        model.setOnsetDateTime(entity.getOnsetDateTime());
        model.setPractitionerId(entity.getPractitionerId());
        model.setCheckPractitionerId(entity.getCheckPractitionerId());
        return model;
    }

    /**
     * 按患者派生过敏史标记与摘要（跨就诊）。
     *
     * @param patientId   患者标识
     * @param encounterId 就诊标识（仅回填读模型，不参与过滤）
     * @return 标记模型；患者缺失时 {@code historyFlag=false} 且文案为未登记
     */
    public AllergyHistoryFlagQueryModel getAllergyHistoryFlag(Long patientId, Long encounterId) {
        AllergyHistoryFlagQueryModel model = new AllergyHistoryFlagQueryModel();
        model.setPatientId(patientId);
        model.setEncounterId(encounterId);
        if (patientId == null) {
            model.setHistoryFlag(Boolean.FALSE);
            model.setNkdaFlag(Boolean.FALSE);
            model.setRecordCount(0L);
            model.setSummaryText("未登记");
            return model;
        }
        List<AllergyIntolerance> entities = repository.findByPatientId(patientId);
        long activeCount = 0L;
        boolean hasNkda = false;
        List<String> allergenNames = new ArrayList<>();
        for (AllergyIntolerance entity : entities) {
            if (entity == null || Boolean.TRUE.equals(entity.getIsDeleted())) {
                continue;
            }
            if (entity.isNkdaRecord()) {
                hasNkda = true;
                continue;
            }
            if (!entity.isActiveAllergen()) {
                continue;
            }
            activeCount = activeCount + 1;
            String name = entity.getDescription();
            if (StringUtils.isEmpty(name)) {
                continue;
            }
            allergenNames.add(name.trim());
        }
        model.setRecordCount(activeCount);
        if (activeCount > 0) {
            model.setHistoryFlag(Boolean.TRUE);
            model.setNkdaFlag(Boolean.FALSE);
            model.setSummaryText(this.joinAllergenNames(allergenNames));
            return model;
        }
        model.setHistoryFlag(Boolean.FALSE);
        if (hasNkda) {
            model.setNkdaFlag(Boolean.TRUE);
            model.setSummaryText("无");
            return model;
        }
        model.setNkdaFlag(Boolean.FALSE);
        model.setSummaryText("未登记");
        return model;
    }

    /**
     * 过敏史列表是否展示该行：主诉、NKDA、阳性皮试。
     *
     * @param entity 聚合根
     * @return 可见则 true
     */
    private boolean isHistoryVisible(AllergyIntolerance entity) {
        // NKDA 声明要展示，便于护士看到「已确认无过敏」
        if (entity.isNkdaRecord()) {
            return true;
        }
        // 主诉行无论是否已停用都展示，便于追溯
        if (entity.isDeclaredRecord()) {
            return true;
        }
        // 皮试只把阳性带进过敏史，阴性留在皮试页
        if (entity.isSkinTestRecord() && entity.isActiveAllergen()) {
            return true;
        }
        return false;
    }

    /**
     * 将过敏原名称用顿号拼接；空列表返回空串。
     *
     * @param names 过敏原名称
     * @return 摘要
     */
    private String joinAllergenNames(List<String> names) {
        if (names == null || names.isEmpty()) {
            return "";
        }
        StringBuilder builder = new StringBuilder();
        for (String name : names) {
            if (StringUtils.isEmpty(name)) {
                continue;
            }
            if (builder.length() > 0) {
                builder.append("、");
            }
            builder.append(name);
        }
        return builder.toString();
    }
}
