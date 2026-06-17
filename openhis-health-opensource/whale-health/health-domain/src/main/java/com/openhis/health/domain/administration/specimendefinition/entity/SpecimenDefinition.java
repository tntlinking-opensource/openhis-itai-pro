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

package com.openhis.health.domain.administration.specimendefinition.entity;

import com.openhis.health.domain.share.constants.ErrorCodeConstants;
import com.openhis.health.domain.share.enums.PublicationStatus;
import com.openhis.health.domain.share.enums.SpecimenType;
import com.openhis.health.domain.share.utils.EnumUtil;
import com.openhis.health.domain.share.utils.SearchCodeUtil;
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
 * 样本定义聚合根（{@code adm_specimen_definition}，DM-ADM-014）。
 *
 * <p>维护检验标本类型目录主数据；下游 {@code ActivityDefinition.specimen_code} 引用 {@link #busNo}。
 * 不变式：业务编码唯一、名称非空、种类须为合法 {@link SpecimenType}；启停仅改 {@link #statusEnum}。</p>
 */
@Getter
@Setter
@Entity
@Table(name = "adm_specimen_definition")
public class SpecimenDefinition extends BaseAuditedEntity {

    /** 院内唯一业务编码；下游诊疗项目 {@code specimen_code} 引用本字段。 */
    @Column(name = "bus_no", nullable = false)
    private String busNo;

    /** 面向临床与维护页的显示名称。 */
    @Column(name = "name", nullable = false)
    private String name;

    /** 拼音检索码。 */
    @Column(name = "py_str")
    private String pyStr;

    /** 五笔检索码。 */
    @Column(name = "wb_str")
    private String wbStr;

    /** 标本种类（存 {@link SpecimenType#getCode()}）。 */
    @Column(name = "type_enum", nullable = false)
    private String typeEnum;

    /** 发布状态（存 {@link PublicationStatus#getCode()}）。 */
    @Column(name = "status_enum", nullable = false)
    private String statusEnum;

    /**
     * 校验业务编码非空。
     */
    public void assertBusNoNotBlank() {
        if (StringUtils.isBlank(this.busNo)) {
            throw new BusinessException(ErrorCodeConstants.SPECIMEN_DEFINITION_INVALID_ARGUMENT, "样本定义业务编码不能为空");
        }
    }

    /**
     * 校验样本名称非空。
     */
    public void assertNameNotBlank() {
        if (StringUtils.isBlank(this.name)) {
            throw new BusinessException(ErrorCodeConstants.SPECIMEN_DEFINITION_INVALID_ARGUMENT, "样本定义名称不能为空");
        }
    }

    /**
     * 校验标本种类为合法 {@link SpecimenType} 编码。
     */
    public void assertTypeEnumValid() {
        if (EnumUtil.fromCode(SpecimenType.class, this.typeEnum) == null) {
            throw new BusinessException(ErrorCodeConstants.SPECIMEN_DEFINITION_INVALID_ARGUMENT, "标本种类不合法");
        }
    }

    /**
     * 按名称回填检索码（命令未显式传入时生成拼音/五笔码）。
     */
    public void refreshSearchCodeFromName() {
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
     * 新建时初始化为启用态。
     */
    public void initAsActive() {
        this.statusEnum = PublicationStatus.ACTIVE.getCode();
    }

    /**
     * 断言当前为启用态（停用前置条件）。
     */
    public void assertActive() {
        if (!PublicationStatus.ACTIVE.getCode().equals(this.statusEnum)) {
            throw new BusinessException(ErrorCodeConstants.SPECIMEN_DEFINITION_INVALID_STATUS, "仅启用状态的样本定义可停用");
        }
    }

    /**
     * 断言当前为停用态（启用前置条件）。
     */
    public void assertInactive() {
        if (!PublicationStatus.INACTIVE.getCode().equals(this.statusEnum)) {
            throw new BusinessException(ErrorCodeConstants.SPECIMEN_DEFINITION_INVALID_STATUS, "仅停用状态的样本定义可启用");
        }
    }

    /**
     * 切换为启用态。
     */
    public void activate() {
        this.statusEnum = PublicationStatus.ACTIVE.getCode();
    }

    /**
     * 切换为停用态。
     */
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
