/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.domain.custom.servicedevicetemplate.entity;

import com.zxplat.domain.share.constants.ErrorCodeConstants;
import com.zxplat.domain.share.enums.PublicationStatus;
import com.zxplat.framework.core.application.exception.BusinessException;
import com.zxplat.framework.core.domain.BaseAuditedEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

/**
 * 诊疗耗材绑定聚合根。
 *
 * <p>本聚合为单表配置型数据：维护来源项（用法/诊疗/号源）与目标项（诊疗/耗材）之间的默认绑定关系。
 * 消费方仅查询启用（active）且未逻辑删除的绑定行参与自动附加。</p>
 */
@Getter
@Setter
@Entity
@Table(name = "cus_service_device_template")
public class ServiceDeviceTemplate extends BaseAuditedEntity {

    /**
     * 绑定范围编码。
     */
    @Column(name = "range_code", nullable = false)
    private String rangeCode;

    /**
     * 绑定类型编码（1 用法/2 诊疗/3 号源）。
     */
    @Column(name = "type_code", nullable = false)
    private String typeCode;

    /**
     * 来源表名。
     */
    @Column(name = "item_table", nullable = false)
    private String itemTable;

    /**
     * 来源项标识（跨表主键类型不一，持久化为 String）。
     */
    @Column(name = "item_id", nullable = false)
    private String itemId;

    /**
     * 目标表名（诊疗定义表或耗材定义表）。
     */
    @Column(name = "dev_act_table", nullable = false)
    private String devActTable;

    /**
     * 目标项标识（诊疗或耗材定义主键）。
     */
    @Column(name = "dev_act_id", nullable = false)
    private Long devActId;

    /**
     * 绑定数量（> 0）。
     */
    @Column(name = "quantity", nullable = false)
    private BigDecimal quantity;

    /**
     * 单位编码。
     */
    @Column(name = "unit_code", nullable = false)
    private String unitCode;

    /**
     * 发布状态（active/retired）。
     */
    @Column(name = "status_enum", nullable = false)
    private String statusEnum;

    /**
     * 初始化为启用态。
     */
    public void initActiveStatus() {
        this.statusEnum = PublicationStatus.ACTIVE.getCode();
    }

    /**
     * 断言必填字段与数值合法性（BR-SDT-001/008）。
     */
    public void assertRequiredFields() {
        if (this.rangeCode == null || this.rangeCode.trim().isEmpty()) {
            throw new BusinessException(ErrorCodeConstants.SERVICE_DEVICE_TEMPLATE_INVALID_ARGUMENT, "绑定范围不能为空");
        }
        if (this.typeCode == null || this.typeCode.trim().isEmpty()) {
            throw new BusinessException(ErrorCodeConstants.SERVICE_DEVICE_TEMPLATE_INVALID_ARGUMENT, "绑定类型不能为空");
        }
        if (this.itemTable == null || this.itemTable.trim().isEmpty()) {
            throw new BusinessException(ErrorCodeConstants.SERVICE_DEVICE_TEMPLATE_INVALID_ARGUMENT, "来源表名不能为空");
        }
        if (this.itemId == null || this.itemId.trim().isEmpty()) {
            throw new BusinessException(ErrorCodeConstants.SERVICE_DEVICE_TEMPLATE_INVALID_ARGUMENT, "来源项标识不能为空");
        }
        if (this.devActTable == null || this.devActTable.trim().isEmpty()) {
            throw new BusinessException(ErrorCodeConstants.SERVICE_DEVICE_TEMPLATE_INVALID_ARGUMENT, "目标表名不能为空");
        }
        if (this.devActId == null) {
            throw new BusinessException(ErrorCodeConstants.SERVICE_DEVICE_TEMPLATE_INVALID_ARGUMENT, "目标项标识不能为空");
        }
        if (this.quantity == null) {
            throw new BusinessException(ErrorCodeConstants.SERVICE_DEVICE_TEMPLATE_INVALID_ARGUMENT, "绑定数量不能为空");
        }
        if (this.quantity.compareTo(BigDecimal.ZERO) <= 0) {
            throw new BusinessException(ErrorCodeConstants.SERVICE_DEVICE_TEMPLATE_INVALID_ARGUMENT, "绑定数量必须为正数");
        }
        if (this.unitCode == null || this.unitCode.trim().isEmpty()) {
            throw new BusinessException(ErrorCodeConstants.SERVICE_DEVICE_TEMPLATE_INVALID_ARGUMENT, "单位编码不能为空");
        }
        if (this.statusEnum == null) {
            throw new BusinessException(ErrorCodeConstants.SERVICE_DEVICE_TEMPLATE_INVALID_ARGUMENT, "发布状态不能为空");
        }
    }

    /**
     * 断言允许启用（retired -> active）。
     */
    public void assertActivatable() {
        if (PublicationStatus.ACTIVE.getCode().equals(this.statusEnum)) {
            throw new BusinessException(ErrorCodeConstants.SERVICE_DEVICE_TEMPLATE_INVALID_STATUS, "当前已是启用状态");
        }
    }

    /**
     * 断言允许停用（active -> retired）。
     */
    public void assertDeactivatable() {
        if (!PublicationStatus.ACTIVE.getCode().equals(this.statusEnum)) {
            throw new BusinessException(ErrorCodeConstants.SERVICE_DEVICE_TEMPLATE_INVALID_STATUS, "当前状态不允许停用");
        }
    }

    /**
     * 启用绑定。
     */
    public void activate() {
        this.assertActivatable();
        this.statusEnum = PublicationStatus.ACTIVE.getCode();
    }

    /**
     * 停用绑定。
     */
    public void deactivate() {
        this.assertDeactivatable();
        this.statusEnum = PublicationStatus.INACTIVE.getCode();
    }

    /**
     * 标记逻辑删除（BR-SDT-007）。
     */
    public void markDeleted() {
        this.setIsDeleted(Boolean.TRUE);
        this.statusEnum = PublicationStatus.DISCARDED.getCode();
    }
}
