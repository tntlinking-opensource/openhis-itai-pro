/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.domain.administration.labdevice.entity;

import com.zxplat.domain.share.constants.ErrorCodeConstants;
import com.zxplat.domain.share.enums.PublicationStatus;
import com.zxplat.domain.share.utils.SearchCodeUtil;
import com.zxplat.framework.core.application.exception.BusinessException;
import com.zxplat.framework.core.domain.BaseAuditedEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.time.LocalDate;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.Setter;
import com.zxplat.framework.core.application.util.StringUtils;

/**
 * 检验/科室设备台账聚合根（{@code adm_device}，DM-ADM-015）。
 */
@Getter
@Setter
@Entity
@Table(name = "adm_device")
public class LabDevice extends BaseAuditedEntity {

    /** 业务编码（LAD 前缀，租户内唯一）。 */
    @Column(name = "bus_no", nullable = false)
    private String busNo;

    /** 设备名称。 */
    @Column(name = "name", nullable = false)
    private String name;

    /** 拼音检索码。 */
    @Column(name = "py_str")
    private String pyStr;

    /** 五笔检索码。 */
    @Column(name = "wb_str")
    private String wbStr;

    /** 归属科室 {@code adm_organization.id}。 */
    @Column(name = "org_id")
    private Long orgId;

    /** 安装位置 {@code adm_location.id}。 */
    @Column(name = "location_id")
    private Long locationId;

    /** 发布状态 code（{@link PublicationStatus}）。 */
    @Column(name = "status_enum", nullable = false)
    private String statusEnum;

    /** 设备分类 code（术语 adm_device_category_code）。 */
    @Column(name = "category_code")
    private String categoryCode;

    /** 型号。 */
    @Column(name = "model_number")
    private String modelNumber;

    /** 序列号。 */
    @Column(name = "serial_number")
    private String serialNumber;

    /** 生产厂家 {@code cus_supplier.id}。 */
    @Column(name = "manufacturer_id")
    private Long manufacturerId;

    /** 生产日期。 */
    @Column(name = "manufacture_date")
    private LocalDate manufactureDate;

    /** 失效/报废日期。 */
    @Column(name = "expiration_date")
    private LocalDate expirationDate;

    /** 备注说明。 */
    @Column(name = "description")
    private String description;

    /** 校验业务编码非空。 */
    public void assertBusNoNotBlank() {
        if (StringUtils.isEmpty(this.busNo)) {
            throw new BusinessException(ErrorCodeConstants.LAB_DEVICE_INVALID_ARGUMENT, "设备业务编码不能为空");
        }
    }

    /** 校验设备名称非空。 */
    public void assertNameNotBlank() {
        if (StringUtils.isEmpty(this.name)) {
            throw new BusinessException(ErrorCodeConstants.LAB_DEVICE_INVALID_ARGUMENT, "设备名称不能为空");
        }
    }

    /** 按名称回填检索码。 */
    public void refreshSearchCodeFromName() {
        if (StringUtils.isEmpty(this.name)) {
            return;
        }
        if (StringUtils.isEmpty(this.pyStr)) {
            this.pyStr = SearchCodeUtil.toPyStr(this.name);
        }
        if (StringUtils.isEmpty(this.wbStr)) {
            this.wbStr = SearchCodeUtil.toWbStr(this.name);
        }
    }

    /** 新建时初始化为启用态。 */
    public void initAsActive() {
        this.statusEnum = PublicationStatus.ACTIVE.getCode();
    }

    /** 断言当前为启用态。 */
    public void assertActive() {
        if (!PublicationStatus.ACTIVE.getCode().equals(this.statusEnum)) {
            throw new BusinessException(ErrorCodeConstants.LAB_DEVICE_INVALID_STATUS, "仅启用状态的设备可停用");
        }
    }

    /** 断言当前为停用态。 */
    public void assertInactive() {
        if (!PublicationStatus.INACTIVE.getCode().equals(this.statusEnum)) {
            throw new BusinessException(ErrorCodeConstants.LAB_DEVICE_INVALID_STATUS, "仅停用状态的设备可启用");
        }
    }

    /** 切换为启用态。 */
    public void activate() {
        this.statusEnum = PublicationStatus.ACTIVE.getCode();
    }

    /** 切换为停用态。 */
    public void deactivate() {
        this.statusEnum = PublicationStatus.INACTIVE.getCode();
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
