/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.chs.application.catalog.mapping.support;

import com.zxplat.plugin.chs.domain.share.enums.ValiFlag;
import com.zxplat.plugin.chs.domain.share.enums.ListType;
import static com.zxplat.framework.core.application.util.StringUtils.trimToNull;

import com.zxplat.plugin.chs.domain.catalog.access.Cat1312Invoker;
import com.zxplat.plugin.chs.domain.share.constants.ChsCatalogConstants;
import com.zxplat.plugin.chs.domain.catalog.access.ChsInsuranceContractReader;
import com.zxplat.plugin.chs.domain.catalog.command.Refresh1312Command;
import com.zxplat.plugin.chs.domain.catalog.command.SuggestMedicationCatalogMappingCommand;
import com.zxplat.plugin.chs.domain.catalog.entity.YbNationalCatalogItem;
import com.zxplat.plugin.chs.domain.catalog.result.CatalogMatchCandidateResult;
import com.zxplat.plugin.chs.domain.catalog.result.MedicationCatalogMappingSuggestResult;
import com.zxplat.plugin.chs.domain.catalog.service.YbNationalCatalogQueryService;
import com.zxplat.plugin.chs.domain.catalog.support.NationalCatalogSelectableComposer;
import com.zxplat.plugin.chs.domain.contract.ChsContractSnapshot;
import com.zxplat.plugin.chs.domain.notice.queryservice.NationalCatalogOverlayQueryService;
import com.zxplat.plugin.chs.domain.share.constants.ChsErrorCodes;
import com.zxplat.framework.core.application.exception.BusinessException;
import com.zxplat.framework.core.infrastructure.multitenancy.TenantUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * 药品医保目录智能推荐编排（默认仅本地镜像打分）。
 * <p>仅当 {@code queryPlatformIfMiss=true} 且本地候选池为空时，才调用 {@link Cat1312Invoker}
 * 拉取平台数据后重新打分；选码弹窗应传 false。出参再经局文 overlay 合成为可选性。</p>
 */
@Component
@RequiredArgsConstructor
public class MedicationCatalogMappingSuggestWorkflow {

    private final ChsInsuranceContractReader contractReader;
    private final YbNationalCatalogQueryService catalogQueryService;
    private final NationalCatalogOverlayQueryService overlayQueryService;
    private final Cat1312Invoker cat1312Invoker;

    /**
     * 按院内药品信息推荐国家医保目录编码候选（仅推荐，不自动落库）。
     */
    public MedicationCatalogMappingSuggestResult suggest(SuggestMedicationCatalogMappingCommand command) {
        if (command == null || trimToNull(command.getContractBusNo()) == null) {
            throw new BusinessException(ChsErrorCodes.CONTRACT_INVALID, "contractBusNo 不能为空");
        }
        ChsContractSnapshot contract = contractReader.requireContract(command.getContractBusNo(), null);
        String contractBusNo = contractReader.requireContractBusNo(contract);
        Long contractId = contract.id();
        Long tenantId = TenantUtils.getCurrentTenantId();

        String listType = ListType.WESTERN_DRUG.getCode();
        if (trimToNull(command.getListType()) != null) {
            listType = command.getListType().trim();
        }
        int max = ChsCatalogConstants.MEDICATION_MATCH_DEFAULT_MAX;
        if (command.getMaxCandidates() != null && command.getMaxCandidates() > 0) {
            max = command.getMaxCandidates();
        }

        List<YbNationalCatalogItem> pool = loadMatchPool(
            command, contractId, tenantId, contractBusNo, listType);
        List<CatalogMatchCandidateResult> candidates = CatalogMedicationMatchScorer.scoreAndSort(pool, command, max);
        overlayQueryService.decorateSelectable(
            tenantId, contractId, listType, candidates, LocalDate.now());
        candidates = dropPlatformUnusable(candidates);
        String level = resolveLevelAfterSelectable(candidates);
        return new MedicationCatalogMappingSuggestResult()
            .setRecommendLevel(level)
            .setHint(CatalogMedicationMatchScorer.buildHint(level, candidates))
            .setCandidates(candidates);
    }

    /**
     * 选码推荐不展示平台停用/已过期行（局文停用仍保留为不可选提示）。
     */
    private static List<CatalogMatchCandidateResult> dropPlatformUnusable(
            List<CatalogMatchCandidateResult> candidates) {
        if (candidates == null || candidates.isEmpty()) {
            return candidates == null ? List.of() : candidates;
        }
        List<CatalogMatchCandidateResult> kept = new ArrayList<>(candidates.size());
        for (CatalogMatchCandidateResult row : candidates) {
            if (row == null) {
                continue;
            }
            String reason = row.getUnselectableReason();
            if (NationalCatalogSelectableComposer.REASON_PLATFORM_EXPIRED.equals(reason)
                || NationalCatalogSelectableComposer.REASON_PLATFORM_INVALID.equals(reason)) {
                continue;
            }
            kept.add(row);
        }
        return kept;
    }

    /**
     * 推荐级别仅看可选候选，避免高置信自动落到局文已停用码。
     */
    private static String resolveLevelAfterSelectable(List<CatalogMatchCandidateResult> candidates) {
        if (candidates == null || candidates.isEmpty()) {
            return "NO_MATCH";
        }
        List<CatalogMatchCandidateResult> selectable = new ArrayList<>();
        for (CatalogMatchCandidateResult row : candidates) {
            if (row != null && !Boolean.FALSE.equals(row.getIsSelectable())) {
                selectable.add(row);
            }
        }
        if (selectable.isEmpty()) {
            return "NO_MATCH";
        }
        return CatalogMedicationMatchScorer.resolveRecommendLevel(selectable);
    }

    /**
     * 加载本地匹配候选池；池为空且允许补库时触发 1312 gateway 拉取后再查。
     */
    private List<YbNationalCatalogItem> loadMatchPool(
            SuggestMedicationCatalogMappingCommand command,
            Long contractId,
            Long tenantId,
            String contractBusNo,
            String listType) {
        String keyword = trimToNull(command.getName());
        String aprvno = trimToNull(command.getApprovalNumber());
        String spec = trimToNull(command.getTotalVolume());
        String codeExact = trimToNull(command.getNationalDrugCode());
        // 名称框直接输医保码：精确码过滤；关键字不再重复 ILIKE，避免双条件
        if (codeExact == null && looksLikeHilistCode(keyword)) {
            codeExact = keyword;
            keyword = null;
        } else if (codeExact != null && codeExact.equalsIgnoreCase(keyword)) {
            keyword = null;
        }
        List<YbNationalCatalogItem> pool = catalogQueryService.listForMedicationMatch(
            contractId,
            tenantId,
            listType,
            keyword,
            aprvno,
            spec,
            codeExact,
            ChsCatalogConstants.MEDICATION_MATCH_CANDIDATE_POOL);
        if (!pool.isEmpty() || !command.isQueryPlatformIfMiss()) {
            return pool;
        }
        if (YbNationalCatalogQueryService.prefers1301Detail(aprvno, spec)) {
            return pool;
        }
        Refresh1312Command refresh = new Refresh1312Command();
        refresh.setContractBusNo(contractBusNo);
        refresh.setListType(listType);
        refresh.setValiFlag(ValiFlag.VALID.getCode());
        refresh.setHilistCode(codeExact);
        refresh.setHilistName(keyword);
        refresh.setPlatformPageNum(1);
        refresh.setPlatformPageSize(ChsCatalogConstants.DEFAULT_PLATFORM_PAGE_SIZE);
        cat1312Invoker.invoke(refresh);
        return catalogQueryService.listForMedicationMatch(
            contractId,
            tenantId,
            listType,
            keyword,
            aprvno,
            spec,
            codeExact,
            ChsCatalogConstants.MEDICATION_MATCH_CANDIDATE_POOL);
    }

    /**
     * 判断输入是否像医保目录编码（字母数字、长度 6～50、无中文），用于名称框直输编码。
     */
    private static boolean looksLikeHilistCode(String value) {
        if (value == null || value.isBlank()) {
            return false;
        }
        String trimmed = value.trim();
        int len = trimmed.length();
        if (len < 6 || len > 50) {
            return false;
        }
        for (int i = 0; i < len; i++) {
            char c = trimmed.charAt(i);
            boolean digit = c >= '0' && c <= '9';
            boolean upper = c >= 'A' && c <= 'Z';
            boolean lower = c >= 'a' && c <= 'z';
            if (!digit && !upper && !lower) {
                return false;
            }
        }
        return true;
    }
}
