/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.domain.administration.healthcareservice.service;

import com.zxplat.domain.administration.healthcareservice.entity.HealthcareService;
import com.zxplat.domain.administration.healthcareservice.repository.HealthcareServiceRepository;
import com.zxplat.domain.share.constants.HealthcareServiceConstants;
import com.zxplat.domain.share.enums.HealthcareServiceStatus;
import org.springframework.stereotype.Service;

import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;

/**
 * 医疗服务（号源/服务项定义）读侧查询服务。
 *
 * <p>职责：为挂号、排班等场景提供启用中的医疗服务列表及按主键加载详情。</p>
 *
 * <p>边界：只读；挂号下拉过滤 {@link HealthcareServiceStatus#ACTIVE}；维护台另见
 * {@link #listServicesForRegistrationManage()}。</p>
 * <p>聚合边界：仅本聚合 {@link HealthcareServiceRepository}。</p>
 */
@Service
@RequiredArgsConstructor
public class HealthcareServiceQueryService {
    private final HealthcareServiceRepository repository;

    /**
     * 列出挂号场景可选的启用医疗服务（维护台已发布且启用）。
     *
     * @return 医疗服务列表，可能为空
     */
    public List<HealthcareService> listServicesForRegistration() {
        // 仅返回已发布（ACTIVE）行，停用服务不出现在挂号下拉中。
        return repository.findAllByStatusEnum(HealthcareServiceStatus.ACTIVE.getCode());
    }

    /**
     * 列出挂号类型维护台可见的医疗服务（草稿/启用/停用；不含已废弃）。
     *
     * <p>与 {@link #listServicesForRegistration()} 分离，避免停用项从维护列表消失后无法再启用或删除。</p>
     *
     * @return 医疗服务列表，可能为空
     */
    public List<HealthcareService> listServicesForRegistrationManage() {
        // 维护台需同时展示启用与停用，删除前也须能看到停用项。
        return repository.findAllByStatusEnumIn(List.of(
            HealthcareServiceStatus.DRAFT.getCode(),
            HealthcareServiceStatus.ACTIVE.getCode(),
            HealthcareServiceStatus.RETIRED.getCode()));
    }

    /**
     * 按主键加载医疗服务详情。
     *
     * @param id 医疗服务主键
     * @return 实体；不存在时 {@code null}，由应用层决定是否提示
     */
    public HealthcareService loadHealthcareServiceById(Long id) {
        return repository.findById(id).orElse(null);
    }

    /**
     * 按主键批量加载医疗服务，供绑定列表 enrich 号源来源项展示名。
     *
     * @param ids 主键集合
     * @return 主键到实体的映射；入参为空时返回空 Map
     */
    public Map<Long, HealthcareService> loadHealthcareServiceMapByIds(Collection<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return Collections.emptyMap();
        }
        List<HealthcareService> rows = repository.findAllById(ids);
        Map<Long, HealthcareService> result = new HashMap<>();
        for (HealthcareService row : rows) {
            if (row != null && row.getId() != null) {
                result.put(row.getId(), row);
            }
        }
        return result;
    }

    /**
     * 判断给定号源是否为产品「复诊0元号」。
     *
     * <p>识别真源：{@link HealthcareServiceConstants#NAME_HISTORICAL_FOLLOW_UP} 与 {@code name} trim 后精确相等；
     * 不按 {@code bus_no}（院点编码不统一）。</p>
     *
     * @param service 号源实体；为空时返回 false
     * @return 复诊0元号返回 true
     */
    public boolean isHistoricalFollowUpService(HealthcareService service) {
        if (service == null || service.getName() == null) {
            return false;
        }
        return HealthcareServiceConstants.NAME_HISTORICAL_FOLLOW_UP.equals(service.getName().trim());
    }

    /**
     * 按业务编号加载启用中的号源（主数据/运维按 bus_no 查询；复诊0元号资格 Gate 不依赖本方法）。
     *
     * @param busNo 业务编号
     * @return 实体；不存在或未启用时 {@code null}
     */
    public HealthcareService findActiveHealthcareServiceByBusNo(String busNo) {
        if (busNo == null || busNo.isBlank()) {
            return null;
        }
        return repository.findByBusNoAndStatusEnum(busNo.trim(), HealthcareServiceStatus.ACTIVE.getCode()).orElse(null);
    }
}
