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

package com.openhis.health.domain.diagnosis.vitalsign.entity;

import com.openhis.health.domain.share.constants.ErrorCodeConstants;
import com.openhis.whale.core.application.exception.BusinessException;
import com.openhis.whale.core.domain.BaseAuditedEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import org.apache.commons.lang3.StringUtils;

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
        if (StringUtils.isBlank(this.termCode)) {
            throw new BusinessException(ErrorCodeConstants.VITAL_SIGN_INVALID_ARGUMENT, "术语体系编码不能为空");
        }
    }

    /**
     * 断言字典项 code 非空。
     */
    public void assertItemCodeNotBlank() {
        if (StringUtils.isBlank(this.itemCode)) {
            throw new BusinessException(ErrorCodeConstants.VITAL_SIGN_INVALID_ARGUMENT, "字典项编码不能为空");
        }
    }

    /**
     * 断言字典项值非空。
     */
    public void assertItemValueNotBlank() {
        if (StringUtils.isBlank(this.itemValue)) {
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
