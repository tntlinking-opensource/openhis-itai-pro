/*
 * Copyright © 2026 OpenHIS Contributors
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 3 of the License, or (at your option) any later version.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library.  If not, see <https://www.gnu.org/licenses/>.
 */

package com.openhis.health.domain.custom.terminologycodemapping.entity;

import com.openhis.health.domain.share.constants.ErrorCodeConstants;
import com.openhis.whale.core.application.exception.BusinessException;
import com.openhis.whale.core.domain.BaseAuditedEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import org.apache.commons.lang3.StringUtils;

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
        if (StringUtils.isBlank(this.sourceItemTable)) {
            throw new BusinessException(ErrorCodeConstants.TERMINOLOGY_MAPPING_INVALID_ARGUMENT, "源对象类型不能为空");
        }
        if (StringUtils.isBlank(this.sourceItemNo)) {
            throw new BusinessException(ErrorCodeConstants.TERMINOLOGY_MAPPING_INVALID_ARGUMENT, "源编码不能为空");
        }
        if (StringUtils.isBlank(this.targetItemNo)) {
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
