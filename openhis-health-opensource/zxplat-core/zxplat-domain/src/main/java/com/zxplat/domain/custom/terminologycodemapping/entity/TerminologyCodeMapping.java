/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.domain.custom.terminologycodemapping.entity;

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
 * 术语编码映射聚合根。
 */
@Getter
@Setter
@Entity
@Table(name = "cus_terminology_code_mapping")
public class TerminologyCodeMapping extends BaseAuditedEntity {

    @Column(name = "source_item_table", nullable = false)
    private String sourceItemTable;

    @Column(name = "source_item_no", nullable = false)
    private String sourceItemNo;

    @Column(name = "target_item_no", nullable = false)
    private String targetItemNo;

    @Column(name = "target_name")
    private String targetName;

    @Column(name = "active_flag", nullable = false)
    private Boolean activeFlag;

    /**
     * 初始化启用状态。
     */
    public void initActive() {
        this.activeFlag = Boolean.TRUE;
    }

    /**
     * 断言必填字段。
     */
    public void assertRequiredFields() {
        if (StringUtils.isEmpty(this.sourceItemTable)) {
            throw new BusinessException(ErrorCodeConstants.TERMINOLOGY_MAPPING_INVALID_ARGUMENT, "源对象类型不能为空");
        }
        if (StringUtils.isEmpty(this.sourceItemNo)) {
            throw new BusinessException(ErrorCodeConstants.TERMINOLOGY_MAPPING_INVALID_ARGUMENT, "源编码不能为空");
        }
        if (StringUtils.isEmpty(this.targetItemNo)) {
            throw new BusinessException(ErrorCodeConstants.TERMINOLOGY_MAPPING_INVALID_ARGUMENT, "目标编码不能为空");
        }
    }

    /**
     * 断言允许停用。
     */
    public void assertDeactivatable() {
        if (!Boolean.TRUE.equals(this.activeFlag)) {
            throw new BusinessException(ErrorCodeConstants.TERMINOLOGY_MAPPING_INVALID_STATUS, "已停用映射不可重复停用");
        }
    }

    /**
     * 断言允许恢复。
     */
    public void assertReactivatable() {
        if (Boolean.TRUE.equals(this.activeFlag)) {
            throw new BusinessException(ErrorCodeConstants.TERMINOLOGY_MAPPING_INVALID_STATUS, "启用映射不可重复恢复");
        }
    }

    /**
     * 标记逻辑删除。
     *
     * @param operatorId 操作人标识
     */
    public void markDeleted(Long operatorId) {
        this.setIsDeleted(Boolean.TRUE);
        this.setDeletedBy(operatorId);
        this.setDeletedAt(LocalDateTime.now());
    }
}
