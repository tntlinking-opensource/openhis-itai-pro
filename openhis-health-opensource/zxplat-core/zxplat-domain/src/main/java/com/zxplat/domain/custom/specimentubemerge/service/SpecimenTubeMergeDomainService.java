/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.domain.custom.specimentubemerge.service;

import com.zxplat.domain.administration.labdevice.queries.model.LabDeviceDetailQueryModel;
import com.zxplat.domain.administration.labdevice.service.LabDeviceQueryService;
import com.zxplat.domain.custom.specimentubemerge.command.AlignSpecimenTubeMergeCommand;
import com.zxplat.domain.custom.specimentubemerge.command.AlignSpecimenTubeMergeLineCommand;
import com.zxplat.domain.custom.specimentubemerge.command.DeleteDeviceBindingsCommand;
import com.zxplat.domain.custom.specimentubemerge.entity.SpecimenTubeMerge;
import com.zxplat.domain.custom.specimentubemerge.repository.SpecimenTubeMergeRepository;
import com.zxplat.domain.share.constants.ErrorCodeConstants;
import com.zxplat.domain.share.constants.SpecimenTubeMergeConstants;
import com.zxplat.domain.share.constants.TableNameConstants;
import com.zxplat.framework.core.application.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import com.zxplat.framework.core.application.util.StringUtils;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * 标本合管配置写侧领域服务（DM-CUS-014）。
 *
 * <p>全量对齐语义：入参集为某设备下<strong>全部组</strong>的绑定全集；缺席旧行软删。
 * 设备存在性经 {@link LabDeviceQueryService} 校验（B.6.4）。</p>
 *
 * <p>BR-STM-002：仅<strong>检验项目</strong>（{@code wkf_activity_definition}，不含采血费）跨设备与跨组全局唯一；
 * 耗材与采血费允许绑定到多台设备、同一设备下多个合管组。</p>
 */
@Service
@RequiredArgsConstructor
public class SpecimenTubeMergeDomainService {

    private final SpecimenTubeMergeRepository repository;
    private final LabDeviceQueryService labDeviceQueryService;

    /**
     * 按设备全量对齐合管绑定行（可含多组）。
     *
     * @param command 对齐命令
     * @return 对齐后仍有效（未软删）的绑定行
     */
    public List<SpecimenTubeMerge> alignBindingsForDevice(AlignSpecimenTubeMergeCommand command) {
        if (command == null || command.getDeviceId() == null) {
            throw new BusinessException(
                ErrorCodeConstants.SPECIMEN_TUBE_MERGE_INVALID_ARGUMENT, "合管配置设备不能为空");
        }
        // 前置校验：设备须存在（B.6.4 跨聚合读 LabDeviceQueryService）
        if (labDeviceQueryService.getLabDevice(command.getDeviceId()) == null) {
            throw new BusinessException(ErrorCodeConstants.LAB_DEVICE_NOT_FOUND, "合管配置设备不存在或已删除");
        }
        List<AlignSpecimenTubeMergeLineCommand> items = command.getLines();
        if (items == null) {
            items = new ArrayList<>();
        }
        // 同组内任意目标不可重复；检验项目（非采血费）跨组亦不可重复
        Set<String> seenTargetsInGroup = new HashSet<>();
        Set<String> seenLabActivities = new HashSet<>();
        Long bloodFeeActivityId = command.getBloodCollectionFeeActivityId();
        // 前置校验：入参行内目标合法、组序号合法；检验项目未被其它设备占用
        for (AlignSpecimenTubeMergeLineCommand item : items) {
            if (item == null || StringUtils.isEmpty(item.getTargetTable()) || item.getTargetId() == null) {
                throw new BusinessException(
                    ErrorCodeConstants.SPECIMEN_TUBE_MERGE_INVALID_ARGUMENT, "合管绑定目标不能为空");
            }
            Integer groupNo = item.getGroupNo();
            if (groupNo == null || groupNo < SpecimenTubeMergeConstants.MIN_GROUP_NO) {
                throw new BusinessException(
                    ErrorCodeConstants.SPECIMEN_TUBE_MERGE_INVALID_ARGUMENT, "合管组序号须从 1 起");
            }
            String targetTable = item.getTargetTable().trim();
            Long targetId = item.getTargetId();
            String groupTargetKey = groupNo + ":" + targetTable + ":" + targetId;
            // 同组内目标重复 → 拒绝（含同组重复采血费/耗材）
            if (!seenTargetsInGroup.add(groupTargetKey)) {
                throw new BusinessException(
                    ErrorCodeConstants.SPECIMEN_TUBE_MERGE_INVALID_ARGUMENT, "同一合管组内绑定目标不可重复");
            }
            // activity 行不允许带数量/单位
            if (TableNameConstants.WKF_ACTIVITY_DEFINITION.equals(targetTable)) {
                item.setQuantity(null);
                item.setUnitCode(null);
            }
            // BR-STM-002：检验项目跨组/跨设备唯一；耗材、采血费可复用
            if (this.requiresGlobalUniqueTarget(targetTable, targetId, bloodFeeActivityId)) {
                String labKey = targetTable + ":" + targetId;
                // 本设备不同组也不能挂同一检验项目
                if (!seenLabActivities.add(labKey)) {
                    throw new BusinessException(
                        ErrorCodeConstants.SPECIMEN_TUBE_MERGE_INVALID_ARGUMENT,
                        "同一检验项目不能配置到同一设备下的多个合管组");
                }
                this.assertActivityNotBoundToOtherDevice(targetTable, targetId, command.getDeviceId());
            }
        }

        List<SpecimenTubeMerge> existing = repository.findByDeviceIdAndIsDeletedFalse(command.getDeviceId());
        Map<String, SpecimenTubeMerge> existingByGroupTarget = new HashMap<>();
        for (SpecimenTubeMerge row : existing) {
            existingByGroupTarget.put(this.buildGroupTargetKey(row), row);
        }

        Set<String> keepKeys = new HashSet<>();
        List<SpecimenTubeMerge> toSave = new ArrayList<>();

        // 加载/组装：入参行 upsert，缺席旧行稍后软删
        for (AlignSpecimenTubeMergeLineCommand item : items) {
            String targetTable = item.getTargetTable().trim();
            Long targetId = item.getTargetId();
            Integer groupNo = item.getGroupNo();
            String groupTargetKey = groupNo + ":" + targetTable + ":" + targetId;
            keepKeys.add(groupTargetKey);
            SpecimenTubeMerge row = existingByGroupTarget.get(groupTargetKey);
            if (row == null) {
                row = new SpecimenTubeMerge();
                row.setDeviceId(command.getDeviceId());
                row.setGroupNo(groupNo);
                row.setTargetTable(targetTable);
                row.setTargetId(targetId);
            }
            row.setQuantity(item.getQuantity());
            if (item.getUnitCode() != null) {
                row.setUnitCode(item.getUnitCode().trim());
            } else {
                row.setUnitCode(null);
            }
            row.setDisplayOrder(item.getDisplayOrder());
            row.assertRequiredFields();
            toSave.add(row);
        }

        // 持久化：入参未覆盖的旧行软删（全量对齐语义，含整组删除）
        for (SpecimenTubeMerge old : existing) {
            String groupTargetKey = this.buildGroupTargetKey(old);
            if (!keepKeys.contains(groupTargetKey)) {
                old.markDeleted(command.getOperatorId());
                toSave.add(old);
            }
        }

        List<SpecimenTubeMerge> savedAll = repository.saveAll(toSave);
        List<SpecimenTubeMerge> active = new ArrayList<>();
        for (SpecimenTubeMerge row : savedAll) {
            if (!Boolean.TRUE.equals(row.getIsDeleted())) {
                active.add(row);
            }
        }
        return active;
    }

    /**
     * 删除设备下全部合管绑定（逻辑删除，含所有组）。
     *
     * @param command 删除命令
     */
    public void deleteDeviceBindings(DeleteDeviceBindingsCommand command) {
        if (command == null || command.getDeviceId() == null) {
            throw new BusinessException(
                ErrorCodeConstants.SPECIMEN_TUBE_MERGE_INVALID_ARGUMENT, "合管配置设备不能为空");
        }
        // 前置校验：设备须存在
        if (labDeviceQueryService.getLabDevice(command.getDeviceId()) == null) {
            throw new BusinessException(ErrorCodeConstants.LAB_DEVICE_NOT_FOUND, "合管配置设备不存在或已删除");
        }
        List<SpecimenTubeMerge> existing = repository.findByDeviceIdAndIsDeletedFalse(command.getDeviceId());
        // 无绑定行时幂等返回
        if (existing.isEmpty()) {
            return;
        }
        // 持久化：逻辑删除全部行
        for (SpecimenTubeMerge row : existing) {
            row.markDeleted(command.getOperatorId());
        }
        repository.saveAll(existing);
    }

    /**
     * 组装设备内「组+目标」稳定键，供 upsert / 软删对齐。
     *
     * @param row 绑定行
     * @return {@code groupNo:targetTable:targetId}
     */
    private String buildGroupTargetKey(SpecimenTubeMerge row) {
        Integer groupNo = row.getGroupNo();
        if (groupNo == null) {
            groupNo = SpecimenTubeMergeConstants.DEFAULT_GROUP_NO;
        }
        return groupNo + ":" + row.getTargetTable() + ":" + row.getTargetId();
    }

    /**
     * 是否须做跨设备/跨组全局唯一：仅检验项目 activity，且不是采血费。
     *
     * @param targetTable        目标表
     * @param targetId           目标主键
     * @param bloodFeeActivityId 采血费 activity 主键；null 表示本批未勾选采血费
     * @return true 须校验全局唯一
     */
    private boolean requiresGlobalUniqueTarget(
        String targetTable,
        Long targetId,
        Long bloodFeeActivityId
    ) {
        if (!TableNameConstants.WKF_ACTIVITY_DEFINITION.equals(targetTable)) {
            return false;
        }
        if (bloodFeeActivityId != null && Objects.equals(bloodFeeActivityId, targetId)) {
            return false;
        }
        return true;
    }

    /**
     * 检验项目已被其它设备占用时拒绝，并带上占用设备名称便于前端理解。
     *
     * @param targetTable 目标表
     * @param targetId    目标主键
     * @param deviceId    当前保存设备（排除自身）
     */
    private void assertActivityNotBoundToOtherDevice(String targetTable, Long targetId, Long deviceId) {
        if (!repository.existsByTargetTableAndTargetIdAndIsDeletedFalseAndDeviceIdNot(
            targetTable, targetId, deviceId)) {
            return;
        }
        String occupiedBy = "其它设备";
        // 用列表反查占用设备名，避免 Optional 单行在脏数据下 NonUnique
        List<SpecimenTubeMerge> conflicts =
            repository.findByTargetTableAndTargetIdInAndIsDeletedFalse(targetTable, List.of(targetId));
        for (SpecimenTubeMerge conflict : conflicts) {
            if (conflict == null || conflict.getDeviceId() == null) {
                continue;
            }
            if (Objects.equals(conflict.getDeviceId(), deviceId)) {
                continue;
            }
            LabDeviceDetailQueryModel otherDevice =
                labDeviceQueryService.getLabDevice(conflict.getDeviceId());
            if (otherDevice != null && StringUtils.isNotEmpty(otherDevice.getName())) {
                occupiedBy = "设备「" + otherDevice.getName().trim() + "」";
            }
            break;
        }
        throw new BusinessException(
            ErrorCodeConstants.SPECIMEN_TUBE_MERGE_TARGET_CONFLICT,
            "该检验项目已绑定到" + occupiedBy + "，同一检验项目不能配置到多个合管组");
    }
}
