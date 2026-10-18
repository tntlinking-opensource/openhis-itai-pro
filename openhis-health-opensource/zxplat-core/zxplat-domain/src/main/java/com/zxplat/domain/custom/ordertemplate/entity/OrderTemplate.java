/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.domain.custom.ordertemplate.entity;

import com.zxplat.domain.share.constants.ErrorCodeConstants;
import com.zxplat.domain.share.converter.OrderTemplateSceneConverter;
import com.zxplat.domain.share.enums.OrderTemplateKind;
import com.zxplat.domain.share.enums.OrderTemplateScene;
import com.zxplat.domain.share.enums.OrderTemplateSource;
import com.zxplat.domain.share.enums.OrderTemplateStatus;
import com.zxplat.domain.share.utils.EnumUtil;

import java.math.BigDecimal;
import com.zxplat.framework.core.application.exception.BusinessException;
import com.zxplat.framework.core.domain.BaseAuditedEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/**
 * 组套模板头聚合根。
 */
@Getter
@Setter
@Entity
@Table(name = "cus_order_template")
public class OrderTemplate extends BaseAuditedEntity {

    /**
     * 归属对象标识。
     */
    @Column(name = "object_id", nullable = false)
    private Long objectId;

    /**
     * 绑定类型（个人/科室/全院）。
     */
    @Column(name = "binding_enum", nullable = false)
    private String bindingEnum;

    /**
     * 适用场景（门诊/住院/通用）。
     */
    @Column(name = "scene_enum", nullable = false)
    @Convert(converter = OrderTemplateSceneConverter.class)
    private OrderTemplateScene sceneEnum;

    /**
     * 组套种类（GENERAL/TCM/PRICING）。
     */
    @Column(name = "template_kind_enum", nullable = false)
    private String templateKindEnum;

    /**
     * 模板来源（MAINTAINED=维护组套，FROM_ADVICE=医嘱模板/开嘱另存）。
     */
    @Column(name = "template_source_enum", nullable = false)
    private String templateSourceEnum;

    /**
     * 模板编码（可空，非空时建议唯一）。
     */
    @Column(name = "template_code")
    private String templateCode;

    /**
     * 模板名称。
     */
    @Column(name = "name", nullable = false)
    private String name;

    /**
     * 描述。
     */
    @Column(name = "description")
    private String description;

    /**
     * 列表排序权重（同层级内升序；数值越小越靠前）。
     */
    @Column(name = "sort_weight")
    private Integer sortWeight;

    /**
     * 状态（草稿/启用/停用）。
     */
    @Column(name = "status_enum", nullable = false)
    private String statusEnum;

    /**
     * 备注。
     */
    @Column(name = "remark")
    private String remark;

    /**
     * TCM 组套默认中药付数。
     */
    @Column(name = "default_chinese_herbs_dose_quantity")
    private BigDecimal defaultChineseHerbsDoseQuantity;

    /**
     * TCM 组套默认代煎标志。
     */
    @Column(name = "default_suffering_flag")
    private Boolean defaultSufferingFlag;

    /**
     * 初始化草稿状态。
     */
    public void initDraft() {
        this.statusEnum = OrderTemplateStatus.DRAFT.getCode();
    }

    /**
     * 断言头必填字段与取值合法（BR-OTL-001/002/005/011）。
     */
    public void assertRequiredFields() {
        if (this.objectId == null) {
            throw new BusinessException(ErrorCodeConstants.ORDER_TEMPLATE_INVALID_ARGUMENT, "归属对象不能为空");
        }
        if (this.bindingEnum == null) {
            throw new BusinessException(ErrorCodeConstants.ORDER_TEMPLATE_INVALID_ARGUMENT, "绑定类型不能为空");
        }
        if (this.sceneEnum == null) {
            throw new BusinessException(ErrorCodeConstants.ORDER_TEMPLATE_INVALID_ARGUMENT, "适用场景不能为空");
        }
        this.assertTemplateKindRules();
        this.assertTemplateSourceRules();
        if (this.name == null || this.name.trim().isEmpty()) {
            throw new BusinessException(ErrorCodeConstants.ORDER_TEMPLATE_INVALID_ARGUMENT, "模板名称不能为空");
        }
        if (this.statusEnum == null) {
            throw new BusinessException(ErrorCodeConstants.ORDER_TEMPLATE_INVALID_ARGUMENT, "状态不能为空");
        }
        if (this.sortWeight != null && this.sortWeight < 0) {
            throw new BusinessException(ErrorCodeConstants.ORDER_TEMPLATE_INVALID_ARGUMENT, "排序权重必须为非负整数");
        }
    }

    /**
     * 断言头允许被修改（已删除禁止写侧操作）。
     */
    public void assertEditable() {
        if (Boolean.TRUE.equals(this.getIsDeleted())) {
            throw new BusinessException(ErrorCodeConstants.ORDER_TEMPLATE_INVALID_STATUS, "已删除模板不允许修改");
        }
    }

    /**
     * 断言允许维护组套模板行（BR-OTL-018）。
     */
    public void assertLineMaintainable() {
        this.assertEditable();
        if (OrderTemplateStatus.RETIRED.getCode().equals(this.statusEnum)) {
            throw new BusinessException(ErrorCodeConstants.ORDER_TEMPLATE_INVALID_STATUS, "停用状态禁止维护组套模板行");
        }
    }

    /**
     * 发布（DRAFT -> ACTIVE）。
     */
    public void publish() {
        this.assertEditable();
        if (!OrderTemplateStatus.DRAFT.getCode().equals(this.statusEnum)) {
            throw new BusinessException(ErrorCodeConstants.ORDER_TEMPLATE_INVALID_STATUS, "当前状态不允许发布");
        }
        this.statusEnum = OrderTemplateStatus.ACTIVE.getCode();
    }

    /**
     * 断言当前状态不是 RETIRED（BR-OTL-017：停用状态禁止修改头属性）。
     */
    public void assertNotRetired() {
        if (OrderTemplateStatus.RETIRED.getCode().equals(this.statusEnum)) {
            throw new BusinessException(ErrorCodeConstants.ORDER_TEMPLATE_INVALID_STATUS, "停用状态禁止修改头属性");
        }
    }

    /**
     * 停用（-> RETIRED）。
     */
    public void retire() {
        this.assertEditable();
        if (OrderTemplateStatus.RETIRED.getCode().equals(this.statusEnum)) {
            throw new BusinessException(ErrorCodeConstants.ORDER_TEMPLATE_INVALID_STATUS, "当前已是停用状态");
        }
        this.statusEnum = OrderTemplateStatus.RETIRED.getCode();
    }

    /**
     * 重新启用（RETIRED → ACTIVE），恢复检索与套用。
     */
    public void reactivate() {
        this.assertEditable();
        if (!OrderTemplateStatus.RETIRED.getCode().equals(this.statusEnum)) {
            throw new BusinessException(ErrorCodeConstants.ORDER_TEMPLATE_INVALID_STATUS, "仅停用状态可重新启用");
        }
        this.statusEnum = OrderTemplateStatus.ACTIVE.getCode();
    }

    /**
     * 逻辑删除头（BR-OTL-007）。
     */
    public void markDeleted() {
        this.setIsDeleted(Boolean.TRUE);
    }

    /**
     * 校验组套种类与场景组合（BR-OTL-002、BR-OTL-027）。
     */
    public void assertTemplateKindRules() {
        if (this.templateKindEnum == null || this.templateKindEnum.trim().isEmpty()) {
            throw new BusinessException(ErrorCodeConstants.ORDER_TEMPLATE_INVALID_ARGUMENT, "组套种类不能为空");
        }
        if (EnumUtil.fromCode(OrderTemplateKind.class, this.templateKindEnum) == null) {
            throw new BusinessException(ErrorCodeConstants.ORDER_TEMPLATE_INVALID_ARGUMENT, "组套种类不合法");
        }
        if (OrderTemplateKind.PRICING.getCode().equals(this.templateKindEnum)) {
            if (this.sceneEnum != OrderTemplateScene.INPATIENT && this.sceneEnum != OrderTemplateScene.COMMON) {
                throw new BusinessException(ErrorCodeConstants.ORDER_TEMPLATE_INVALID_ARGUMENT, "划价组套适用场景仅允许住院或通用");
            }
        }
    }

    /**
     * 校验模板来源与种类组合（BR-OTL-029、BR-OTL-030）。
     */
    public void assertTemplateSourceRules() {
        if (this.templateSourceEnum == null || this.templateSourceEnum.trim().isEmpty()) {
            throw new BusinessException(ErrorCodeConstants.ORDER_TEMPLATE_INVALID_ARGUMENT, "模板来源不能为空");
        }
        if (EnumUtil.fromCode(OrderTemplateSource.class, this.templateSourceEnum) == null) {
            throw new BusinessException(ErrorCodeConstants.ORDER_TEMPLATE_INVALID_ARGUMENT, "模板来源不合法");
        }
        if (OrderTemplateSource.FROM_ADVICE.getCode().equals(this.templateSourceEnum)
            && OrderTemplateKind.PRICING.getCode().equals(this.templateKindEnum)) {
            throw new BusinessException(ErrorCodeConstants.ORDER_TEMPLATE_INVALID_ARGUMENT, "医嘱模板不支持划价组套种类");
        }
        // 申请单组套只在维护页编制，不从医嘱另存
        if (OrderTemplateSource.FROM_ADVICE.getCode().equals(this.templateSourceEnum)
            && OrderTemplateKind.SERVICE_APPLY.getCode().equals(this.templateKindEnum)) {
            throw new BusinessException(ErrorCodeConstants.ORDER_TEMPLATE_INVALID_ARGUMENT, "医嘱模板不支持申请单组套种类");
        }
    }

    /**
     * 发布前校验 TCM 头默认付数（BR-OTL-023）。
     */
    public void assertReadyToPublish() {
        if (OrderTemplateKind.TCM.getCode().equals(this.templateKindEnum)) {
            if (this.defaultChineseHerbsDoseQuantity == null
                || this.defaultChineseHerbsDoseQuantity.compareTo(BigDecimal.ZERO) <= 0) {
                throw new BusinessException(ErrorCodeConstants.ORDER_TEMPLATE_INVALID_ARGUMENT, "中医组套发布前须填写有效默认付数");
            }
        }
    }
}
