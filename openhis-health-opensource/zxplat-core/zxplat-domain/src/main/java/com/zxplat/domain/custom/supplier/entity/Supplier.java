/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.domain.custom.supplier.entity;

import com.zxplat.domain.share.constants.ErrorCodeConstants;
import com.zxplat.domain.share.enums.SupplierStatus;
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
 * 供应商聚合根实体。
 */
@Getter
@Setter
@Entity
@Table(name = "cus_supplier")
public class Supplier extends BaseAuditedEntity {

    @Column(name = "bus_no", nullable = false)
    private String busNo;

    @Column(name = "name", nullable = false)
    private String name;

    @Column(name = "type_enum", nullable = false)
    private String typeEnum;

    @Column(name = "credit_code", nullable = false)
    private String creditCode;

    @Column(name = "legal_representative")
    private String legalRepresentative;

    @Column(name = "address")
    private String address;

    @Column(name = "py_str", nullable = false)
    private String pyStr;

    @Column(name = "wb_str", nullable = false)
    private String wbStr;

    @Column(name = "phone")
    private String phone;

    @Column(name = "email")
    private String email;

    @Column(name = "bank_name")
    private String bankName;

    @Column(name = "bank_account")
    private String bankAccount;

    @Column(name = "org_id", nullable = false)
    private Long orgId;

    @Column(name = "display_order")
    private Integer displayOrder;

    @Column(name = "status_enum", nullable = false)
    private String statusEnum;

    @Column(name = "note")
    private String note;

    /**
     * 初始化草稿状态（注册流程中的中间态；业务要求注册完成后须再 {@link #activate()} 为启用）。
     */
    public void initDraftStatus() {
        this.statusEnum = SupplierStatus.DRAFT.getCode();
    }

    /**
     * 断言主档必填字段。
     */
    public void assertRequiredFields() {
        if (StringUtils.isEmpty(this.name)) {
            throw new BusinessException(ErrorCodeConstants.SUPPLIER_INVALID_ARGUMENT, "供应商名称不能为空");
        }
        if (this.typeEnum == null) {
            throw new BusinessException(ErrorCodeConstants.SUPPLIER_INVALID_ARGUMENT, "供应商类型不能为空");
        }

    }

    /**
     * 判断当前是否从停用状态激活（区分"停用->启用"与"草稿->启用"两个语义路径，用于选择对应发布事件）。
     *
     * @return true 表示从停用状态激活，false 表示从其他状态（如草稿）激活
     */
    public boolean isFromSuspended() {
        return SupplierStatus.SUSPENDED.getCode().equals(this.statusEnum);
    }

    /**
     * 断言允许启用。
     */
    public void assertActivatable() {
        if (!SupplierStatus.DRAFT.getCode().equals(this.statusEnum)
            && !SupplierStatus.SUSPENDED.getCode().equals(this.statusEnum)) {
            throw new BusinessException(ErrorCodeConstants.SUPPLIER_INVALID_STATUS, "当前状态不允许启用");
        }
    }

    /**
     * 断言允许停用。
     */
    public void assertSuspendable() {
        if (!SupplierStatus.ACTIVE.getCode().equals(this.statusEnum)) {
            throw new BusinessException(ErrorCodeConstants.SUPPLIER_INVALID_STATUS, "仅启用状态允许停用");
        }
    }

    /**
     * 断言允许维护信息。
     */
    public void assertInfoMaintainable() {
        if (SupplierStatus.DELETED.getCode().equals(this.statusEnum)) {
            throw new BusinessException(ErrorCodeConstants.SUPPLIER_INVALID_STATUS, "已废弃供应商不可维护信息");
        }
    }

    /**
     * 迁移到启用状态。
     */
    public void activate() {
        this.statusEnum = SupplierStatus.ACTIVE.getCode();
    }

    /**
     * 迁移到停用状态。
     */
    public void suspend() {
        this.statusEnum = SupplierStatus.SUSPENDED.getCode();
    }

    /**
     * 标记废弃。
     *
     * @param operatorId 操作人标识
     */
    public void retire(Long operatorId) {
        this.statusEnum = SupplierStatus.DELETED.getCode();
        this.setIsDeleted(Boolean.TRUE);
        this.setDeletedBy(operatorId);
        this.setDeletedAt(LocalDateTime.now());
    }
}
