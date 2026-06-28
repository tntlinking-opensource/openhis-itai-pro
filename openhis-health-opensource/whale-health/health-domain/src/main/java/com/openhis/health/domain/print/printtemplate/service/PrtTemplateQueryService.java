/*
 * Copyright © 2026 OpenHIS Contributors
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 3 of the License, or (at your option) any later version.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library.  If not, see <https://www.gnu.org/licenses/>.
 */

package com.openhis.health.domain.print.printtemplate.service;

import static com.openhis.whale.core.application.util.StringUtils.trimToNull;

import com.openhis.health.domain.print.printtemplate.entity.PrtTemplate;
import com.openhis.health.domain.print.printtemplate.repository.PrtTemplateRepository;
import com.openhis.health.domain.share.constants.ErrorCodeConstants;
import com.openhis.health.domain.share.constants.GeneralSharedConstants;
import com.openhis.health.domain.share.enums.PrintTemplateStatus;
import com.openhis.whale.core.application.exception.BusinessException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * 画布打印模板读侧查询服务（DM-PRT-001 · PrtTemplate）。
 *
 * <p>仅注入本聚合 {@link PrtTemplateRepository}；跨聚合读不在此类扩展（B.6.4）。</p>
 * <p>读方法白名单见 DM §6.1；{@link #findDraftById}、{@link #findPublishedByScenarioCode} 为 Flyway
 * {@code scenario_code} 索引与 AS preparePrint 所需扩展（DOC-CONFLICT：未列入 DM §6.1）。</p>
 */
@Service
@RequiredArgsConstructor
public class PrtTemplateQueryService {

    private final PrtTemplateRepository prtTemplateRepository;

    /**
     * 取最新 published（不含 archived）；租户无则 fallback 平台（TC-PRT-TPL-RD-001）。
     *
     * @param busNo    业务键
     * @param tenantId 租户
     * @return published 行或 null
     */
    public PrtTemplate findPublished(String busNo, Long tenantId) {
        String resolvedBusNo = trimToNull(busNo);
        if (resolvedBusNo == null || tenantId == null) {
            return null;
        }
        // Pre：租户 published 优先。
        PrtTemplate tenantRow = prtTemplateRepository
            .findFirstByTenantIdAndBusNoAndStatusAndIsDeletedFalseOrderByVersionDesc(
                tenantId, resolvedBusNo, PrintTemplateStatus.PUBLISHED.getCode());
        if (tenantRow != null) {
            return tenantRow;
        }
        // Pre：平台 fallback，供租户未定制时使用出厂模板。
        if (GeneralSharedConstants.PLATFORM_TENANT_ID != tenantId) {
            return prtTemplateRepository
                .findFirstByTenantIdAndBusNoAndStatusAndIsDeletedFalseOrderByVersionDesc(
                    GeneralSharedConstants.PLATFORM_TENANT_ID, resolvedBusNo, PrintTemplateStatus.PUBLISHED.getCode());
        }
        return null;
    }

    /**
     * 设计态加载：租户 draft 优先，否则 published（TC-PRT-TPL-RD-002）。
     *
     * @param busNo    业务键
     * @param tenantId 租户
     * @return 模板行或 null
     */
    public PrtTemplate resolveForDesign(String busNo, Long tenantId) {
        String resolvedBusNo = trimToNull(busNo);
        if (resolvedBusNo == null || tenantId == null) {
            return null;
        }
        PrtTemplate draft = findTenantDraft(resolvedBusNo, tenantId);
        if (draft != null) {
            return draft;
        }
        return findPublished(resolvedBusNo, tenantId);
    }

    /**
     * 查租户草稿（平台 tenant 不查 draft，TC-PRT-TPL-RD-003）。
     *
     * @param busNo    业务键
     * @param tenantId 租户
     * @return draft 行或 null
     */
    public PrtTemplate findTenantDraft(String busNo, Long tenantId) {
        String resolvedBusNo = trimToNull(busNo);
        if (resolvedBusNo == null || tenantId == null) {
            return null;
        }
        // 平台租户仅维护 published，无 draft 行。
        if (GeneralSharedConstants.PLATFORM_TENANT_ID == tenantId) {
            return null;
        }
        return prtTemplateRepository.findFirstByTenantIdAndBusNoAndStatusAndIsDeletedFalseOrderByVersionDesc(
            tenantId, resolvedBusNo, PrintTemplateStatus.DRAFT.getCode());
    }

    /**
     * 按主键加载租户草稿（AS saveDraft Gate 用；DOC-CONFLICT：未列入 DM §6.1）。
     *
     * @param draftId  草稿 id
     * @param tenantId 租户
     * @return draft 行或 null
     */
    public PrtTemplate findDraftById(Long draftId, Long tenantId) {
        if (draftId == null || tenantId == null) {
            return null;
        }
        if (GeneralSharedConstants.PLATFORM_TENANT_ID == tenantId) {
            return null;
        }
        PrtTemplate row = prtTemplateRepository.findById(draftId).orElse(null);
        if (row == null || Boolean.TRUE.equals(row.getIsDeleted())) {
            return null;
        }
        if (!tenantId.equals(row.getTenantId())) {
            return null;
        }
        if (!PrintTemplateStatus.DRAFT.sameAs(row.getStatus())) {
            return null;
        }
        return row;
    }

    /**
     * 合并平台与租户模板列表（TC-PRT-TPL-RD-004 · SDD §9）。
     *
     * <p>算法：加载平台与租户全量物理行 → 租户 busNo 集合覆盖平台同键 → 默认排除 archived；
     * 返回租户全部物理行 + 未被租户覆盖的平台行（供 App 按 busNo 聚合 draft/published/archived）。</p>
     *
     * @param tenantId        当前租户
     * @param includeArchived 是否包含 archived
     * @return 合并后的模板行
     */
    public List<PrtTemplate> listMergedForTenant(Long tenantId, boolean includeArchived) {
        if (tenantId == null) {
            return List.of();
        }
        List<PrtTemplate> platformRows;
        List<PrtTemplate> tenantRows;
        if (includeArchived) {
            platformRows = prtTemplateRepository
                .findByTenantIdAndIsDeletedFalseOrderByBusNoAscVersionDesc(GeneralSharedConstants.PLATFORM_TENANT_ID);
            tenantRows = prtTemplateRepository
                .findByTenantIdAndIsDeletedFalseOrderByBusNoAscVersionDesc(tenantId);
        } else {
            // Pre：archived 条件下推 DB，避免 findAll 后内存过滤（BR-PRT-012）。
            String archivedCode = PrintTemplateStatus.ARCHIVED.getCode();
            platformRows = prtTemplateRepository
                .findByTenantIdAndStatusNotAndIsDeletedFalseOrderByBusNoAscVersionDesc(
                    GeneralSharedConstants.PLATFORM_TENANT_ID, archivedCode);
            tenantRows = prtTemplateRepository
                .findByTenantIdAndStatusNotAndIsDeletedFalseOrderByBusNoAscVersionDesc(tenantId, archivedCode);
        }

        // Cmd：SDD §9 — 租户 busNo 覆盖平台；保留租户全量物理行供 App 聚合。
        Set<String> tenantBusNos = new HashSet<>();
        for (PrtTemplate row : tenantRows) {
            tenantBusNos.add(row.getBusNo());
        }
        Map<String, PrtTemplate> platformByBusNo = new LinkedHashMap<>();
        for (PrtTemplate platformRow : platformRows) {
            if (!tenantBusNos.contains(platformRow.getBusNo())) {
                platformByBusNo.putIfAbsent(platformRow.getBusNo(), platformRow);
            }
        }

        List<PrtTemplate> merged = new ArrayList<>(tenantRows);
        merged.addAll(platformByBusNo.values());
        return merged;
    }

    /**
     * 按场景码取最新 published（租户优先，平台 fallback；DOC-CONFLICT：Flyway scenario_code + preparePrint）。
     *
     * @param scenarioCode 场景码
     * @param tenantId     租户
     * @return published 行或 null
     */
    public PrtTemplate findPublishedByScenarioCode(String scenarioCode, Long tenantId) {
        String code = trimToNull(scenarioCode);
        if (code == null || tenantId == null) {
            return null;
        }
        PrtTemplate tenantRow = prtTemplateRepository
            .findFirstByTenantIdAndScenarioCodeAndStatusAndIsDeletedFalseOrderByVersionDesc(
                tenantId, code, PrintTemplateStatus.PUBLISHED.getCode());
        if (tenantRow != null) {
            return tenantRow;
        }
        if (GeneralSharedConstants.PLATFORM_TENANT_ID != tenantId) {
            return prtTemplateRepository
                .findFirstByTenantIdAndScenarioCodeAndStatusAndIsDeletedFalseOrderByVersionDesc(
                    GeneralSharedConstants.PLATFORM_TENANT_ID, code, PrintTemplateStatus.PUBLISHED.getCode());
        }
        return null;
    }
}
