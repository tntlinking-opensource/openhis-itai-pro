/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.domain.clinical.observation.entity;

import com.zxplat.domain.share.constants.ErrorCodeConstants;
import com.zxplat.domain.share.constants.ObservationStatusConstants;
import com.zxplat.framework.core.application.exception.BusinessException;
import com.zxplat.framework.core.application.util.StringUtils;
import com.zxplat.framework.core.domain.BaseAuditedEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 临床观察实例聚合根（表 {@code cln_observation}，DM-CLN-007）。
 *
 * <p>手工录入与 LIS/PACS 投影统一落本表；手工路径禁止填写 {@code sourceObservationId}。</p>
 */
@Getter
@Setter
@Entity
@Table(name = "cln_observation")
public class Observation extends BaseAuditedEntity {

    /** 院区/机构标识。 */
    @Column(name = "org_id")
    private Long orgId;

    /** 就诊标识。 */
    @Column(name = "encounter_id", nullable = false)
    private Long encounterId;

    /** 服务申请标识。 */
    @Column(name = "service_request_id")
    private Long serviceRequestId;

    /** 观察项定义标识。 */
    @Column(name = "definition_id", nullable = false)
    private Long definitionId;

    /** 观察项编码（冗余 OD.code）。 */
    @Column(name = "code")
    private String code;

    /** 数值结果。 */
    @Column(name = "value_quantity")
    private BigDecimal valueQuantity;

    /** 文本结果。 */
    @Column(name = "value_string")
    private String valueString;

    /** 编码概念结果。 */
    @Column(name = "value_codeable_concept")
    private String valueCodeableConcept;

    /** 解读编码。 */
    @Column(name = "interpretation_code")
    private String interpretationCode;

    /** 状态枚举（preliminary/final/amended/cancelled）。 */
    @Column(name = "status_enum", nullable = false)
    private String statusEnum;

    /** 有效时间。 */
    @Column(name = "effective_time")
    private LocalDateTime effectiveTime;

    /** 来源观察实例标识（LIS 投影必填；手工录入须为空）。 */
    @Column(name = "source_observation_id")
    private Long sourceObservationId;

    /** 来源模块枚举。 */
    @Column(name = "source_module_enum")
    private String sourceModuleEnum;

    /** 执行人标识。 */
    @Column(name = "performer_id")
    private Long performerId;

    @Version
    @Column(name = "version", nullable = false)
    private Integer version;

    /**
     * 写入结果值字段（按 OD 类型择填）。
     *
     * @param quantity           数值
     * @param stringValue        文本
     * @param codeableConcept    编码概念
     * @param interpretationCode 解读码
     */
    public void applyValue(
        BigDecimal quantity,
        String stringValue,
        String codeableConcept,
        String interpretationCode) {
        this.valueQuantity = quantity;
        this.valueString = stringValue;
        this.valueCodeableConcept = codeableConcept;
        this.interpretationCode = interpretationCode;
        this.effectiveTime = LocalDateTime.now();
    }

    /**
     * 标记为初步结果。
     */
    public void markPreliminary() {
        this.statusEnum = ObservationStatusConstants.STATUS_PRELIMINARY;
    }

    /**
     * 确认为最终结果。
     */
    public void finalizeObservation() {
        this.statusEnum = ObservationStatusConstants.STATUS_FINAL;
        if (this.effectiveTime == null) {
            this.effectiveTime = LocalDateTime.now();
        }
    }

    /**
     * 标记为已修订（final 后改值，BR-CLN-OBS-03）。
     */
    public void markAmended() {
        this.statusEnum = ObservationStatusConstants.STATUS_AMENDED;
    }

    /**
     * 作废观察实例。
     *
     * @param reason 作废原因（不落库，仅校验非空）
     */
    public void cancel(String reason) {
        if (StringUtils.isEmpty(reason)) {
            throw new BusinessException(
                ErrorCodeConstants.OBSERVATION_INVALID_ARGUMENT, "作废原因不能为空");
        }
        this.statusEnum = ObservationStatusConstants.STATUS_CANCELLED;
    }

    /**
     * 断言手工录入路径：禁止携带 LIS 来源链（BR-CLN-OBS-01 手工侧）。
     */
    public void assertManualNoSource() {
        if (this.sourceObservationId != null) {
            throw new BusinessException(
                ErrorCodeConstants.OBSERVATION_INVALID_ARGUMENT,
                "手工录入禁止填写来源观察实例标识");
        }
    }
}
