/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.application.basic.ordertemplate.support;

import com.zxplat.application.basic.ordertemplate.dto.OrderTemplateHeaderRowDto;
import com.zxplat.domain.administration.organization.service.OrganizationQueryService;
import com.zxplat.domain.administration.practitioner.entity.Practitioner;
import com.zxplat.domain.administration.practitioner.service.PractitionerQueryService;
import com.zxplat.domain.share.enums.OrderTemplateBinding;
import com.zxplat.domain.share.utils.EnumUtil;
import com.zxplat.framework.core.application.util.StringUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 组套头行归属对象展示名 enrich（个人→医务人员姓名，科室/全院/协定→组织名称）。
 */
@Component
@RequiredArgsConstructor
public class OrderTemplateHeaderOwnerEnrichAssembler {

    /** 医务人员读侧。 */
    private final PractitionerQueryService practitionerQueryService;

    /** 组织读侧。 */
    private final OrganizationQueryService organizationQueryService;

    /**
     * 为头行列表批量填充 {@code objectName}；入参为空时直接返回。
     *
     * @param headers 头行 DTO 列表
     */
    public void enrichObjectNames(List<OrderTemplateHeaderRowDto> headers) {
        if (headers == null || headers.isEmpty()) {
            return;
        }
        // 按绑定类型收集归属 id，避免个人与科室混用同一批查询
        Set<Long> personalObjectIds = new LinkedHashSet<>();
        Set<Long> organizationObjectIds = new LinkedHashSet<>();
        for (OrderTemplateHeaderRowDto header : headers) {
            if (header == null || header.getObjectId() == null) {
                continue;
            }
            OrderTemplateBinding binding = EnumUtil.fromCode(OrderTemplateBinding.class, header.getBindingEnum());
            if (binding == OrderTemplateBinding.PERSONAL) {
                personalObjectIds.add(header.getObjectId());
                continue;
            }
            if (binding == OrderTemplateBinding.DEPARTMENT
                || binding == OrderTemplateBinding.HOSPITAL
                || binding == OrderTemplateBinding.AGREEMENT) {
                organizationObjectIds.add(header.getObjectId());
            }
        }
        Map<Long, String> practitionerNameById = mapPractitionerNames(personalObjectIds);
        Map<Long, String> organizationNameById = organizationQueryService.mapNamesByIds(organizationObjectIds);
        for (OrderTemplateHeaderRowDto header : headers) {
            if (header == null || header.getObjectId() == null) {
                continue;
            }
            OrderTemplateBinding binding = EnumUtil.fromCode(OrderTemplateBinding.class, header.getBindingEnum());
            if (binding == OrderTemplateBinding.PERSONAL) {
                header.setObjectName(practitionerNameById.get(header.getObjectId()));
                continue;
            }
            if (binding == OrderTemplateBinding.DEPARTMENT
                || binding == OrderTemplateBinding.HOSPITAL
                || binding == OrderTemplateBinding.AGREEMENT) {
                header.setObjectName(organizationNameById.get(header.getObjectId()));
            }
        }
    }

    /**
     * 为单条头行填充归属展示名。
     *
     * @param header 头行；null 时忽略
     */
    public void enrichObjectName(OrderTemplateHeaderRowDto header) {
        if (header == null) {
            return;
        }
        List<OrderTemplateHeaderRowDto> singleton = new ArrayList<>(1);
        singleton.add(header);
        enrichObjectNames(singleton);
    }

    /**
     * 批量解析医务人员姓名。
     *
     * @param practitionerIds 医务人员主键集合
     * @return id → 姓名；未命中不写入
     */
    private Map<Long, String> mapPractitionerNames(Collection<Long> practitionerIds) {
        Map<Long, String> nameById = new HashMap<>();
        if (practitionerIds == null || practitionerIds.isEmpty()) {
            return nameById;
        }
        // Pre：一次批量加载，禁止 findById 循环
        List<Practitioner> practitioners = practitionerQueryService.findAllByIds(practitionerIds);
        for (Practitioner practitioner : practitioners) {
            if (practitioner == null || practitioner.getId() == null) {
                continue;
            }
            if (StringUtils.isNotEmpty(practitioner.getName())) {
                nameById.put(practitioner.getId(), practitioner.getName());
            }
        }
        return nameById;
    }
}
