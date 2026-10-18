/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.domain.custom.chargediscountconfig.entity;

import com.zxplat.domain.share.constants.ChargeDiscountConfigConstants;
import com.zxplat.domain.share.constants.ErrorCodeConstants;
import com.zxplat.domain.share.enums.ChargeDiscountConfigStatus;
import com.zxplat.framework.core.application.exception.BusinessException;
import com.zxplat.framework.core.application.util.StringUtils;
import com.zxplat.framework.core.domain.BaseAuditedEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 收费折扣配置聚合根（{@code cus_charge_discount_config}；DM-CUS-016）。
 *
 * <p>一行只表达一种命中键：大类（{@code categoryCode}）或单项（{@code productId}）互斥；
 * {@code discountRate} 有值=固定率、空=款员定率。无 scope/mode 枚举列。</p>
 */
@Getter
@Setter
@Entity
@Table(name = "cus_charge_discount_config")
public class ChargeDiscountConfig extends BaseAuditedEntity {

    /**
     * 产品定义表名（三大目录之一）。
     */
    @Column(name = "product_table", nullable = false)
    private String productTable;

    /**
     * 大类编码；大类行非空，单项行必须空。
     */
    @Column(name = "category_code")
    private String categoryCode;

    /**
     * 产品定义主键；单项行非空，大类行必须空。
     */
    @Column(name = "product_id")
    private Long productId;

    /**
     * 折扣率（折后占原价，(0,1]）；空表示款员收费时定率。
     */
    @Column(name = "discount_rate")
    private BigDecimal discountRate;

    /**
     * 状态 code（{@link ChargeDiscountConfigStatus#getCode()}）。
     */
    @Column(name = "status_enum", nullable = false)
    private String statusEnum;

    /**
     * 配置说明。
     */
    @Column(name = "remark")
    private String remark;

    /**
     * 院区标识（预留；首版可空=租户全院）。
     */
    @Column(name = "org_id")
    private Long orgId;

    /**
     * 新建默认 ACTIVE（参与命中与折扣相关显隐）。
     */
    public void initActiveStatus() {
        this.statusEnum = ChargeDiscountConfigStatus.ACTIVE.getCode();
    }

    /**
     * 创建/修改字段不变式（BR-CDC-001～003）。
     *
     * @throws BusinessException 行形态互斥、表名或折扣率非法时
     */
    public void assertCreateInvariants() {
        this.assertProductTableAllowed();
        this.assertCategoryProductXor();
        this.assertDiscountRateRange();
        // 状态必填：新建由 initActiveStatus 写入，修改路径须保留或显式传入
        if (StringUtils.isEmpty(this.statusEnum)) {
            throw new BusinessException(
                ErrorCodeConstants.CHARGE_DISCOUNT_CONFIG_INVALID_ARGUMENT, "状态不能为空");
        }
    }

    /**
     * 修改字段不变式，与创建同口径（BR-CDC-001～003）。
     *
     * @throws BusinessException 校验失败时
     */
    public void assertModifyInvariants() {
        this.assertCreateInvariants();
    }

    /**
     * 启停目标仅允许 ACTIVE↔INACTIVE（BR 启停）。
     *
     * @param targetStatusEnum 目标状态 code
     * @throws BusinessException 目标非法或与当前相同无意义切换时仍允许幂等由调用方决定；此处仅校验码合法
     */
    public void assertStatusTransition(String targetStatusEnum) {
        // 目标状态码为空无法启停
        if (StringUtils.isEmpty(targetStatusEnum)) {
            throw new BusinessException(
                ErrorCodeConstants.CHARGE_DISCOUNT_CONFIG_INVALID_STATUS, "目标状态不能为空");
        }
        boolean active = ChargeDiscountConfigStatus.ACTIVE.getCode().equals(targetStatusEnum);
        boolean inactive = ChargeDiscountConfigStatus.INACTIVE.getCode().equals(targetStatusEnum);
        // 仅允许 ACTIVE↔INACTIVE；同态重复提交由调用方幂等接受
        if (!active && !inactive) {
            throw new BusinessException(
                ErrorCodeConstants.CHARGE_DISCOUNT_CONFIG_INVALID_STATUS, "目标状态仅允许 ACTIVE 或 INACTIVE");
        }
    }

    /**
     * 断言当前行允许软删（已删则拒绝）。
     *
     * @throws BusinessException 已软删时
     */
    public void assertDeletable() {
        // 已软删行不可再次删除（唯一键释放后可重建新行）
        if (Boolean.TRUE.equals(this.getIsDeleted())) {
            throw new BusinessException(
                ErrorCodeConstants.CHARGE_DISCOUNT_CONFIG_NOT_FOUND, "配置已删除");
        }
    }

    /**
     * 标记逻辑删除（写 isDeleted / deletedAt / deletedBy）。
     *
     * @param operatorId 操作人；可空
     */
    public void markDeleted(Long operatorId) {
        this.setIsDeleted(Boolean.TRUE);
        this.setDeletedBy(operatorId);
        this.setDeletedAt(LocalDateTime.now());
    }

    /**
     * 切换启停状态（调用前须 {@link #assertStatusTransition}）。
     *
     * @param targetStatusEnum 目标状态 code
     */
    public void changeStatus(String targetStatusEnum) {
        this.statusEnum = targetStatusEnum;
    }

    /**
     * 是否大类行（有 category、无 product）。
     *
     * @return true 表示大类行
     */
    public boolean isCategoryRow() {
        return !StringUtils.isEmpty(this.categoryCode) && this.productId == null;
    }

    /**
     * 是否单项行（有 product、无 category）。
     *
     * @return true 表示单项行
     */
    public boolean isProductRow() {
        return this.productId != null && StringUtils.isEmpty(this.categoryCode);
    }

    /**
     * BR-CDC-002：{@code product_table} 必须在三大目录白名单内。
     *
     * @throws BusinessException 表名为空或不在白名单时
     */
    private void assertProductTableAllowed() {
        // 表名必填
        if (StringUtils.isEmpty(this.productTable)) {
            throw new BusinessException(
                ErrorCodeConstants.CHARGE_DISCOUNT_CONFIG_INVALID_ARGUMENT, "产品表名不能为空");
        }
        // 拒绝挂号服务等非三大目录表（BR-CDC-002）
        if (!ChargeDiscountConfigConstants.ALLOWED_PRODUCT_TABLES.contains(this.productTable)) {
            throw new BusinessException(
                ErrorCodeConstants.CHARGE_DISCOUNT_CONFIG_INVALID_ARGUMENT,
                "产品表名必须是药品/诊疗/耗材三大目录之一");
        }
    }

    /**
     * BR-CDC-001：{@code category_code} 与 {@code product_id} 恰好其一非空。
     *
     * @throws BusinessException 同填或同空时
     */
    private void assertCategoryProductXor() {
        boolean hasCategory = !StringUtils.isEmpty(this.categoryCode);
        boolean hasProduct = this.productId != null;
        // 同填或同空均非法：禁止混成「既大类又单项」或「空行」
        if (hasCategory == hasProduct) {
            throw new BusinessException(
                ErrorCodeConstants.CHARGE_DISCOUNT_CONFIG_INVALID_ARGUMENT,
                "大类编码与产品主键须恰好其一非空");
        }
    }

    /**
     * BR-CDC-003：{@code discount_rate} 空或 ∈ (0,1]。
     *
     * @throws BusinessException 有值但不在 (0,1] 时
     */
    private void assertDiscountRateRange() {
        // 空=款员定率，跳过区间校验
        if (this.discountRate == null) {
            return;
        }
        // 有值须 (0,1]：0 与负数、大于 1 均拒绝
        if (this.discountRate.compareTo(BigDecimal.ZERO) <= 0
            || this.discountRate.compareTo(BigDecimal.ONE) > 0) {
            throw new BusinessException(
                ErrorCodeConstants.CHARGE_DISCOUNT_CONFIG_INVALID_ARGUMENT,
                "折扣率须为空或属于 (0,1]");
        }
    }
}
