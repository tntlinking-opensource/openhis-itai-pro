/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.domain.administration.healthcareservice.entity;

import com.zxplat.domain.share.constants.ErrorCodeConstants;
import com.zxplat.domain.share.enums.HealthcareServiceStatus;
import com.zxplat.framework.core.application.exception.BusinessException;
import com.zxplat.framework.core.domain.BaseAuditedEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * 医疗服务聚合根，对应表 {@code adm_healthcare_service}。
 *
 * <p>承载科室/医疗服务的生命周期管理：草稿 → 已发布 → 已停用；支持废弃（逻辑删除）。
 * 对外暴露状态断言方法（{@code assertXxx}）和状态迁移方法（{@code transitToXxx}），
 * 禁止在领域服务中绕过这些方法直接修改 {@code statusEnum}。</p>
 *
 * <p>DOC-CONFLICT：DM-ADM-007 §2.3 要求 {@code status_enum} 对齐
 * {@link com.zxplat.domain.share.enums.PublicationStatus}（DRAFT=1/ACTIVE=2/RETIRED=3/DELETED=4）；
 * 但当前持久化码值为 0/1/2/3（见 {@link HealthcareServiceStatus}），与 PublicationStatus 码值体系不一致。
 * 完整对齐须先完成 {@code adm_healthcare_service} 存量数据迁移，未完成前禁止直接替换枚举引用。</p>
 */
@Getter
@Setter
@Entity
@Table(name = "adm_healthcare_service")
public class HealthcareService extends BaseAuditedEntity {

    /** 医疗服务名称，如"内科门诊"。 */
    @Column(name = "name", nullable = false)
    private String name;

    /** 服务类型编码（type_code），区分普通门诊、专家门诊等服务类型。 */
    @Column(name = "type_code", nullable = false)
    private String typeCode;

    /** 服务类别编码（category_code），区分门诊、住院等服务大类。 */
    @Column(name = "category_code")
    private String categoryCode;

    /** 提供该服务的机构主键（offered_org_id），关联 {@code adm_organization}。 */
    @Column(name = "offered_org_id")
    private Long offeredOrgId;

    /** 专科码（specialty_code），如"CARDIOLOGY""SURGERY"，可为空。 */
    @Column(name = "specialty_code")
    private String specialtyCode;

    /** 服务所在地点主键，关联 {@code adm_location}，可为空。 */
    @Column(name = "location_id")
    private Long locationId;

    /**
     * 服务状态码（status_enum，String code）。
     *
     * <p>取值见 {@link HealthcareServiceStatus}：DRAFT=0 / ACTIVE=1 / RETIRED=2 / DELETED=3。</p>
     */
    @Column(name = "status_enum", nullable = false)
    private String statusEnum;

    /** 服务是否处于激活可用状态，与 statusEnum=ACTIVE 联动维护。 */
    @Column(name = "active_flag", nullable = false)
    private Boolean activeFlag;

    /** 预约是否必填标志，true 表示患者必须预约后才可就诊。 */
    @Column(name = "appointment_required_flag")
    private Boolean appointmentRequiredFlag;

    /**
     * 医保编号列（yb_no）。
     *
     * <p><b>产品废弃</b>：新业务禁止读写依赖；挂号费医保码取自绑定诊查费 ActivityDefinition。
     * 存量列可保留至迁移清空。</p>
     */
    @Column(name = "yb_no")
    private String ybNo;

    /**
     * 诊查费 ActivityDefinition 主键 CSV（activity_ids）。
     *
     * <p>逗号分隔；可多绑；零元号可空（BR-HCS-010～012）。顺序即展示/落费顺序。</p>
     */
    @Column(name = "activity_ids", length = 512)
    private String activityIds;

    /** 业务编号（bus_no），系统内唯一标识医疗服务的人读编码，可为空。 */
    @Column(name = "bus_no")
    private String busNo;

    /** 备注说明，对外展示的补充描述，可为空。 */
    @Column(name = "comment")
    private String comment;

    /** 扩展详情（JSON 字符串），存储结构化附加信息，可为空。 */
    @Column(name = "extra_details")
    private String extraDetails;

    /** 联系方式（JSON 字符串），医疗服务对外联系信息，可为空。 */
    @Column(name = "contact")
    private String contact;

    /**
     * 初始化为草稿状态，对应 {@link HealthcareServiceStatus#DRAFT}。
     *
     * <p>新建医疗服务时调用，设置初始状态和激活标志。</p>
     */
    public void initDraft() {
        this.statusEnum = HealthcareServiceStatus.DRAFT.getCode();
        this.activeFlag = Boolean.FALSE;
    }

    /**
     * 断言当前状态允许发布（仅草稿可发布）。
     *
     * @throws BusinessException 若当前状态非草稿，抛出 HEALTHCARE_SERVICE_INVALID_STATUS
     */
    public void assertPublishAllowed() {
        if (!HealthcareServiceStatus.DRAFT.getCode().equals(this.statusEnum)) {
            throw new BusinessException(ErrorCodeConstants.HEALTHCARE_SERVICE_INVALID_STATUS, "仅草稿可发布");
        }
    }

    /**
     * 断言当前状态允许修改（草稿或已发布态可修改）。
     *
     * @throws BusinessException 若当前状态不是草稿或已发布，抛出 HEALTHCARE_SERVICE_INVALID_STATUS
     */
    public void assertModifiable() {
        if (!HealthcareServiceStatus.DRAFT.getCode().equals(this.statusEnum)
            && !HealthcareServiceStatus.ACTIVE.getCode().equals(this.statusEnum)) {
            throw new BusinessException(ErrorCodeConstants.HEALTHCARE_SERVICE_INVALID_STATUS, "仅草稿/发布态可修改");
        }
    }

    /**
     * 断言当前状态允许停用（仅已发布态可停用）。
     *
     * @throws BusinessException 若当前状态非已发布，抛出 HEALTHCARE_SERVICE_INVALID_STATUS
     */
    public void assertRetirable() {
        if (!HealthcareServiceStatus.ACTIVE.getCode().equals(this.statusEnum)) {
            throw new BusinessException(ErrorCodeConstants.HEALTHCARE_SERVICE_INVALID_STATUS, "仅发布态可停用");
        }
    }

    /**
     * 断言当前状态允许重新发布（仅已停用态可重新发布）。
     *
     * @throws BusinessException 若当前状态非已停用，抛出 HEALTHCARE_SERVICE_INVALID_STATUS
     */
    public void assertReactivatable() {
        if (!HealthcareServiceStatus.RETIRED.getCode().equals(this.statusEnum)) {
            throw new BusinessException(ErrorCodeConstants.HEALTHCARE_SERVICE_INVALID_STATUS, "仅停用态可重新发布");
        }
    }

    /**
     * 断言当前状态允许废弃（草稿或已停用态可废弃）。
     *
     * @throws BusinessException 若当前状态不是草稿或已停用，抛出 HEALTHCARE_SERVICE_INVALID_STATUS
     */
    public void assertAbolishable() {
        if (!HealthcareServiceStatus.DRAFT.getCode().equals(this.statusEnum)
            && !HealthcareServiceStatus.RETIRED.getCode().equals(this.statusEnum)) {
            throw new BusinessException(ErrorCodeConstants.HEALTHCARE_SERVICE_INVALID_STATUS, "仅草稿/停用可废弃");
        }
    }

    /**
     * 断言 ACTIVE 态禁止修改服务类别与服务类型（BR-HCS-008）。
     *
     * @param nextCategoryCode 拟写入的类别；与当前相同或均为空视为未改
     * @param nextTypeCode     拟写入的类型；与当前相同视为未改
     * @throws BusinessException 已发布且类别/类型发生变更时
     */
    public void assertActiveImmutableCategoryAndType(String nextCategoryCode, String nextTypeCode) {
        if (!HealthcareServiceStatus.ACTIVE.getCode().equals(this.statusEnum)) {
            return;
        }
        // 已发布号源改类别/类型会破坏已引用语义，拒绝。
        if (!java.util.Objects.equals(this.normalizeCode(this.categoryCode), this.normalizeCode(nextCategoryCode))
            || !java.util.Objects.equals(this.normalizeCode(this.typeCode), this.normalizeCode(nextTypeCode))) {
            throw new BusinessException(ErrorCodeConstants.HEALTHCARE_SERVICE_RULE_CONFLICT,
                "已启用号源不允许修改服务类别或服务类型");
        }
    }

    /**
     * 断言诊查费绑定串格式合法（BR-HCS-010～012 的领域侧格式门禁）。
     *
     * <p>空串允许（零元号）；非空时须为逗号分隔的正整数主键。AD 是否诊查费/ACTIVE 由应用层强校验（跨聚合）。</p>
     *
     * @param activityIdsCsv 拟写入的绑定串
     * @throws BusinessException 格式非法时
     */
    public void assertActivityIdsBindingAllowed(String activityIdsCsv) {
        if (activityIdsCsv == null || activityIdsCsv.isBlank()) {
            return;
        }
        String[] tokens = activityIdsCsv.split(",");
        for (String raw : tokens) {
            // 空段（连续逗号）视为非法绑定串。
            if (raw == null || raw.isBlank()) {
                throw new BusinessException(ErrorCodeConstants.HEALTHCARE_SERVICE_INVALID_ARGUMENT,
                    "诊查费绑定串格式非法");
            }
            String token = raw.trim();
            try {
                long id = Long.parseLong(token);
                // 主键须为正数。
                if (id <= 0L) {
                    throw new BusinessException(ErrorCodeConstants.HEALTHCARE_SERVICE_INVALID_ARGUMENT,
                        "诊查费绑定主键须为正整数");
                }
            } catch (NumberFormatException ex) {
                throw new BusinessException(ErrorCodeConstants.HEALTHCARE_SERVICE_INVALID_ARGUMENT,
                    "诊查费绑定串含非数字主键");
            }
        }
    }

    /**
     * 归一字典/编码比较值：空白视为 {@code null}。
     *
     * @param code 原始编码
     * @return trim 后编码或 null
     */
    private String normalizeCode(String code) {
        if (code == null || code.isBlank()) {
            return null;
        }
        return code.trim();
    }

    /**
     * 迁移到已发布（ACTIVE）状态，并将 activeFlag 置为 true。
     */
    public void transitToActive() {
        this.statusEnum = HealthcareServiceStatus.ACTIVE.getCode();
        this.activeFlag = Boolean.TRUE;
    }

    /**
     * 迁移到已停用（RETIRED）状态，并将 activeFlag 置为 false。
     */
    public void transitToRetired() {
        this.statusEnum = HealthcareServiceStatus.RETIRED.getCode();
        this.activeFlag = Boolean.FALSE;
    }

    /**
     * 迁移到已废弃（DELETED）状态，并执行逻辑删除标记。
     *
     * @param operatorId 执行废弃操作的操作人主键
     */
    public void transitToDeleted(Long operatorId) {
        this.statusEnum = HealthcareServiceStatus.DELETED.getCode();
        this.activeFlag = Boolean.FALSE;
        this.setIsDeleted(Boolean.TRUE);
        this.setDeletedBy(operatorId);
        this.setDeletedAt(LocalDateTime.now());
    }
}
