/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.domain.workflow.activitydefinition.entity;

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
 * 诊疗项目子项实体。
 *
 * <p>对应表：wkf_activity_definition_children。</p>
 *
 * <p>注意：子项的归属以 activityId 为边界；任何更新都必须校验同一父项下的归属一致性。</p>
 */
@Getter
@Setter
@Entity
@Table(name = "wkf_activity_definition_children")
public class ActivityDefinitionChild extends BaseAuditedEntity {

    /** 父项标识（外键：wkf_activity_definition.id）。 */
    @Column(name = "activity_id", nullable = false)
    private Long activityId;

    /** 子项诊疗项目标识（被引用项目主键）。 */
    @Column(name = "children_id", nullable = false)
    private Long childrenId;

    /** 子项数量。 */
    @Column(name = "children_num")
    private Integer childrenNum;

    /** 子项名称快照。 */
    @Column(name = "children_name")
    private String childrenName;

    /**
     * 校验归属一致性。
     *
     * @param expectedActivityId 期望的父项标识
     * @throws BusinessException 归属不一致
     */
    public void assertBelongsTo(Long expectedActivityId) {
        if (expectedActivityId == null) {
            throw new BusinessException(ErrorCodeConstants.ACTIVITY_DEFINITION_INVALID_ARGUMENT, "校验子项归属时 activityId 不能为空");
        }
        if (!expectedActivityId.equals(this.activityId)) {
            throw new BusinessException(ErrorCodeConstants.ACTIVITY_DEFINITION_INVALID_ARGUMENT, "子项归属不一致，禁止越权更新");
        }
    }

    /**
     * 校验子项引用键合法。
     *
     * @throws BusinessException 参数非法
     */
    public void assertChildrenIdProvided() {
        if (this.childrenId == null) {
            throw new BusinessException(ErrorCodeConstants.ACTIVITY_DEFINITION_INVALID_ARGUMENT, "子项诊疗项目标识不能为空");
        }
    }

    /**
     * 校验子项名称快照允许为空，但当传入时不允许全空白。
     *
     * @throws BusinessException 参数非法
     */
    public void assertChildrenNameValidWhenProvided() {
        if (this.childrenName != null && StringUtils.isEmpty(this.childrenName)) {
            throw new BusinessException(ErrorCodeConstants.ACTIVITY_DEFINITION_INVALID_ARGUMENT, "子项名称快照不允许为空白字符串");
        }
    }

    /**
     * 逻辑删除本行。
     *
     * @param operatorId 操作人
     */
    public void markDeleted(Long operatorId) {
        this.setIsDeleted(Boolean.TRUE);
        this.setDeletedBy(operatorId);
        this.setDeletedAt(LocalDateTime.now());
    }
}
