/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.domain.print.printbinding.service;

import static com.openhis.whale.core.application.util.StringUtils.trimToNull;

import com.openhis.health.domain.print.printbinding.entity.PrtPrintBinding;
import com.openhis.health.domain.print.printbinding.repository.PrtPrintBindingRepository;
import com.openhis.health.domain.share.constants.ErrorCodeConstants;
import com.openhis.health.domain.share.constants.GeneralSharedConstants;
import com.openhis.whale.core.application.exception.BusinessException;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * 业务场景模板绑定读侧查询服务（DM-PRT-003 · PrtPrintBinding）。
 *
 * <p>仅注入本聚合 {@link PrtPrintBindingRepository}；跨聚合读不在此类扩展（B.6.4）。</p>
 *
 * <p>聚合边界：仅本聚合 {@link PrtPrintBindingRepository}。</p>
 */
@Service
@RequiredArgsConstructor
public class PrtPrintBindingQueryService {

    private final PrtPrintBindingRepository prtPrintBindingRepository;

    /**
     * 解析 bindingCode → busNo（TC-PRT-BND-RES-001）。
     *
     * @param bindingCode 场景入口码
     * @param tenantId    租户
     * @return 有效 busNo
     * @throws BusinessException 绑定无效或未配置
     */
    public String resolve(String bindingCode, Long tenantId) {
        String code = trimToNull(bindingCode);
        if (code == null || tenantId == null) {
            throw new BusinessException(ErrorCodeConstants.PRT_NOT_FOUND, "绑定场景无效");
        }
        // Pre：租户 override 优先。
        PrtPrintBinding tenantRow = prtPrintBindingRepository
            .findByTenantIdAndBindingCodeAndOrganizationIdAndIsDeletedFalse(tenantId, code, null);
        if (tenantRow != null && Boolean.TRUE.equals(tenantRow.getEnabled())) {
            return tenantRow.getBusNo();
        }
        // Pre：平台 seed fallback。
        PrtPrintBinding platformRow = prtPrintBindingRepository
            .findByTenantIdAndBindingCodeAndOrganizationIdAndIsDeletedFalse(
                GeneralSharedConstants.PLATFORM_TENANT_ID, code, null);
        if (platformRow != null && Boolean.TRUE.equals(platformRow.getEnabled())) {
            return platformRow.getBusNo();
        }
        throw new BusinessException(ErrorCodeConstants.PRT_NOT_FOUND, "未找到绑定场景：" + code);
    }

    /**
     * 平台出厂 binding 定义清单（TC-PRT-BND-LST-001）。
     *
     * @return 平台 seed 行
     */
    public List<PrtPrintBinding> listDefinitions() {
        return prtPrintBindingRepository.findByTenantIdAndIsDeletedFalse(GeneralSharedConstants.PLATFORM_TENANT_ID);
    }

    /**
     * 租户 override 绑定行（TC-PRT-BND-LST-002）。
     *
     * @param tenantId 租户
     * @return 租户绑定行（organization_id 为 NULL）
     */
    public List<PrtPrintBinding> listOverridesForTenant(Long tenantId) {
        if (tenantId == null) {
            return List.of();
        }
        return prtPrintBindingRepository.findByTenantIdAndOrganizationIdIsNullAndIsDeletedFalse(tenantId);
    }
}
