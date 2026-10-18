/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.domain.diagnosis.vitalsign.entity;

import com.zxplat.domain.share.constants.ErrorCodeConstants;
import com.zxplat.framework.core.application.exception.BusinessException;
import com.zxplat.framework.core.domain.BaseAuditedEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import com.zxplat.framework.core.application.util.StringUtils;

import java.time.LocalDateTime;

/**
 * 生命体征明细行（{@code dia_vital_sign_detail}，DM-DIA-002 v2.1 §3.2）。
 */
@Getter
@Setter
@Entity
@Table(name = "dia_vital_sign_detail")
public class VitalSignDetail extends BaseAuditedEntity {

    /** 主表标识 FK {@code dia_vital_sign.id}。 */
    @Column(name = "vital_sign_id", nullable = false)
    private Long vitalSignId;

    /** 术语体系 code（如 {@code vital_signs_code}）。 */
    @Column(name = "term_code", nullable = false, length = 100)
    private String termCode;

    /** 字典项 code。 */
    @Column(name = "item_code", nullable = false, length = 100)
    private String itemCode;

    /** 字典项值。 */
    @Column(name = "item_value", nullable = false, length = 500)
    private String itemValue;

    /** 计量单位。 */
    @Column(name = "units", length = 100)
    private String units;

    /** 明细备注。 */
    @Column(name = "remark", length = 500)
    private String remark;

    /**
     * 断言术语体系 code 非空。
     */
    public void assertTermCodeNotBlank() {
        if (StringUtils.isEmpty(this.termCode)) {
            throw new BusinessException(ErrorCodeConstants.VITAL_SIGN_INVALID_ARGUMENT, "术语体系编码不能为空");
        }
    }

    /**
     * 断言字典项 code 非空。
     */
    public void assertItemCodeNotBlank() {
        if (StringUtils.isEmpty(this.itemCode)) {
            throw new BusinessException(ErrorCodeConstants.VITAL_SIGN_INVALID_ARGUMENT, "字典项编码不能为空");
        }
    }

    /**
     * 断言字典项值非空。
     */
    public void assertItemValueNotBlank() {
        if (StringUtils.isEmpty(this.itemValue)) {
            throw new BusinessException(ErrorCodeConstants.VITAL_SIGN_INVALID_ARGUMENT, "字典项值不能为空");
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
