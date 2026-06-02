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

package com.openhis.health.domain.administration.organization.entity;

import com.openhis.health.domain.share.constants.ErrorCodeConstants;
import com.openhis.health.domain.share.converter.OrganizationClassificationConverter;
import com.openhis.health.domain.share.enums.OrganizationClassification;
import com.openhis.health.domain.share.enums.OrganizationStatus;
import com.openhis.health.domain.share.enums.OrganizationType;
import com.openhis.whale.core.application.exception.BusinessException;
import com.openhis.whale.core.domain.BaseAuditedEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import org.apache.commons.lang3.StringUtils;

import java.time.LocalDateTime;

/**
 * 机构聚合根实体。
 */
@Getter
@Setter
@Entity
@Table(name = "adm_organization")
public class Organization extends BaseAuditedEntity {

    /** 名称。 */
    @Column(name = "name", nullable = false)
    private String name;

    /** bus 编号。 */
    @Column(name = "bus_no", nullable = false)
    private String busNo;

    /** parent 标识。 */
    @Column(name = "parent_id")
    private Long parentId;

    /** type 枚举值。 */
    @Column(name = "type_enum", nullable = false)
    private String typeEnum;

    /** class 枚举值。 */
    @Column(name = "class_enum")
    @Convert(converter = OrganizationClassificationConverter.class)
    private OrganizationClassification classEnum;

    /** status 枚举值。 */
    @Column(name = "status_enum", nullable = false)
    private String statusEnum;

    /** active 标志位。 */
    @Column(name = "active_flag", nullable = false)
    private Boolean activeFlag;

    /** displayOrder。 */
    @Column(name = "display_order")
    private Integer displayOrder;

    /** defDoctor 标识。 */
    @Column(name = "def_doctor_id")
    private Long defDoctorId;

    /** pyStr。 */
    @Column(name = "py_str")
    private String pyStr;

    /** wbStr。 */
    @Column(name = "wb_str")
    private String wbStr;

    /** yb 编号。 */
    @Column(name = "yb_no")
    private String ybNo;

    /** yb 名称。 */
    @Column(name = "yb_name")
    private String ybName;

    /** medins 标识。 */
    @Column(name = "medins_id")
    private String medinsId;

    /** medinsAdmdvs。 */
    @Column(name = "medins_admdvs")
    private String medinsAdmdvs;

    /** medinsType。 */
    @Column(name = "medins_type")
    private String medinsType;

    /** medinsLv。 */
    @Column(name = "medins_lv")
    private String medinsLv;

    /**
     * 科别。
     */
    /** caty。 */
    @Column(name = "caty")
    private String caty;

    /**
     * 初始化草稿态。
     */
    public void initAsDraft() {
        this.statusEnum = OrganizationStatus.DRAFT.getCode();
        this.activeFlag = Boolean.FALSE;
    }

    /**
     * 校验创建前置条件。
     */
    public void assertCreateAllowed() {
        if (StringUtils.isBlank(this.name)) {
            throw new BusinessException(ErrorCodeConstants.ORGANIZATION_INVALID_ARGUMENT, "机构名称不能为空");
        }
        if (this.typeEnum == null) {
            throw new BusinessException(ErrorCodeConstants.ORGANIZATION_INVALID_ARGUMENT, "机构类型不能为空");
        }
        if (OrganizationType.HOSPITAL.getCode().equals(this.typeEnum)
            && StringUtils.isBlank(this.ybNo)) {
            throw new BusinessException(ErrorCodeConstants.ORGANIZATION_RULE_CONFLICT, "医保机构等级不能为空");
        }
        if (OrganizationType.DEPARTMENT.getCode().equals(this.typeEnum)
            && StringUtils.isNotBlank(this.medinsId)
            && StringUtils.isBlank(this.medinsLv)) {
            throw new BusinessException(ErrorCodeConstants.ORGANIZATION_RULE_CONFLICT, "医保机构等级不能为空");
        }
    }

    /**
     * 校验发布前置条件。
     */
    public void assertPublishAllowed() {
        if (!OrganizationStatus.DRAFT.getCode().equals(this.statusEnum)) {
            throw new BusinessException(ErrorCodeConstants.ORGANIZATION_INVALID_STATUS, "仅草稿机构可发布");
        }
        this.assertCreateAllowed();
    }

    /**
     * 校验可修改。
     */
    public void assertModifiable() {
        if (!OrganizationStatus.DRAFT.getCode().equals(this.statusEnum)
            && !OrganizationStatus.ACTIVE.getCode().equals(this.statusEnum)) {
            throw new BusinessException(ErrorCodeConstants.ORGANIZATION_INVALID_STATUS, "仅草稿/启用机构可修改");
        }
    }

    /**
     * 校验可调整上级。
     */
    public void assertRelocateAllowed() {
        if (!OrganizationStatus.ACTIVE.getCode().equals(this.statusEnum)) {
            throw new BusinessException(ErrorCodeConstants.ORGANIZATION_INVALID_STATUS, "仅启用机构可调整上级");
        }
    }

    /**
     * 校验可停用。
     */
    public void assertRetireAllowed() {
        if (!OrganizationStatus.ACTIVE.getCode().equals(this.statusEnum)) {
            throw new BusinessException(ErrorCodeConstants.ORGANIZATION_INVALID_STATUS, "仅启用机构可停用");
        }
    }

    /**
     * 校验可重新启用。
     */
    public void assertReactivateAllowed() {
        if (!OrganizationStatus.RETIRED.getCode().equals(this.statusEnum)) {
            throw new BusinessException(ErrorCodeConstants.ORGANIZATION_INVALID_STATUS, "仅停用机构可重新启用");
        }
    }

    /**
     * 校验可废弃。
     */
    public void assertAbolishAllowed() {
        if (!OrganizationStatus.DRAFT.getCode().equals(this.statusEnum)
            && !OrganizationStatus.RETIRED.getCode().equals(this.statusEnum)) {
            throw new BusinessException(ErrorCodeConstants.ORGANIZATION_INVALID_STATUS, "仅草稿/停用机构可废弃");
        }
    }

    /**
     * 断言当前机构处于启用状态，用于需要上级机构启用才能执行的操作（如子机构启用前置校验）。
     *
     * @throws BusinessException 机构未启用
     */
    public void assertActive() {
        if (!OrganizationStatus.ACTIVE.getCode().equals(this.statusEnum)) {
            throw new BusinessException(ErrorCodeConstants.ORGANIZATION_RULE_CONFLICT, "上级机构未启用，当前机构不可启用");
        }
    }

    /**
     * 迁移到启用态。
     */
    public void transitToActive() {
        this.statusEnum = OrganizationStatus.ACTIVE.getCode();
        this.activeFlag = Boolean.TRUE;
    }

    /**
     * 迁移到停用态。
     */
    public void transitToRetired() {
        this.statusEnum = OrganizationStatus.RETIRED.getCode();
        this.activeFlag = Boolean.FALSE;
    }

    /**
     * 迁移到废弃态并逻辑删除。
     *
     * @param operatorId 操作人
     */
    public void transitToDeleted(Long operatorId) {
        this.statusEnum = OrganizationStatus.DELETED.getCode();
        this.activeFlag = Boolean.FALSE;
        this.setIsDeleted(Boolean.TRUE);
        this.setDeletedBy(operatorId);
        this.setDeletedAt(LocalDateTime.now());
    }
}

