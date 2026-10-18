/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.domain.administration.devicedefinition.service;

import static com.zxplat.framework.core.application.util.StringUtils.trimToEmpty;

import com.zxplat.domain.share.constants.AggregateTypeConstants;
import com.zxplat.domain.share.constants.BusinessSequenceConstants;
import com.zxplat.domain.share.constants.FieldNameConstants;
import com.zxplat.domain.administration.devicedefinition.command.*;
import com.zxplat.domain.administration.devicedefinition.entity.DeviceDefinition;
import com.zxplat.domain.administration.devicedefinition.repository.DeviceDefinitionRepository;
import com.zxplat.domain.share.constants.ErrorCodeConstants;
import com.zxplat.domain.share.events.administration.devicedefinition.DeviceDefinitionCreatedEvent;
import com.zxplat.domain.share.events.administration.devicedefinition.DeviceDefinitionStartEvent;
import com.zxplat.domain.share.events.administration.devicedefinition.DeviceDefinitionStopEvent;
import com.zxplat.domain.share.events.administration.devicedefinition.DeviceDefinitionUpdateEvent;
import com.zxplat.domain.share.utils.CommandBatchUtil;
import com.zxplat.domain.share.utils.SearchCodeUtil;
import com.zxplat.framework.core.application.exception.BusinessException;
import com.zxplat.framework.core.domain.event.IDomainEventPublisher;
import com.zxplat.framework.core.infrastructure.sequence.BusinessSequenceService;
import lombok.RequiredArgsConstructor;
import com.zxplat.framework.core.application.util.StringUtils;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.*;

/**
 * 器材定义（DeviceDefinition）聚合写侧领域服务：创建与批量更新、逻辑删除、停用/启用、目录导入及医保对码同步。
 */
@Service
@RequiredArgsConstructor
public class DeviceDefinitionDomainService {
    private final DeviceDefinitionRepository repository;
    private final IDomainEventPublisher domainEventPublisher;
    private final BusinessSequenceService businessSequenceService;

    /** 采番撞号时单条最大重试次数（序号落后或并发占用）。 */
    private static final int BUS_NO_ALLOCATE_MAX_ATTEMPTS = 32;


    /**
     * 批量创建器材定义：校验 busNo 唯一、聚合可创建，自动生成缺失业务号并发创建事件。
     *
     * @param cmd 创建命令（器材明细列表）
     * @return 首条落库记录主键
     */
    public Long createDeviceDefinition(CreateDeviceDefinitionCommand cmd) {
        List<CreateDeviceDefinitionCommand.Item> items = cmd.resolveItems();
        // Gate：批量创建入参不能为空，否则无法生成任何器材主数据。
        if (items.isEmpty()) {
            throw new BusinessException(ErrorCodeConstants.DEVICE_DEFINITION_INVALID_ARGUMENT, "创建器材定义入参不能为空");
        }
        List<DeviceDefinition> toSave = new ArrayList<>();
        for (CreateDeviceDefinitionCommand.Item item : items) {
            DeviceDefinition row = new DeviceDefinition();
            row.setBusNo(this.allocateUniqueBusNo());
            row.setName(item.getName());
            row.setMerchandiseName(item.getMerchandiseName());
            row.setCategoryCode(item.getCategoryCode());
            row.setTypeCode(item.getTypeCode());
            row.setPurchaseUnitCode(item.getUnitCode());
            row.setTotalVolume(item.getTotalVolume());
            row.setSplitUnitCode(item.getMinUnitCode());
            row.setPackageUnitCode(item.getPackageUnitCode());
            row.setStockUnitCode(item.getStockUnitCode());
            row.setDoseUnitCode(item.getDoseUnitCode());

row.setDoseUnitRatio(item.getDoseUnitRatio());
            row.setPurchaseUnitRatio(item.getPurchaseUnitRatio());
            row.setStockUnitRatio(item.getStockUnitRatio());
            row.setPackageUnitRatio(item.getPackageUnitRatio());
            row.setTraceUnitCode(item.getTraceUnitCode());
            row.setTraceUnitRatio(item.getTraceUnitRatio());
            row.setOrgId(item.getOrgId());
            row.setLocationId(item.getLocationId());
            row.setModelNumber(item.getModelNumber());
            row.setHvcmFlag(item.getHvcmFlag() != null && item.getHvcmFlag() == 1);
            row.setChargeableFlag(item.getChargeableFlag() != null && item.getChargeableFlag() == 1);
            row.setMrSettlementTypeCode(item.getMrSettlementTypeCode());
            row.setApprovalNumber(item.getApprovalNumber());
            row.setYbFlag(item.getYbFlag() != null && item.getYbFlag() == 1);
            row.setYbNo(item.getYbNo());
            row.setYbMatchFlag(item.getYbMatchFlag() != null && item.getYbMatchFlag() == 1);
            row.setChrgitmLv(item.getChrgitmLv());
            row.setSubstanceText(item.getSubstanceText());
            row.setManufacturerId(item.getManufacturerId());
            row.setSupplyId(item.getSupplyId());
            row.setAllergenFlag(item.getAllergenFlag() != null && item.getAllergenFlag() == 1);
            row.setJurisdiction(item.getJurisdiction());
            row.setItemMinQuantity(item.getItemMinQuantity());
            row.setItemMaxQuantity(item.getItemMaxQuantity());
            row.setDescription(item.getDescription());
            row.setDefEncounterUnitCode(item.getDefEncounterUnitCode());
            row.setDefInhospitalUnitCode(item.getDefInhospitalUnitCode());
            row.refreshMerchandiseSearchCodes();

            // 聚合内前置校验：必填字段。
            row.assertCreatable();

            // 初始化为启用状态；拼音/五笔优先采用请求值，否则按名称生成（BR-DEVDEF-002）。
            row.initAsActive();
            DeviceDefinitionDomainService.applyCreateNameSearchCodes(row, item.getPyStr(), item.getWbStr());
            toSave.add(row);
        }
        List<DeviceDefinition> saved = repository.saveAll(toSave);
        for (DeviceDefinition row : saved) {
            domainEventPublisher.publish(new DeviceDefinitionCreatedEvent(row.getId(), row.getBusNo(), row.getName(), row.getStatusEnum()));
        }
        return saved.get(0).getId();
    }

    /**
     * 批量更新器材定义（受业务可编辑码门控），名称/检索码规则与创建一致，逐条发布更新事件。
     *
     * @param cmd 更新命令（含变更字段清单）
     */
    public void updateDeviceDefinition(UpdateDeviceDefinitionCommand cmd) {
        List<UpdateDeviceDefinitionCommand.Item> items = cmd.resolveItems();
        Map<Long, DeviceDefinition> map = this.loadMap(CommandBatchUtil.extractIds(items, UpdateDeviceDefinitionCommand.Item::getId));
        List<DeviceDefinition> toSave = new ArrayList<>();
        for (UpdateDeviceDefinitionCommand.Item item : cmd.resolveItems()) {
            DeviceDefinition row = require(map, item.getId());
            // 业务码门控：由他域判定，本聚合按业务码执行（BR-DEVDEF-005/007）。
            row.assertUpdatableByBusinessCode(item.getEditabilityCode());

            // 记录名称变更，以决定是否重算检索码（BR-DEVDEF-002）。
            String originName = row.getName();

row.setName(item.getName());
            row.setMerchandiseName(item.getMerchandiseName());
            row.setCategoryCode(item.getCategoryCode());
            row.setTypeCode(item.getTypeCode());
            row.setPurchaseUnitCode(item.getUnitCode());
            row.setTotalVolume(item.getTotalVolume());
            row.setSplitUnitCode(item.getMinUnitCode());
            row.setPackageUnitCode(item.getPackageUnitCode());
            row.setStockUnitCode(item.getStockUnitCode());
            row.setDoseUnitCode(item.getDoseUnitCode());

row.setDoseUnitRatio(item.getDoseUnitRatio());
            row.setPurchaseUnitRatio(item.getPurchaseUnitRatio());
            row.setStockUnitRatio(item.getStockUnitRatio());
            row.setPackageUnitRatio(item.getPackageUnitRatio());
            row.setTraceUnitCode(item.getTraceUnitCode());
            row.setTraceUnitRatio(item.getTraceUnitRatio());
            row.setModelNumber(item.getModelNumber());
            row.setHvcmFlag(item.getHvcmFlag() != null && item.getHvcmFlag() == 1);
            row.setChargeableFlag(item.getChargeableFlag() != null && item.getChargeableFlag() == 1);
            row.setMrSettlementTypeCode(item.getMrSettlementTypeCode());
            row.setApprovalNumber(item.getApprovalNumber());
            row.setOrgId(item.getOrgId());
            row.setLocationId(item.getLocationId());
            row.setYbFlag(item.getYbFlag() != null && item.getYbFlag() == 1);
            row.setYbNo(item.getYbNo());
            row.setYbMatchFlag(item.getYbMatchFlag() != null && item.getYbMatchFlag() == 1);
            row.setChrgitmLv(item.getChrgitmLv());
            row.setSubstanceText(item.getSubstanceText());
            row.setManufacturerId(item.getManufacturerId());
            row.setSupplyId(item.getSupplyId());
            row.setAllergenFlag(item.getAllergenFlag() != null && item.getAllergenFlag() == 1);
            row.setJurisdiction(item.getJurisdiction());
            row.setItemMinQuantity(item.getItemMinQuantity());
            row.setItemMaxQuantity(item.getItemMaxQuantity());
            row.setDescription(item.getDescription());
            row.setDefEncounterUnitCode(item.getDefEncounterUnitCode());
            row.setDefInhospitalUnitCode(item.getDefInhospitalUnitCode());
            row.refreshMerchandiseSearchCodes();

            boolean nameChanged = !Objects.equals(originName, row.getName());
            boolean pyProvided = item.getPyStr() != null;
            boolean wbProvided = item.getWbStr() != null;
            if (nameChanged) {
                if (!pyProvided && !wbProvided) {
                    row.refreshSearchCodeOnNameChanged();
                } else {
                    if (pyProvided) {
                        row.setPyStr(trimToEmpty(item.getPyStr()));
                    } else {
                        row.setPyStr(SearchCodeUtil.toPyStr(row.getName()));
                    }
                    if (wbProvided) {
                        row.setWbStr(trimToEmpty(item.getWbStr()));
                    } else {
                        row.setWbStr(SearchCodeUtil.toWbStr(row.getName()));
                    }
                }
            } else {
                if (pyProvided) {
                    row.setPyStr(trimToEmpty(item.getPyStr()));
                }
                if (wbProvided) {
                    row.setWbStr(trimToEmpty(item.getWbStr()));
                }
            }

            toSave.add(row);
        }
        repository.saveAll(toSave);
        for (UpdateDeviceDefinitionCommand.Item item : items) {
            DeviceDefinition row = require(map, item.getId());
            domainEventPublisher.publish(new DeviceDefinitionUpdateEvent(row.getId(), item.getChangedFields()));
        }
    }

    /**
     * deleteDeviceDefinitions（逻辑删除）。
     *
     * @param cmd 删除命令
     */
    public void deleteDeviceDefinitions(DeleteDeviceDefinitionsCommand cmd) {
        List<DeleteDeviceDefinitionsCommand.Item> items = cmd.resolveItems();
        List<Long> ids = CommandBatchUtil.extractIds(items, DeleteDeviceDefinitionsCommand.Item::getId);
        if (ids.isEmpty()) {
            throw new BusinessException(ErrorCodeConstants.DEVICE_DEFINITION_INVALID_ARGUMENT, "删除器材定义入参不能为空");
        }
        Map<Long, DeviceDefinition> map = this.loadMap(ids);
        List<DeviceDefinition> toSave = new ArrayList<>();
        for (Long id : ids) {
            DeviceDefinition row = require(map, id);
            row.setIsDeleted(Boolean.TRUE);
            row.setDeletedAt(LocalDateTime.now());
            row.setDeletedBy(cmd.getOperatorId());
            toSave.add(row);
        }
        repository.saveAll(toSave);
        // 事件：复用停用/删除事件体系不完整时，先不额外发布新事件，避免破坏既有订阅方。
    }

    /**
     * 批量停用（退役）器材定义，状态迁移后发布停用事件。
     *
     * @param cmd 停用命令（目标主键列表）
     */
    public void retireDeviceDefinitions(RetireDeviceDefinitionsCommand cmd) {
        List<RetireDeviceDefinitionsCommand.Item> items = cmd.resolveItems();
        Map<Long, DeviceDefinition> map = this.loadMap(CommandBatchUtil.extractIds(items, RetireDeviceDefinitionsCommand.Item::getId));
        List<DeviceDefinition> toSave = new ArrayList<>();
        for (RetireDeviceDefinitionsCommand.Item item : items) {
            DeviceDefinition row = require(map, item.getId());
            row.transitToInactive();
            toSave.add(row);
        }
        repository.saveAll(toSave);
        for (RetireDeviceDefinitionsCommand.Item item : items) {
            DeviceDefinition row = require(map, item.getId());
            domainEventPublisher.publish(new DeviceDefinitionStopEvent(row.getId(), row.getStatusEnum()));
        }
    }

    /**
     * 批量重新启用器材定义，落库后发布启用事件。
     *
     * @param cmd 启用命令（目标主键列表）
     */
    public void activateDeviceDefinitions(ActivateDeviceDefinitionsCommand cmd) {
        List<ActivateDeviceDefinitionsCommand.Item> items = cmd.resolveItems();
        Map<Long, DeviceDefinition> map = this.loadMap(CommandBatchUtil.extractIds(items, ActivateDeviceDefinitionsCommand.Item::getId));
        List<DeviceDefinition> toSave = new ArrayList<>();
        for (ActivateDeviceDefinitionsCommand.Item item : items) {
            DeviceDefinition row = require(map, item.getId());
            row.transitToActive();
            toSave.add(row);
        }
        repository.saveAll(toSave);
        for (ActivateDeviceDefinitionsCommand.Item item : items) {
            DeviceDefinition row = require(map, item.getId());
            domainEventPublisher.publish(new DeviceDefinitionStartEvent(row.getId(), row.getStatusEnum()));
        }
    }

    /**
     * 导入器材目录：按 busNo 去重与库内存在性分流新增或覆盖更新，收集行级失败并汇总结果。
     *
     * @param cmd 导入命令（行数据、是否覆盖等）
     * @return 创建数、更新数、失败明细
     */
    public ImportDeviceDefinitionCatalogResult importDeviceDefinitionCatalog(ImportDeviceDefinitionCatalogCommand cmd) {
        List<ImportDeviceDefinitionCatalogCommand.Item> items = cmd.resolveItems();
        // Gate：导入目录行不能为空。
        if (items.isEmpty()) {
            throw new BusinessException(ErrorCodeConstants.DEVICE_DEFINITION_INVALID_ARGUMENT, "导入器材目录入参不能为空");
        }

        Boolean overwrite = cmd.getOverwrite();
        // 未传覆盖标志时默认不覆盖已存在记录。
        if (overwrite == null) {
            overwrite = Boolean.FALSE;
        }

        // 导入内存去重：以 busNo 为业务唯一键（BR-DEVDEF-001）。
        Map<String, Integer> firstLineByBusNo = new HashMap<>();
        List<String> busNos = new ArrayList<>();
        for (int idx = 0; idx < items.size(); idx++) {
            ImportDeviceDefinitionCatalogCommand.Item item = items.get(idx);
            int lineNo = idx + 1;
            // Gate：导入行对象不能为空。
            if (item == null) {
                throw new BusinessException(ErrorCodeConstants.DEVICE_DEFINITION_INVALID_ARGUMENT, "导入器材目录时存在空行数据");
            }
            // Gate：业务编码 busNo 为导入去重与匹配键，不能为空。
            if (item.getBusNo() == null || item.getBusNo().trim().isEmpty()) {
                throw new BusinessException(ErrorCodeConstants.DEVICE_DEFINITION_INVALID_ARGUMENT, "导入器材目录时 busNo 不能为空");
            }
            String normalized = item.getBusNo().trim();
            Integer first = firstLineByBusNo.get(normalized);
            // Gate：同一导入批次内 busNo 重复则整批拒绝。
            if (first != null) {
                throw new BusinessException(ErrorCodeConstants.DEVICE_DEFINITION_INVALID_ARGUMENT,
                    "导入器材目录时 busNo 重复，重复值=" + normalized + "，首次行=" + first);
            }
            firstLineByBusNo.put(normalized, lineNo);
            busNos.add(normalized);
        }

        // 批量下推数据库校验：一次性加载已存在记录，避免循环单条查询（批量 I/O 门禁）。
        List<DeviceDefinition> existed = repository.findByBusNoIn(busNos);
        Map<String, DeviceDefinition> existedByBusNo = new HashMap<>();
        for (DeviceDefinition row : existed) {
            existedByBusNo.put(row.getBusNo(), row);
        }

        List<DeviceDefinition> toSave = new ArrayList<>();
        Set<String> createdBusNos = new HashSet<>();
        Set<String> updatedBusNos = new HashSet<>();
        List<ImportDeviceDefinitionCatalogResult.ImportFailure> failures = new ArrayList<>();

        for (int idx = 0; idx < items.size(); idx++) {
            ImportDeviceDefinitionCatalogCommand.Item item = items.get(idx);
            int lineNo = idx + 1;
            try {
                String busNo = item.getBusNo().trim();
                DeviceDefinition target = existedByBusNo.get(busNo);

                // 库内不存在同 busNo 记录时走新增路径。
                if (target == null) {
                    // 新增路径：初始化为启用；业务编码由领域层采番（冲突自动跳号），忽略导入行 busNo（B.10.9）。
                    DeviceDefinition created = new DeviceDefinition();
                    String generatedBusNo = this.allocateUniqueBusNo();
                    created.setBusNo(generatedBusNo);
                    created.setName(item.getName());
                    created.setMerchandiseName(item.getName());
                    created.setCategoryCode(item.getCategoryCode());
                    created.setTypeCode(item.getTypeCode());
                    created.setPurchaseUnitCode(item.getUnitCode());
                    created.setTotalVolume(item.getTotalVolume());
                    created.setStockUnitCode(DeviceDefinitionDomainService.trimStockUnitCode(item.getStockUnitCode()));
                    created.setOrgId(item.getOrgId());
                    created.setLocationId(item.getLocationId());
                    created.setYbFlag(item.getYbFlag());
                    created.setYbNo(item.getYbNo());
                                        created.setYbMatchFlag(item.getYbMatchFlag());
                    created.setChrgitmLv(item.getChrgitmLv());
                    created.setDescription(item.getDescription());

                    created.assertImportItemValid();
                    created.initAsActive();
                    created.refreshSearchCodeOnNameChanged();
                    created.refreshMerchandiseSearchCodes();
                    toSave.add(created);
                    createdBusNos.add(generatedBusNo);
                } else {
                    // 已存在：由 overwrite 决定是否覆盖更新。
                    if (!Boolean.TRUE.equals(overwrite)) {
                        failures.add(new ImportDeviceDefinitionCatalogResult.ImportFailure(lineNo, "业务编码已存在且不允许覆盖更新，busNo=" + busNo));
                        continue;
                    }
                    target.setName(item.getName());
                    target.setMerchandiseName(item.getName());
                    target.setCategoryCode(item.getCategoryCode());
                    target.setTypeCode(item.getTypeCode());
                    target.setPurchaseUnitCode(item.getUnitCode());
                    target.setTotalVolume(item.getTotalVolume());
                    target.setStockUnitCode(DeviceDefinitionDomainService.trimStockUnitCode(item.getStockUnitCode()));
                    target.setOrgId(item.getOrgId());
                    target.setLocationId(item.getLocationId());
                    target.setYbFlag(item.getYbFlag());
                    target.setYbNo(item.getYbNo());
                                        target.setYbMatchFlag(item.getYbMatchFlag());
                    target.setChrgitmLv(item.getChrgitmLv());
                    target.setDescription(item.getDescription());

                    target.assertImportItemValid();
                    target.refreshSearchCodeOnNameChanged();
                    target.refreshMerchandiseSearchCodes();
                    toSave.add(target);
                    updatedBusNos.add(busNo);
                }
            } catch (BusinessException ex) {
                failures.add(new ImportDeviceDefinitionCatalogResult.ImportFailure(lineNo, ex.getMessage()));
            }
        }

        // 先落库后发事件，避免幽灵事件。
        List<DeviceDefinition> saved = repository.saveAll(toSave);
        for (DeviceDefinition row : saved) {
            String busNo = row.getBusNo();
            // 本次导入新建的记录发布创建事件。
            if (createdBusNos.contains(busNo)) {
                domainEventPublisher.publish(new DeviceDefinitionCreatedEvent(row.getId(), row.getBusNo(), row.getName(), row.getStatusEnum()));
                continue;
            }
            // 本次导入覆盖更新的记录发布更新事件。
            if (updatedBusNos.contains(busNo)) {
                List<String> changed = new ArrayList<>();
                changed.add(FieldNameConstants.NAME);
                changed.add(FieldNameConstants.CATEGORY_CODE);
                changed.add(FieldNameConstants.TYPE_CODE);
                changed.add(FieldNameConstants.PURCHASE_UNIT_CODE);
                changed.add(FieldNameConstants.TOTAL_VOLUME);
                changed.add(FieldNameConstants.STOCK_UNIT_CODE);
                changed.add(FieldNameConstants.ORG_ID);
                changed.add(FieldNameConstants.LOCATION_ID);
                changed.add(FieldNameConstants.YB_FLAG);
                changed.add(FieldNameConstants.YB_NO);
                    changed.add(FieldNameConstants.YB_MATCH_FLAG);
                changed.add(FieldNameConstants.CHRGITM_LV);
                changed.add("description");
                domainEventPublisher.publish(new DeviceDefinitionUpdateEvent(row.getId(), changed));
            }
        }

        ImportDeviceDefinitionCatalogResult result = new ImportDeviceDefinitionCatalogResult();
        result.setCreatedCount(createdBusNos.size());
        result.setUpdatedCount(updatedBusNos.size());
        result.setFailureCount((long) failures.size());
        result.setFailures(failures);
        return result;
    }

    /**
     * 批量回写医保对码相关字段，落库后发布更新事件供下游投影/同步。
     *
     * @param cmd 医保映射同步命令（目标器材与医保字段）
     */
    public void syncDeviceDefinitionYbMapping(SyncDeviceDefinitionYbMappingCommand cmd) {
        List<SyncDeviceDefinitionYbMappingCommand.Item> items = cmd.resolveItems();
        Map<Long, DeviceDefinition> map = this.loadMap(CommandBatchUtil.extractIds(items, SyncDeviceDefinitionYbMappingCommand.Item::getId));
        List<DeviceDefinition> toSave = new ArrayList<>();
        for (SyncDeviceDefinitionYbMappingCommand.Item item : items) {
            DeviceDefinition row = require(map, item.getId());
            // 医保回写：按 SDD 字段全集更新；保留 ybMappingCode 兼容存量。
            row.setYbFlag(item.getYbFlag());
            row.setYbNo(item.getYbNo());
            row.setYbMatchFlag(item.getYbMatchFlag());
            row.setChrgitmLv(item.getChrgitmLv());
            row.setYbMappingCode(item.getYbMappingCode());

            toSave.add(row);
        }
        // 先落库后发事件（DM-ADM-008 §5 要求同步医保对码后通知下游）。
        List<DeviceDefinition> saved = repository.saveAll(toSave);
        for (DeviceDefinition row : saved) {
            List<String> changed = new ArrayList<>();
            changed.add(FieldNameConstants.YB_FLAG);
            changed.add(FieldNameConstants.YB_NO);
            changed.add(FieldNameConstants.YB_MATCH_FLAG);
            changed.add(FieldNameConstants.CHRGITM_LV);
            changed.add("ybMappingCode");
            domainEventPublisher.publish(new DeviceDefinitionUpdateEvent(row.getId(), changed));
        }
    }

    /**
     * 按主键批量加载器材定义。
     */
    private Map<Long, DeviceDefinition> loadMap(List<Long> ids) {
        List<DeviceDefinition> list = repository.findAllByIdIn(ids);
        Map<Long, DeviceDefinition> map = new HashMap<>();
        for (DeviceDefinition row : list) {
            map.put(row.getId(), row);
        }
        return map;
    }

    /**
     * 从预加载映射取器材定义，缺失则抛业务异常。
     */
    private DeviceDefinition require(Map<Long, DeviceDefinition> map, Long id) {
        DeviceDefinition row = map.get(id);
        // 预加载映射未命中则目标器材定义不存在。
        if (row == null) {
            throw new BusinessException(ErrorCodeConstants.DEVICE_DEFINITION_NOT_FOUND, "器材定义不存在");
        }
        return row;
    }

    /** 导入库存单位：空白视为未携带，落 {@code null}。 */
    private static String trimStockUnitCode(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        return raw.trim();
    }

    /**
     * 创建时写入名称检索码：请求未携带则按名称自动生成；仅携带其一则另一侧补全。
     */
    private static void applyCreateNameSearchCodes(DeviceDefinition row, String pyStr, String wbStr) {
        boolean pyBlank = StringUtils.isEmpty(pyStr);
        boolean wbBlank = StringUtils.isEmpty(wbStr);
        if (pyBlank && wbBlank) {
            row.refreshSearchCodeOnNameChanged();
            return;
        }
        if (!pyBlank) {
            row.setPyStr(pyStr.trim());
        } else {
            row.setPyStr(SearchCodeUtil.toPyStr(row.getName()));
        }
        if (!wbBlank) {
            row.setWbStr(wbStr.trim());
        } else {
            row.setWbStr(SearchCodeUtil.toWbStr(row.getName()));
        }
    }

    /**
     * 采番一条未被占用的器材业务编码；冲突则继续取下一号。
     *
     * @return 未占用的业务编码
     */
    private String allocateUniqueBusNo() {
        for (int attempt = 0; attempt < BUS_NO_ALLOCATE_MAX_ATTEMPTS; attempt++) {
            String busNo = this.generateBusNo();
            if (!repository.existsByBusNo(busNo)) {
                return busNo;
            }
        }
        throw new BusinessException(ErrorCodeConstants.DEVICE_DEFINITION_RULE_CONFLICT, "器材业务编码采番冲突，请稍后重试");
    }

    /**
     * 基于框架业务序列生成器生成器材业务编码。
     */
    private String generateBusNo() {
        return businessSequenceService.getSeq(
            AggregateTypeConstants.DEVICE_DEFINITION, BusinessSequenceConstants.CATALOG_DEFINITION_BUS_NO_SEQ_LENGTH);
    }
}
