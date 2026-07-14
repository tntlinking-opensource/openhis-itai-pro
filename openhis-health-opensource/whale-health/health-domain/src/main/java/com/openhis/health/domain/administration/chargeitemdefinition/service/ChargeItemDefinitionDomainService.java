/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.domain.administration.chargeitemdefinition.service;

import com.openhis.health.domain.administration.chargeitemdefinition.command.DeactivateActivePriceLinesCommand;
import com.openhis.health.domain.administration.chargeitemdefinition.command.RemovePricingByInstanceCommand;
import com.openhis.health.domain.administration.chargeitemdefinition.command.SaveAllChargeItemDefinitionsCommand;
import com.openhis.health.domain.administration.chargeitemdefinition.command.SavePriceLineCommand;
import com.openhis.health.domain.administration.chargeitemdefinition.command.UpdatePricingItemInfoCommand;
import com.openhis.health.domain.administration.chargeitemdefinition.entity.ChargeItemDefinition;
import com.openhis.health.domain.administration.chargeitemdefinition.repository.ChargeItemDefinitionRepository;
import com.openhis.health.domain.share.constants.ErrorCodeConstants;
import com.openhis.health.domain.share.constants.FieldNameConstants;
import com.openhis.health.domain.share.events.administration.chargeitemdefinition.ChargeItemDefinitionDomainEvents;
import com.openhis.health.domain.share.enums.PublicationStatus;
import com.openhis.whale.core.application.exception.BusinessException;
import com.openhis.whale.core.domain.event.IDomainEventPublisher;
import lombok.RequiredArgsConstructor;
import com.openhis.whale.core.application.util.StringUtils;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;

import static com.openhis.whale.core.application.util.StringUtils.trimToNull;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 收费项定价（{@code adm_charge_item_definition}）聚合写侧领域服务。
 *
 * <p>职责：按实例维护零售价/采购价/限价/批号价行，批量停用、按实例移除及项目公共信息批量更新。</p>
 * <p>不变量：新建行须落 {@code status_enum=ACTIVE} 与 {@code type_code}（BR-CHGDEF-WR-002）；持久化先于价格行领域事件发布。</p>
 * <p>仅注入本聚合 {@link ChargeItemDefinitionRepository}。</p>
 */
@Service
@RequiredArgsConstructor
public class ChargeItemDefinitionDomainService {
    private final ChargeItemDefinitionRepository repository;
    private final IDomainEventPublisher domainEventPublisher;


    /**
     * 新增零售价行（委托 {@link #addPriceLineByCommand}）。
     *
     * @param cmd 价格行保存命令（实例表、实例主键、价格类型、批号、金额等）
     */
    public void addItemRetailPrice(SavePriceLineCommand cmd) { this.addPriceLineByCommand(cmd); }
    /**
     * 新增采购价行。
     *
     * @param cmd 价格行保存命令（实例表、实例主键、价格类型、批号、金额等）
     */
    public void addItemPurchasePrice(SavePriceLineCommand cmd) { this.addPriceLineByCommand(cmd); }
    /**
     * 新增限价行。
     *
     * @param cmd 价格行保存命令（实例表、实例主键、价格类型、批号、金额等）
     */
    public void addItemLimitPrice(SavePriceLineCommand cmd) { this.addPriceLineByCommand(cmd); }
    /**
     * 更新已有零售价行。
     *
     * @param cmd 价格行保存命令（须含待更新行主键及新价格信息）
     */
    public void updateItemRetailPrice(SavePriceLineCommand cmd) { this.updatePriceLineByCommand(cmd); }
    /**
     * 更新已有采购价行。
     *
     * @param cmd 价格行保存命令（须含待更新行主键及新价格信息）
     */
    public void updateItemPurchasePrice(SavePriceLineCommand cmd) { this.updatePriceLineByCommand(cmd); }
    /**
     * 更新已有限价行。
     *
     * @param cmd 价格行保存命令（须含待更新行主键及新价格信息）
     */
    public void updateItemLimitPrice(SavePriceLineCommand cmd) { this.updatePriceLineByCommand(cmd); }
    /**
     * 按实例批量更新收费定义的公共项目信息（审计字段），并发布批量信息更新事件。
     *
     * @param cmd 更新命令（实例表、实例主键、操作人等）
     */
    public void updatePricingItemInfo(UpdatePricingItemInfoCommand cmd) {
        // 前置校验：按实例标识加载待更新行，空集则拒绝
        // Pre：先按实例标识加载价格定义，确保“更新项目公共信息”具备真实落库对象。
        List<ChargeItemDefinition> rows;
        // 优先按实例表+实例ID查询，否则仅按实例ID查询。
        if (cmd.getInstanceTable() != null && !cmd.getInstanceTable().isBlank()) {
            rows = repository.findAllByInstanceTableAndInstanceId(cmd.getInstanceTable().trim(), cmd.getInstanceId());
        } else {
            rows = repository.findAllByInstanceId(cmd.getInstanceId());
        }
        if (rows.isEmpty()) {
            throw new BusinessException(ErrorCodeConstants.CHARGE_ITEM_DEFINITION_NOT_FOUND, "待更新收费定义不存在");
        }

        for (ChargeItemDefinition row : rows) {
            row.setUpdatedBy(cmd.getOperatorId());
            row.setUpdatedAt(LocalDateTime.now());
        }

        // 持久化
        repository.saveAll(rows);
        // 发布事件：批量信息更新
        String instanceTable = rows.get(0).getInstanceTable();
        domainEventPublisher.publish(new ChargeItemDefinitionDomainEvents.ChargeItemDefinitionInfoBatchUpdatedEvent(
            cmd.getInstanceId(), cmd.getInstanceId(), instanceTable));
    }
    /**
     * 新增带批号的采购价行。
     *
     * @param cmd 价格行保存命令（批号、价格类型等）
     */
    public void addLotPurchasePrice(SavePriceLineCommand cmd) { this.addPriceLineByCommand(cmd); }
    /**
     * 新增带批号的销售价行。
     *
     * @param cmd 价格行保存命令（批号、价格类型等）
     */
    public void addLotSalePrice(SavePriceLineCommand cmd) { this.addPriceLineByCommand(cmd); }

    /**
     * 批量停用匹配键下的当前 {@code ACTIVE} 定价行（调价审批 BR-PRC-APR-003）。
     *
     * @param cmd 停用命令（实例 + 价类 + 可选批号）
     */
    public void deactivateActivePriceLinesByCommand(DeactivateActivePriceLinesCommand cmd) {
        // Pre：空命令或无停用项则跳过。
        if (cmd == null) {
            return;
        }
        List<DeactivateActivePriceLinesCommand.Item> itemList = cmd.getItems();
        if (itemList == null || itemList.isEmpty()) {
            return;
        }
        Long operatorId = cmd.getOperatorId();
        List<ChargeItemDefinition> toSave = new ArrayList<>();
        for (DeactivateActivePriceLinesCommand.Item item : itemList) {
            if (item == null || item.getInstanceId() == null || StringUtils.isEmpty(item.getPriceTypeCode())) {
                continue;
            }
            // Pre：按匹配键加载当前 ACTIVE 行。
            List<ChargeItemDefinition> activeRows = this.loadActivePriceLinesForDeactivate(item);
            for (ChargeItemDefinition row : activeRows) {
                row.setStatusEnum(PublicationStatus.INACTIVE.getCode());
                row.setUpdatedBy(operatorId);
                row.setUpdatedAt(LocalDateTime.now());
                toSave.add(row);
            }
        }
        if (toSave.isEmpty()) {
            return;
        }
        // Cmd：批量持久化停用并发布价格行更新事件。
        List<ChargeItemDefinition> saved = repository.saveAll(toSave);
        for (ChargeItemDefinition row : saved) {
            domainEventPublisher.publish(new ChargeItemDefinitionDomainEvents.PriceLineUpdatedEvent(row.getId()));
        }
    }
    /**
     * 按实例逻辑删除其下全部收费/定价行，并逐条发布移除事件。
     *
     * @param cmd 移除命令（实例表、实例主键、操作人等）
     */
    public void removePricingByInstance(RemovePricingByInstanceCommand cmd) {
        // Pre：按实例标识加载待移除的价格定义。
        List<ChargeItemDefinition> rows = new ArrayList<>();
        // 优先按实例表+实例ID查询，否则仅按实例ID查询。
        if (cmd.getInstanceTable() != null) {
            rows.addAll(repository.findAllByInstanceTableAndInstanceId(cmd.getInstanceTable(), cmd.getInstanceId()));
        } else {
            rows.addAll(repository.findAllByInstanceId(cmd.getInstanceId()));
        }
        List<Long> removedIds = new ArrayList<>();
        for (ChargeItemDefinition row : rows) {
            removedIds.add(row.getId());
            row.setIsDeleted(Boolean.TRUE);
            row.setDeletedBy(cmd.getOperatorId());
            row.setDeletedAt(LocalDateTime.now());
        }
        repository.saveAll(rows);
        // Event：先发布按实例移除的批量事件。
        domainEventPublisher.publish(new ChargeItemDefinitionDomainEvents.ChargeItemDefinitionRemovedByInstanceEvent(
            cmd.getInstanceId(), cmd.getInstanceId(), removedIds, cmd.getInstanceTable()));
        // Event：再逐条发布价格行移除事件。
        for (Long removedId : removedIds) {
            domainEventPublisher.publish(new ChargeItemDefinitionDomainEvents.PriceLineRemovedEvent(removedId));
        }
    }
    /**
     * 批量新建收费项定义（多类型/多价格行一次落库），发布批量与逐行添加事件。
     *
     * @param cmd 批量保存命令（明细列表）
     */
    public void saveAllChargeItemDefinitions(SaveAllChargeItemDefinitionsCommand cmd) {
        // Pre：遍历命令项，逐条转换为收费定义实体。
        List<ChargeItemDefinition> rows = new ArrayList<>();
        for (SaveAllChargeItemDefinitionsCommand.Item item : cmd.resolveItems()) {
            String typeCode = trimToNull(item.getTypeCode());
            if (typeCode == null) {
                throw new BusinessException(
                    ErrorCodeConstants.CHARGE_ITEM_DEFINITION_INVALID_ARGUMENT,
                    "新建定价行缺少财务类型 type_code，instanceTable="
                        + item.getInstanceTable()
                        + ", instanceId="
                        + item.getInstanceId());
            }
            ChargeItemDefinition row = this.toEntity(
                item.getInstanceTable(),
                item.getInstanceId(),
                typeCode,
                item.getYbType(),
                item.getPriceTypeCode(),
                item.getLotNo(),
                item.getPriceValue(),
                item.getChargeName(),
                null);
            this.ensurePublicationStatusForInsert(row, null);
            rows.add(row);
        }
        List<ChargeItemDefinition> saved = repository.saveAll(rows);
        // Pre：取首条 ID 作为批次锚点，空批次时默认为 0L。
        Long anchorId = 0L;
        if (!saved.isEmpty()) {
            anchorId = saved.get(0).getId();
        }
        // Event：发布批量添加事件（通知型）。
        domainEventPublisher.publish(new ChargeItemDefinitionDomainEvents.PriceBatchAddedEvent(anchorId, saved.size()));
        // Event：逐条发布价格行添加事件，驱动 ProductCatalogProjector 回写价格列。
        for (ChargeItemDefinition row : saved) {
            if (row != null && row.getId() != null) {
                domainEventPublisher.publish(new ChargeItemDefinitionDomainEvents.PriceLineAddedEvent(row.getId()));
            }
        }
    }

    /**
     * 解析命令中的价格行明细并落库，逐条发布 {@code PriceLineAdded} 事件。
     *
     * @param cmd 价格行保存命令
     */
    private void addPriceLineByCommand(SavePriceLineCommand cmd) {
        // Pre：遍历命令项，逐条组装价格定义实体。
        List<ChargeItemDefinition> toSave = new ArrayList<>();
        for (SavePriceLineCommand.Item item : cmd.resolveItems()) {
            String typeCode = trimToNull(item.getTypeCode());
            if (typeCode == null) {
                // 批号价/调价新增行：继承同实例历史定价行的 fin_type，避免 LOT 行 type_code 为空
                typeCode = this.resolveSiblingTypeCode(item.getInstanceTable(), item.getInstanceId());
            }
            String ybType = trimToNull(item.getYbType());
            ChargeItemDefinition row = this.toEntity(
                item.getInstanceTable(),
                item.getInstanceId(),
                typeCode,
                ybType,
                item.getPriceTypeCode(),
                item.getLotNo(),
                item.getPriceValue(),
                null,
                item.getId());
            // 新建行必须落 status_enum=ACTIVE（或命令显式指定），否则当前价读路径查不到
            if (item.getId() == null) {
                this.ensurePublicationStatusForInsert(row, item.getStatusEnum());
                this.ensureTypeCodeForInsert(row);
            }
            toSave.add(row);
        }
        List<ChargeItemDefinition> saved = repository.saveAll(toSave);
        // Event：逐条发布价格行添加事件。
        for (ChargeItemDefinition row : saved) {
            domainEventPublisher.publish(new ChargeItemDefinitionDomainEvents.PriceLineAddedEvent(row.getId()));
        }
    }

    /**
     * 更新已有价格行并发布调价与行更新事件。
     *
     * @param cmd 价格行保存命令
     */
    private void updatePriceLineByCommand(SavePriceLineCommand cmd) {
        // Pre：解析命令条目并加载已存在记录。
        List<SavePriceLineCommand.Item> items = cmd.resolveItems();
        Map<Long, ChargeItemDefinition> existsMap = this.loadExistsMap(items);
        List<ChargeItemDefinition> toSave = new ArrayList<>();
        List<BigDecimal> oldPrices = new ArrayList<>();
        // Loop：逐条校验存在性、记录旧价格、更新字段。
        for (SavePriceLineCommand.Item item : cmd.resolveItems()) {
            ChargeItemDefinition oldRow = existsMap.get(item.getId());
            if (oldRow == null) {
                throw new BusinessException(ErrorCodeConstants.CHARGE_ITEM_DEFINITION_NOT_FOUND, "待更新定价行不存在");
            }
            // Pre：记录旧价格，用于后续调价事件。
            oldPrices.add(oldRow.getPriceValue());
            oldRow.setInstanceTable(item.getInstanceTable());
            oldRow.setInstanceId(item.getInstanceId());
            oldRow.setPriceTypeCode(item.getPriceTypeCode());
            oldRow.setLotNo(item.getLotNo());
            oldRow.setPriceValue(item.getPriceValue());
            toSave.add(oldRow);
        }
        List<ChargeItemDefinition> saved = repository.saveAll(toSave);
        // Event：逐条发布价格行更新事件与调价记录事件。
        for (int i = 0; i < saved.size(); i++) {
            ChargeItemDefinition row = saved.get(i);
            domainEventPublisher.publish(new ChargeItemDefinitionDomainEvents.PriceLineUpdatedEvent(row.getId()));
            domainEventPublisher.publish(new ChargeItemDefinitionDomainEvents.PriceAdjustmentRecordedEvent(row.getId(), oldPrices.get(i), row.getPriceValue()));
        }
    }

    /**
     * 按明细中的主键加载待更新实体映射（排除已软删行）。
     *
     * @param items 价格行命令条目列表
     * @return 以主键为键的有效收费定义映射
     */
    private Map<Long, ChargeItemDefinition> loadExistsMap(List<SavePriceLineCommand.Item> items) {
        // Pre：收集所有非空主键。
        List<Long> ids = new ArrayList<>();
        for (SavePriceLineCommand.Item item : items) {
            if (item.getId() != null) {
                ids.add(item.getId());
            }
        }
        // Pre：批量查询收费定义记录。
        List<ChargeItemDefinition> exists = repository.findAllById(ids);
        // Asm：过滤已软删行，构建有效记录映射。
        Map<Long, ChargeItemDefinition> map = new HashMap<>();
        for (ChargeItemDefinition row : exists) {
            if (!Boolean.TRUE.equals(row.getIsDeleted())) {
                map.put(row.getId(), row);
            }
        }
        return map;
    }

    /**
     * 由命令字段组装 {@link ChargeItemDefinition}；{@code id} 非空表示更新路径预置主键。
     */
    private ChargeItemDefinition toEntity(
        String instanceTable,
        Long instanceId,
        String typeCode,
        String ybType,
        String priceTypeCode,
        String lotNo,
        BigDecimal priceValue,
        String chargeName,
        Long id) {
        ChargeItemDefinition row = new ChargeItemDefinition();
        row.setId(id);
        row.setInstanceTable(instanceTable);
        row.setInstanceId(instanceId);
        row.setTypeCode(typeCode);
        row.setYbType(ybType);
        row.setPriceTypeCode(priceTypeCode);
        row.setLotNo(lotNo);
        row.setPriceValue(priceValue);
        row.setChargeName(chargeName);
        return row;
    }

    /**
     * 新建定价行落库前校验 {@code type_code}（**BR-CHGDEF-WR-002**）：日结/费用明细仅锚历史定价行 fin_type。
     *
     * @param row 待 insert 实体
     * @throws BusinessException type_code 仍为空
     */
    private void ensureTypeCodeForInsert(ChargeItemDefinition row) {
        if (row == null) {
            return;
        }
        if (StringUtils.isEmpty(row.getTypeCode())) {
            throw new BusinessException(
                ErrorCodeConstants.CHARGE_ITEM_DEFINITION_INVALID_ARGUMENT,
                "新建定价行缺少财务类型 type_code，instanceTable="
                    + row.getInstanceTable()
                    + ", instanceId="
                    + row.getInstanceId());
        }
    }

    /**
     * 从同实例任意未软删定价行继承 {@code type_code}（含 INACTIVE 历史行）。
     *
     * @param instanceTable 实例表
     * @param instanceId    实例主键
     * @return trim 后的 type_code；无命中时为 {@code null}
     */
    private String resolveSiblingTypeCode(String instanceTable, Long instanceId) {
        if (StringUtils.isEmpty(instanceTable) || instanceId == null) {
            return null;
        }
        List<ChargeItemDefinition> siblings =
            repository.findAllByInstanceTableAndInstanceId(instanceTable.trim(), instanceId);
        for (ChargeItemDefinition sibling : siblings) {
            if (sibling == null || Boolean.TRUE.equals(sibling.getIsDeleted())) {
                continue;
            }
            String siblingTypeCode = trimToNull(sibling.getTypeCode());
            if (siblingTypeCode != null) {
                return siblingTypeCode;
            }
        }
        return null;
    }

    /**
     * 新建定价行落库前写入 {@code status_enum}（**BR-CHGDEF-QRY-004** 写侧配套）：命令未指定时默认 {@link PublicationStatus#ACTIVE}。
     *
     * @param row                 待 insert 实体
     * @param explicitStatusEnum  命令显式状态码，可空
     */
    private void ensurePublicationStatusForInsert(ChargeItemDefinition row, String explicitStatusEnum) {
        if (row == null) {
            return;
        }
        if (StringUtils.isNotEmpty(explicitStatusEnum)) {
            row.setStatusEnum(explicitStatusEnum.trim());
            return;
        }
        if (StringUtils.isEmpty(row.getStatusEnum())) {
            row.setStatusEnum(PublicationStatus.ACTIVE.getCode());
        }
    }

    /**
     * 加载待停用的当前启用定价行（匹配实例 + 价类 + 批号口径）。
     *
     * @param item 停用匹配键
     * @return 未软删且 {@code status_enum=ACTIVE} 的行
     */
    private List<ChargeItemDefinition> loadActivePriceLinesForDeactivate(DeactivateActivePriceLinesCommand.Item item) {
        String instanceTable = item.getInstanceTable();
        Long instanceId = item.getInstanceId();
        String priceTypeCode = item.getPriceTypeCode().trim();
        String lotTrimmed = trimLotNoOrNull(item.getLotNo());
        Specification<ChargeItemDefinition> specification = (root, cq, cb) -> {
            List<jakarta.persistence.criteria.Predicate> predicates = new ArrayList<>();
            predicates.add(cb.isFalse(root.get(FieldNameConstants.IS_DELETED)));
            predicates.add(cb.equal(root.get(FieldNameConstants.STATUS_ENUM), PublicationStatus.ACTIVE.getCode()));
            predicates.add(cb.equal(root.get(FieldNameConstants.PRICE_TYPE_CODE), priceTypeCode));
            predicates.add(cb.equal(root.get(FieldNameConstants.INSTANCE_ID), instanceId));
            if (StringUtils.isNotEmpty(instanceTable)) {
                predicates.add(cb.equal(root.get(FieldNameConstants.INSTANCE_TABLE), instanceTable.trim()));
            }
            if (lotTrimmed == null) {
                predicates.add(cb.or(
                    cb.isNull(root.get(FieldNameConstants.LOT_NO)),
                    cb.equal(root.get(FieldNameConstants.LOT_NO), "")
                ));
            } else {
                predicates.add(cb.equal(root.get(FieldNameConstants.LOT_NO), lotTrimmed));
            }
            return cb.and(predicates.toArray(new jakarta.persistence.criteria.Predicate[0]));
        };
        return repository.findAll(specification);
    }

    /**
     * 批号 trim；空白时返回 null（项目级价类停用匹配键用）。
     *
     * @param lotNo 原始批号
     * @return trim 后批号；空白时为 null
     */
    private static String trimLotNoOrNull(String lotNo) {
        if (StringUtils.isEmpty(lotNo)) {
            return null;
        }
        return lotNo.trim();
    }
}
