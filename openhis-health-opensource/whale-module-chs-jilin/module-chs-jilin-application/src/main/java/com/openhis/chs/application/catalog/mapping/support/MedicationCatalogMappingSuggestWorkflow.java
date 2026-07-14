/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.chs.application.catalog.mapping.support;

import com.openhis.chs.domain.share.enums.ValiFlag;
import com.openhis.chs.domain.share.enums.ListType;
import static com.openhis.whale.core.application.util.StringUtils.trimToNull;

import com.openhis.chs.domain.catalog.access.Cat1312Invoker;
import com.openhis.chs.domain.share.constants.ChsCatalogConstants;
import com.openhis.chs.domain.catalog.access.ChsInsuranceContractReader;
import com.openhis.chs.domain.catalog.command.Refresh1312Command;
import com.openhis.chs.domain.catalog.command.SuggestMedicationCatalogMappingCommand;
import com.openhis.chs.domain.catalog.entity.YbNationalCatalogItem;
import com.openhis.chs.domain.catalog.result.CatalogMatchCandidateResult;
import com.openhis.chs.domain.catalog.result.MedicationCatalogMappingSuggestResult;
import com.openhis.chs.domain.catalog.service.YbNationalCatalogQueryService;
import com.openhis.chs.domain.contract.ChsContractSnapshot;
import com.openhis.chs.domain.share.constants.ChsErrorCodes;
import com.openhis.whale.core.application.exception.BusinessException;
import com.openhis.whale.core.infrastructure.multitenancy.TenantUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 药品医保目录智能推荐编排（本地镜像打分，可触发 1312 补库）。
 * <p>本地候选池不足且允许补库时，调用 {@link Cat1312Invoker} 拉取平台数据后重新打分。</p>
 */
@Component
@RequiredArgsConstructor
public class MedicationCatalogMappingSuggestWorkflow {

    private final ChsInsuranceContractReader contractReader;
    private final YbNationalCatalogQueryService catalogQueryService;
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
        String level = CatalogMedicationMatchScorer.resolveRecommendLevel(candidates);
        return new MedicationCatalogMappingSuggestResult()
            .setRecommendLevel(level)
            .setHint(CatalogMedicationMatchScorer.buildHint(level, candidates))
            .setCandidates(candidates);
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
}
