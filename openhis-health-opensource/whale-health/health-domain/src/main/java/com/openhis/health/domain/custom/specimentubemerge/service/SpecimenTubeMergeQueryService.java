/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.domain.custom.specimentubemerge.service;

import com.openhis.health.domain.custom.specimentubemerge.entity.SpecimenTubeMerge;
import com.openhis.health.domain.custom.specimentubemerge.queries.model.SpecimenTubeMergeLineQueryModel;
import com.openhis.health.domain.custom.specimentubemerge.repository.SpecimenTubeMergeRepository;
import com.openhis.health.domain.share.constants.TableNameConstants;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 标本合管配置读侧查询服务（DM-CUS-014）。
 *
 * <p>聚合边界：仅本聚合 {@link SpecimenTubeMergeRepository}。</p>
 *
 * <p>activity → 设备反查仅适用于「检验项目全局唯一」目标；采血费/耗材可跨设备多行，
 * 多行命中时不作为合管分组依据（返回 empty / 不写入 Map）。</p>
 */
@Service
@RequiredArgsConstructor
public class SpecimenTubeMergeQueryService {

    private final SpecimenTubeMergeRepository repository;

    /**
     * 按设备主键列出未软删的合管绑定行。
     *
     * @param deviceId 设备主键
     * @return 绑定行读模型列表；deviceId 为空时返回空列表
     */
    public List<SpecimenTubeMergeLineQueryModel> listByDeviceId(Long deviceId) {
        if (deviceId == null) {
            return List.of();
        }
        List<SpecimenTubeMerge> rows = repository.findByDeviceIdAndIsDeletedFalse(deviceId);
        List<SpecimenTubeMergeLineQueryModel> result = new ArrayList<>();
        for (SpecimenTubeMerge row : rows) {
            SpecimenTubeMergeLineQueryModel model = new SpecimenTubeMergeLineQueryModel();
            model.setId(row.getId());
            model.setDeviceId(row.getDeviceId());
            model.setTargetTable(row.getTargetTable());
            model.setTargetId(row.getTargetId());
            model.setQuantity(row.getQuantity());
            model.setUnitCode(row.getUnitCode());
            model.setDisplayOrder(row.getDisplayOrder());
            result.add(model);
        }
        return result;
    }

    /**
     * 按检验 activity 反查合管设备主键。
     *
     * <p>仅当该 activity 恰好绑定一台设备时返回；采血费跨设备多行时返回 empty。</p>
     *
     * @param activityId 检验 activity 主键
     * @return 合管设备主键；未配置、activityId 为空或多设备绑定时 empty
     */
    public Optional<Long> findDeviceIdByActivityId(Long activityId) {
        if (activityId == null) {
            return Optional.empty();
        }
        List<SpecimenTubeMerge> rows = repository.findByTargetTableAndTargetIdInAndIsDeletedFalse(
            TableNameConstants.WKF_ACTIVITY_DEFINITION, List.of(activityId));
        return this.resolveUniqueDeviceId(rows);
    }

    /**
     * 批量反查 activity → 合管设备主键。
     *
     * <p>同一 activity 绑定多台设备时不写入 Map（避免采血费跨设备污染合管分组）。</p>
     *
     * @param activityIds 检验 activity 主键集合
     * @return activityId → deviceId；未配置或非唯一归属的 activity 不出现在 Map 中
     */
    public Map<Long, Long> mapDeviceIdByActivityIds(Collection<Long> activityIds) {
        Map<Long, Long> result = new LinkedHashMap<>();
        if (activityIds == null || activityIds.isEmpty()) {
            return result;
        }
        List<Long> idList = new ArrayList<>();
        for (Long activityId : activityIds) {
            if (activityId != null) {
                idList.add(activityId);
            }
        }
        if (idList.isEmpty()) {
            return result;
        }
        List<SpecimenTubeMerge> rows = repository.findByTargetTableAndTargetIdInAndIsDeletedFalse(
            TableNameConstants.WKF_ACTIVITY_DEFINITION, idList);
        // 先按 targetId 归组，仅唯一设备归属写入结果
        Map<Long, List<Long>> deviceIdsByActivity = new LinkedHashMap<>();
        for (SpecimenTubeMerge row : rows) {
            if (row == null || row.getTargetId() == null || row.getDeviceId() == null) {
                continue;
            }
            List<Long> deviceIds = deviceIdsByActivity.get(row.getTargetId());
            if (deviceIds == null) {
                deviceIds = new ArrayList<>();
                deviceIdsByActivity.put(row.getTargetId(), deviceIds);
            }
            if (!deviceIds.contains(row.getDeviceId())) {
                deviceIds.add(row.getDeviceId());
            }
        }
        for (Map.Entry<Long, List<Long>> entry : deviceIdsByActivity.entrySet()) {
            List<Long> deviceIds = entry.getValue();
            // 多设备归属（采血费）不参与合管分组反查
            if (deviceIds.size() != 1) {
                continue;
            }
            result.put(entry.getKey(), deviceIds.get(0));
        }
        return result;
    }

    /**
     * 从同 target 绑定行中解析唯一设备主键；0 行或多设备时 empty。
     *
     * @param rows 同 activity 的绑定行
     * @return 唯一 deviceId
     */
    private Optional<Long> resolveUniqueDeviceId(List<SpecimenTubeMerge> rows) {
        if (rows == null || rows.isEmpty()) {
            return Optional.empty();
        }
        Long deviceId = null;
        for (SpecimenTubeMerge row : rows) {
            if (row == null || row.getDeviceId() == null) {
                continue;
            }
            if (deviceId == null) {
                deviceId = row.getDeviceId();
                continue;
            }
            // 同一 activity 出现多台设备 → 非检验项目唯一归属（如采血费）
            if (!deviceId.equals(row.getDeviceId())) {
                return Optional.empty();
            }
        }
        if (deviceId == null) {
            return Optional.empty();
        }
        return Optional.of(deviceId);
    }
}
