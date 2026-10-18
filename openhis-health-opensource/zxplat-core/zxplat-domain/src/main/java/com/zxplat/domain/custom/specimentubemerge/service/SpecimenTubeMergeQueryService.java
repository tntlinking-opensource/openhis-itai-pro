/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.domain.custom.specimentubemerge.service;

import com.zxplat.domain.custom.specimentubemerge.entity.SpecimenTubeMerge;
import com.zxplat.domain.custom.specimentubemerge.queries.model.SpecimenTubeMergeGroupQueryModel;
import com.zxplat.domain.custom.specimentubemerge.queries.model.SpecimenTubeMergeLineQueryModel;
import com.zxplat.domain.custom.specimentubemerge.repository.SpecimenTubeMergeRepository;
import com.zxplat.domain.share.constants.SpecimenTubeMergeConstants;
import com.zxplat.domain.share.constants.TableNameConstants;
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
 * <p>activity → 合管组反查仅适用于「检验项目全局唯一」目标；采血费/耗材可跨设备、跨组多行，
 * 多行命中时不作为合管分组依据（返回 empty / 不写入 Map）。</p>
 */
@Service
@RequiredArgsConstructor
public class SpecimenTubeMergeQueryService {

    private final SpecimenTubeMergeRepository repository;

    /**
     * 按设备主键列出未软删的合管绑定行（含全部组）。
     *
     * @param deviceId 设备主键
     * @return 绑定行读模型列表；deviceId 为空时返回空列表
     */
    public List<SpecimenTubeMergeLineQueryModel> listByDeviceId(Long deviceId) {
        if (deviceId == null) {
            return List.of();
        }
        List<SpecimenTubeMerge> rows = repository.findByDeviceIdAndIsDeletedFalse(deviceId);
        return this.toLineModels(rows);
    }

    /**
     * 按设备与合管组列出未软删绑定行。
     *
     * @param deviceId 设备主键
     * @param groupNo  组序号
     * @return 绑定行读模型；参数不全时返回空列表
     */
    public List<SpecimenTubeMergeLineQueryModel> listByDeviceIdAndGroupNo(Long deviceId, Integer groupNo) {
        if (deviceId == null || groupNo == null) {
            return List.of();
        }
        List<SpecimenTubeMerge> rows =
            repository.findByDeviceIdAndGroupNoAndIsDeletedFalse(deviceId, groupNo);
        return this.toLineModels(rows);
    }

    /**
     * 按检验 activity 反查合管设备主键。
     *
     * <p>仅当该 activity 恰好归属一个合管组时返回设备；采血费跨组多行时返回 empty。</p>
     *
     * @param activityId 检验 activity 主键
     * @return 合管设备主键；未配置、activityId 为空或多组绑定时 empty
     */
    public Optional<Long> findDeviceIdByActivityId(Long activityId) {
        return this.findGroupRefByActivityId(activityId).map(SpecimenTubeMergeGroupQueryModel::getDeviceId);
    }

    /**
     * 按检验 activity 反查合管分组（设备 + 组序号）。
     *
     * @param activityId 检验 activity 主键
     * @return 合管分组引用；未配置或非唯一归属时 empty
     */
    public Optional<SpecimenTubeMergeGroupQueryModel> findGroupRefByActivityId(Long activityId) {
        if (activityId == null) {
            return Optional.empty();
        }
        List<SpecimenTubeMerge> rows = repository.findByTargetTableAndTargetIdInAndIsDeletedFalse(
            TableNameConstants.WKF_ACTIVITY_DEFINITION, List.of(activityId));
        return this.resolveUniqueGroupRef(rows);
    }

    /**
     * 批量反查 activity → 合管设备主键。
     *
     * <p>同一 activity 绑定多组时不写入 Map（避免采血费跨组污染合管分组）。</p>
     *
     * @param activityIds 检验 activity 主键集合
     * @return activityId → deviceId；未配置或非唯一归属的 activity 不出现在 Map 中
     */
    public Map<Long, Long> mapDeviceIdByActivityIds(Collection<Long> activityIds) {
        Map<Long, SpecimenTubeMergeGroupQueryModel> groupRefs = this.mapGroupRefByActivityIds(activityIds);
        Map<Long, Long> result = new LinkedHashMap<>();
        for (Map.Entry<Long, SpecimenTubeMergeGroupQueryModel> entry : groupRefs.entrySet()) {
            if (entry.getValue() != null && entry.getValue().getDeviceId() != null) {
                result.put(entry.getKey(), entry.getValue().getDeviceId());
            }
        }
        return result;
    }

    /**
     * 批量反查 activity → 合管分组（设备 + 组序号）。
     *
     * <p>同一 activity 出现多组归属时不写入 Map（采血费场景）。</p>
     *
     * @param activityIds 检验 activity 主键集合
     * @return activityId → 合管分组引用
     */
    public Map<Long, SpecimenTubeMergeGroupQueryModel> mapGroupRefByActivityIds(Collection<Long> activityIds) {
        Map<Long, SpecimenTubeMergeGroupQueryModel> result = new LinkedHashMap<>();
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
        // 先按 targetId 归组，仅唯一「设备+组」归属写入结果
        Map<Long, List<SpecimenTubeMerge>> rowsByActivity = new LinkedHashMap<>();
        for (SpecimenTubeMerge row : rows) {
            if (row == null || row.getTargetId() == null || row.getDeviceId() == null) {
                continue;
            }
            List<SpecimenTubeMerge> bucket = rowsByActivity.get(row.getTargetId());
            if (bucket == null) {
                bucket = new ArrayList<>();
                rowsByActivity.put(row.getTargetId(), bucket);
            }
            bucket.add(row);
        }
        for (Map.Entry<Long, List<SpecimenTubeMerge>> entry : rowsByActivity.entrySet()) {
            Optional<SpecimenTubeMergeGroupQueryModel> refOpt = this.resolveUniqueGroupRef(entry.getValue());
            if (refOpt.isEmpty()) {
                continue;
            }
            result.put(entry.getKey(), refOpt.get());
        }
        return result;
    }

    /**
     * 实体列表 → 行读模型。
     *
     * @param rows 实体行
     * @return 读模型列表
     */
    private List<SpecimenTubeMergeLineQueryModel> toLineModels(List<SpecimenTubeMerge> rows) {
        List<SpecimenTubeMergeLineQueryModel> result = new ArrayList<>();
        if (rows == null || rows.isEmpty()) {
            return result;
        }
        for (SpecimenTubeMerge row : rows) {
            SpecimenTubeMergeLineQueryModel model = new SpecimenTubeMergeLineQueryModel();
            model.setId(row.getId());
            model.setDeviceId(row.getDeviceId());
            Integer groupNo = row.getGroupNo();
            if (groupNo == null) {
                groupNo = SpecimenTubeMergeConstants.DEFAULT_GROUP_NO;
            }
            model.setGroupNo(groupNo);
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
     * 从同 target 绑定行中解析唯一合管组；0 行或多组时 empty。
     *
     * @param rows 同 activity 的绑定行
     * @return 唯一分组引用
     */
    private Optional<SpecimenTubeMergeGroupQueryModel> resolveUniqueGroupRef(List<SpecimenTubeMerge> rows) {
        if (rows == null || rows.isEmpty()) {
            return Optional.empty();
        }
        Long deviceId = null;
        Integer groupNo = null;
        for (SpecimenTubeMerge row : rows) {
            if (row == null || row.getDeviceId() == null) {
                continue;
            }
            Integer rowGroupNo = row.getGroupNo();
            if (rowGroupNo == null) {
                rowGroupNo = SpecimenTubeMergeConstants.DEFAULT_GROUP_NO;
            }
            if (deviceId == null) {
                deviceId = row.getDeviceId();
                groupNo = rowGroupNo;
                continue;
            }
            // 同一 activity 出现多台设备或多组 → 非检验项目唯一归属（如采血费）
            if (!deviceId.equals(row.getDeviceId()) || !groupNo.equals(rowGroupNo)) {
                return Optional.empty();
            }
        }
        if (deviceId == null || groupNo == null) {
            return Optional.empty();
        }
        return Optional.of(SpecimenTubeMergeGroupQueryModel.of(deviceId, groupNo));
    }
}
