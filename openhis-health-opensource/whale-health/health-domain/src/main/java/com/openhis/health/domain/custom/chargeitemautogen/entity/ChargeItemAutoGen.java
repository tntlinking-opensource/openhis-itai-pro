/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.domain.custom.chargeitemautogen.entity;

import com.openhis.health.domain.share.constants.ErrorCodeConstants;
import com.openhis.health.domain.share.enums.PublicationStatus;
import com.openhis.whale.core.application.exception.BusinessException;
import com.openhis.whale.core.domain.BaseAuditedEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalTime;

/**
 * 收费明细自动生成规则聚合根。
 *
 * <p>该聚合为单表配置型数据，仅维护「触发来源 → 目标定价」映射与启停状态；
 * 不创建收费明细事实，不参与计费金额计算。</p>
 *
 * <p>住院滚费（床位/可日结配置）：{@code businessTime} 为 billDate 当日落账时刻，
 * {@code billingPractitionerId} 为计费人；护理级别滚费不读本表。</p>
 */
@Getter
@Setter
@Entity
@Table(name = "cus_charge_item_auto_gen")
public class ChargeItemAutoGen extends BaseAuditedEntity {

    /**
     * 规则名称。
     */
    @Column(name = "rule_name", nullable = false)
    private String ruleName;

    /**
     * 状态枚举（发布状态）。
     */
    @Column(name = "status_enum", nullable = false)
    private String statusEnum;

    /**
     * 住院就诊标识（adm_encounter.id）。
     */
    @Column(name = "encounter_id", nullable = false)
    private Long encounterId;

    /**
     * 触发来源表名（落库字符串须与来源聚合主表表名一致）。
     */
    @Column(name = "trigger_source_table", nullable = false)
    private String triggerSourceTable;

    /**
     * 触发来源主键。
     */
    @Column(name = "trigger_source_id", nullable = false)
    private Long triggerSourceId;

    /**
     * 收费项定价标识（adm_charge_item_definition.id）。
     */
    @Column(name = "charge_definition_id", nullable = false)
    private Long chargeDefinitionId;

    /**
     * 执行科室标识（为空表示全局规则）。
     */
    @Column(name = "performing_org_id")
    private Long performingOrgId;

    /**
     * 默认数量（大于 0）。
     */
    @Column(name = "quantity_value", nullable = false)
    private BigDecimal quantityValue;

    /**
     * 优先级（数值越小越靠前）。
     */
    @Column(name = "priority", nullable = false)
    private Integer priority;

    /**
     * 备注。
     */
    @Column(name = "remark")
    private String remark;

    /**
     * 滚费业务时刻（billDate 当日；默认 23:59:59）。
     */
    @Column(name = "business_time", nullable = false)
    private LocalTime businessTime;

    /**
     * 滚费计费人（{@code adm_practitioner.id}；落 Charge.performer_id）。
     */
    @Column(name = "billing_practitioner_id")
    private Long billingPractitionerId;

    /**
     * 初始化为启用态（active）。
     */
    public void initActiveStatus() {
        this.statusEnum = PublicationStatus.ACTIVE.getCode();
    }

    /**
     * 断言必填字段与数值合法性。
     */
    public void assertRequiredFields() {
        if (this.ruleName == null || this.ruleName.trim().isEmpty()) {
            throw new BusinessException(ErrorCodeConstants.CHARGE_ITEM_AUTO_GEN_INVALID_ARGUMENT, "规则名称不能为空");
        }
        if (this.statusEnum == null) {
            throw new BusinessException(ErrorCodeConstants.CHARGE_ITEM_AUTO_GEN_INVALID_ARGUMENT, "状态不能为空");
        }
        if (this.encounterId == null) {
            throw new BusinessException(ErrorCodeConstants.CHARGE_ITEM_AUTO_GEN_INVALID_ARGUMENT, "住院就诊标识不能为空");
        }
        if (this.triggerSourceTable == null || this.triggerSourceTable.trim().isEmpty()) {
            throw new BusinessException(ErrorCodeConstants.CHARGE_ITEM_AUTO_GEN_INVALID_ARGUMENT, "触发来源表名不能为空");
        }
        if (this.triggerSourceId == null) {
            throw new BusinessException(ErrorCodeConstants.CHARGE_ITEM_AUTO_GEN_INVALID_ARGUMENT, "触发来源主键不能为空");
        }
        if (this.chargeDefinitionId == null) {
            throw new BusinessException(ErrorCodeConstants.CHARGE_ITEM_AUTO_GEN_INVALID_ARGUMENT, "收费项定价标识不能为空");
        }
        if (this.quantityValue == null) {
            throw new BusinessException(ErrorCodeConstants.CHARGE_ITEM_AUTO_GEN_INVALID_ARGUMENT, "默认数量不能为空");
        }
        if (this.quantityValue.compareTo(BigDecimal.ZERO) <= 0) {
            throw new BusinessException(ErrorCodeConstants.CHARGE_ITEM_AUTO_GEN_INVALID_ARGUMENT, "默认数量必须大于零");
        }
        if (this.priority == null) {
            throw new BusinessException(ErrorCodeConstants.CHARGE_ITEM_AUTO_GEN_INVALID_ARGUMENT, "优先级不能为空");
        }
        if (this.priority < 0) {
            throw new BusinessException(ErrorCodeConstants.CHARGE_ITEM_AUTO_GEN_INVALID_ARGUMENT, "优先级必须为非负整数");
        }
        if (this.businessTime == null) {
            throw new BusinessException(ErrorCodeConstants.CHARGE_ITEM_AUTO_GEN_INVALID_ARGUMENT, "业务时间不能为空");
        }
        if (this.billingPractitionerId == null) {
            throw new BusinessException(ErrorCodeConstants.CHARGE_ITEM_AUTO_GEN_INVALID_ARGUMENT, "计费人不能为空");
        }
    }

    /**
     * 断言该规则允许修改（禁止对 deleted 终态做任何修改/启停）。
     */
    public void assertEditable() {
        if (PublicationStatus.DISCARDED.getCode().equals(this.statusEnum)) {
            throw new BusinessException(ErrorCodeConstants.CHARGE_ITEM_AUTO_GEN_INVALID_STATUS, "已废弃规则不允许修改");
        }
    }

    /**
     * 断言允许启用（INACTIVE -> ACTIVE）。
     */
    public void assertActivatable() {
        this.assertEditable();
        if (!PublicationStatus.INACTIVE.getCode().equals(this.statusEnum)) {
            throw new BusinessException(ErrorCodeConstants.CHARGE_ITEM_AUTO_GEN_INVALID_STATUS, "当前状态不允许启用");
        }
    }

    /**
     * 断言允许停用（ACTIVE -> INACTIVE）。
     */
    public void assertSuspendable() {
        this.assertEditable();
        if (!PublicationStatus.ACTIVE.getCode().equals(this.statusEnum)) {
            throw new BusinessException(ErrorCodeConstants.CHARGE_ITEM_AUTO_GEN_INVALID_STATUS, "当前状态不允许停用");
        }
    }

    /**
     * 启用规则。
     */
    public void activate() {
        this.assertActivatable();
        this.statusEnum = PublicationStatus.ACTIVE.getCode();
    }

    /**
     * 停用规则。
     */
    public void suspend() {
        this.assertSuspendable();
        this.statusEnum = PublicationStatus.INACTIVE.getCode();
    }

    /**
     * 标记废弃（deleted）并逻辑删除。
     */
    public void markDeleted() {
        this.statusEnum = PublicationStatus.DISCARDED.getCode();
        this.setIsDeleted(Boolean.TRUE);
    }
}
