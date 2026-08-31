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
     * @param activityId 检验 activity 主键
     * @return 合管设备主键；未配置或 activityId 为空时 empty
     */
    public Optional<Long> findDeviceIdByActivityId(Long activityId) {
        if (activityId == null) {
            return Optional.empty();
        }
        Optional<SpecimenTubeMerge> row = repository.findByTargetTableAndTargetIdAndIsDeletedFalse(
            TableNameConstants.WKF_ACTIVITY_DEFINITION, activityId);
        if (row.isEmpty() || row.get().getDeviceId() == null) {
            return Optional.empty();
        }
        return Optional.of(row.get().getDeviceId());
    }

    /**
     * 批量反查 activity → 合管设备主键。
     *
     * @param activityIds 检验 activity 主键集合
     * @return activityId → deviceId；未配置的 activity 不出现在 Map 中
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
        for (SpecimenTubeMerge row : rows) {
            if (row == null || row.getTargetId() == null || row.getDeviceId() == null) {
                continue;
            }
            result.put(row.getTargetId(), row.getDeviceId());
        }
        return result;
    }
}
