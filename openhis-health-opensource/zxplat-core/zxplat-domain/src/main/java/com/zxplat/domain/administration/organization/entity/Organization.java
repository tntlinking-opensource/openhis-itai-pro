/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.domain.administration.organization.entity;

import com.zxplat.domain.share.constants.ErrorCodeConstants;
import com.zxplat.domain.share.converter.OrganizationClassificationConverter;
import com.zxplat.domain.share.enums.OrganizationClassification;
import com.zxplat.domain.share.enums.OrganizationType;
import com.zxplat.domain.share.enums.PublicationStatus;
import com.zxplat.framework.core.application.exception.BusinessException;
import com.zxplat.framework.core.domain.BaseAuditedEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import com.zxplat.framework.core.application.util.StringUtils;

import java.time.LocalDateTime;

/**
 * 机构聚合根实体。
 *
 * <p>主状态 {@link #statusEnum} 与 {@link PublicationStatus} 编码对齐。</p>
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

    /** status 枚举值（{@link PublicationStatus#getCode()}）。 */
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

    /** yb 编号（科室医保编码；4401/4101A 入出院科别出站真源）。 */
    @Column(name = "yb_no")
    private String ybNo;

    /** yb 名称（科室医保名称）。 */
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
     * 初始化草稿态（兼容旧发布流程）。
     */
    public void initAsDraft() {
        this.statusEnum = PublicationStatus.DRAFT.getCode();
        this.activeFlag = Boolean.FALSE;
    }

    /**
     * 新建默认启用（{@link PublicationStatus#ACTIVE}）。
     */
    public void initAsActive() {
        this.statusEnum = PublicationStatus.ACTIVE.getCode();
        this.activeFlag = Boolean.TRUE;
    }

    /**
     * 校验创建前置条件。
     */
    public void assertCreateAllowed() {
        if (StringUtils.isEmpty(this.name)) {
            throw new BusinessException(ErrorCodeConstants.ORGANIZATION_INVALID_ARGUMENT, "机构名称不能为空");
        }
        if (this.typeEnum == null) {
            throw new BusinessException(ErrorCodeConstants.ORGANIZATION_INVALID_ARGUMENT, "机构类型不能为空");
        }
        if (OrganizationType.DEPARTMENT.getCode().equals(this.typeEnum)
            && StringUtils.isNotEmpty(this.medinsId)
            && StringUtils.isEmpty(this.medinsLv)) {
            throw new BusinessException(ErrorCodeConstants.ORGANIZATION_RULE_CONFLICT, "医保机构等级不能为空");
        }
    }

    /**
     * 校验发布前置条件。
     */
    public void assertPublishAllowed() {
        if (!PublicationStatus.DRAFT.getCode().equals(this.statusEnum)) {
            throw new BusinessException(ErrorCodeConstants.ORGANIZATION_INVALID_STATUS, "仅草稿机构可发布");
        }
        this.assertCreateAllowed();
    }

    /**
     * 校验可修改。
     */
    public void assertModifiable() {
        if (!PublicationStatus.DRAFT.getCode().equals(this.statusEnum)
            && !PublicationStatus.ACTIVE.getCode().equals(this.statusEnum)) {
            throw new BusinessException(ErrorCodeConstants.ORGANIZATION_INVALID_STATUS, "仅草稿/启用机构可修改");
        }
    }

    /**
     * 校验可调整上级。
     */
    public void assertRelocateAllowed() {
        if (!PublicationStatus.ACTIVE.getCode().equals(this.statusEnum)) {
            throw new BusinessException(ErrorCodeConstants.ORGANIZATION_INVALID_STATUS, "仅启用机构可调整上级");
        }
    }

    /**
     * 校验可停用。
     */
    public void assertDeactivatable() {
        if (!PublicationStatus.ACTIVE.getCode().equals(this.statusEnum)) {
            throw new BusinessException(ErrorCodeConstants.ORGANIZATION_INVALID_STATUS, "仅启用机构可停用");
        }
    }

    /**
     * 校验可重新启用。
     */
    public void assertActivatable() {
        if (!PublicationStatus.INACTIVE.getCode().equals(this.statusEnum)) {
            throw new BusinessException(ErrorCodeConstants.ORGANIZATION_INVALID_STATUS, "仅停用机构可重新启用");
        }
    }

    /**
     * 校验可废弃。
     */
    public void assertAbolishAllowed() {
        if (isDiscardedStatus(this.statusEnum) || Boolean.TRUE.equals(this.getIsDeleted())) {
            throw new BusinessException(ErrorCodeConstants.ORGANIZATION_INVALID_STATUS, "机构已废弃");
        }
    }

    /**
     * 断言当前机构处于启用状态，用于需要上级机构启用才能执行的操作（如子机构启用前置校验）。
     *
     * @throws BusinessException 机构未启用
     */
    public void assertActive() {
        if (!PublicationStatus.ACTIVE.getCode().equals(this.statusEnum)) {
            throw new BusinessException(ErrorCodeConstants.ORGANIZATION_RULE_CONFLICT, "上级机构未启用，当前机构不可启用");
        }
    }

    /**
     * 迁移到启用态。
     */
    public void transitToActive() {
        this.statusEnum = PublicationStatus.ACTIVE.getCode();
        this.activeFlag = Boolean.TRUE;
    }

    /**
     * 迁移到停用态（ACTIVE -> INACTIVE）。
     */
    public void transitToInactive() {
        this.statusEnum = PublicationStatus.INACTIVE.getCode();
        this.activeFlag = Boolean.FALSE;
    }

    /**
     * 迁移到废弃态并逻辑删除。
     *
     * @param operatorId 操作人
     */
    public void transitToDeleted(Long operatorId) {
        this.statusEnum = PublicationStatus.DISCARDED.getCode();
        this.activeFlag = Boolean.FALSE;
        this.setIsDeleted(Boolean.TRUE);
        this.setDeletedBy(operatorId);
        this.setDeletedAt(LocalDateTime.now());
    }

    /**
     * 组织是否已废弃/删除态（DISCARDED 或历史 DELETED 码）。
     *
     * @param statusEnum 持久化状态码
     * @return true 表示不可再用
     */
    private static boolean isDiscardedStatus(String statusEnum) {
        return PublicationStatus.DISCARDED.getCode().equals(statusEnum)
            || "DELETED".equals(statusEnum);
    }
}
