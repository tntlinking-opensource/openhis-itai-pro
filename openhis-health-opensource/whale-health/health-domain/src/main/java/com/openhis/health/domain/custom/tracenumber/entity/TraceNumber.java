/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.domain.custom.tracenumber.entity;

import com.openhis.health.domain.share.constants.ErrorCodeConstants;
import com.openhis.health.domain.share.constants.TableNameConstants;
import com.openhis.health.domain.share.enums.InHospitalStatus;
import com.openhis.health.domain.share.enums.TraceRecordCategory;
import com.openhis.health.domain.share.enums.TraceDirection;
import com.openhis.whale.core.application.exception.BusinessException;
import com.openhis.whale.core.domain.BaseAuditedEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import com.openhis.whale.core.application.util.StringUtils;

import java.math.BigDecimal;

/**
 * 追溯码主档聚合根（DM-CUS-010 §3.1）。
 *
 * <p>一码一行；全院在院态与拆零剩余由 {@code HOSPITAL_IO} 流水驱动维护。</p>
 */
@Getter
@Setter
@Entity
@Table(name = "cus_trace_number")
public class TraceNumber extends BaseAuditedEntity {

    /** 追溯码字符串（租户内业务唯一）。 */
    @Column(name = "trace_no", nullable = false)
    private String traceNo;

    /** 品项物理表名（本期仅 {@link TableNameConstants#MED_MEDICATION_DEFINITION}）。 */
    @Column(name = "item_table", nullable = false)
    private String itemTable;

    /** 药品定义标识。 */
    @Column(name = "item_id", nullable = false)
    private Long itemId;

    /** 批号。 */
    @Column(name = "lot_number", nullable = false)
    private String lotNumber;

    /** 追溯码所贴包装单位。 */
    @Column(name = "trace_unit_code", nullable = false, length = 64)
    private String traceUnitCode;

    /**
     * 建码时 1 个追溯码单位对应的拆零数量快照（来自当时药典 {@code trace_unit_ratio}）。
     *
     * <p>发退/应扫换算优先本列；药典日后变更不回写。</p>
     */
    @Column(name = "trace_unit_ratio", precision = 18, scale = 6)
    private BigDecimal traceUnitRatio;

    /** 拆零剩余量（最小单位）。 */
    @Column(name = "remaining_split_quantity", nullable = false, precision = 18, scale = 4)
    private BigDecimal remainingSplitQuantity;

    /** 在院状态：{@link InHospitalStatus#getCode()}。 */
    @Column(name = "in_hospital_status_enum", nullable = false, length = 32)
    private String inHospitalStatusEnum;

    /**
     * 断言主档必填字段完整（BR-TNR-001）。
     */
    public void assertRequiredFields() {
        if (StringUtils.isEmpty(this.traceNo)) {
            throw new BusinessException(ErrorCodeConstants.TRACE_NUMBER_INVALID_ARGUMENT, "追溯码不能为空");
        }
        if (StringUtils.isEmpty(this.itemTable)) {
            throw new BusinessException(ErrorCodeConstants.TRACE_NUMBER_INVALID_ARGUMENT, "品项表不能为空");
        }
        // 本期成码范围仅药品；耗材主档须待后续域扩展
        if (!TableNameConstants.MED_MEDICATION_DEFINITION.equals(this.itemTable)) {
            throw new BusinessException(ErrorCodeConstants.TRACE_NUMBER_MEDICATION_ONLY, "本期仅支持药品追溯码");
        }
        if (this.itemId == null) {
            throw new BusinessException(ErrorCodeConstants.TRACE_NUMBER_INVALID_ARGUMENT, "药品标识不能为空");
        }
        if (StringUtils.isEmpty(this.lotNumber)) {
            throw new BusinessException(ErrorCodeConstants.TRACE_NUMBER_INVALID_ARGUMENT, "批号不能为空");
        }
        if (StringUtils.isEmpty(this.traceUnitCode)) {
            throw new BusinessException(ErrorCodeConstants.TRACE_NUMBER_INVALID_ARGUMENT, "追溯码单位不能为空");
        }
        // 建码须落比例快照，否则发退无法在药典变更后仍按原贴码层级换算
        if (this.traceUnitRatio == null || this.traceUnitRatio.compareTo(BigDecimal.ZERO) <= 0) {
            throw new BusinessException(ErrorCodeConstants.TRACE_NUMBER_INVALID_ARGUMENT, "追溯码单位换算比快照须大于 0");
        }
        if (this.remainingSplitQuantity == null) {
            throw new BusinessException(ErrorCodeConstants.TRACE_NUMBER_INVALID_ARGUMENT, "拆零剩余量不能为空");
        }
        if (StringUtils.isEmpty(this.inHospitalStatusEnum)) {
            throw new BusinessException(ErrorCodeConstants.TRACE_NUMBER_INVALID_ARGUMENT, "在院状态不能为空");
        }
    }

    /**
     * 按进出院流水方向增减拆零剩余并翻转在院态（DM-SDD §17 · BR-TNR-003/011）。
     *
     * @param recordCategoryEnum  流水类别；非 {@link TraceRecordCategory#HOSPITAL_IO} 时不改变主档
     * @param recordDirectionEnum 轨迹方向 IN/OUT
     * @param splitDelta        已换算为拆零单位的正数增量
     */
    public void applyHospitalIoDelta(String recordCategoryEnum, String recordDirectionEnum, BigDecimal splitDelta) {
        // 院内流转类流水不改变主表剩余与在院态（BR-TNR-003）
        if (!TraceRecordCategory.HOSPITAL_IO.getCode().equals(recordCategoryEnum)) {
            return;
        }
        if (splitDelta == null || splitDelta.compareTo(BigDecimal.ZERO) <= 0) {
            throw new BusinessException(ErrorCodeConstants.TRACE_NUMBER_INVALID_ARGUMENT, "拆零动账量必须大于 0");
        }
        if (TraceDirection.IN.getCode().equals(recordDirectionEnum)) {
            this.remainingSplitQuantity = this.remainingSplitQuantity.add(splitDelta);
            // 有剩余即在院
            if (this.remainingSplitQuantity.compareTo(BigDecimal.ZERO) > 0) {
                this.inHospitalStatusEnum = InHospitalStatus.IN_HOSPITAL.getCode();
            }
            return;
        }
        if (TraceDirection.OUT.getCode().equals(recordDirectionEnum)) {
            // 出库不得超过主表剩余（BR-TNR-011）
            if (this.remainingSplitQuantity.compareTo(splitDelta) < 0) {
                throw new BusinessException(
                    ErrorCodeConstants.TRACE_NUMBER_NOT_IN_HOSPITAL,
                    "追溯码剩余不足，无法出库"
                );
            }
            this.remainingSplitQuantity = this.remainingSplitQuantity.subtract(splitDelta);
            // 剩余耗尽则翻不在院
            if (this.remainingSplitQuantity.compareTo(BigDecimal.ZERO) <= 0) {
                this.remainingSplitQuantity = BigDecimal.ZERO;
                this.inHospitalStatusEnum = InHospitalStatus.NOT_IN_HOSPITAL.getCode();
            }
            return;
        }
        throw new BusinessException(ErrorCodeConstants.TRACE_NUMBER_INVALID_ARGUMENT, "轨迹方向非法");
    }
}
