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

package com.openhis.health.domain.workflow.inventoryitem.service;

import com.openhis.health.domain.share.constants.FieldNameConstants;
import com.openhis.health.domain.share.constants.ErrorCodeConstants;
import com.openhis.health.domain.workflow.inventoryitem.command.AllocateAndDecreaseCommand;
import com.openhis.health.domain.workflow.inventoryitem.command.CreateInventoryItemCommand;
import com.openhis.health.domain.workflow.inventoryitem.command.DecreaseQuantityCommand;
import com.openhis.health.domain.workflow.inventoryitem.command.DeleteInventoryOnZeroQuantityCommand;
import com.openhis.health.domain.workflow.inventoryitem.command.ForceUnlockCommand;
import com.openhis.health.domain.workflow.inventoryitem.command.IncreaseQuantityCommand;
import com.openhis.health.domain.workflow.inventoryitem.command.LockInventoryCommand;
import com.openhis.health.domain.workflow.inventoryitem.command.ResumeSupplyCommand;
import com.openhis.health.domain.workflow.inventoryitem.command.StopSupplyCommand;
import com.openhis.health.domain.workflow.inventoryitem.command.UnlockByBusNoCommand;
import com.openhis.health.domain.workflow.inventoryitem.entity.InventoryItem;
import com.openhis.health.domain.workflow.inventoryitem.entity.InventoryLock;
import com.openhis.health.domain.workflow.inventoryitem.repository.InventoryItemRepository;
import com.openhis.health.domain.workflow.inventoryitem.repository.InventoryLockRepository;
import com.openhis.health.domain.workflow.inventoryitem.valueobject.BatchAllocationResult;
import com.openhis.whale.core.application.exception.BusinessException;
import org.apache.commons.lang3.StringUtils;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.RequiredArgsConstructor;

/**
 * 库存项写侧领域服务（DM-WKF-008）。
 *
 * <p>DOC-CONFLICT：DM-WKF-008 §5 事件白名单标注"未发"，故本服务不发布任何领域事件；
 * 原代码中的 InventoryItemCreatedEvent 等事件发布已按文档移除。</p>
 */
@Service
@RequiredArgsConstructor
public class InventoryItemDomainService {

    private final InventoryItemRepository inventoryItemRepository;
    private final InventoryLockRepository inventoryLockRepository;

    /**
     * 新建库存项。
     *
     * @param command 领域命令（批次内仅一条明细：物品、四元组定位、数量等）
     * @return 已持久化的库存项
     * @throws BusinessException 批次条数不为 1、同四元组已存在或不变式失败时
     */
    public InventoryItem createInventoryItem(CreateInventoryItemCommand command) {
        List<CreateInventoryItemCommand.CreateInventoryItemItemCommand> items = command.resolveItems();
        // 业务分支处理。
        if (items.size() != 1) {
            throw new BusinessException(ErrorCodeConstants.INVENTORY_ITEM_INVALID_ARGUMENT, "一次仅允许新建一条库存项");
        }
        CreateInventoryItemCommand.CreateInventoryItemItemCommand item = items.get(0);

        if (this.existsNonDeletedInventoryByQuadruple(
            item.getLotNumber(), item.getLocationId(), item.getItemTable(), item.getItemId())) {
            throw new BusinessException(ErrorCodeConstants.INVENTORY_ITEM_INVALID_ARGUMENT, "四元组已存在匹配库存项，禁止重复新建");
        }

        InventoryItem entity = new InventoryItem();
        entity.setItemTable(item.getItemTable());
        entity.setItemId(item.getItemId());
        entity.setName(item.getName());
        entity.setSupplierId(item.getSupplierId());
        entity.setUnitCode(item.getUnitCode());
        entity.setQuantity(item.getQuantity());
        entity.setLotNumber(item.getLotNumber());
        entity.setLocationId(item.getLocationId());
        entity.setLocationStoreId(item.getLocationStoreId());
        entity.setProductionDate(item.getProductionDate());
        entity.setExpirationDate(item.getExpirationDate());
        entity.setCategoryCode(item.getCategoryCode());
        entity.setPyStr(item.getPyStr());
        entity.setWbStr(item.getWbStr());
        entity.assertCreatable();
        entity.initActive();

        return inventoryItemRepository.save(entity);
    }

    /**
     * 库存数量增加。
     *
     * @param command 批次内一条：库存项主键与增加数量（&gt;0）
     * @return 已持久化的库存项
     * @throws BusinessException 参数非法、项不存在、已锁定或不允许增加时
     */
    public InventoryItem increaseQuantity(IncreaseQuantityCommand command) {
        List<IncreaseQuantityCommand.IncreaseQuantityItemCommand> items = command.resolveItems();
        // 业务分支处理。
        if (items.size() != 1) {
            throw new BusinessException(ErrorCodeConstants.INVENTORY_ITEM_INVALID_ARGUMENT, "一次仅允许增加一条库存项数量");
        }
        IncreaseQuantityCommand.IncreaseQuantityItemCommand item = items.get(0);
        // 业务分支处理。
        if (item.getId() == null) {
            throw new BusinessException(ErrorCodeConstants.INVENTORY_ITEM_INVALID_ARGUMENT, "id 不能为空");
        }
        // 业务分支处理。
        if (item.getIncreaseQty() == null || item.getIncreaseQty().compareTo(BigDecimal.ZERO) <= 0) {
            throw new BusinessException(ErrorCodeConstants.INVENTORY_ITEM_INVALID_ARGUMENT, "increaseQty 必须大于 0");
        }

        InventoryItem entity = this.mustGetActiveRow(item.getId());
        this.assertNotLocked(entity.getId());
        entity.assertIncreaseAllowed();
        entity.increaseQuantity(item.getIncreaseQty());
        return inventoryItemRepository.save(entity);
    }

    /**
     * 批量增加库存项可用量（同一事务内多条目一次落库）。
     *
     * @param command 批量递增命令；每条须含 id 与 increaseQty（&gt;0）
     * @throws BusinessException 参数非法、项不存在、已锁定或不允许增加时
     */
    public void increaseQuantities(IncreaseQuantityCommand command) {
        List<IncreaseQuantityCommand.IncreaseQuantityItemCommand> items = command.resolveItems();
        List<InventoryItem> toSave = new ArrayList<>();
        for (IncreaseQuantityCommand.IncreaseQuantityItemCommand item : items) {
            if (item.getId() == null) {
                throw new BusinessException(ErrorCodeConstants.INVENTORY_ITEM_INVALID_ARGUMENT, "id 不能为空");
            }
            if (item.getIncreaseQty() == null || item.getIncreaseQty().compareTo(BigDecimal.ZERO) <= 0) {
                throw new BusinessException(ErrorCodeConstants.INVENTORY_ITEM_INVALID_ARGUMENT, "increaseQty 必须大于 0");
            }
            InventoryItem entity = this.mustGetActiveRow(item.getId());
            this.assertNotLocked(entity.getId());
            entity.assertIncreaseAllowed();
            entity.increaseQuantity(item.getIncreaseQty());
            toSave.add(entity);
        }
        inventoryItemRepository.saveAll(toSave);
    }

    /**
     * 库存数量扣减。
     *
     * @param command 批次内一条：主键、扣减数量（&gt;0）及是否允许负库存
     * @return 已持久化的库存项
     * @throws BusinessException 参数非法、已锁定、可用量不足或不允许扣减时
     */
    public InventoryItem decreaseQuantity(DecreaseQuantityCommand command) {
        List<DecreaseQuantityCommand.DecreaseQuantityItemCommand> items = command.resolveItems();
        // 业务分支处理。
        if (items.size() != 1) {
            throw new BusinessException(ErrorCodeConstants.INVENTORY_ITEM_INVALID_ARGUMENT, "一次仅允许扣减一条库存项数量");
        }
        DecreaseQuantityCommand.DecreaseQuantityItemCommand item = items.get(0);
        // 业务分支处理。
        if (item.getId() == null) {
            throw new BusinessException(ErrorCodeConstants.INVENTORY_ITEM_INVALID_ARGUMENT, "id 不能为空");
        }
        // 业务分支处理。
        if (item.getDecreaseQty() == null || item.getDecreaseQty().compareTo(BigDecimal.ZERO) <= 0) {
            throw new BusinessException(ErrorCodeConstants.INVENTORY_ITEM_INVALID_ARGUMENT, "decreaseQty 必须大于 0");
        }

        InventoryItem entity = this.mustGetActiveRow(item.getId());
        this.assertNotLocked(entity.getId());
        entity.assertDecreaseAllowed(item.getDecreaseQty(), item.getAllowNegative());
        entity.decreaseQuantity(item.getDecreaseQty());
        return inventoryItemRepository.save(entity);
    }

    /**
     * 按库存项主键合并扣减量后一次性 {@code saveAll}，满足门诊发药等多行指向同一批次时的批量落库口径。
     *
     * <p>先在内存逐行校验锁定与可用量，再统一持久化；任一主键缺失或合并量非法时整体失败。</p>
     *
     * @param operatorId               操作人标识（与单条扣减命令对齐，供后续审计扩展预留）
     * @param inventoryIdToDecreaseQty 库存项 id → 最小单位扣减总量
     * @throws BusinessException       主键不存在、已锁定、可用量不足或合并量非法时
     */
    public void decreaseQuantitiesMerged(Long operatorId, Map<Long, BigDecimal> inventoryIdToDecreaseQty) {
        if (operatorId == null) {
            throw new BusinessException(ErrorCodeConstants.INVENTORY_ITEM_INVALID_ARGUMENT, "operatorId 不能为空");
        }
        if (inventoryIdToDecreaseQty == null || inventoryIdToDecreaseQty.isEmpty()) {
            return;
        }
        List<Long> ids = new ArrayList<>(inventoryIdToDecreaseQty.keySet());
        List<InventoryItem> loaded = inventoryItemRepository.findByIdIn(ids);
        if (loaded.size() != ids.size()) {
            throw new BusinessException(ErrorCodeConstants.INVENTORY_ITEM_NOT_FOUND, "批量扣减涉及的库存项主键不完整或不存在");
        }
        Map<Long, InventoryItem> rowById = new HashMap<>();
        for (InventoryItem row : loaded) {
            if (row.getId() != null) {
                rowById.put(row.getId(), row);
            }
        }
        for (Long inventoryItemId : ids) {
            InventoryItem entity = rowById.get(inventoryItemId);
            if (entity == null) {
                throw new BusinessException(ErrorCodeConstants.INVENTORY_ITEM_NOT_FOUND, "库存项不存在");
            }
            BigDecimal decreaseQty = inventoryIdToDecreaseQty.get(inventoryItemId);
            if (decreaseQty == null || decreaseQty.compareTo(BigDecimal.ZERO) <= 0) {
                throw new BusinessException(ErrorCodeConstants.INVENTORY_ITEM_INVALID_ARGUMENT, "合并扣减量必须大于 0");
            }
            this.assertNotLocked(entity.getId());
            entity.assertDecreaseAllowed(decreaseQty, Boolean.FALSE);
            entity.decreaseQuantity(decreaseQty);
        }
        inventoryItemRepository.saveAll(loaded);
    }

    /**
     * 批量分配并扣减库存（FEFO 策略）。
     *
     * @param command 批次内一条：物品表、物品 id、库位、需求数量等
     * @return 各批次分配结果列表
     * @throws BusinessException 无候选、总可用不足或参数非法时
     */
    public List<BatchAllocationResult> allocateAndDecrease(AllocateAndDecreaseCommand command) {
        List<AllocateAndDecreaseCommand.AllocateAndDecreaseItemCommand> items = command.resolveItems();
        // 业务分支处理。
        if (items.size() != 1) {
            throw new BusinessException(ErrorCodeConstants.INVENTORY_ITEM_INVALID_ARGUMENT, "一次仅允许发起一组分配扣减请求");
        }
        AllocateAndDecreaseCommand.AllocateAndDecreaseItemCommand item = items.get(0);
        // 业务分支处理。
        if (item.getItemTable() == null || item.getItemTable().trim().isEmpty()) {
            throw new BusinessException(ErrorCodeConstants.INVENTORY_ITEM_INVALID_ARGUMENT, "itemTable 不能为空");
        }
        // 业务分支处理。
        if (item.getItemId() == null) {
            throw new BusinessException(ErrorCodeConstants.INVENTORY_ITEM_INVALID_ARGUMENT, "itemId 不能为空");
        }
        // 业务分支处理。
        if (item.getRequiredQty() == null || item.getRequiredQty().compareTo(BigDecimal.ZERO) <= 0) {
            throw new BusinessException(ErrorCodeConstants.INVENTORY_ITEM_INVALID_ARGUMENT, "requiredQty 必须大于 0");
        }

        LocalDateTime now = LocalDateTime.now();
        List<InventoryItem> candidates = this.findAvailableCandidates(item.getItemTable(), item.getItemId(), item.getLocationId(), now);
        // 业务分支处理。
        if (candidates.isEmpty()) {
            throw new BusinessException(ErrorCodeConstants.INVENTORY_ITEM_INVALID_ARGUMENT, "无可用库存候选");
        }

        BigDecimal total = BigDecimal.ZERO;
        for (InventoryItem row : candidates) {
            // 业务分支处理。
            if (row.getQuantity() != null && row.getQuantity().compareTo(BigDecimal.ZERO) > 0) {
                total = total.add(row.getQuantity());
            }
        }
        // 业务分支处理。
        if (total.compareTo(item.getRequiredQty()) < 0) {
            throw new BusinessException(ErrorCodeConstants.INVENTORY_ITEM_INVALID_ARGUMENT, "可用库存不足，无法分配");
        }

        BigDecimal remainingNeed = item.getRequiredQty();
        List<BatchAllocationResult> results = new ArrayList<>();
        for (InventoryItem row : candidates) {
            // 业务分支处理。
            if (remainingNeed.compareTo(BigDecimal.ZERO) <= 0) {
                break;
            }
            BigDecimal available = row.getQuantity();
            // 业务分支处理。
            if (available == null || available.compareTo(BigDecimal.ZERO) <= 0) {
                continue;
            }

            BigDecimal allocate;
            // 业务分支处理。
            if (available.compareTo(remainingNeed) >= 0) {
                allocate = remainingNeed;
            } else {
                allocate = available;
            }

            row.decreaseQuantity(allocate);

            BatchAllocationResult r = new BatchAllocationResult();
            r.setInventoryItemId(row.getId());
            r.setLotNumber(row.getLotNumber());
            r.setAllocatedQuantity(allocate);
            r.setRemainingQuantity(row.getQuantity());
            r.setLocationId(row.getLocationId());
            r.setLocationStoreId(row.getLocationStoreId());
            r.setExpirationDate(row.getExpirationDate());
            results.add(r);

            remainingNeed = remainingNeed.subtract(allocate);
        }

        inventoryItemRepository.saveAll(candidates);
        return results;
    }

    /**
     * 锁定库存项。
     *
     * @param command 批次内一条：库存项主键与业务单号 busNo
     * @return 已持久化的锁记录
     * @throws BusinessException 已锁定、参数非法或项不存在时
     */
    public InventoryLock lockInventory(LockInventoryCommand command) {
        List<LockInventoryCommand.LockInventoryItemCommand> items = command.resolveItems();
        // 业务分支处理。
        if (items.size() != 1) {
            throw new BusinessException(ErrorCodeConstants.INVENTORY_ITEM_INVALID_ARGUMENT, "一次仅允许锁定一个库存项");
        }
        LockInventoryCommand.LockInventoryItemCommand item = items.get(0);
        // 业务分支处理。
        if (item.getInventoryId() == null) {
            throw new BusinessException(ErrorCodeConstants.INVENTORY_ITEM_INVALID_ARGUMENT, "inventoryId 不能为空");
        }
        // 业务分支处理。
        if (item.getBusNo() == null || item.getBusNo().trim().isEmpty()) {
            throw new BusinessException(ErrorCodeConstants.INVENTORY_ITEM_INVALID_ARGUMENT, "busNo 不能为空");
        }

        this.mustGetActiveRow(item.getInventoryId());
        // 业务分支处理。
        if (inventoryLockRepository.findByInventoryIdAndLockFlagTrue(item.getInventoryId()).isPresent()) {
            throw new BusinessException(ErrorCodeConstants.INVENTORY_ITEM_INVALID_ARGUMENT, "库存项已锁定");
        }

        List<InventoryLock> existing = inventoryLockRepository.findByBusNoOrderByOrderFlagAsc(item.getBusNo().trim());
        int orderFlag = existing.size();

        InventoryLock lock = new InventoryLock();
        lock.setInventoryId(item.getInventoryId());
        lock.setBusNo(item.getBusNo().trim());
        lock.setOrderFlag(orderFlag);
        lock.assertLockable();
        lock.lockOn();

        return inventoryLockRepository.save(lock);
    }

    /**
     * 按业务单号解锁所有关联锁记录。
     *
     * @param command 批次内一条：busNo；无锁定行时幂等返回
     * @throws BusinessException 单号为空或批次条数非法时
     */
    public void unlockByBusNo(UnlockByBusNoCommand command) {
        List<UnlockByBusNoCommand.UnlockByBusNoItemCommand> items = command.resolveItems();
        // 业务分支处理。
        if (items.size() != 1) {
            throw new BusinessException(ErrorCodeConstants.INVENTORY_ITEM_INVALID_ARGUMENT, "一次仅允许按一个单号解锁");
        }
        UnlockByBusNoCommand.UnlockByBusNoItemCommand item = items.get(0);
        // 业务分支处理。
        if (item.getBusNo() == null || item.getBusNo().trim().isEmpty()) {
            throw new BusinessException(ErrorCodeConstants.INVENTORY_ITEM_INVALID_ARGUMENT, "busNo 不能为空");
        }

        List<InventoryLock> locks = inventoryLockRepository.findByBusNoAndLockFlagTrueOrderByOrderFlagAsc(item.getBusNo().trim());
        // 业务分支处理。
        if (locks.isEmpty()) {
            return;
        }

        for (InventoryLock lock : locks) {
            lock.lockToUnlocked();
        }
        inventoryLockRepository.saveAll(locks);
    }

    /**
     * 强制解锁指定锁记录。
     *
     * @param command 批次内一条：锁记录主键 lockId
     * @throws BusinessException 锁不存在或不允许强制解锁时
     */
    public void forceUnlock(ForceUnlockCommand command) {
        List<ForceUnlockCommand.ForceUnlockItemCommand> items = command.resolveItems();
        // 业务分支处理。
        if (items.size() != 1) {
            throw new BusinessException(ErrorCodeConstants.INVENTORY_ITEM_INVALID_ARGUMENT, "一次仅允许强制解锁一条记录");
        }
        ForceUnlockCommand.ForceUnlockItemCommand item = items.get(0);
        // 业务分支处理。
        if (item.getLockId() == null) {
            throw new BusinessException(ErrorCodeConstants.INVENTORY_ITEM_INVALID_ARGUMENT, "lockId 不能为空");
        }

        InventoryLock lock = inventoryLockRepository.findById(item.getLockId()).orElseThrow(
            () -> new BusinessException(ErrorCodeConstants.INVENTORY_ITEM_NOT_FOUND, "锁记录不存在"));
        lock.assertForceUnlockable();
        lock.lockToForceUnlocked();
        inventoryLockRepository.save(lock);
    }

    /**
     * 停供库存项（active → retired），支持批量，同一调用内先聚合内存态再一次性落库。
     *
     * @param command 命令；{@link StopSupplyCommand#getOperatorId()} 必填；{@code items} 每条须含主键
     * @return 已持久化集合；入参非法或任一行业务断言失败时抛出
     * @throws BusinessException 参数非法、项不存在或状态不允许停供时
     */
    public List<InventoryItem> stopSupply(StopSupplyCommand command) {
        if (command.getOperatorId() == null) {
            throw new BusinessException(ErrorCodeConstants.INVENTORY_ITEM_INVALID_ARGUMENT, "操作人不能为空");
        }
        List<StopSupplyCommand.StopSupplyItemCommand> items = command.resolveItems();
        List<InventoryItem> toSave = new ArrayList<>();
        for (StopSupplyCommand.StopSupplyItemCommand item : items) {
            if (item.getId() == null) {
                throw new BusinessException(ErrorCodeConstants.INVENTORY_ITEM_INVALID_ARGUMENT, "id 不能为空");
            }
            InventoryItem entity = this.mustGetActiveRow(item.getId());
            entity.stopSupply();
            toSave.add(entity);
        }
        return inventoryItemRepository.saveAll(toSave);
    }

    /**
     * 恢复供应（retired → active），支持批量，同一调用内先聚合内存态再一次性落库。
     *
     * @param command 命令；{@link ResumeSupplyCommand#getOperatorId()} 必填；{@code items} 每条须含主键
     * @return 已持久化集合
     * @throws BusinessException 参数非法、项不存在或状态不允许恢复时
     */
    public List<InventoryItem> resumeSupply(ResumeSupplyCommand command) {
        if (command.getOperatorId() == null) {
            throw new BusinessException(ErrorCodeConstants.INVENTORY_ITEM_INVALID_ARGUMENT, "操作人不能为空");
        }
        List<ResumeSupplyCommand.ResumeSupplyItemCommand> items = command.resolveItems();
        List<InventoryItem> toSave = new ArrayList<>();
        for (ResumeSupplyCommand.ResumeSupplyItemCommand item : items) {
            if (item.getId() == null) {
                throw new BusinessException(ErrorCodeConstants.INVENTORY_ITEM_INVALID_ARGUMENT, "id 不能为空");
            }
            InventoryItem entity = this.mustGetActiveRow(item.getId());
            entity.resumeSupply();
            toSave.add(entity);
        }
        return inventoryItemRepository.saveAll(toSave);
    }

    /**
     * 零库存逻辑删除。
     *
     * @param command 批次内一条：主键及应用侧确认的「无在途发放」等门控标志
     * @return 已持久化的库存项
     * @throws BusinessException 数量非零、存在未结发放或参数非法时
     */
    public InventoryItem deleteInventoryOnZeroQuantity(DeleteInventoryOnZeroQuantityCommand command) {
        List<DeleteInventoryOnZeroQuantityCommand.DeleteInventoryOnZeroQuantityItemCommand> items = command.resolveItems();
        // 业务分支处理。
        if (items.size() != 1) {
            throw new BusinessException(ErrorCodeConstants.INVENTORY_ITEM_INVALID_ARGUMENT, "一次仅允许清零删除一条库存项");
        }
        DeleteInventoryOnZeroQuantityCommand.DeleteInventoryOnZeroQuantityItemCommand item = items.get(0);
        // 业务分支处理。
        if (item.getId() == null) {
            throw new BusinessException(ErrorCodeConstants.INVENTORY_ITEM_INVALID_ARGUMENT, "id 不能为空");
        }

        InventoryItem entity = this.mustGetActiveRow(item.getId());
        entity.assertZeroAndNoPending(item.getApplicationConfirmedNoPendingDelivery());
        entity.logicalDelete();
        return inventoryItemRepository.save(entity);
    }

    /**
     * 按主键加载库存项，不存在则抛出业务异常。
     *
     * @param id 库存项主键
     * @return 库存项实体
     * @throws BusinessException 库存项不存在时
     */
    private InventoryItem mustGetActiveRow(Long id) {
        return inventoryItemRepository.findById(id).orElseThrow(
            () -> new BusinessException(ErrorCodeConstants.INVENTORY_ITEM_NOT_FOUND, "库存项不存在"));
    }

    /**
     * 若库存项存在有效锁定记录则抛出业务异常。
     *
     * @param inventoryId 库存项主键
     * @throws BusinessException 库存项已锁定时
     */
    private void assertNotLocked(Long inventoryId) {
        if (inventoryLockRepository.findByInventoryIdAndLockFlagTrue(inventoryId).isPresent()) {
            throw new BusinessException(ErrorCodeConstants.INVENTORY_ITEM_INVALID_ARGUMENT, "库存项已锁定");
        }
    }

    /**
     * 查询可用于 FEFO 分配的候选行：启用态、数量&gt;0、未过期、可选库位相等，并排除仍处锁定中的行；按效期、创建时间升序。
     *
     * <p>BR-INV-001：FEFO（先过期先出）策略的候选行查询逻辑。</p>
     * <ul>
     *   <li>筛选条件：启用态、数量>0、未过期、指定库位（可选）</li>
     *   <li>排序规则：按效期升序，效期相同按创建时间升序</li>
     *   <li>锁定排除：查询锁定表排除已被锁定的库存项</li>
     * </ul>
     *
     * @param itemTable  物品表名
     * @param itemId     物品ID
     * @param locationId 库位ID（可为 null）
     * @param now        当前时间
     * @return 符合条件的候选库存项列表（已排除锁定项）
     */
    private List<InventoryItem> findAvailableCandidates(String itemTable, Long itemId, Long locationId, LocalDateTime now) {
        // Pre：构建查询条件。
        Specification<InventoryItem> specification = (root, cq, cb) -> {
            List<jakarta.persistence.criteria.Predicate> predicates = new ArrayList<>();
            predicates.add(cb.isFalse(root.get(FieldNameConstants.IS_DELETED)));
            predicates.add(cb.equal(root.get(FieldNameConstants.ITEM_TABLE), itemTable));
            predicates.add(cb.equal(root.get(FieldNameConstants.ITEM_ID), itemId));
            predicates.add(cb.equal(root.get(FieldNameConstants.INVENTORY_STATUS_ENUM), 2));
            predicates.add(cb.greaterThan(root.get(FieldNameConstants.QUANTITY), BigDecimal.ZERO));
            // 业务分支处理：指定库位时添加库位过滤。
            if (locationId != null) {
                predicates.add(cb.equal(root.get(FieldNameConstants.LOCATION_ID), locationId));
            }
            // 未过期条件：效期为空或大于当前时间。
            jakarta.persistence.criteria.Predicate notExpired = cb.or(
                cb.isNull(root.get(FieldNameConstants.EXPIRATION_DATE)),
                cb.greaterThan(root.get(FieldNameConstants.EXPIRATION_DATE), now)
            );
            predicates.add(notExpired);
            return cb.and(predicates.toArray(new jakarta.persistence.criteria.Predicate[0]));
        };

        Sort sort = Sort.by(Sort.Order.asc(FieldNameConstants.EXPIRATION_DATE), Sort.Order.asc(FieldNameConstants.CREATED_AT));
        List<InventoryItem> rows = inventoryItemRepository.findAll(specification, sort);

        // Pre：收集所有候选行 ID 用于查询锁定状态。
        List<Long> ids = new ArrayList<>();
        for (InventoryItem row : rows) {
            if (row.getId() != null) {
                ids.add(row.getId());
            }
        }
        // 业务分支处理：无候选行时直接返回。
        if (ids.isEmpty()) {
            return rows;
        }

        // Pre：查询所有锁定记录。
        List<InventoryLock> locks = inventoryLockRepository.findByInventoryIdInAndLockFlagTrue(ids);
        Set<Long> lockedIds = new HashSet<>();
        for (InventoryLock lock : locks) {
            if (lock.getInventoryId() != null) {
                lockedIds.add(lock.getInventoryId());
            }
        }

        // 业务分支处理：无锁定记录时直接返回全部候选行。
        if (lockedIds.isEmpty()) {
            return rows;
        }

        // Asm：过滤掉已锁定的候选行。
        List<InventoryItem> unlocked = new ArrayList<>();
        for (InventoryItem row : rows) {
            // 业务分支处理：ID 为空的跳过。
            if (row.getId() == null) {
                continue;
            }
            // 业务分支处理：未锁定的加入结果列表。
            if (!lockedIds.contains(row.getId())) {
                unlocked.add(row);
            }
        }
        return unlocked;
    }

    /**
     * 新建前：同四元组是否已有未逻辑删除的占用行（与读侧 {@code findFirstIdByInventoryMatchKey} 四元组口径一致）。
     */
    private boolean existsNonDeletedInventoryByQuadruple(String lotNumber, Long locationId, String itemTable, Long itemId) {
        if (locationId == null || itemId == null || StringUtils.isBlank(itemTable)) {
            return false;
        }
        String lot;
        if (lotNumber == null) {
            // 无批号与空串批号在仓储四元组中等价。
            lot = "";
        } else {
            lot = lotNumber;
        }
        List<InventoryItem> rows = inventoryItemRepository.findByLotNumberAndLocationIdAndItemTableAndItemIdOrderByIdAsc(
            lot, locationId, itemTable.trim(), itemId);
        for (InventoryItem row : rows) {
            if (row != null && !Boolean.TRUE.equals(row.getIsDeleted())) {
                return true;
            }
        }
        return false;
    }
}
