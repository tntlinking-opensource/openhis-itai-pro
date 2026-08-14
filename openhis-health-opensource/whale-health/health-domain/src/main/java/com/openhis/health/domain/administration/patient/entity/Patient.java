/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.domain.administration.patient.entity;

import com.openhis.health.domain.share.constants.GeneralSharedConstants;
import com.openhis.health.domain.administration.patient.valueobject.Address;
import com.openhis.health.domain.administration.patient.valueobject.BloodType;
import com.openhis.health.domain.administration.patient.valueobject.ContactPerson;
import com.openhis.health.domain.share.constants.ErrorCodeConstants;
import com.openhis.health.domain.share.enums.PatientStatus;
import com.openhis.whale.core.application.exception.BusinessException;
import com.openhis.whale.core.domain.BaseAuditedEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import com.openhis.whale.core.application.util.StringUtils;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 患者聚合根实体。
 */
@Getter
@Setter
@Entity
@Table(name = "adm_patient")
public class Patient extends BaseAuditedEntity {

    /**
     * 院内编码。
     */
    @Column(name = "bus_no", nullable = false)
    private String busNo;

    /**
     * 患者姓名。
     */
    @Column(name = "name", nullable = false)
    private String name;

    /**
     * 姓名扩展 JSON。
     */
    @Column(name = "name_json")
    private String nameJson;

    /**
     * 性别编码。
     */
    @Column(name = "gender_enum", nullable = false)
    private String genderEnum;

    /**
     * 出生日期。
     */
    @Column(name = "birth_date")
    private LocalDate birthDate;

    /**
     * 患者状态编码。
     */
    @Column(name = "status_enum", nullable = false)
    private String statusEnum;

    /**
     * 启用标记。
     */
    @Column(name = "active_flag", nullable = false)
    private Boolean activeFlag;

    /**
     * 临时建档标记。
     */
    @Column(name = "temp_flag", nullable = false)
    private Boolean tempFlag;

    /**
     * 身份证号。
     */
    @Column(name = "id_card")
    private String idCard;

    /**
     * 婚姻状态编码。
     */
    @Column(name = "marital_status_enum")
    private String maritalStatusEnum;

    /**
     * 职业编码。
     */
    @Column(name = "prfs_enum")
    private String prfsEnum;

    /**
     * 联系电话。
     */
    @Column(name = "phone")
    private String phone;

    /**
     * 主要地址。
     */
    @Embedded
    private Address address;

    /**
     * 地址扩展 JSON。
     */
    @Column(name = "address_extend_json")
    private String addressExtendJson;

    /**
     * 民族编码。
     */
    @Column(name = "nationality_code")
    private String nationalityCode;

    /**
     * 拼音码。
     */
    @Column(name = "py_str")
    private String pyStr;

    /**
     * 五笔码。
     */
    @Column(name = "wb_str")
    private String wbStr;

    /**
     * 血型。
     */
    @Embedded
    private BloodType bloodType;

    /**
     * 工作单位。
     */
    @Column(name = "work_company")
    private String workCompany;

    /**
     * 籍贯。
     */
    @Column(name = "native_place")
    private String nativePlace;

    /**
     * 国家地区编码。
     */
    @Column(name = "country_code")
    private String countryCode;

    /**
     * 主要联系人。
     */
    @Embedded
    private ContactPerson linkName;

    /**
     * 联系人扩展 JSON。
     */
    @Column(name = "link_jsons")
    private String linkJsons;

    /**
     * 死亡时间。
     */
    @Column(name = "deceased_date")
    private LocalDateTime deceasedDate;

    /**
     * 死亡原因。
     */
    @Column(name = "deceased_reason")
    private String deceasedReason;

    /**
     * 建档机构标识。
     */
    @Column(name = "organization_id", nullable = false)
    private Long organizationId;

    /**
     * 合并目标患者标识。
     */
    @Column(name = "merged_target_patient_id")
    private Long mergedTargetPatientId;

    /**
     * 合并时间。
     */
    @Column(name = "merged_at")
    private LocalDateTime mergedAt;

    /**
     * 校验建档最小必填信息。
     */
    public void assertRegisterRequiredFields() {
        if (StringUtils.isEmpty(this.name)) {
            throw new BusinessException(ErrorCodeConstants.PATIENT_INVALID_ARGUMENT, "患者姓名不能为空");
        }
        if (this.genderEnum == null) {
            throw new BusinessException(ErrorCodeConstants.PATIENT_INVALID_ARGUMENT, "患者性别不能为空");
        }
    }

    /**
     * 按临时标记初始化状态。
     */
    public void initStatusByTempFlag() {
        if (Boolean.TRUE.equals(this.tempFlag)) {
            this.statusEnum = PatientStatus.DRAFT.getCode();
            this.activeFlag = Boolean.FALSE;
        } else {
            this.statusEnum = PatientStatus.ACTIVE.getCode();
            this.activeFlag = Boolean.TRUE;
        }
    }

    /**
     * 校验档案是否允许修改。
     */
    public void assertProfileModifiable() {
        if (!PatientStatus.DRAFT.getCode().equals(this.statusEnum)
            && !PatientStatus.ACTIVE.getCode().equals(this.statusEnum)) {
            throw new BusinessException(ErrorCodeConstants.PATIENT_INVALID_STATUS, "仅临时或活跃患者允许修改档案");
        }
    }

    /**
     * 校验是否允许确认身份。
     */
    public void assertCanConfirmIdentity() {
        if (!PatientStatus.DRAFT.getCode().equals(this.statusEnum)) {
            throw new BusinessException(ErrorCodeConstants.PATIENT_INVALID_STATUS, "仅临时建档患者可确认身份");
        }
        this.assertRegisterRequiredFields();
    }

    /**
     * 执行临时到活跃的状态迁移。
     */
    public void confirmIdentity() {
        this.assertCanConfirmIdentity();
        this.statusEnum = PatientStatus.ACTIVE.getCode();
        this.activeFlag = Boolean.TRUE;
        this.tempFlag = Boolean.FALSE;
    }

    /**
     * 校验手机号格式。
     *
     * @param inputPhone 待校验手机号
     */
    public void assertPhoneFormatValid(String inputPhone) {
        if (StringUtils.isEmpty(inputPhone)) {
            throw new BusinessException(ErrorCodeConstants.PATIENT_INVALID_ARGUMENT, "手机号不能为空");
        }
        if (!GeneralSharedConstants.PHONE_PATTERN.matcher(inputPhone).matches()) {
            throw new BusinessException(ErrorCodeConstants.PATIENT_INVALID_ARGUMENT, "手机号格式不正确");
        }
    }

    /**
     * 校验是否允许更新手机号。
     */
    public void assertPhoneUpdatable() {
        if (!PatientStatus.ACTIVE.getCode().equals(this.statusEnum)) {
            throw new BusinessException(ErrorCodeConstants.PATIENT_INVALID_STATUS, "仅活跃患者允许更新手机号");
        }
    }

    /**
     * 校验是否允许更新地址全文（挂号读卡回写等窄写路径）。
     */
    public void assertAddressUpdatable() {
        if (!PatientStatus.ACTIVE.getCode().equals(this.statusEnum)) {
            throw new BusinessException(ErrorCodeConstants.PATIENT_INVALID_STATUS, "仅活跃患者允许更新地址");
        }
    }

    /**
     * 校验是否允许同步患者证件。
     */
    public void assertIdentifierSyncAllowed() {
        if (!PatientStatus.ACTIVE.getCode().equals(this.statusEnum)) {
            throw new BusinessException(ErrorCodeConstants.PATIENT_INVALID_STATUS, "仅活跃患者允许同步证件");
        }
    }

    /**
     * 校验是否允许停用。
     */
    public void assertCanDeactivate() {
        // DOC-CONFLICT: DM §6.1.6 前置条件写明仅 active 可停用，但 DM §5.2 触发表允许 draft 停用。
        // 采纳 §5.2 与状态机口径，允许 active/draft 进入停用流程。
        if (!PatientStatus.ACTIVE.getCode().equals(this.statusEnum)
            && !PatientStatus.DRAFT.getCode().equals(this.statusEnum)) {
            throw new BusinessException(ErrorCodeConstants.PATIENT_INVALID_STATUS, "仅活跃或临时患者允许停用");
        }
    }

    /**
     * 执行停用状态迁移。
     */
    public void deactivate() {
        this.assertCanDeactivate();
        this.statusEnum = PatientStatus.INACTIVE.getCode();
        this.activeFlag = Boolean.FALSE;
    }

    /**
     * 校验是否允许重新启用。
     */
    public void assertCanReactivate() {
        if (!PatientStatus.INACTIVE.getCode().equals(this.statusEnum)) {
            throw new BusinessException(ErrorCodeConstants.PATIENT_INVALID_STATUS, "仅停用患者允许重新启用");
        }
    }

    /**
     * 执行重新启用状态迁移。
     */
    public void reactivate() {
        this.assertCanReactivate();
        this.statusEnum = PatientStatus.ACTIVE.getCode();
        this.activeFlag = Boolean.TRUE;
    }

    /**
     * 校验合并目标合法性。
     *
     * @param targetPatientId 目标患者标识
     * @param targetStatusEnum 目标状态编码
     */
    public void assertMergeTargetValid(Long targetPatientId, String targetStatusEnum) {
        if (targetPatientId == null) {
            throw new BusinessException(ErrorCodeConstants.PATIENT_INVALID_ARGUMENT, "目标患者标识不能为空");
        }
        if (this.getId().equals(targetPatientId)) {
            throw new BusinessException(ErrorCodeConstants.PATIENT_INVALID_ARGUMENT, "源患者与目标患者不能相同");
        }
        // DOC-CONFLICT: DM §5.2 触发表包含 draft 合并，但 DM §6.1.8 与 BR-PAT-010 要求源/目标均为 active。
        // 采纳 §6.1.8 与 BR-PAT-010，限制仅 active 源患者可合并。
        if (!PatientStatus.ACTIVE.getCode().equals(this.statusEnum)) {
            throw new BusinessException(ErrorCodeConstants.PATIENT_INVALID_STATUS, "仅活跃源患者可执行合并");
        }
        if (!PatientStatus.ACTIVE.getCode().equals(targetStatusEnum)) {
            throw new BusinessException(ErrorCodeConstants.PATIENT_INVALID_STATUS, "目标患者必须为活跃状态");
        }
    }

    /**
     * 标记已合并并记录合并目标。
     *
     * @param targetPatientId 目标患者标识
     * @param mergeTime 合并时间
     */
    public void markMergedTo(Long targetPatientId, LocalDateTime mergeTime) {
        this.statusEnum = PatientStatus.MERGED.getCode();
        this.activeFlag = Boolean.FALSE;
        this.mergedTargetPatientId = targetPatientId;
        this.mergedAt = mergeTime;
    }

    /**
     * 校验死亡时间合法性。
     *
     * @param inputDeceasedDate 死亡时间
     */
    public void assertDeceasedDateValid(LocalDateTime inputDeceasedDate) {
        if (inputDeceasedDate == null) {
            throw new BusinessException(ErrorCodeConstants.PATIENT_INVALID_ARGUMENT, "死亡时间不能为空");
        }
        if (inputDeceasedDate.isAfter(LocalDateTime.now())) {
            throw new BusinessException(ErrorCodeConstants.PATIENT_INVALID_ARGUMENT, "死亡时间不得晚于当前时间");
        }
    }

    /**
     * 标记死亡事实。
     *
     * @param inputDeceasedDate 死亡时间
     * @param inputDeceasedReason 死亡原因
     */
    public void markDeceased(LocalDateTime inputDeceasedDate, String inputDeceasedReason) {
        this.assertDeceasedDateValid(inputDeceasedDate);
        this.deceasedDate = inputDeceasedDate;
        this.deceasedReason = inputDeceasedReason;
    }

    /**
     * 校验是否允许标记死亡。
     */
    public void assertCanMarkDeceased() {
        // DOC-CONFLICT: DM §6.1.9 前置条件写明仅 active 可标记死亡，但 DM §5.2 触发表允许 draft/inactive。
        // 采纳 §5.2 与状态机口径，允许 active/draft/inactive 标记死亡。
        if (!PatientStatus.ACTIVE.getCode().equals(this.statusEnum)
            && !PatientStatus.DRAFT.getCode().equals(this.statusEnum)
            && !PatientStatus.INACTIVE.getCode().equals(this.statusEnum)) {
            throw new BusinessException(ErrorCodeConstants.PATIENT_INVALID_STATUS, "当前状态不允许标记死亡");
        }
    }

    /**
     * 校验是否允许废弃临时建档。
     */
    public void assertCanDiscardDraft() {
        if (!PatientStatus.DRAFT.getCode().equals(this.statusEnum)) {
            throw new BusinessException(ErrorCodeConstants.PATIENT_INVALID_STATUS, "仅临时建档患者可废弃");
        }
    }

    /**
     * 执行废弃临时建档迁移。
     */
    public void discardDraft() {
        this.assertCanDiscardDraft();
        this.statusEnum = PatientStatus.CANCELLED.getCode();
        this.activeFlag = Boolean.FALSE;
    }

    /**
     * 校验是否允许逻辑删除。
     */
    public void assertCanBeDeleted() {
        if (PatientStatus.MERGED.getCode().equals(this.statusEnum)
            || PatientStatus.CANCELLED.getCode().equals(this.statusEnum)) {
            throw new BusinessException(ErrorCodeConstants.PATIENT_INVALID_STATUS, "终止状态患者不允许删除");
        }
    }

    /**
     * 标记逻辑删除。
     *
     * @param operatorId 操作人标识
     */
    public void markDeleted(Long operatorId) {
        this.setIsDeleted(Boolean.TRUE);
        this.setDeletedAt(LocalDateTime.now());
        this.setDeletedBy(operatorId);
    }
}
