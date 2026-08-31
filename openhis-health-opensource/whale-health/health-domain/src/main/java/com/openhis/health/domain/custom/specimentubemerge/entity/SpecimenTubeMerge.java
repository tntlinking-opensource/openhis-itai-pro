/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.domain.custom.specimentubemerge.entity;

import com.openhis.health.domain.share.constants.ErrorCodeConstants;
import com.openhis.health.domain.share.constants.TableNameConstants;
import com.openhis.whale.core.application.exception.BusinessException;
import com.openhis.whale.core.domain.BaseAuditedEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.Setter;
import com.openhis.whale.core.application.util.StringUtils;

/**
 * 标本合管配置明细（{@code cus_specimen_tube_merge}，DM-CUS-014）。
 */
@Getter
@Setter
@Entity
@Table(name = "cus_specimen_tube_merge")
public class SpecimenTubeMerge extends BaseAuditedEntity {

    /** 检验设备主键 {@code adm_device.id}。 */
    @Column(name = "device_id", nullable = false)
    private Long deviceId;

    /** 绑定目标表：{@code wkf_activity_definition} 或 {@code adm_device_definition}。 */
    @Column(name = "target_table", nullable = false)
    private String targetTable;

    /** 绑定目标主键。 */
    @Column(name = "target_id", nullable = false)
    private Long targetId;

    /** 耗材用量；activity 行须为 null。 */
    @Column(name = "quantity")
    private BigDecimal quantity;

    /** 耗材计量单位 code；activity 行须为 null。 */
    @Column(name = "unit_code")
    private String unitCode;

    /** 同设备下展示排序。 */
    @Column(name = "display_order")
    private Integer displayOrder;

    /** 校验绑定行必填字段与耗材数量口径。 */
    public void assertRequiredFields() {
        if (this.deviceId == null) {
            throw new BusinessException(ErrorCodeConstants.SPECIMEN_TUBE_MERGE_INVALID_ARGUMENT, "合管配置设备不能为空");
        }
        if (StringUtils.isEmpty(this.targetTable) || this.targetId == null) {
            throw new BusinessException(ErrorCodeConstants.SPECIMEN_TUBE_MERGE_INVALID_ARGUMENT, "合管绑定目标不能为空");
        }
        if (!TableNameConstants.WKF_ACTIVITY_DEFINITION.equals(this.targetTable)
            && !TableNameConstants.ADM_DEVICE_DEFINITION.equals(this.targetTable)) {
            throw new BusinessException(ErrorCodeConstants.SPECIMEN_TUBE_MERGE_INVALID_ARGUMENT, "合管绑定目标表不合法");
        }
        if (TableNameConstants.ADM_DEVICE_DEFINITION.equals(this.targetTable)) {
            if (this.quantity == null || this.quantity.compareTo(BigDecimal.ZERO) <= 0) {
                throw new BusinessException(ErrorCodeConstants.SPECIMEN_TUBE_MERGE_INVALID_ARGUMENT, "合管耗材数量须大于 0");
            }
            if (StringUtils.isEmpty(this.unitCode)) {
                throw new BusinessException(ErrorCodeConstants.SPECIMEN_TUBE_MERGE_INVALID_ARGUMENT, "合管耗材单位不能为空");
            }
        }
    }

    /**
     * 逻辑删除并记录操作人。
     *
     * @param operatorId 操作人主键
     */
    public void markDeleted(Long operatorId) {
        this.setIsDeleted(Boolean.TRUE);
        this.setDeletedBy(operatorId);
        this.setDeletedAt(LocalDateTime.now());
    }
}
