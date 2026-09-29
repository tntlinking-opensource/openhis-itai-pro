/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.domain.custom.chargediscountconfig.service;

import com.openhis.health.domain.custom.chargediscountconfig.command.ChangeChargeDiscountConfigStatusCommand;
import com.openhis.health.domain.custom.chargediscountconfig.command.CreateChargeDiscountConfigCommand;
import com.openhis.health.domain.custom.chargediscountconfig.command.DeleteChargeDiscountConfigCommand;
import com.openhis.health.domain.custom.chargediscountconfig.command.ModifyChargeDiscountConfigCommand;
import com.openhis.health.domain.custom.chargediscountconfig.entity.ChargeDiscountConfig;
import com.openhis.health.domain.custom.chargediscountconfig.repository.ChargeDiscountConfigRepository;
import com.openhis.health.domain.share.constants.ErrorCodeConstants;
import com.openhis.health.domain.share.events.custom.chargediscountconfig.ChargeDiscountConfigChangedEvent;
import com.openhis.whale.core.application.exception.BusinessException;
import com.openhis.whale.core.application.util.StringUtils;
import com.openhis.whale.core.domain.event.IDomainEventPublisher;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * 收费折扣配置写侧领域服务（DM-CUS-016 §6.1）。
 *
 * <p>职责：配置行创建/修改/启停/软删；执行 BR-CDC-001～004；持久化先于 {@link ChargeDiscountConfigChangedEvent}。</p>
 * <p>不变量：大类/单项互斥；三大目录表白名单；折扣率空或 (0,1]；未删唯一键。</p>
 * <p>仅注入本聚合 {@link ChargeDiscountConfigRepository}。</p>
 */
@Service
@RequiredArgsConstructor
public class ChargeDiscountConfigDomainService {

    /** 本聚合仓储（仅 {@code cus_charge_discount_config}）。 */
    private final ChargeDiscountConfigRepository repository;
    /** 领域事件发布（持久化后、beforeCommit）。 */
    private final IDomainEventPublisher domainEventPublisher;

    /**
     * 创建配置行，默认 ACTIVE，并发布变更事件。
     *
     * @param command 创建命令（命中键二选一、可选折扣率）
     * @return 新配置主键
     * @throws BusinessException 不变式失败或唯一键冲突时
     */
    public Long createChargeDiscountConfig(CreateChargeDiscountConfigCommand command) {
        // 前置校验：命令非空
        if (command == null) {
            throw new BusinessException(
                ErrorCodeConstants.CHARGE_DISCOUNT_CONFIG_INVALID_ARGUMENT, "创建命令不能为空");
        }

        // 组装聚合：表名/大类码去空白，避免空串绕过互斥与白名单
        ChargeDiscountConfig entity = new ChargeDiscountConfig();
        entity.setProductTable(this.normalizeBlankToNull(command.getProductTable()));
        entity.setCategoryCode(this.normalizeBlankToNull(command.getCategoryCode()));
        entity.setProductId(command.getProductId());
        entity.setDiscountRate(command.getDiscountRate());
        entity.setRemark(command.getRemark());
        entity.setOrgId(command.getOrgId());
        entity.initActiveStatus();

        // 前置校验：BR-CDC-001～003
        entity.assertCreateInvariants();
        // 前置校验：BR-CDC-004 唯一键
        this.assertUniqueOnCreate(entity);

        // 持久化
        ChargeDiscountConfig saved = repository.save(entity);

        // 发布事件：配置变更（能力门控缓存可失效）
        domainEventPublisher.publish(new ChargeDiscountConfigChangedEvent(saved.getId(), saved.getOrgId()));
        return saved.getId();
    }

    /**
     * 修改配置行（全量覆盖命中键与折扣率），并发布变更事件。
     *
     * <p>实现约定（对齐 SDD「命中键以实现为准」）：应用层须提交完整行形态字段；
     * {@code discountRate=null} 表示改为款员定率，而非「保持原值」。</p>
     *
     * @param command 修改命令
     * @throws BusinessException 行不存在、不变式失败或唯一键冲突时
     */
    public void modifyChargeDiscountConfig(ModifyChargeDiscountConfigCommand command) {
        // 前置校验：命令与主键
        if (command == null || command.getId() == null) {
            throw new BusinessException(
                ErrorCodeConstants.CHARGE_DISCOUNT_CONFIG_INVALID_ARGUMENT, "修改命令或主键不能为空");
        }

        // 加载/组装：未删行；命中键与折扣率全量覆盖
        ChargeDiscountConfig entity = this.requireConfig(command.getId());
        entity.setProductTable(this.normalizeBlankToNull(command.getProductTable()));
        entity.setCategoryCode(this.normalizeBlankToNull(command.getCategoryCode()));
        entity.setProductId(command.getProductId());
        // null 折扣率=款员定率（与创建语义一致）
        entity.setDiscountRate(command.getDiscountRate());
        entity.setRemark(command.getRemark());
        entity.setOrgId(command.getOrgId());

        // 前置校验：BR-CDC-001～003
        entity.assertModifyInvariants();
        // 前置校验：BR-CDC-004 唯一键（排除自身）
        this.assertUniqueOnModify(entity);

        // 持久化
        repository.save(entity);

        // 发布事件
        domainEventPublisher.publish(new ChargeDiscountConfigChangedEvent(entity.getId(), entity.getOrgId()));
    }

    /**
     * 启停配置（ACTIVE↔INACTIVE），并发布变更事件。
     *
     * @param command 启停命令
     * @throws BusinessException 行不存在或目标状态非法时
     */
    public void changeChargeDiscountConfigStatus(ChangeChargeDiscountConfigStatusCommand command) {
        // 前置校验：命令与主键
        if (command == null || command.getId() == null) {
            throw new BusinessException(
                ErrorCodeConstants.CHARGE_DISCOUNT_CONFIG_INVALID_ARGUMENT, "启停命令或主键不能为空");
        }

        // 加载聚合
        ChargeDiscountConfig entity = this.requireConfig(command.getId());
        // 前置校验：目标状态仅 ACTIVE/INACTIVE
        entity.assertStatusTransition(command.getStatusEnum());
        entity.changeStatus(command.getStatusEnum());

        // 持久化
        repository.save(entity);

        // 发布事件：启停影响能力门控计数
        domainEventPublisher.publish(new ChargeDiscountConfigChangedEvent(entity.getId(), entity.getOrgId()));
    }

    /**
     * 软删配置，并发布变更事件。
     *
     * @param command 删除命令
     * @throws BusinessException 行不存在或已删时
     */
    public void deleteChargeDiscountConfig(DeleteChargeDiscountConfigCommand command) {
        // 前置校验：命令与主键
        if (command == null || command.getId() == null) {
            throw new BusinessException(
                ErrorCodeConstants.CHARGE_DISCOUNT_CONFIG_INVALID_ARGUMENT, "删除命令或主键不能为空");
        }

        // 加载聚合
        ChargeDiscountConfig entity = this.requireConfig(command.getId());
        // 前置校验：未删才可软删
        entity.assertDeletable();
        entity.markDeleted(command.getOperatorId());

        // 持久化
        repository.save(entity);

        // 发布事件：软删后可能关闭能力门控
        domainEventPublisher.publish(new ChargeDiscountConfigChangedEvent(entity.getId(), entity.getOrgId()));
    }

    /**
     * 加载未删配置，不存在则抛业务异常。
     *
     * @param id 主键
     * @return 配置实体
     */
    private ChargeDiscountConfig requireConfig(Long id) {
        return repository.findByIdAndIsDeletedFalse(id)
            .orElseThrow(() -> new BusinessException(
                ErrorCodeConstants.CHARGE_DISCOUNT_CONFIG_NOT_FOUND, "收费折扣配置不存在"));
    }

    /**
     * 创建时唯一键校验（BR-CDC-004）。
     *
     * @param entity 待插入实体
     */
    private void assertUniqueOnCreate(ChargeDiscountConfig entity) {
        // 大类行：唯一键 (product_table, category_code)
        if (entity.isCategoryRow()) {
            if (repository.existsByProductTableAndCategoryCodeAndProductIdIsNullAndIsDeletedFalse(
                entity.getProductTable(), entity.getCategoryCode())) {
                throw new BusinessException(
                    ErrorCodeConstants.CHARGE_DISCOUNT_CONFIG_IDENTITY_CONFLICT, "该大类折扣配置已存在");
            }
            return;
        }
        // 单项行：唯一键 (product_table, product_id)；同大类下可并存单项
        if (repository.existsByProductTableAndProductIdAndCategoryCodeIsNullAndIsDeletedFalse(
            entity.getProductTable(), entity.getProductId())) {
            throw new BusinessException(
                ErrorCodeConstants.CHARGE_DISCOUNT_CONFIG_IDENTITY_CONFLICT, "该单项折扣配置已存在");
        }
    }

    /**
     * 修改时唯一键校验（排除自身，BR-CDC-004）。
     *
     * @param entity 待更新实体
     */
    private void assertUniqueOnModify(ChargeDiscountConfig entity) {
        // 大类行改键：排除本行后仍冲突则拒绝
        if (entity.isCategoryRow()) {
            if (repository.existsByProductTableAndCategoryCodeAndProductIdIsNullAndIdNotAndIsDeletedFalse(
                entity.getProductTable(), entity.getCategoryCode(), entity.getId())) {
                throw new BusinessException(
                    ErrorCodeConstants.CHARGE_DISCOUNT_CONFIG_IDENTITY_CONFLICT, "该大类折扣配置已存在");
            }
            return;
        }
        // 单项行改键：排除本行后仍冲突则拒绝
        if (repository.existsByProductTableAndProductIdAndCategoryCodeIsNullAndIdNotAndIsDeletedFalse(
            entity.getProductTable(), entity.getProductId(), entity.getId())) {
            throw new BusinessException(
                ErrorCodeConstants.CHARGE_DISCOUNT_CONFIG_IDENTITY_CONFLICT, "该单项折扣配置已存在");
        }
    }

    /**
     * 空白串归一为 null，避免大类/单项互斥被空串干扰。
     *
     * @param value 原始字符串
     * @return trim 后空则 null，否则 trim 后的值
     */
    private String normalizeBlankToNull(String value) {
        // null/空串直接视为未填
        if (StringUtils.isEmpty(value)) {
            return null;
        }
        String trimmed = value.trim();
        // 仅空白字符同样视为未填
        if (trimmed.isEmpty()) {
            return null;
        }
        return trimmed;
    }
}
