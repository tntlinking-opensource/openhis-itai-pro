/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.domain.workflow.activitydefinition.entity;

import com.zxplat.domain.share.constants.ErrorCodeConstants;
import com.zxplat.domain.share.enums.ActivityPermittedGender;
import com.zxplat.domain.share.enums.PublicationStatus;
import com.zxplat.domain.share.utils.SearchCodeUtil;
import com.zxplat.framework.core.application.exception.BusinessException;
import com.zxplat.framework.core.domain.BaseAuditedEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import com.zxplat.framework.core.application.util.StringUtils;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 诊疗项目定义聚合根。
 *
 * <p>对应表：wkf_activity_definition。</p>
 *
 * <p>聚合边界：本实体只承载诊疗项目目录主数据及其发布状态、检索码与医保承载字段；不承担收费域的计费事实价。</p>
 */
@Getter
@Setter
@Entity
@Table(name = "wkf_activity_definition")
public class ActivityDefinition extends BaseAuditedEntity {

    /** 目录类别编码。 */
    @Column(name = "category_code", nullable = false)
    private String categoryCode;

    /** 业务编码（全局唯一）。 */
    @Column(name = "bus_no", nullable = false)
    private String busNo;

    /** 项目名称。 */
    @Column(name = "name", nullable = false)
    private String name;

    /** 拼音检索码。 */
    @Column(name = "py_str")
    private String pyStr;

    /** 五笔检索码。 */
    @Column(name = "wb_str")
    private String wbStr;

    /** 销售/计价单位编码。 */
    @Column(name = "package_unit_code")
    private String packageUnitCode;

    /** 诊疗项目业务版本（非乐观锁 version）。 */
    @Column(name = "activity_version")
    private String activityVersion;

    /** 商品名称（可与项目名称分列维护）。 */
    @Column(name = "merchandise_name")
    private String merchandiseName;

    /** 商品名拼音检索码。 */
    @Column(name = "merchandise_py_str")
    private String merchandisePyStr;

    /** 商品名五笔检索码。 */
    @Column(name = "merchandise_wb_str")
    private String merchandiseWbStr;

    /** 默认门诊/就诊单位编码。 */
    @Column(name = "def_encounter_unit_code")
    private String defEncounterUnitCode;

    /** 默认住院单位编码。 */
    @Column(name = "def_inhospital_unit_code")
    private String defInhospitalUnitCode;

    /**
     * 诊疗业务类型标签（列名 {@code type_enum}；术语 {@code activity_type_code}）。
     * 仅目录维护/申请单 chips 用；医嘱通道与 LIS/PACS 认 {@code category_code}，现场可增码。
     */
    @Column(name = "type_enum", nullable = false)
    private String typeEnum;

    /** 发布状态枚举编码。 */
    @Column(name = "status_enum", nullable = false)
    private String statusEnum;

    /** 说明文本。 */
    @Column(name = "description_text")
    private String descriptionText;

    /** 身体部位编码。 */
    @Column(name = "body_site_code")
    private String bodySiteCode;

    /** 标本编码。 */
    @Column(name = "specimen_code")
    private String specimenCode;

    /** 所属机构标识。 */
    @Column(name = "org_id")
    private Long orgId;

    /** 位置标识。 */
    @Column(name = "location_id")
    private Long locationId;

    /** 计费规则标识。 */
    @Column(name = "rule_id")
    private Integer ruleId;

    /** 划价标记。 */
    @Column(name = "pricing_flag")
    private Boolean pricingFlag;

    /** 医保标记。 */
    @Column(name = "yb_flag")
    private Boolean ybFlag;

    /** 医保编码。 */
    @Column(name = "yb_no")
    private String ybNo;

    /** 医保对码标记。 */
    @Column(name = "yb_match_flag")
    private Boolean ybMatchFlag;

    /** 医保收费等级。 */
    @Column(name = "chrgitm_lv")
    private Integer chrgitmLv;

    /** 病案结算类型编码（术语 {@code mr_settlement_type_code}）。 */
    @Column(name = "mr_settlement_type_code")
    private String mrSettlementTypeCode;

    /** 子项 JSON 快照。 */
    @Column(name = "children_json")
    private String childrenJson;

    /** 子项标记（是否已绑定套餐子项；需与子项子表有效行一致，BR-ACTDEF-006）。 */
    @Column(name = "children_flag")
    private Boolean childrenFlag;

    /**
     * 适用性别枚举编码（{@link com.zxplat.domain.share.enums.ActivityPermittedGender}）。
     *
     * <p>体检目录用于男/女适用过滤；缺省 {@code BOTH}。</p>
     */
    @Column(name = "permitted_gender_enum", nullable = false)
    private String permittedGenderEnum;

    /**
     * 目录展示顺序（数值越小越靠前；可空表示未排序）。
     */
    @Column(name = "sort_order")
    private Integer sortOrder;

    /**
     * 校验创建/维护时的必填字段。
     *
     * @throws BusinessException 参数非法
     */
    public void assertRequiredFieldsProvided() {
        if (StringUtils.isEmpty(this.categoryCode)) {
            throw new BusinessException(ErrorCodeConstants.ACTIVITY_DEFINITION_INVALID_ARGUMENT, "诊疗项目目录类别编码不能为空");
        }
        if (StringUtils.isEmpty(this.name)) {
            throw new BusinessException(ErrorCodeConstants.ACTIVITY_DEFINITION_INVALID_ARGUMENT, "诊疗项目名称不能为空");
        }
        // 业务类型标签必填（术语 activity_type_code）；空串亦拒绝
        if (StringUtils.isEmpty(this.typeEnum)) {
            throw new BusinessException(ErrorCodeConstants.ACTIVITY_DEFINITION_INVALID_ARGUMENT, "诊疗项目业务类型不能为空");
        }
        // 适用性别缺省不限，避免存量/导入漏写导致校验失败
        if (StringUtils.isEmpty(this.permittedGenderEnum)) {
            this.permittedGenderEnum = ActivityPermittedGender.BOTH.getCode();
        }
    }

    /**
     * 校验业务编码已生成。
     *
     * @throws BusinessException 参数非法
     */
    public void assertBusNoGenerated() {
        if (StringUtils.isEmpty(this.busNo)) {
            throw new BusinessException(ErrorCodeConstants.ACTIVITY_DEFINITION_INVALID_ARGUMENT, "诊疗项目业务编码不能为空");
        }
    }

    /**
     * 根据名称重新生成拼音首字母码（pyStr）与五笔首字母码（wbStr）。
     *
     * <p>委托 {@link SearchCodeUtil} 实现，底层依赖 pinyin4j 库。</p>
     */
    public void rebuildSearchCodes() {
        this.pyStr = SearchCodeUtil.toPyStr(this.name);
        this.wbStr = SearchCodeUtil.toWbStr(this.name);
    }

    /**
     * 按商品名称重算商品拼音码与五笔码。
     */
    public void rebuildMerchandiseSearchCodes() {
        if (StringUtils.isEmpty(this.merchandiseName)) {
            this.merchandisePyStr = null;
            this.merchandiseWbStr = null;
            return;
        }
        this.merchandisePyStr = SearchCodeUtil.toPyStr(this.merchandiseName);
        this.merchandiseWbStr = SearchCodeUtil.toWbStr(this.merchandiseName);
    }

    /**
     * 初始化发布状态（草稿/已发布等由上层传入的 statusEnum 决定）。
     *
     * @param initStatusEnum 初始状态编码
     * @throws BusinessException 参数非法
     */
    public void applyInitStatus(String initStatusEnum) {
        if (initStatusEnum == null) {
            throw new BusinessException(ErrorCodeConstants.ACTIVITY_DEFINITION_INVALID_ARGUMENT, "诊疗项目初始发布状态不能为空");
        }
        this.statusEnum = initStatusEnum;
    }

    /**
     * 执行发布迁移（草稿 → 已发布）。
     *
     * <p>对应 DM-WKF-001 §2.3「草稿 ──发布──> 已发布」；维护端保存后首次「启用」亦走本迁移。</p>
     *
     * @throws BusinessException 当前状态不是草稿
     */
    public void publish() {
        if (!PublicationStatus.DRAFT.getCode().equals(this.statusEnum)) {
            throw new BusinessException(ErrorCodeConstants.ACTIVITY_DEFINITION_INVALID_ARGUMENT, "仅草稿状态的诊疗项目允许发布");
        }
        this.statusEnum = PublicationStatus.ACTIVE.getCode();
    }

    /**
     * 执行停用迁移。
     *
     * @throws BusinessException 状态不允许
     */
    public void retire() {
        if (!PublicationStatus.ACTIVE.getCode().equals(this.statusEnum)) {
            throw new BusinessException(ErrorCodeConstants.ACTIVITY_DEFINITION_INVALID_ARGUMENT, "仅已发布状态的诊疗项目允许停用");
        }
        this.statusEnum = PublicationStatus.INACTIVE.getCode();
    }

    /**
     * 执行启用迁移（已停用 → 已发布）。
     *
     * @throws BusinessException 状态不允许
     */
    public void activate() {
        if (!PublicationStatus.INACTIVE.getCode().equals(this.statusEnum)) {
            throw new BusinessException(ErrorCodeConstants.ACTIVITY_DEFINITION_INVALID_ARGUMENT, "仅已停用状态的诊疗项目允许启用");
        }
        this.statusEnum = PublicationStatus.ACTIVE.getCode();
    }

    /**
     * 对齐子项标记与子项事实行。
     *
     * @param children 子项事实行集合（只包含未删除行）
     * @return 本次被设置为 true 的情况，用于上层决定变更字段摘要
     */
    public boolean syncChildrenFlag(List<ActivityDefinitionChild> children) {
        boolean hasChildren = children != null && !children.isEmpty();
        Boolean origin = this.childrenFlag;
        if (hasChildren) {
            this.childrenFlag = Boolean.TRUE;
        } else {
            this.childrenFlag = Boolean.FALSE;
        }
        if (origin == null) {
            return hasChildren;
        }
        return !origin.equals(this.childrenFlag);
    }

    /**
     * 构造用于事件“变更字段摘要”的字段列表。
     *
     * @param changedFields 已判定发生变更的字段名
     * @return 防御性拷贝后的字段列表
     */
    public List<String> snapshotChangedFields(List<String> changedFields) {
        if (changedFields == null || changedFields.isEmpty()) {
            return new ArrayList<>();
        }
        return new ArrayList<>(changedFields);
    }

    /**
     * 逻辑删除本记录。
     *
     * @param operatorId 操作人
     */
    public void markDeleted(Long operatorId) {
        this.setIsDeleted(Boolean.TRUE);
        this.setDeletedBy(operatorId);
        this.setDeletedAt(LocalDateTime.now());
    }
}
