/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.domain.diagnosis.vitalsign.entity;

import com.openhis.health.domain.diagnosis.vitalsign.enums.VitalSignTypeEnum;
import com.openhis.health.domain.share.constants.ErrorCodeConstants;
import com.openhis.whale.core.application.exception.BusinessException;
import com.openhis.whale.core.domain.BaseAuditedEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import com.openhis.whale.core.application.util.StringUtils;

import java.time.LocalDateTime;
import java.util.Objects;

import static com.openhis.whale.core.application.util.StringUtils.trimToNull;

/**
 * 生命体征单据聚合根（主表 {@code dia_vital_sign}，DM-DIA-002 v2.1 §3.1）。
 */
@Getter
@Setter
@Entity
@Table(name = "dia_vital_sign")
public class VitalSign extends BaseAuditedEntity {

    /** 患者标识 {@code adm_patient.id}（BR-VS-001）。 */
    @Column(name = "patient_id", nullable = false)
    private Long patientId;

    /** 就诊标识 {@code adm_encounter.id}（BR-VS-001）。 */
    @Column(name = "encounter_id", nullable = false)
    private Long encounterId;

    /** 记录时点。 */
    @Column(name = "recorded_at", nullable = false)
    private LocalDateTime recordedAt;

    /** 单据类型 code，存 {@link VitalSignTypeEnum#getCode()}。 */
    @Column(name = "type_enum", nullable = false, length = 50)
    private String typeEnum;

    /** 业务单号，租户内唯一（BR-VS-002）。 */
    @Column(name = "bus_no", nullable = false, length = 100)
    private String busNo;

    /** 记录时病床 {@code Location.id}（BR-VS-006）。 */
    @Column(name = "location_id")
    private Long locationId;

    /** 婴儿序号；自然键比对时 null 归一化为 0。 */
    @Column(name = "baby_no")
    private Integer babyNo;

    /** 记录人 {@code Practitioner.id}。 */
    @Column(name = "recorder_id")
    private Long recorderId;

    /** 整单备注。 */
    @Column(name = "remark", length = 500)
    private String remark;

    /**
     * 婴儿序号归一化：{@code null} 视为 0。
     *
     * @param babyNo 原始婴儿序号
     * @return 归一化后的序号
     */
    public static Integer normalizeBabyNo(Integer babyNo) {
        if (babyNo == null) {
            return 0;
        }
        return babyNo;
    }

    /**
     * 断言患者与就诊归属一致且非空（BR-VS-001）。
     *
     * @param patientId   命令患者标识
     * @param encounterId 命令就诊标识
     */
    public static void assertPatientEncounterBound(Long patientId, Long encounterId) {
        if (patientId == null || encounterId == null) {
            throw new BusinessException(ErrorCodeConstants.VITAL_SIGN_INVALID_ARGUMENT, "患者与就诊标识不能为空");
        }
    }

    /**
     * 断言单据类型为合法枚举 code。
     *
     * @param typeEnum 单据类型 code
     */
    public static void assertTypeEnumValid(String typeEnum) {
        VitalSignTypeEnum.fromCode(typeEnum);
    }

    /**
     * 断言业务单号非空（BR-VS-002）。
     */
    public void assertBusNoNotBlank() {
        if (StringUtils.isEmpty(this.busNo)) {
            throw new BusinessException(ErrorCodeConstants.VITAL_SIGN_INVALID_ARGUMENT, "业务单号不能为空");
        }
    }

    /**
     * 断言至少一项业务值：明细行，或护理记录整单备注（BR-VS-008）。
     *
     * @param typeEnum    单据类型 code
     * @param remark      整单备注
     * @param detailCount 明细有效行数
     */
    public static void assertHasBusinessContent(String typeEnum, String remark, int detailCount) {
        if (detailCount > 0) {
            return;
        }
        // 业务分支：护理记录允许仅填病情观察（remark），不要求必有观测明细
        if (VitalSignTypeEnum.NURSING_RECORD.getCode().equals(typeEnum) && trimToNull(remark) != null) {
            return;
        }
        throw new BusinessException(ErrorCodeConstants.VITAL_SIGN_INVALID_ARGUMENT, "明细与护理备注至少填写一项");
    }

    /**
     * 断言本行患者/就诊与命令上下文一致（BR-VS-001）。
     *
     * @param patientId   命令患者标识
     * @param encounterId 命令就诊标识
     */
    public void assertSamePatientEncounter(Long patientId, Long encounterId) {
        if (!Objects.equals(this.patientId, patientId) || !Objects.equals(this.encounterId, encounterId)) {
            throw new BusinessException(ErrorCodeConstants.VITAL_SIGN_INVALID_ARGUMENT, "生命体征单据与就诊上下文不一致");
        }
    }

    /**
     * 逻辑删除并记录操作人（BR-VS-004）。
     *
     * @param operatorId 操作人主键
     */
    public void markDeleted(Long operatorId) {
        this.setIsDeleted(Boolean.TRUE);
        this.setDeletedBy(operatorId);
        this.setDeletedAt(LocalDateTime.now());
    }
}
