/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.whale.crb.domain.dict;

import com.openhis.whale.crb.domain.dict.command.ImportDeptCodeMapsCommand;
import com.openhis.whale.crb.domain.dict.command.ImportDrugCodeMapsCommand;
import com.openhis.whale.crb.domain.dict.command.ImportLabItemMapsCommand;
import com.openhis.whale.crb.domain.dict.command.ImportResult;
import com.openhis.whale.crb.domain.dict.command.SoftDeleteMapCommand;
import com.openhis.whale.crb.domain.dict.command.UpsertDeptCodeMapCommand;
import com.openhis.whale.crb.domain.dict.command.UpsertDrugCodeMapCommand;
import com.openhis.whale.crb.domain.dict.command.UpsertLabItemMapCommand;
import com.openhis.whale.crb.domain.share.events.dict.CrbDeptCodeMapChangedEvent;
import com.openhis.whale.crb.domain.share.events.dict.CrbDrugCodeMapChangedEvent;
import com.openhis.whale.crb.domain.share.events.dict.CrbLabItemMapChangedEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static com.openhis.whale.core.application.util.StringUtils.trimToNull;

/**
 * 编码对照写侧门面（科室 / 检验 / 药品三表 upsert、导入、软删）。
 */
@Service
@RequiredArgsConstructor
public class CrbCodeMapDomainService {

    private final CrbDeptCodeMapRepository deptCodeMapRepository;
    private final CrbLabItemMapRepository labItemMapRepository;
    private final CrbDrugCodeMapRepository drugCodeMapRepository;
    private final ApplicationEventPublisher applicationEventPublisher;

    /**
     * 科室对照单行 upsert。
     *
     * @param cmd upsert 命令
     * @return 持久化后的对照行
     */
    @Transactional
    public CrbDeptCodeMap upsertDeptCodeMap(UpsertDeptCodeMapCommand cmd) {
        // 校验 → 持久化 → 事件
        validateUpsertDeptCommand(cmd);
        String locationBusNo = trimToNull(cmd.getLocationBusNo());
        String targetDeptCode = trimToNull(cmd.getTargetDeptCode());
        String targetDeptName = trimToNull(cmd.getTargetDeptName());
        CrbDeptCodeMap.assertCodeNamePair(targetDeptCode, targetDeptName);

        CrbDeptCodeMap entity = resolveDeptEntity(cmd.getTenantId(), cmd.getId(), locationBusNo);
        assertDeptBusNoUnique(cmd.getTenantId(), cmd.getId(), locationBusNo, entity.getId());

        entity.setLocationBusNo(locationBusNo);
        entity.setLocationName(trimToNull(cmd.getLocationName()));
        entity.setTargetDeptCode(targetDeptCode);
        entity.setTargetDeptName(targetDeptName);
        entity.setIsDeleted(false);
        entity.setUpdatedAt(LocalDateTime.now());
        if (entity.getCreatedAt() == null) {
            entity.setCreatedAt(LocalDateTime.now());
        }

        CrbDeptCodeMap saved = deptCodeMapRepository.save(entity);
        applicationEventPublisher.publishEvent(
                new CrbDeptCodeMapChangedEvent(saved.getId(), saved.getLocationBusNo()));
        return saved;
    }

    /**
     * 科室对照批量导入。
     *
     * @param cmd 导入命令
     * @return 汇总结果
     */
    @Transactional
    public ImportResult importDeptCodeMaps(ImportDeptCodeMapsCommand cmd) {
        // 循环校验 → upsert → 汇总
        ImportResult result = new ImportResult();
        if (cmd == null || cmd.getTenantId() == null) {
            throw new IllegalArgumentException(CrbDictConstants.MSG_TENANT_OR_BUS_NO_REQUIRED);
        }
        List<UpsertDeptCodeMapCommand> rows = cmd.getRows();
        if (rows == null || rows.isEmpty()) {
            throw new IllegalArgumentException(CrbDictConstants.MSG_IMPORT_ROWS_REQUIRED);
        }
        for (UpsertDeptCodeMapCommand row : rows) {
            importOneDeptRow(cmd.getTenantId(), row, result);
        }
        return result;
    }

    /**
     * 科室对照软删。
     *
     * @param cmd 软删命令
     */
    @Transactional
    public void softDeleteDeptCodeMap(SoftDeleteMapCommand cmd) {
        // 加载 → 软删 → 事件
        CrbDeptCodeMap entity = loadActiveDeptMap(cmd);
        entity.setIsDeleted(true);
        entity.setUpdatedAt(LocalDateTime.now());
        deptCodeMapRepository.save(entity);
        applicationEventPublisher.publishEvent(
                new CrbDeptCodeMapChangedEvent(entity.getId(), entity.getLocationBusNo()));
    }

    /**
     * 检验项目对照单行 upsert。
     *
     * @param cmd upsert 命令
     * @return 持久化后的对照行
     */
    @Transactional
    public CrbLabItemMap upsertLabItemMap(UpsertLabItemMapCommand cmd) {
        // 校验 → 持久化 → 事件
        validateUpsertLabCommand(cmd);
        String itemBusNo = trimToNull(cmd.getItemBusNo());
        String resultShape = trimToNull(cmd.getResultShape());
        String annex2Scope = trimToNull(cmd.getAnnex2Scope());
        CrbLabItemMap.assertResultShapeValid(resultShape);
        boolean pushEnabled = CrbLabItemMap.resolvePushEnabled(annex2Scope, cmd.getPushEnabled());

        CrbLabItemMap entity = resolveLabEntity(cmd.getTenantId(), cmd.getId(), itemBusNo);
        assertLabBusNoUnique(cmd.getTenantId(), cmd.getId(), itemBusNo, entity.getId());

        entity.setItemBusNo(itemBusNo);
        entity.setItemName(trimToNull(cmd.getItemName()));
        entity.setNationalItemCode(trimToNull(cmd.getNationalItemCode()));
        entity.setResultShape(resultShape);
        entity.setAnnex2Scope(annex2Scope);
        entity.setPushEnabled(pushEnabled);
        entity.setIsDeleted(false);
        entity.setUpdatedAt(LocalDateTime.now());
        if (entity.getCreatedAt() == null) {
            entity.setCreatedAt(LocalDateTime.now());
        }

        CrbLabItemMap saved = labItemMapRepository.save(entity);
        applicationEventPublisher.publishEvent(
                new CrbLabItemMapChangedEvent(saved.getId(), saved.getItemBusNo()));
        return saved;
    }

    /**
     * 检验项目对照批量导入。
     *
     * @param cmd 导入命令
     * @return 汇总结果
     */
    @Transactional
    public ImportResult importLabItemMaps(ImportLabItemMapsCommand cmd) {
        // 循环 → 汇总
        ImportResult result = new ImportResult();
        if (cmd == null || cmd.getTenantId() == null) {
            throw new IllegalArgumentException(CrbDictConstants.MSG_TENANT_OR_BUS_NO_REQUIRED);
        }
        List<UpsertLabItemMapCommand> rows = cmd.getRows();
        if (rows == null || rows.isEmpty()) {
            throw new IllegalArgumentException(CrbDictConstants.MSG_IMPORT_ROWS_REQUIRED);
        }
        for (UpsertLabItemMapCommand row : rows) {
            importOneLabRow(cmd.getTenantId(), row, result);
        }
        return result;
    }

    /**
     * 检验项目对照软删。
     *
     * @param cmd 软删命令
     */
    @Transactional
    public void softDeleteLabItemMap(SoftDeleteMapCommand cmd) {
        // 加载 → 软删 → 事件
        CrbLabItemMap entity = loadActiveLabMap(cmd);
        entity.setIsDeleted(true);
        entity.setUpdatedAt(LocalDateTime.now());
        labItemMapRepository.save(entity);
        applicationEventPublisher.publishEvent(
                new CrbLabItemMapChangedEvent(entity.getId(), entity.getItemBusNo()));
    }

    /**
     * 药品对照单行 upsert。
     *
     * @param cmd upsert 命令
     * @return 持久化后的对照行
     */
    @Transactional
    public CrbDrugCodeMap upsertDrugCodeMap(UpsertDrugCodeMapCommand cmd) {
        // 校验 → 持久化 → 事件
        validateUpsertDrugCommand(cmd);
        String medBusNo = trimToNull(cmd.getMedBusNo());
        String nationalDrugCode = trimToNull(cmd.getNationalDrugCode());
        String nationalDrugName = trimToNull(cmd.getNationalDrugName());
        CrbDrugCodeMap.assertCodeNamePair(nationalDrugCode, nationalDrugName);

        CrbDrugCodeMap entity = resolveDrugEntity(cmd.getTenantId(), cmd.getId(), medBusNo);
        assertDrugBusNoUnique(cmd.getTenantId(), cmd.getId(), medBusNo, entity.getId());

        entity.setMedBusNo(medBusNo);
        entity.setMedName(trimToNull(cmd.getMedName()));
        entity.setNationalDrugCode(nationalDrugCode);
        entity.setNationalDrugName(nationalDrugName);
        entity.setIsDeleted(false);
        entity.setUpdatedAt(LocalDateTime.now());
        if (entity.getCreatedAt() == null) {
            entity.setCreatedAt(LocalDateTime.now());
        }

        CrbDrugCodeMap saved = drugCodeMapRepository.save(entity);
        applicationEventPublisher.publishEvent(
                new CrbDrugCodeMapChangedEvent(saved.getId(), saved.getMedBusNo()));
        return saved;
    }

    /**
     * 药品对照批量导入。
     *
     * @param cmd 导入命令
     * @return 汇总结果
     */
    @Transactional
    public ImportResult importDrugCodeMaps(ImportDrugCodeMapsCommand cmd) {
        // 循环 → 汇总
        ImportResult result = new ImportResult();
        if (cmd == null || cmd.getTenantId() == null) {
            throw new IllegalArgumentException(CrbDictConstants.MSG_TENANT_OR_BUS_NO_REQUIRED);
        }
        List<UpsertDrugCodeMapCommand> rows = cmd.getRows();
        if (rows == null || rows.isEmpty()) {
            throw new IllegalArgumentException(CrbDictConstants.MSG_IMPORT_ROWS_REQUIRED);
        }
        for (UpsertDrugCodeMapCommand row : rows) {
            importOneDrugRow(cmd.getTenantId(), row, result);
        }
        return result;
    }

    /**
     * 药品对照软删。
     *
     * @param cmd 软删命令
     */
    @Transactional
    public void softDeleteDrugCodeMap(SoftDeleteMapCommand cmd) {
        // 加载 → 软删 → 事件
        CrbDrugCodeMap entity = loadActiveDrugMap(cmd);
        entity.setIsDeleted(true);
        entity.setUpdatedAt(LocalDateTime.now());
        drugCodeMapRepository.save(entity);
        applicationEventPublisher.publishEvent(
                new CrbDrugCodeMapChangedEvent(entity.getId(), entity.getMedBusNo()));
    }

    /**
     * 校验科室 upsert 命令必填项。
     *
     * @param cmd upsert 命令
     */
    private void validateUpsertDeptCommand(UpsertDeptCodeMapCommand cmd) {
        if (cmd == null || cmd.getTenantId() == null || trimToNull(cmd.getLocationBusNo()) == null) {
            throw new IllegalArgumentException(CrbDictConstants.MSG_TENANT_OR_BUS_NO_REQUIRED);
        }
    }

    /**
     * 校验检验 upsert 命令必填项。
     *
     * @param cmd upsert 命令
     */
    private void validateUpsertLabCommand(UpsertLabItemMapCommand cmd) {
        if (cmd == null || cmd.getTenantId() == null || trimToNull(cmd.getItemBusNo()) == null) {
            throw new IllegalArgumentException(CrbDictConstants.MSG_TENANT_OR_BUS_NO_REQUIRED);
        }
    }

    /**
     * 校验药品 upsert 命令必填项。
     *
     * @param cmd upsert 命令
     */
    private void validateUpsertDrugCommand(UpsertDrugCodeMapCommand cmd) {
        if (cmd == null || cmd.getTenantId() == null || trimToNull(cmd.getMedBusNo()) == null) {
            throw new IllegalArgumentException(CrbDictConstants.MSG_TENANT_OR_BUS_NO_REQUIRED);
        }
    }

    /**
     * 解析科室 upsert 目标实体（按 id 或 busNo，支持软删复活）。
     *
     * @param tenantId 租户 id
     * @param id 主键，可空
     * @param locationBusNo 院内科室业务码
     * @return 待更新实体
     */
    private CrbDeptCodeMap resolveDeptEntity(Long tenantId, Long id, String locationBusNo) {
        if (id != null) {
            return deptCodeMapRepository.findByIdAndTenantId(id, tenantId)
                    .orElseThrow(() -> new IllegalArgumentException(CrbDictConstants.MSG_MAP_NOT_FOUND));
        }
        Optional<CrbDeptCodeMap> existing = deptCodeMapRepository.findByTenantIdAndLocationBusNo(
                tenantId, locationBusNo);
        if (existing.isPresent()) {
            return existing.get();
        }
        CrbDeptCodeMap created = new CrbDeptCodeMap();
        created.setTenantId(tenantId);
        created.setLocationBusNo(locationBusNo);
        created.setIsDeleted(false);
        created.setCreatedAt(LocalDateTime.now());
        return created;
    }

    /**
     * 解析检验 upsert 目标实体（按 id 或 busNo，支持软删复活）。
     *
     * @param tenantId 租户 id
     * @param id 主键，可空
     * @param itemBusNo 院内项目业务码
     * @return 待更新实体
     */
    private CrbLabItemMap resolveLabEntity(Long tenantId, Long id, String itemBusNo) {
        if (id != null) {
            return labItemMapRepository.findByIdAndTenantId(id, tenantId)
                    .orElseThrow(() -> new IllegalArgumentException(CrbDictConstants.MSG_MAP_NOT_FOUND));
        }
        Optional<CrbLabItemMap> existing = labItemMapRepository.findByTenantIdAndItemBusNo(tenantId, itemBusNo);
        if (existing.isPresent()) {
            return existing.get();
        }
        CrbLabItemMap created = new CrbLabItemMap();
        created.setTenantId(tenantId);
        created.setItemBusNo(itemBusNo);
        created.setPushEnabled(CrbDictConstants.DEFAULT_LAB_PUSH_ENABLED);
        created.setIsDeleted(false);
        created.setCreatedAt(LocalDateTime.now());
        return created;
    }

    /**
     * 解析药品 upsert 目标实体（按 id 或 busNo，支持软删复活）。
     *
     * @param tenantId 租户 id
     * @param id 主键，可空
     * @param medBusNo 院内药品业务码
     * @return 待更新实体
     */
    private CrbDrugCodeMap resolveDrugEntity(Long tenantId, Long id, String medBusNo) {
        if (id != null) {
            return drugCodeMapRepository.findByIdAndTenantId(id, tenantId)
                    .orElseThrow(() -> new IllegalArgumentException(CrbDictConstants.MSG_MAP_NOT_FOUND));
        }
        Optional<CrbDrugCodeMap> existing = drugCodeMapRepository.findByTenantIdAndMedBusNo(tenantId, medBusNo);
        if (existing.isPresent()) {
            return existing.get();
        }
        CrbDrugCodeMap created = new CrbDrugCodeMap();
        created.setTenantId(tenantId);
        created.setMedBusNo(medBusNo);
        created.setIsDeleted(false);
        created.setCreatedAt(LocalDateTime.now());
        return created;
    }

    /**
     * 校验科室业务码唯一（未删行不可冲突）。
     *
     * @param tenantId 租户 id
     * @param cmdId 命令主键
     * @param locationBusNo 院内科室业务码
     * @param entityId 当前实体主键
     */
    private void assertDeptBusNoUnique(Long tenantId, Long cmdId, String locationBusNo, Long entityId) {
        Optional<CrbDeptCodeMap> active = deptCodeMapRepository
                .findByTenantIdAndLocationBusNoAndIsDeletedFalse(tenantId, locationBusNo);
        if (active.isPresent() && !active.get().getId().equals(entityId)) {
            throw new IllegalStateException(CrbDictConstants.MSG_DUPLICATE_BUS_NO);
        }
    }

    /**
     * 校验检验业务码唯一（未删行不可冲突）。
     *
     * @param tenantId 租户 id
     * @param cmdId 命令主键
     * @param itemBusNo 院内项目业务码
     * @param entityId 当前实体主键
     */
    private void assertLabBusNoUnique(Long tenantId, Long cmdId, String itemBusNo, Long entityId) {
        Optional<CrbLabItemMap> active = labItemMapRepository
                .findByTenantIdAndItemBusNoAndIsDeletedFalse(tenantId, itemBusNo);
        if (active.isPresent() && !active.get().getId().equals(entityId)) {
            throw new IllegalStateException(CrbDictConstants.MSG_DUPLICATE_BUS_NO);
        }
    }

    /**
     * 校验药品业务码唯一（未删行不可冲突）。
     *
     * @param tenantId 租户 id
     * @param cmdId 命令主键
     * @param medBusNo 院内药品业务码
     * @param entityId 当前实体主键
     */
    private void assertDrugBusNoUnique(Long tenantId, Long cmdId, String medBusNo, Long entityId) {
        Optional<CrbDrugCodeMap> active = drugCodeMapRepository
                .findByTenantIdAndMedBusNoAndIsDeletedFalse(tenantId, medBusNo);
        if (active.isPresent() && !active.get().getId().equals(entityId)) {
            throw new IllegalStateException(CrbDictConstants.MSG_DUPLICATE_BUS_NO);
        }
    }

    /**
     * 加载未删除科室对照行。
     *
     * @param cmd 软删命令
     * @return 对照行
     */
    private CrbDeptCodeMap loadActiveDeptMap(SoftDeleteMapCommand cmd) {
        if (cmd == null || cmd.getTenantId() == null || cmd.getId() == null) {
            throw new IllegalArgumentException(CrbDictConstants.MSG_MAP_NOT_FOUND);
        }
        return deptCodeMapRepository.findByIdAndTenantIdAndIsDeletedFalse(cmd.getId(), cmd.getTenantId())
                .orElseThrow(() -> new IllegalArgumentException(CrbDictConstants.MSG_MAP_NOT_FOUND));
    }

    /**
     * 加载未删除检验对照行。
     *
     * @param cmd 软删命令
     * @return 对照行
     */
    private CrbLabItemMap loadActiveLabMap(SoftDeleteMapCommand cmd) {
        if (cmd == null || cmd.getTenantId() == null || cmd.getId() == null) {
            throw new IllegalArgumentException(CrbDictConstants.MSG_MAP_NOT_FOUND);
        }
        return labItemMapRepository.findByIdAndTenantIdAndIsDeletedFalse(cmd.getId(), cmd.getTenantId())
                .orElseThrow(() -> new IllegalArgumentException(CrbDictConstants.MSG_MAP_NOT_FOUND));
    }

    /**
     * 加载未删除药品对照行。
     *
     * @param cmd 软删命令
     * @return 对照行
     */
    private CrbDrugCodeMap loadActiveDrugMap(SoftDeleteMapCommand cmd) {
        if (cmd == null || cmd.getTenantId() == null || cmd.getId() == null) {
            throw new IllegalArgumentException(CrbDictConstants.MSG_MAP_NOT_FOUND);
        }
        return drugCodeMapRepository.findByIdAndTenantIdAndIsDeletedFalse(cmd.getId(), cmd.getTenantId())
                .orElseThrow(() -> new IllegalArgumentException(CrbDictConstants.MSG_MAP_NOT_FOUND));
    }

    /**
     * 导入单行科室对照并更新汇总。
     *
     * @param tenantId 租户 id
     * @param row 导入行
     * @param result 汇总结果
     */
    private void importOneDeptRow(Long tenantId, UpsertDeptCodeMapCommand row, ImportResult result) {
        if (row == null || trimToNull(row.getLocationBusNo()) == null) {
            result.incrementSkipCount();
            result.addMessage("跳过空科室业务码行");
            return;
        }
        try {
            row.setTenantId(tenantId);
            upsertDeptCodeMap(row);
            result.incrementSuccessCount();
        } catch (RuntimeException ex) {
            result.incrementFailCount();
            result.addMessage(ex.getMessage());
        }
    }

    /**
     * 导入单行检验对照并更新汇总。
     *
     * @param tenantId 租户 id
     * @param row 导入行
     * @param result 汇总结果
     */
    private void importOneLabRow(Long tenantId, UpsertLabItemMapCommand row, ImportResult result) {
        if (row == null || trimToNull(row.getItemBusNo()) == null) {
            result.incrementSkipCount();
            result.addMessage("跳过空检验项目业务码行");
            return;
        }
        try {
            row.setTenantId(tenantId);
            upsertLabItemMap(row);
            result.incrementSuccessCount();
        } catch (RuntimeException ex) {
            result.incrementFailCount();
            result.addMessage(ex.getMessage());
        }
    }

    /**
     * 导入单行药品对照并更新汇总。
     *
     * @param tenantId 租户 id
     * @param row 导入行
     * @param result 汇总结果
     */
    private void importOneDrugRow(Long tenantId, UpsertDrugCodeMapCommand row, ImportResult result) {
        if (row == null || trimToNull(row.getMedBusNo()) == null) {
            result.incrementSkipCount();
            result.addMessage("跳过空药品业务码行");
            return;
        }
        try {
            row.setTenantId(tenantId);
            upsertDrugCodeMap(row);
            result.incrementSuccessCount();
        } catch (RuntimeException ex) {
            result.incrementFailCount();
            result.addMessage(ex.getMessage());
        }
    }
}
