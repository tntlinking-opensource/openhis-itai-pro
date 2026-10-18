/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.domain.custom.conditiontemplate.entity;

import com.zxplat.domain.share.constants.ErrorCodeConstants;
import com.zxplat.framework.core.application.exception.BusinessException;
import com.zxplat.framework.core.domain.BaseAuditedEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * 诊断常用模板聚合根。
 */
@Getter
@Setter
@Entity
@Table(name = "cus_condition_template")
public class ConditionTemplate extends BaseAuditedEntity {

    /**
     * 归属对象标识。
     */
    @Column(name = "object_id", nullable = false)
    private Long objectId;

    /**
     * 诊断定义标识。
     */
    @Column(name = "definition_id", nullable = false)
    private Long definitionId;

    /**
     * 绑定类型枚举。
     */
    @Column(name = "binding_enum", nullable = false)
    private String bindingEnum;

    /**
     * 排序权重。
     */
    @Column(name = "sort_weight")
    private Integer sortWeight;

    /**
     * 备注。
     */
    @Column(name = "remark")
    private String remark;

    /**
     * 软删当前模板行（逻辑删除）。
     *
     * @param operatorId 操作人标识
     */
    public void markDeleted(Long operatorId) {
        this.setIsDeleted(Boolean.TRUE);
        this.setDeletedBy(operatorId);
        this.setDeletedAt(LocalDateTime.now());
    }

    /**
     * 断言行级必填字段。
     */
    public void assertRequiredFields() {
        if (this.objectId == null) {
            throw new BusinessException(ErrorCodeConstants.CONDITION_TEMPLATE_INVALID_ARGUMENT, "归属对象标识不能为空");
        }
        if (this.definitionId == null) {
            throw new BusinessException(ErrorCodeConstants.CONDITION_TEMPLATE_INVALID_ARGUMENT, "诊断定义标识不能为空");
        }
        if (this.bindingEnum == null) {
            throw new BusinessException(ErrorCodeConstants.CONDITION_TEMPLATE_INVALID_ARGUMENT, "绑定类型不能为空");
        }
        if (this.sortWeight != null && this.sortWeight < 0) {
            throw new BusinessException(ErrorCodeConstants.CONDITION_TEMPLATE_INVALID_ARGUMENT, "排序权重必须为非负整数");
        }
    }
}
