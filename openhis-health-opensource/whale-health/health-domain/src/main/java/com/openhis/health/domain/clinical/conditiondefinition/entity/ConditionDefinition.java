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

package com.openhis.health.domain.clinical.conditiondefinition.entity;

import com.openhis.health.domain.share.constants.ErrorCodeConstants;
import com.openhis.health.domain.share.enums.PublicationStatus;
import com.openhis.health.domain.share.utils.SearchCodeUtil;
import com.openhis.whale.core.application.exception.BusinessException;
import com.openhis.whale.core.domain.BaseAuditedEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import org.apache.commons.lang3.StringUtils;

/**
 * 诊断定义聚合根。
 */
@Getter
@Setter
@Entity
@Table(name = "cln_condition_definition")
public class ConditionDefinition extends BaseAuditedEntity {
    @Column(name = "source_enum", nullable = false)
    private String sourceEnum;
    @Column(name = "condition_code", nullable = false)
    private String conditionCode;
    @Column(name = "name", nullable = false)
    private String name;
    @Column(name = "py_str")
    private String pyStr;
    @Column(name = "wb_str")
    private String wbStr;
    @Column(name = "type_code")
    private String typeCode;
    @Column(name = "description")
    private String description;
    @Column(name = "yb_flag")
    private Boolean ybFlag;
    @Column(name = "disease_code")
    private String diseaseCode;
    @Column(name = "yb_no")
    private String ybNo;
    @Column(name = "yb_match_flag")
    private Boolean ybMatchFlag;
    @Column(name = "status_enum", nullable = false)
    private String statusEnum;

    /**
     * 校验核心参数。
     */
    public void assertCoreFields() {
        if (StringUtils.isBlank(this.conditionCode) || StringUtils.isBlank(this.name) || StringUtils.isBlank(this.sourceEnum)) {
            throw new BusinessException(ErrorCodeConstants.CONDITION_DEFINITION_INVALID_ARGUMENT, "诊断定义关键字段不能为空");
        }
    }

    /**
     * 重建检索码（仅当助记码为空时从名称生成，避免覆盖手工录入）。
     * <p>拼音助记为名称的拼音首字母（大写）；五笔助记为名称的五笔首字母（大写），与 {@link SearchCodeUtil} 口径一致。</p>
     */
    public void rebuildSearchKey() {
        if (StringUtils.isBlank(this.name)) {
            return;
        }
        if (StringUtils.isBlank(this.pyStr)) {
            this.pyStr = SearchCodeUtil.toPyStr(this.name);
        }
        if (StringUtils.isBlank(this.wbStr)) {
            this.wbStr = SearchCodeUtil.toWbStr(this.name);
        }
    }

    /**
     * 置为启用状态。
     */
    public void enable() {
        this.statusEnum = PublicationStatus.ACTIVE.getCode();
    }

    /**
     * 置为停用状态。
     */
    public void disable() {
        this.statusEnum = PublicationStatus.INACTIVE.getCode();
    }
}

