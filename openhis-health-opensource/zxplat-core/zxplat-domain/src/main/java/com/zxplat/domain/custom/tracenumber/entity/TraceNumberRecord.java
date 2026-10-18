/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.domain.custom.tracenumber.entity;

import com.zxplat.domain.share.constants.ErrorCodeConstants;
import com.zxplat.domain.share.constants.TraceNumberConstants;
import com.zxplat.framework.core.application.exception.BusinessException;
import com.zxplat.framework.core.domain.BaseAuditedEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import com.zxplat.framework.core.application.util.StringUtils;

import java.math.BigDecimal;

/**
 * 追溯码记录子实体（DM-CUS-010 §3.2）。
 *
 * <p>对应物理表 {@link TraceNumberConstants#TABLE_CUS_TRACE_NUMBER_RECORD}；只 INSERT（BR-TNR-002）。</p>
 */
@Getter
@Setter
@Entity
@Table(name = TraceNumberConstants.TABLE_CUS_TRACE_NUMBER_RECORD)
public class TraceNumberRecord extends BaseAuditedEntity {

    /** 主档外键。 */
    @Column(name = "trace_number_id", nullable = false)
    private Long traceNumberId;

    /** 追溯码冗余。 */
    @Column(name = "trace_no", nullable = false)
    private String traceNo;

    /** 记录类别：{@link com.zxplat.domain.share.enums.TraceRecordCategory#getCode()}。 */
    @Column(name = "record_category_enum", nullable = false, length = 32)
    private String recordCategoryEnum;

    /** 轨迹方向：{@link com.zxplat.domain.share.enums.TraceDirection#getCode()}。 */
    @Column(name = "record_direction_enum", nullable = false, length = 16)
    private String recordDirectionEnum;

    /** 仓库类型（记录用，可空）。 */
    @Column(name = "location_type_enum", length = 64)
    private String locationTypeEnum;

    /** 库位标识（记录用，可空）。 */
    @Column(name = "location_id")
    private Long locationId;

    /** 仓位标识（可空）。 */
    @Column(name = "location_store_id")
    private Long locationStoreId;

    /** 批号。 */
    @Column(name = "lot_number", nullable = false)
    private String lotNumber;

    /** 本条业务数量。 */
    @Column(name = "quantity", nullable = false, precision = 18, scale = 4)
    private BigDecimal quantity;

    /** 本条业务单位。 */
    @Column(name = "unit_code", nullable = false, length = 64)
    private String unitCode;

    /** 本条拆零量（写侧换算后落库，读侧库位轧差直读）。 */
    @Column(name = "split_quantity", precision = 18, scale = 4)
    private BigDecimal splitQuantity;

    /** 操作类型 code。 */
    @Column(name = "operation_type", nullable = false, length = 64)
    private String operationType;

    /** 业务单据号。 */
    @Column(name = "bus_no")
    private String busNo;

    /**
     * 断言记录必填字段完整。
     */
    public void assertRequiredFields() {
        if (this.traceNumberId == null) {
            throw new BusinessException(ErrorCodeConstants.TRACE_NUMBER_INVALID_ARGUMENT, "主档标识不能为空");
        }
        if (StringUtils.isEmpty(this.traceNo)) {
            throw new BusinessException(ErrorCodeConstants.TRACE_NUMBER_INVALID_ARGUMENT, "追溯码不能为空");
        }
        if (StringUtils.isEmpty(this.recordCategoryEnum)) {
            throw new BusinessException(ErrorCodeConstants.TRACE_NUMBER_INVALID_ARGUMENT, "记录类别不能为空");
        }
        if (StringUtils.isEmpty(this.recordDirectionEnum)) {
            throw new BusinessException(ErrorCodeConstants.TRACE_NUMBER_INVALID_ARGUMENT, "轨迹方向不能为空");
        }
        if (StringUtils.isEmpty(this.lotNumber)) {
            throw new BusinessException(ErrorCodeConstants.TRACE_NUMBER_INVALID_ARGUMENT, "批号不能为空");
        }
        if (this.quantity == null || this.quantity.compareTo(BigDecimal.ZERO) <= 0) {
            throw new BusinessException(ErrorCodeConstants.TRACE_NUMBER_INVALID_ARGUMENT, "记录数量必须大于 0");
        }
        if (StringUtils.isEmpty(this.unitCode)) {
            throw new BusinessException(ErrorCodeConstants.TRACE_NUMBER_INVALID_ARGUMENT, "业务单位不能为空");
        }
        if (this.splitQuantity == null || this.splitQuantity.compareTo(BigDecimal.ZERO) <= 0) {
            throw new BusinessException(ErrorCodeConstants.TRACE_NUMBER_INVALID_ARGUMENT, "拆零量必须大于 0");
        }
        if (StringUtils.isEmpty(this.operationType)) {
            throw new BusinessException(ErrorCodeConstants.TRACE_NUMBER_INVALID_ARGUMENT, "操作类型不能为空");
        }
    }
}
