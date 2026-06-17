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

package com.openhis.health.domain.workflow.supplydelivery.service;

import com.openhis.health.domain.share.constants.TableNameConstants;
import com.openhis.health.domain.share.enums.DispenseStatus;
import com.openhis.health.domain.share.enums.SupplyType;
import com.openhis.health.domain.share.events.workflow.supplydelivery.SupplyDeliveryCompletedEvent;
import com.openhis.health.domain.share.events.workflow.supplydelivery.SupplyDeliveryCreatedEvent;
import com.openhis.health.domain.share.events.workflow.supplydelivery.SupplyDeliveryDeletedEvent;
import com.openhis.health.domain.workflow.supplydelivery.command.*;
import com.openhis.health.domain.workflow.supplydelivery.entity.SupplyDelivery;
import com.openhis.health.domain.workflow.supplydelivery.repository.SupplyDeliveryRepository;
import com.openhis.whale.core.domain.event.IDomainEventPublisher;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * 供应发放写侧领域服务。
 *
 * <p>边界：本服务仅写 {@code wkf_supply_delivery}；库存账面增减由应用层编排调用 InventoryItem 完成。</p>
 *
 * <p>命令与实体中 {@code typeEnum}/{@code statusEnum} 等字段与库表 VARCHAR 一致，使用各枚举 {@code getCode()}（B.10.8.1）。</p>
 */
@Service
@RequiredArgsConstructor
public class SupplyDeliveryDomainService {

    private final SupplyDeliveryRepository repository;
    private final IDomainEventPublisher domainEventPublisher;

    /**
     * 创建供应发放（普通路径：药库进货同意后的入库发放、出库、退库、损益等；实体创建即完成态，见 {@link com.openhis.health.domain.workflow.supplydelivery.entity.SupplyDelivery#initCompleted()}）。
     *
     * <p>前置条件：{@code BR-SDL-001} 数量非零；上游供应请求行已处于可发放状态（由应用层编排保证）。</p>
     *
     * @param cmd 创建命令；各行 {@code typeEnum} 等为持久化 code（B.10.8.1）
     * @return 已保存发放主键列表（顺序与持久化返回一致）
     */
    public List<Long> createSupplyDelivery(CreateSupplyDeliveryCommand cmd) {
        List<CreateSupplyDeliveryCommand.CreateSupplyDeliveryItemCommand> items = cmd.resolveItems();
        List<SupplyDelivery> toSave = new ArrayList<>();
        for (CreateSupplyDeliveryCommand.CreateSupplyDeliveryItemCommand item : items) {
            SupplyDelivery delivery = new SupplyDelivery();
            delivery.setRequestId(item.getRequestId());
            delivery.setTypeEnum(item.getTypeEnum());
            delivery.setItemTable(item.getItemTable());
            delivery.setItemId(item.getItemId());
            delivery.setQuantity(item.getQuantity());
            delivery.setUnitCode(item.getUnitCode());
            delivery.setLotNumber(item.getLotNumber());
            delivery.setOccurrenceTime(item.getOccurrenceTime());
            delivery.assertQuantityNonZero();
            // 普通路径：创建即完成（BR-SDL-003）。
            delivery.initCompleted();
            toSave.add(delivery);
        }
        List<SupplyDelivery> saved = repository.saveAll(toSave);
        List<Long> ids = new ArrayList<>();
        for (SupplyDelivery delivery : saved) {
            ids.add(delivery.getId());
            domainEventPublisher.publish(new SupplyDeliveryCreatedEvent(
                delivery.getId(), delivery.getRequestId(), delivery.getTypeEnum(),
                delivery.getStatusEnum(), delivery.getQuantity()));
        }
        return ids;
    }

    /**
     * 创建调拨发放（源减目增双侧记录，双侧均创建即 COMPLETED）。
     *
     * <p>前置条件：{@code BR-SDL-001} 数量非零；上游调拨执行单已 AGREE；源 ≠ 目（{@code BR-SR-007}）。</p>
     *
     * @param cmd 创建命令（含调拨行集合）
     * @return 已保存发放标识列表（每行产生出库 + 入库两条）
     */
    public List<Long> createTransferDelivery(CreateTransferDeliveryCommand cmd) {
        List<CreateTransferDeliveryCommand.CreateTransferDeliveryItemCommand> items = cmd.resolveItems();
        List<SupplyDelivery> toSave = new ArrayList<>();
        for (CreateTransferDeliveryCommand.CreateTransferDeliveryItemCommand item : items) {
            // 出库侧（源位置）记录。
            SupplyDelivery outDelivery = new SupplyDelivery();
            outDelivery.setRequestId(item.getRequestId());
            outDelivery.setTypeEnum(item.getTypeEnum());
            outDelivery.setItemId(item.getItemId());
            outDelivery.setQuantity(item.getQuantity().negate());
            outDelivery.assertQuantityNonZero();
            outDelivery.initCompleted();
            toSave.add(outDelivery);

            // 入库侧（目标位置）记录。
            SupplyDelivery inDelivery = new SupplyDelivery();
            inDelivery.setRequestId(item.getRequestId());
            inDelivery.setTypeEnum(item.getTypeEnum());
            inDelivery.setItemId(item.getItemId());
            inDelivery.setQuantity(item.getQuantity());
            inDelivery.assertQuantityNonZero();
            inDelivery.initCompleted();
            toSave.add(inDelivery);
        }
        List<SupplyDelivery> saved = repository.saveAll(toSave);
        List<Long> ids = new ArrayList<>();
        for (SupplyDelivery delivery : saved) {
            ids.add(delivery.getId());
            domainEventPublisher.publish(new SupplyDeliveryCreatedEvent(
                delivery.getId(), delivery.getRequestId(), delivery.getTypeEnum(),
                delivery.getStatusEnum(), delivery.getQuantity()));
        }
        return ids;
    }

    /**
     * 创建盘点差异发放（仅对有差异须落账的明细行，创建即 COMPLETED）。
     *
     * <p>前置条件：{@code BR-SDL-001} 数量非零（零差异行由应用层过滤后不传入）；上游盘点执行单已 AGREE。</p>
     *
     * @param cmd 创建命令（含差异行集合）
     * @return 已保存发放标识列表
     */
    public List<Long> createStocktakingDelivery(CreateStocktakingDeliveryCommand cmd) {
        List<CreateStocktakingDeliveryCommand.CreateStocktakingDeliveryItemCommand> items = cmd.resolveItems();
        List<SupplyDelivery> toSave = new ArrayList<>();
        for (CreateStocktakingDeliveryCommand.CreateStocktakingDeliveryItemCommand item : items) {
            // 业务分支处理。
            if (item.getDifferenceLines() == null || item.getDifferenceLines().isEmpty()) {
                continue;
            }
            for (CreateStocktakingDeliveryCommand.DifferenceLineCommand line : item.getDifferenceLines()) {
                SupplyDelivery delivery = new SupplyDelivery();
                delivery.setRequestId(item.getRequestId());
                delivery.setTypeEnum(item.getTypeEnum());
                delivery.setItemTable(line.getItemTable());
                delivery.setItemId(line.getItemId());
                delivery.setQuantity(line.getQuantity());
                delivery.setUnitCode(line.getUnitCode());
                delivery.setLotNumber(line.getLotNumber());
                delivery.assertQuantityNonZero();
                // 盘点路径：创建即完成（BR-SDL-003）。
                delivery.initCompleted();
                toSave.add(delivery);
            }
        }
        List<SupplyDelivery> saved = repository.saveAll(toSave);
        List<Long> ids = new ArrayList<>();
        for (SupplyDelivery delivery : saved) {
            ids.add(delivery.getId());
            domainEventPublisher.publish(new SupplyDeliveryCreatedEvent(
                delivery.getId(), delivery.getRequestId(), delivery.getTypeEnum(),
                delivery.getStatusEnum(), delivery.getQuantity()));
        }
        return ids;
    }

    /**
     * 创建药品汇总发放（type 27，药房核发时创建，进入汇总发放首态）。
     *
     * <p>前置条件：来源 SupplyRequest type 27 已处于 APPROVAL（护士站发送后）；患者与目录引用合法。</p>
     * <p>库存扣减由应用层在 MedicationDispense 完成路径编排，本方法不写库存台账。</p>
     *
     * @param cmd 创建命令（含汇总明细集合）
     */
    public void createSummarySupplyDelivery(CreateSummarySupplyDeliveryCommand cmd) {
        List<CreateSummarySupplyDeliveryCommand.CreateSummarySupplyDeliveryItemCommand> items = cmd.resolveItems();
        List<SupplyDelivery> toSave = new ArrayList<>();
        for (CreateSummarySupplyDeliveryCommand.CreateSummarySupplyDeliveryItemCommand item : items) {
            SupplyDelivery delivery = new SupplyDelivery();
            delivery.setRequestId(item.getSupplyRequestId());
            delivery.setSummaryId(item.getSummaryId());
            delivery.setPatientId(item.getPatientId());
            // 汇总发放 itemTable 固定为药品定义表。
            delivery.setItemTable(TableNameConstants.MED_MEDICATION_DEFINITION);
            delivery.setItemId(item.getItemId());
            delivery.setQuantity(item.getQuantity());
            delivery.setUnitCode(item.getUnitCode());
            delivery.setLotNumber(item.getLotNumber());
            delivery.setReceiverId(item.getReceiverId());
            // BR-SDL-005：发放 type 与来源 SupplyRequest（type 27）一致。
            delivery.setTypeEnum(SupplyType.MEDICATION_SUMMARY_ORDER.getCode());
            // 汇总路径首态 PREPARATION（BR-SDL-004；对齐 AS-IP-NUR-010 BR-IP-SUM-011 待发药）。
            delivery.initDraftOrPreparation(DispenseStatus.PREPARATION.getCode());
            toSave.add(delivery);
        }
        List<SupplyDelivery> saved = repository.saveAll(toSave);
        for (SupplyDelivery delivery : saved) {
            domainEventPublisher.publish(new SupplyDeliveryCreatedEvent(
                delivery.getId(), delivery.getRequestId(), delivery.getTypeEnum(),
                delivery.getStatusEnum(), delivery.getQuantity()));
        }
    }

    /**
     * 批量更新为已完成（汇总或批量发放场景，将多条发放记录统一推进到完成态）。
     *
     * <p>前置条件：当前状态允许进入 COMPLETED；非终态锁定（{@code BR-SDL-002}）。</p>
     *
     * @param cmd 批量完成命令（含发放标识列表与经办人信息）
     */
    public void updateCompletedStatusBatch(UpdateCompletedStatusBatchCommand cmd) {
        List<UpdateCompletedStatusBatchCommand.UpdateCompletedStatusBatchItemCommand> items = cmd.resolveItems();
        for (UpdateCompletedStatusBatchCommand.UpdateCompletedStatusBatchItemCommand item : items) {
            // 业务分支处理。
            if (item.getSupplyDeliveryIdList() == null || item.getSupplyDeliveryIdList().isEmpty()) {
                continue;
            }
            List<SupplyDelivery> deliveries = repository.findByIdIn(item.getSupplyDeliveryIdList());
            List<SupplyDelivery> toSave = new ArrayList<>();
            for (SupplyDelivery delivery : deliveries) {
                delivery.assertCompletable();
                delivery.toCompleted(item.getCompletedTime(), item.getPractitionerId());
                toSave.add(delivery);
            }
            List<SupplyDelivery> saved = repository.saveAll(toSave);
            for (SupplyDelivery delivery : saved) {
                domainEventPublisher.publish(new SupplyDeliveryCompletedEvent(
                    delivery.getId(), delivery.getRequestId(), delivery.getOccurrenceTime()));
            }
        }
    }

    /**
     * 按请求删除发放（审批回退或纠错场景，逻辑删除尚未被下游固化的发放行）。
     *
     * <p>边界：若发放已驱动库存扣减，须先由编排完成回补或冲正，不得仅靠本方法静默修复跨域状态。</p>
     *
     * @param cmd 删除命令（含请求标识列表）
     */
    public void deleteSupplyDeliveryByReqId(DeleteSupplyDeliveryByReqIdCommand cmd) {
        List<DeleteSupplyDeliveryByReqIdCommand.DeleteSupplyDeliveryByReqIdItemCommand> items = cmd.resolveItems();
        for (DeleteSupplyDeliveryByReqIdCommand.DeleteSupplyDeliveryByReqIdItemCommand item : items) {
            // 业务分支处理。
            if (item.getRequestIdList() == null || item.getRequestIdList().isEmpty()) {
                continue;
            }
            List<SupplyDelivery> deliveries = repository.findByRequestIdIn(item.getRequestIdList());
            // 业务分支处理。
            if (deliveries.isEmpty()) {
                continue;
            }
            // 收集请求标识用于发布删除事件。
            List<SupplyDelivery> toDelete = new ArrayList<>(deliveries);
            repository.deleteAll(toDelete);
            for (SupplyDelivery delivery : toDelete) {
                domainEventPublisher.publish(new SupplyDeliveryDeletedEvent(
                    delivery.getId(), delivery.getRequestId()));
            }
        }
    }
}
