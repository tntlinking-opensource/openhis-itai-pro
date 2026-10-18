/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.peis.application.group.support;

import com.zxplat.plugin.peis.application.group.dto.CompanyTreeNodeDto;
import com.zxplat.plugin.peis.domain.group.entity.PeisGroupCompany;
import com.zxplat.plugin.peis.domain.group.service.PeisGroupCheckQueryService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 团检单位树组装（读侧；B.6.4 分步查 QS 后内存建树）。
 */
@Component
@RequiredArgsConstructor
public class PeisGroupCompanyTreeLoader {

    private final PeisGroupCheckQueryService peisGroupCheckQueryService;

    /**
     * 加载单位树。
     *
     * @param orgId 机构过滤（可选）
     * @return 根节点列表
     */
    public List<CompanyTreeNodeDto> loadTree(Long orgId) {
        List<PeisGroupCompany> all = peisGroupCheckQueryService.listCompanies();
        List<PeisGroupCompany> filtered = new ArrayList<>();
        for (PeisGroupCompany company : all) {
            if (company == null) {
                continue;
            }
            if (orgId != null && company.getOrgId() != null && !orgId.equals(company.getOrgId())) {
                continue;
            }
            filtered.add(company);
        }
        Map<Long, CompanyTreeNodeDto> nodeMap = new HashMap<>();
        for (PeisGroupCompany company : filtered) {
            nodeMap.put(company.getId(), toNode(company));
        }
        for (CompanyTreeNodeDto node : nodeMap.values()) {
            if (node.getParentId() != null && nodeMap.containsKey(node.getParentId())) {
                CompanyTreeNodeDto parent = nodeMap.get(node.getParentId());
                parent.getChildren().add(node);
                parent.setHasChildren(Boolean.TRUE);
            }
        }
        List<CompanyTreeNodeDto> roots = new ArrayList<>();
        for (CompanyTreeNodeDto node : nodeMap.values()) {
            if (node.getParentId() == null || !nodeMap.containsKey(node.getParentId())) {
                roots.add(node);
            }
        }
        return roots;
    }

    /**
     * 解析选中节点及其全部下级单位 id。
     *
     * @param selectedCompanyId 选中节点
     * @return 范围 id 列表
     */
    public List<Long> resolveScopeCompanyIds(Long selectedCompanyId) {
        return peisGroupCheckQueryService.listDescendantCompanyIds(selectedCompanyId);
    }

    private static CompanyTreeNodeDto toNode(PeisGroupCompany entity) {
        CompanyTreeNodeDto dto = new CompanyTreeNodeDto();
        dto.setCompanyId(entity.getId());
        dto.setParentId(entity.getParentId());
        dto.setName(entity.getName());
        dto.setOrgId(entity.getOrgId());
        dto.setCampusCollegeId(entity.getCampusCollegeId());
        dto.setContactName(entity.getContactName());
        dto.setContactPhone(entity.getContactPhone());
        dto.setHasChildren(Boolean.FALSE);
        return dto;
    }
}
