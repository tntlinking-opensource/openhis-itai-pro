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

package com.openhis.health.domain.administration.chargeitemdefinition.service;

import com.openhis.health.domain.administration.chargeitemdefinition.command.RemovePricingByInstanceCommand;
import com.openhis.health.domain.administration.chargeitemdefinition.command.SaveAllChargeItemDefinitionsCommand;
import com.openhis.health.domain.administration.chargeitemdefinition.command.SavePriceLineCommand;
import com.openhis.health.domain.administration.chargeitemdefinition.command.UpdatePricingItemInfoCommand;
import com.openhis.health.domain.administration.chargeitemdefinition.entity.ChargeItemDefinition;
import com.openhis.health.domain.administration.chargeitemdefinition.repository.ChargeItemDefinitionRepository;
import com.openhis.health.domain.share.constants.ErrorCodeConstants;
import com.openhis.health.domain.share.events.administration.chargeitemdefinition.ChargeItemDefinitionDomainEvents;
import com.openhis.whale.core.application.exception.BusinessException;
import com.openhis.whale.core.domain.event.IDomainEventPublisher;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

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
        // Pre：先按实例标识加载价格定义，确保“更新项目公共信息”具备真实落库对象。
        List<ChargeItemDefinition> rows;
        // 业务分支处理：优先按实例表+实例ID查询，否则仅按实例ID查询。
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

        repository.saveAll(rows);
        // Event：发布批量信息更新领域事件。
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
     * 按实例逻辑删除其下全部收费/定价行，并逐条发布移除事件。
     *
     * @param cmd 移除命令（实例表、实例主键、操作人等）
     */
    public void removePricingByInstance(RemovePricingByInstanceCommand cmd) {
        // Pre：按实例标识加载待移除的价格定义。
        List<ChargeItemDefinition> rows = new ArrayList<>();
        // 业务分支处理：优先按实例表+实例ID查询，否则仅按实例ID查询。
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
            rows.add(this.toEntity(
                item.getInstanceTable(),
                item.getInstanceId(),
                item.getTypeCode(),
                item.getYbType(),
                item.getPriceTypeCode(),
                item.getLotNo(),
                item.getPriceValue(),
                item.getChargeName(),
                null));
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
            ChargeItemDefinition row = this.toEntity(
                item.getInstanceTable(),
                item.getInstanceId(),
                null,
                null,
                item.getPriceTypeCode(),
                item.getLotNo(),
                item.getPriceValue(),
                null,
                item.getId());
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
}
