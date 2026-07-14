/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.domain.administration.practitioner.entity;

import com.openhis.health.domain.share.constants.ErrorCodeConstants;
import com.openhis.health.domain.share.enums.PublicationStatus;
import com.openhis.whale.core.application.exception.BusinessException;
import com.openhis.whale.core.domain.BaseAuditedEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import com.openhis.whale.core.application.util.StringUtils;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 医务人员聚合根实体。
 *
 * <p>与 identity {@code User} <strong>主键独立</strong>，仅通过 {@link #userId} 轻关联（不设库表外键），
 * 与 {@code Organization} ↔ {@code OrganizationUnit} 的 code 桥接同一原则。</p>
 * <p>主状态 {@link #statusEnum} 与 {@link PublicationStatus} 编码对齐。</p>
 */
@Getter
@Setter
@Entity
@Table(name = "adm_practitioner")
public class Practitioner extends BaseAuditedEntity {

    /** 人员姓名。 */
    @Column(name = "name", nullable = false)
    private String name;

    /** 其他名称 JSON（曾用名、英文名等）。 */
    @Column(name = "name_json")
    private String nameJson;

    /** 行政性别编码（{@link com.openhis.health.domain.share.enums.AdministrativeGender}）。 */
    @Column(name = "gender_enum", nullable = false)
    private String genderEnum;

    /** 出生日期。 */
    @Column(name = "birth_date")
    private LocalDate birthDate;

    /** 死亡时间。 */
    @Column(name = "deceased_date")
    private LocalDateTime deceasedDate;

    /** 联系电话。 */
    @Column(name = "phone")
    private String phone;

    /** 人员状态（{@link PublicationStatus}：ACTIVE / INACTIVE / DISCARDED）。 */
    @Column(name = "status_enum", nullable = false)
    private String statusEnum;

    /** 是否启用；{@code status_enum == ACTIVE} 时为 {@code true}。 */
    @Column(name = "active_flag", nullable = false)
    private Boolean activeFlag;

    /** 院内工号/业务编号。 */
    @Column(name = "bus_no")
    private String busNo;

    /** 关联平台用户主键（与 {@link #getId()} 独立，不设库表外键）。 */
    @Column(name = "user_id", nullable = false)
    private Long userId;

    /** 默认登录科室/当前工作科室（Organization 主键）。 */
    @Column(name = "org_id")
    private Long orgId;

    /** 地址-省。 */
    @Column(name = "address_province")
    private String addressProvince;

    /** 地址-市。 */
    @Column(name = "address_city")
    private String addressCity;

    /** 地址-区/县。 */
    @Column(name = "address_district")
    private String addressDistrict;

    /** 地址-街道/详细地址。 */
    @Column(name = "address_street")
    private String addressStreet;

    /** 地址扩展 JSON。 */
    @Column(name = "address_json")
    private String addressJson;

    /** 医保人员编码。 */
    @Column(name = "yb_no")
    private String ybNo;

    /** 职称编码。 */
    @Column(name = "dr_profttl_code")
    private Integer drProfttlCode;

    /** 医师/药师执业资格证编号。 */
    @Column(name = "phar_prac_cert_no")
    private String pharPracCertNo;

    /** 执业证件类型编码。 */
    @Column(name = "prsc_dr_cert_code")
    private String prscDrCertCode;

    /** 开票点编码。 */
    @Column(name = "kpd_code")
    private String kpdCode;

    /** POS 机终端编号。 */
    @Column(name = "pos_no")
    private String posNo;

    /** 医生签名图片 Base64（可含 {@code data:image/*;base64,} 前缀）；库列为 PostgreSQL TEXT。 */
    @Column(name = "signature", columnDefinition = "TEXT")
    private String signature;

    /** 姓名拼音检索码。 */
    @Column(name = "py_str")
    private String pyStr;

    /** 姓名五笔检索码。 */
    @Column(name = "wb_str")
    private String wbStr;

    /**
     * 新建默认启用（{@link PublicationStatus#ACTIVE}）。
     */
    public void initAsActive() {
        this.statusEnum = PublicationStatus.ACTIVE.getCode();
        this.activeFlag = Boolean.TRUE;
    }

    /**
      * 断言业务前置条件满足。
     */
    public void assertCreateAllowed() {
        if (this.userId == null) {
            throw new BusinessException(ErrorCodeConstants.PRACTITIONER_INVALID_ARGUMENT, "用户标识不能为空");
        }
        if (StringUtils.isEmpty(this.name)) {
            throw new BusinessException(ErrorCodeConstants.PRACTITIONER_INVALID_ARGUMENT, "人员姓名不能为空");
        }
        if (StringUtils.isEmpty(this.genderEnum)) {
            throw new BusinessException(ErrorCodeConstants.PRACTITIONER_INVALID_ARGUMENT, "人员性别不能为空");
        }
    }

    /**
     * 绑定平台用户（独立主键，仅写入 {@code user_id}，不设库表外键）。
     *
     * @param userId 平台用户主键
     */
    public void bindUser(Long userId) {
        this.userId = userId;
    }

    /**
      * 断言业务前置条件满足。
     */
    public void assertEditable() {
        if (!PublicationStatus.ACTIVE.getCode().equals(this.statusEnum)) {
            throw new BusinessException(ErrorCodeConstants.PRACTITIONER_INVALID_STATUS, "仅启用人员可编辑");
        }
    }

    /**
      * 断言业务前置条件满足。
     */
    public void assertDeactivatable() {
        if (!PublicationStatus.ACTIVE.getCode().equals(this.statusEnum)) {
            throw new BusinessException(ErrorCodeConstants.PRACTITIONER_INVALID_STATUS, "仅启用人员可停用");
        }
    }

    /**
      * 断言业务前置条件满足。
     */
    public void assertActivatable() {
        if (!PublicationStatus.INACTIVE.getCode().equals(this.statusEnum)) {
            throw new BusinessException(ErrorCodeConstants.PRACTITIONER_INVALID_STATUS, "仅停用人员可启用");
        }
    }

    /**
      * 断言业务前置条件满足。
     */
    public void assertRetirable() {
        if (isDiscardedStatus(this.statusEnum) || Boolean.TRUE.equals(this.getIsDeleted())) {
            throw new BusinessException(ErrorCodeConstants.PRACTITIONER_INVALID_STATUS, "人员已废弃");
        }
        if (!PublicationStatus.ACTIVE.getCode().equals(this.statusEnum)
            && !PublicationStatus.INACTIVE.getCode().equals(this.statusEnum)) {
            throw new BusinessException(ErrorCodeConstants.PRACTITIONER_INVALID_STATUS, "仅启用/停用人员可废弃");
        }
    }

    /**
      * 断言业务前置条件满足。
     */
    public void assertSwitchOrgAllowed() {
        if (!PublicationStatus.ACTIVE.getCode().equals(this.statusEnum)) {
            throw new BusinessException(ErrorCodeConstants.PRACTITIONER_INVALID_STATUS, "仅启用人员可切换科室");
        }
    }

    /**
      * 处理transitToInactive逻辑。
     */
    public void transitToInactive() {
        this.statusEnum = PublicationStatus.INACTIVE.getCode();
        this.activeFlag = Boolean.FALSE;
    }

    /**
      * 处理transitToActive逻辑。
     */
    public void transitToActive() {
        this.statusEnum = PublicationStatus.ACTIVE.getCode();
        this.activeFlag = Boolean.TRUE;
    }

    /**
     * 退休/注销医务人员：状态迁至已废弃并软删，记录操作人。
     *
     * @param operatorId 操作人主键
     */
    public void transitToDeleted(Long operatorId) {
        this.statusEnum = PublicationStatus.DISCARDED.getCode();
        this.activeFlag = Boolean.FALSE;
        this.setIsDeleted(Boolean.TRUE);
        this.setDeletedBy(operatorId);
        this.setDeletedAt(LocalDateTime.now());
    }

    /**
     * 医务人员是否已废弃/删除态（DISCARDED 或历史 DELETED 码）。
     *
     * @param statusEnum 持久化状态码
     * @return true 表示不可再用
     */
    private static boolean isDiscardedStatus(String statusEnum) {
        return PublicationStatus.DISCARDED.getCode().equals(statusEnum)
            || "DELETED".equals(statusEnum);
    }
}
