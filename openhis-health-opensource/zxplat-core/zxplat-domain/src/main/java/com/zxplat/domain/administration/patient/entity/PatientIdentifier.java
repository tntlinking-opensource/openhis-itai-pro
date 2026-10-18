/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.domain.administration.patient.entity;

import com.zxplat.domain.share.constants.GeneralSharedConstants;
import com.zxplat.domain.share.constants.ErrorCodeConstants;
import com.zxplat.domain.share.converter.IdentifierStateConverter;
import com.zxplat.domain.share.enums.IdentifierState;
import com.zxplat.framework.core.application.exception.BusinessException;
import com.zxplat.framework.core.domain.BaseAuditedEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import com.zxplat.framework.core.application.util.StringUtils;

import java.time.LocalDateTime;

/**
 * 患者非身份证标识子实体。
 */
@Getter
@Setter
@Entity
@Table(name = "adm_patient_identifier")
public class PatientIdentifier extends BaseAuditedEntity {

    /**
     * 证件类型编码。
     */
    @Column(name = "type_code", nullable = false)
    private String typeCode;

    /**
     * 标识号码。
     */
    @Column(name = "identifier_no", nullable = false)
    private String identifierNo;

    /**
     * 标识状态编码。
     */
    @Column(name = "state_enum")
    @Convert(converter = IdentifierStateConverter.class)
    private IdentifierState stateEnum;

    /**
     * 有效开始时间。
     */
    @Column(name = "start_time")
    private LocalDateTime startTime;

    /**
     * 有效结束时间。
     */
    @Column(name = "end_time")
    private LocalDateTime endTime;

    /**
     * 所属患者标识。
     */
    @Column(name = "patient_id", nullable = false)
    private Long patientId;

    /**
     * 校验子实体参数。
     */
    public void assertValid() {
        if (StringUtils.isEmpty(this.typeCode)) {
            throw new BusinessException(ErrorCodeConstants.PATIENT_INVALID_ARGUMENT, "证件类型不能为空");
        }
        if (StringUtils.isEmpty(this.identifierNo)) {
            throw new BusinessException(ErrorCodeConstants.PATIENT_INVALID_ARGUMENT, "证件号码不能为空");
        }
        this.assertNotIdCardType();
        this.assertValidityWindow();
    }

    /**
     * 校验非身份证分工规则。
     */
    public void assertNotIdCardType() {
        if (GeneralSharedConstants.ID_CARD_TYPE_CODE.equals(this.typeCode)) {
            throw new BusinessException(ErrorCodeConstants.PATIENT_INVALID_ARGUMENT, "身份证号只能存储在患者主表");
        }
    }

    /**
     * 校验证件有效期窗口。
     */
    public void assertValidityWindow() {
        if (this.startTime != null && this.endTime != null && this.startTime.isAfter(this.endTime)) {
            throw new BusinessException(ErrorCodeConstants.PATIENT_INVALID_ARGUMENT, "证件有效期开始时间不能晚于结束时间");
        }
    }

    /**
     * 归一化状态值。
     */
    public void normalizeState() {
        if (this.stateEnum == null) {
            this.stateEnum = IdentifierState.USUAL;
        }
    }

    /**
     * 判断当前证件标识是否处于常用（USUAL）或官方（OFFICIAL）状态。
     *
     * <p>用于唯一性约束：同一证件类型内，常用或官方状态只允许存在一条（BR-PAT-004）。</p>
     *
     * @return 若 stateEnum 为 USUAL 或 OFFICIAL 则返回 true
     */
    public boolean isUsualOrOfficial() {
        return IdentifierState.USUAL.equals(this.stateEnum)
            || IdentifierState.OFFICIAL.equals(this.stateEnum);
    }

    /**
     * 按状态迁移规则变更状态。
     *
     * @param targetState 目标状态编码
     */
    public void transitState(IdentifierState targetState) {
        if (IdentifierState.OLD.equals(this.stateEnum) && !IdentifierState.OLD.equals(targetState)) {
            throw new BusinessException(ErrorCodeConstants.PATIENT_INVALID_STATUS, "已失效标识不可恢复");
        }
        this.stateEnum = targetState;
    }

    /**
     * 当证件过期时自动转为 old。
     *
     * @param now 当前时间
     */
    public void markOldWhenExpired(LocalDateTime now) {
        if (this.endTime != null && this.endTime.isBefore(now)) {
            this.stateEnum = IdentifierState.OLD;
        }
    }
}
