/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.chs.bridge.foundation.platformdict.workflow;

import static com.openhis.chs.bridge.foundation.platformdict.support.DictPlatformSupport.normalizeParent;
import static com.openhis.whale.core.application.util.StringUtils.isNotEmpty;

import com.openhis.chs.bridge.foundation.platformdict.invoker.Dict1901PageInvoker;
import com.openhis.chs.bridge.foundation.platformdict.support.DictPlatformSupport;
import com.openhis.chs.bridge.foundation.platformdict.support.DictPlatformSupport.DictContractContext;
import com.openhis.chs.domain.catalog.access.ChsContractPropertiesAccess;
import com.openhis.chs.domain.share.constants.PlatformDictConstants;
import com.openhis.chs.domain.platformdict.access.PlatformDictSyncAccess;
import com.openhis.chs.domain.platformdict.command.SyncPlatformDictFrom1901Command;
import com.openhis.chs.domain.platformdict.command.UpsertPlatformDictRowCommand;
import com.openhis.chs.domain.platformdict.entity.YbPlatformDictSyncLog;
import com.openhis.chs.domain.platformdict.result.PlatformDictSyncResult;
import com.openhis.chs.domain.platformdict.service.YbPlatformDictDomainService;
import com.openhis.chs.domain.share.constants.ChsErrorCodes;
import com.openhis.chs.gateway.support.ChsCurrentOperatorResolver;
import com.openhis.whale.core.application.exception.BusinessException;
import com.openhis.whale.core.application.util.StringUtils;
import com.openhis.whale.core.infrastructure.multitenancy.TenantUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Queue;
import java.util.Set;

/**
 * 【1901】平台字典手动同步 bridge 编排实现（P15）。
 *
 * <p><b>职责：</b>BFS 分页拉取 1901 字典、零行时探测区划组合后重试，并写入同步日志。</p>
 * <p><b>实现端口：</b>{@link com.openhis.chs.domain.platformdict.access.PlatformDictSyncAccess}</p>
 * <p><b>调用方：</b>{@code InsurancePlatformDictAppService}</p>
 * <p><b>架构边界：</b>bridge 层组装 gateway 出站上下文；application 只依赖 domain 端口。</p>
 * <p><b>迁移阶段：</b>P15</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class Dict1901SyncWorkflow implements PlatformDictSyncAccess {

    private final DictPlatformSupport dictPlatformSupport;
    private final Dict1901PageInvoker dict1901PageInvoker;
    private final ChsContractPropertiesAccess contractPropertiesAccess;
    private final ChsCurrentOperatorResolver currentOperatorResolver;
    private final YbPlatformDictDomainService platformDictDomainService;

    @Override
    @Transactional
    public PlatformDictSyncResult sync(SyncPlatformDictFrom1901Command command) {
        if (command == null) {
            throw new BusinessException(ChsErrorCodes.CONTRACT_INVALID, "同步请求不能为空");
        }
        DictContractContext ctx = dictPlatformSupport.resolveContract(command.getContractBusNo(), command.getContractId());
        Long contractId = ctx.contract().id();
        String contractBusNo = ctx.busNo();
        Long tenantId = TenantUtils.getCurrentTenantId();
        Long operatorId = currentOperatorResolver.requireOperatorId("请先登录后再同步医保字典");

        String dictType = dictPlatformSupport.resolveDictType(command.getDictType());
        String queryAdmdvs = dictPlatformSupport.resolveQueryAdmdvs(command.getQueryAdmdvs(), contractBusNo);
        boolean recursive = command.getRecursive() == null || Boolean.TRUE.equals(command.getRecursive());
        int pageSize = dictPlatformSupport.resolvePageSize(command.getPageSize());
        String rootParent = StringUtils.defaultString(command.getParentValue(), "");

        LocalDateTime startedAt = LocalDateTime.now();
        YbPlatformDictSyncLog syncLog = new YbPlatformDictSyncLog();
        syncLog.setTenantId(tenantId);
        syncLog.setDictType(dictType);
        syncLog.setQueryAdmdvs(queryAdmdvs);
        syncLog.setParentValue(rootParent);
        syncLog.setRecursive(recursive);
        syncLog.setContractId(contractId);
        syncLog.setRowsUpserted(0);
        syncLog.setStartedAt(startedAt);
        syncLog.setCreatedBy(operatorId);
        syncLog.setUpdatedBy(operatorId);

        int totalUpserted = 0;
        boolean failurePersisted = false;
        try {
            totalUpserted = pullAndUpsert(
                contractBusNo, contractId, tenantId, operatorId,
                dictType, queryAdmdvs, rootParent, recursive, pageSize);
            if (totalUpserted == 0) {
                Admdvs1901ProbeCandidate probeHit = probeSeed(
                    contractBusNo, dictType, queryAdmdvs, rootParent, pageSize, operatorId);
                if (probeHit != null) {
                    dictType = probeHit.dictType();
                    queryAdmdvs = probeHit.queryAdmdvs();
                    rootParent = probeHit.parentValue();
                    syncLog.setDictType(dictType);
                    syncLog.setQueryAdmdvs(queryAdmdvs);
                    syncLog.setParentValue(rootParent);
                    totalUpserted = pullAndUpsert(
                        contractBusNo, contractId, tenantId, operatorId,
                        dictType, queryAdmdvs, rootParent, recursive, pageSize);
                }
            }
            syncLog.setRowsUpserted(totalUpserted);
            syncLog.setFinishedAt(LocalDateTime.now());
            if (totalUpserted == 0) {
                int medTypeProbeSize = dict1901PageInvoker.fetchPage(
                    contractBusNo, PlatformDictConstants.DICT_TYPE_MED_TYPE, "100000", "",
                    1, 10, operatorId, PlatformDictConstants.VALI_FLAG_KEY).size();
                String hint = buildZeroRowsHint(dictType, queryAdmdvs, medTypeProbeSize);
                syncLog.setStatus(PlatformDictConstants.SYNC_STATUS_FAILED);
                syncLog.setErrorMessage(hint);
                platformDictDomainService.saveSyncLog(syncLog);
                failurePersisted = true;
                throw new BusinessException(ChsErrorCodes.CONTRACT_INVALID, hint);
            }
            syncLog.setStatus(PlatformDictConstants.SYNC_STATUS_SUCCESS);
            platformDictDomainService.saveSyncLog(syncLog);
            log.info("[platform-dict][1901] dictType={} queryAdmdvs={} rowsUpserted={}",
                dictType, queryAdmdvs, totalUpserted);
            return new PlatformDictSyncResult()
                .setDictType(dictType)
                .setQueryAdmdvs(queryAdmdvs)
                .setRowsUpserted(totalUpserted)
                .setStatus(PlatformDictConstants.SYNC_STATUS_SUCCESS)
                .setFinishedAt(syncLog.getFinishedAt());
        } catch (RuntimeException ex) {
            if (!failurePersisted) {
                syncLog.setRowsUpserted(totalUpserted);
                syncLog.setStatus(PlatformDictConstants.SYNC_STATUS_FAILED);
                syncLog.setErrorMessage(ex.getMessage());
                syncLog.setFinishedAt(LocalDateTime.now());
                platformDictDomainService.saveSyncLog(syncLog);
            }
            throw ex;
        }
    }

    private int pullAndUpsert(
            String contractBusNo,
            Long contractId,
            Long tenantId,
            Long operatorId,
            String dictType,
            String queryAdmdvs,
            String rootParent,
            boolean recursive,
            int pageSize) {
        Queue<String> parentQueue = new ArrayDeque<>();
        parentQueue.add(normalizeParent(rootParent));
        Set<String> visitedParents = new HashSet<>();
        LocalDateTime syncedAt = LocalDateTime.now();
        int total = 0;
        boolean recursiveEnabled = recursive;

        while (!parentQueue.isEmpty()) {
            String parentValue = parentQueue.poll();
            if (!visitedParents.add(parentValue)) {
                continue;
            }
            int pageNum = 1;
            while (true) {
                List<UpsertPlatformDictRowCommand> rows = dict1901PageInvoker.fetchPage(
                    contractBusNo, dictType, queryAdmdvs, parentValue, pageNum, pageSize, operatorId,
                    PlatformDictConstants.VALI_FLAG_KEY);
                if (rows.isEmpty()) {
                    break;
                }
                total += platformDictDomainService.bulkUpsert(
                    tenantId, operatorId, contractId, queryAdmdvs, syncedAt, rows);
                if (recursiveEnabled && pageNum == 1 && parentValue.isEmpty()) {
                    boolean hierarchical = rows.stream()
                        .anyMatch(row -> isNotEmpty(row.getParentValue()));
                    if (!hierarchical) {
                        recursiveEnabled = false;
                    }
                }
                if (recursiveEnabled) {
                    for (UpsertPlatformDictRowCommand row : rows) {
                        if (!parentValue.equals(normalizeParent(row.getParentValue()))) {
                            continue;
                        }
                        if (isNotEmpty(row.getDictValue())) {
                            parentQueue.add(row.getDictValue().trim());
                        }
                    }
                }
                if (rows.size() < pageSize) {
                    break;
                }
                pageNum++;
            }
        }
        return total;
    }

    private Admdvs1901ProbeCandidate probeSeed(
            String contractBusNo,
            String dictType,
            String queryAdmdvs,
            String rootParent,
            int pageSize,
            Long operatorId) {
        String contractAdmVs = contractPropertiesAccess.findAdmVs(contractBusNo).orElse(null);
        List<Admdvs1901ProbeCandidate> candidates = buildProbeCandidates(
            dictType, queryAdmdvs, rootParent, contractAdmVs);
        for (Admdvs1901ProbeCandidate candidate : candidates) {
            try {
                List<UpsertPlatformDictRowCommand> rows = dict1901PageInvoker.fetchPage(
                    contractBusNo,
                    candidate.dictType(),
                    candidate.queryAdmdvs(),
                    candidate.parentValue(),
                    1,
                    pageSize,
                    operatorId,
                    candidate.valiFlagKey());
                if (!rows.isEmpty()) {
                    return candidate;
                }
            } catch (RuntimeException ignored) {
                // try next probe combination
            }
        }
        return null;
    }

    private static List<Admdvs1901ProbeCandidate> buildProbeCandidates(
            String dictType,
            String queryAdmdvs,
            String rootParent,
            String contractAdmVs) {
        List<Admdvs1901ProbeCandidate> candidates = new ArrayList<>();
        String vali = PlatformDictConstants.VALI_FLAG_KEY;
        addProbeCandidate(candidates, PlatformDictConstants.DICT_TYPE_INSUPLC_ADMDVS_FIELD, "100000", "", vali);
        addProbeCandidate(candidates, PlatformDictConstants.DICT_TYPE_INSUPLC_ADMDVS_FIELD, "220000", "", vali);
        addProbeCandidate(candidates, PlatformDictConstants.DICT_TYPE_INSUPLC_ADMDVS_FIELD, "229900", "", vali);
        addProbeCandidate(candidates, PlatformDictConstants.DICT_TYPE_MDTRTAREA_ADMVS, "100000", "", vali);
        addProbeCandidate(candidates, PlatformDictConstants.DICT_TYPE_MDTRTAREA_ADMVS, "220000", "", vali);
        addProbeCandidate(candidates, PlatformDictConstants.DICT_TYPE_MDTRTAREA_ADMVS, "229900", "", vali);
        addProbeCandidate(candidates, PlatformDictConstants.DICT_TYPE_ADMDVS, "100000", "", vali);
        addProbeCandidate(candidates, PlatformDictConstants.DICT_TYPE_ADMDVS, "220000", "", vali);
        addProbeCandidate(candidates, PlatformDictConstants.DICT_TYPE_ADMDVS, "220000", "220000", vali);
        addProbeCandidate(candidates, PlatformDictConstants.DICT_TYPE_ADMDVS, "229900", "220000", vali);
        addProbeCandidate(candidates, PlatformDictConstants.DICT_TYPE_ADMDVS, "229900", "", vali);
        addProbeCandidate(candidates, PlatformDictConstants.DICT_TYPE_ADMDVS, "220199", "", vali);
        addProbeCandidate(candidates, PlatformDictConstants.DICT_TYPE_ADMDVS, "220199", "220000", vali);
        if (isNotEmpty(contractAdmVs)) {
            String admVs = contractAdmVs.trim();
            addProbeCandidate(candidates, PlatformDictConstants.DICT_TYPE_ADMDVS, admVs, "", vali);
            addProbeCandidate(candidates, PlatformDictConstants.DICT_TYPE_ADMDVS, admVs, "220000", vali);
            if (admVs.length() >= 4) {
                String prefix4 = admVs.substring(0, 4) + "00";
                addProbeCandidate(candidates, PlatformDictConstants.DICT_TYPE_ADMDVS, prefix4, "", vali);
                addProbeCandidate(candidates, PlatformDictConstants.DICT_TYPE_ADMDVS, admVs, prefix4, vali);
            }
        }
        addProbeCandidate(candidates, PlatformDictConstants.DICT_TYPE_INSUPLC_ADMDVS, "100000", "", vali);
        addProbeCandidate(candidates, PlatformDictConstants.DICT_TYPE_INSUPLC_ADMDVS, "220000", "", vali);
        addProbeCandidate(candidates, PlatformDictConstants.DICT_TYPE_INSUPLC_ADMDVS, "229900", "220000", vali);
        addProbeCandidate(candidates, PlatformDictConstants.DICT_TYPE_INSUPLC_ADMDVS, "229900", "", vali);
        addProbeCandidate(candidates, dictType, queryAdmdvs, rootParent, vali);
        return candidates;
    }

    private static String buildZeroRowsHint(String dictType, String queryAdmdvs, int medTypeProbeSize) {
        String base = "1901 未返回可入库的区划字典（dictType=" + dictType
            + "，queryAdmdvs=" + queryAdmdvs + "），已自动探测 ADMDVS/INSUPLC_ADMDVS/insuplc_admdvs 等组合均为空。";
        if (medTypeProbeSize > 0) {
            return base + " 1901 同步链路正常（med_type 可返回 " + medTypeProbeSize
                + " 条，可先同步 med_type 验证入库）。区划字典 type 请向吉林局点确认。";
        }
        return base + " med_type 探测也为空，请确认已签到且测试环境 1901 字典已开通。";
    }

    private static void addProbeCandidate(
            List<Admdvs1901ProbeCandidate> candidates,
            String dictType,
            String queryAdmdvs,
            String parentValue,
            String valiFlagKey) {
        Admdvs1901ProbeCandidate candidate = new Admdvs1901ProbeCandidate(
            StringUtils.trimToEmpty(dictType),
            StringUtils.trimToEmpty(queryAdmdvs),
            StringUtils.defaultString(parentValue, ""),
            valiFlagKey);
        if (candidates.stream().anyMatch(candidate::equals)) {
            return;
        }
        candidates.add(candidate);
    }

    private record Admdvs1901ProbeCandidate(
            String dictType,
            String queryAdmdvs,
            String parentValue,
            String valiFlagKey) {
    }
}
