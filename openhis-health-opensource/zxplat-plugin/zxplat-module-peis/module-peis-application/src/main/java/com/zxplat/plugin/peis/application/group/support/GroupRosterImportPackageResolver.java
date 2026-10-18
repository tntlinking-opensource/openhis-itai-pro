/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.peis.application.group.support;

import com.zxplat.plugin.peis.domain.group.service.PeisGroupCheckQueryService;
import com.zxplat.domain.workflow.activitydefinition.queries.model.ActivityDefinitionBaseQueryModel;
import com.zxplat.domain.workflow.activitydefinition.service.ActivityDefinitionQueryService;
import com.zxplat.framework.core.application.exception.BusinessException;
import com.zxplat.framework.core.application.util.StringUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * 导入模板「套餐」列名称解析为 AD 主键（限定在本分组可选范围内）。
 */
@Component
@RequiredArgsConstructor
public class GroupRosterImportPackageResolver {

    private final PeisGroupCheckQueryService peisGroupCheckQueryService;
    private final ActivityDefinitionQueryService activityDefinitionQueryService;

    /**
     * 将套餐名称列表解析为本分组内的 AD 主键。
     *
     * @param batchId       分组主键
     * @param packageNames  顿号/逗号分隔的套餐名
     * @return AD 主键列表
     */
    public List<Long> resolvePackageIdsByNames(Long batchId, List<String> packageNames) {
        List<Long> batchPackageIds = peisGroupCheckQueryService.listBatchPackageIds(batchId);
        if (batchPackageIds.isEmpty()) {
            throw new BusinessException("GROUP_BATCH_PACKAGE_INVALID", "分组未配置可选套餐");
        }
        List<Long> resolved = new ArrayList<>();
        for (String rawName : packageNames) {
            if (StringUtils.isEmpty(rawName)) {
                continue;
            }
            String name = rawName.trim();
            Long matched = null;
            for (Long packageActivityId : batchPackageIds) {
                com.zxplat.domain.workflow.activitydefinition.queries.model.ActivityDefinitionDetailQueryModel detail =
                    activityDefinitionQueryService.getServiceCatalog(packageActivityId);
                ActivityDefinitionBaseQueryModel base = detail == null ? null : detail.getBaseInfo();
                if (base != null && name.equals(base.getName())) {
                    matched = packageActivityId;
                    break;
                }
            }
            if (matched == null) {
                throw new BusinessException(
                    "GROUP_BATCH_PACKAGE_INVALID",
                    "套餐「" + name + "」不在本分组可选范围内");
            }
            resolved.add(matched);
        }
        if (resolved.isEmpty()) {
            throw new BusinessException("GROUP_ROSTER_PACKAGE_EMPTY", "可选套餐不能为空");
        }
        return resolved;
    }
}
