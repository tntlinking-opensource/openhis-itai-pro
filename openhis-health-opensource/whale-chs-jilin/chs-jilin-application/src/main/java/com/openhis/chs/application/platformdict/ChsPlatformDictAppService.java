/*
 * Copyright © 2026 OpenHIS Contributors
 */

package com.openhis.chs.application.platformdict;

import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import com.openhis.chs.api.ChsInfNo;
import com.openhis.chs.api.InsuranceGatewayClient;
import com.openhis.chs.api.InvokeContext;
import com.openhis.chs.api.ResultBody;
import com.openhis.chs.api.support.ChsOutputParser;
import com.openhis.chs.application.gateway.session.ChsSignCredentialResolver;
import com.openhis.chs.application.gateway.support.ChsCurrentOperatorResolver;
import com.openhis.chs.application.outpatient.support.ChsPlatformResultSupport;
import com.openhis.chs.domain.ChsErrorCodes;
import com.openhis.chs.domain.catalog.port.ChsContractPropertiesPort;
import com.openhis.chs.domain.catalog.port.ChsContractSnapshot;
import com.openhis.chs.domain.catalog.port.ChsInsuranceContractPort;
import com.openhis.chs.domain.platformdict.PlatformDictConstants;
import com.openhis.chs.domain.platformdict.command.UpsertPlatformDictRowCommand;
import com.openhis.chs.domain.platformdict.dto.request.LookupPlatformDictLabelsRequest;
import com.openhis.chs.domain.platformdict.dto.request.QueryPlatformDictPageRequest;
import com.openhis.chs.domain.platformdict.dto.request.SyncPlatformDictFrom1901Request;
import com.openhis.chs.domain.platformdict.dto.response.PlatformDictItemDto;
import com.openhis.chs.domain.platformdict.dto.response.PlatformDictLabelsDto;
import com.openhis.chs.domain.platformdict.dto.response.PlatformDictSyncLogDto;
import com.openhis.chs.domain.platformdict.dto.response.PlatformDictSyncResultDto;
import com.openhis.chs.domain.platformdict.entity.YbPlatformDictItem;
import com.openhis.chs.domain.platformdict.entity.YbPlatformDictSyncLog;
import com.openhis.chs.domain.platformdict.facade.ChsPlatformDictOperations;
import com.openhis.chs.domain.platformdict.repository.YbPlatformDictBulkUpsertRepository;
import com.openhis.chs.domain.platformdict.repository.YbPlatformDictSyncLogRepository;
import com.openhis.chs.domain.platformdict.service.YbPlatformDictLookupService;
import com.openhis.chs.domain.platformdict.service.YbPlatformDictPageQueryService;
import com.openhis.whale.core.application.dto.PagedResult;
import com.openhis.whale.core.application.exception.BusinessException;
import com.openhis.whale.core.infrastructure.multitenancy.TenantUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Set;

/**
 * 1901 平台字典：手动同步入库 + 本地 value→label 查询。
 *
 * <p>HTTP：embedded 为 {@code /api/app/insurancePlatformDict/*}（bridge）。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
@ConditionalOnProperty(name = "openhis.modules.chs.enabled", havingValue = "true")
public class ChsPlatformDictAppService implements ChsPlatformDictOperations {

    private static final String DEFAULT_QUERY_ADMDVS = "100000";

    private final InsuranceGatewayClient gatewayClient;
    private final ChsInsuranceContractPort contractPort;
    private final ChsContractPropertiesPort contractPropertiesPort;
    private final ChsCurrentOperatorResolver currentOperatorResolver;
    private final ChsSignCredentialResolver signCredentialResolver;
    private final YbPlatformDictBulkUpsertRepository bulkUpsertRepository;
    private final YbPlatformDictSyncLogRepository syncLogRepository;
    private final YbPlatformDictLookupService lookupService;
    private final YbPlatformDictPageQueryService pageQueryService;

    @Override
    @Transactional
    public PlatformDictSyncResultDto syncFrom1901(SyncPlatformDictFrom1901Request request) {
        if (request == null) {
            throw new BusinessException(ChsErrorCodes.CONTRACT_INVALID, "同步请求不能为空");
        }
        ChsContractSnapshot contract = contractPort.requireContract(
            request.getContractBusNo(), request.getContractId());
        String contractBusNo = contractPort.requireContractBusNo(contract);
        Long contractId = contract.id();
        Long tenantId = TenantUtils.getCurrentTenantId();
        Long operatorId = currentOperatorResolver.requireOperatorId("请先登录后再同步医保字典");

        String requestedDictType = resolveDictType(request.getDictType());
        String dictType = requestedDictType;
        String queryAdmdvs = resolveQueryAdmdvs(request.getQueryAdmdvs(), contractBusNo);
        boolean recursive = request.getRecursive() == null || Boolean.TRUE.equals(request.getRecursive());
        int pageSize = resolvePageSize(request.getPageSize());
        String rootParent = StringUtils.defaultString(request.getParentValue(), "");

        LocalDateTime startedAt = LocalDateTime.now();
        YbPlatformDictSyncLog syncLog = new YbPlatformDictSyncLog();
        syncLog.setTenantId(tenantId);
        syncLog.setDictType(requestedDictType);
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
        InvokeContext invokeContext = buildInvokeContext(operatorId);
        try {
            String storageDictType = requestedDictType;
            String valiFlagKey = PlatformDictConstants.VALI_FLAG_KEY_SPEC;
            totalUpserted = pullAndUpsert1901(
                contractBusNo, contractId, tenantId, operatorId,
                dictType, storageDictType, queryAdmdvs, rootParent, recursive, pageSize, invokeContext,
                valiFlagKey);
            if (totalUpserted == 0) {
                Dict1901ProbeCandidate probeHit = probe1901Context(
                    contractBusNo, dictType, queryAdmdvs, rootParent, pageSize, invokeContext);
                if (probeHit != null) {
                    queryAdmdvs = probeHit.queryAdmdvs();
                    rootParent = probeHit.parentValue();
                    valiFlagKey = probeHit.valiFlagKey();
                    syncLog.setQueryAdmdvs(queryAdmdvs);
                    syncLog.setParentValue(rootParent);
                    totalUpserted = pullAndUpsert1901(
                        contractBusNo, contractId, tenantId, operatorId,
                        dictType, storageDictType, queryAdmdvs, rootParent, recursive, pageSize, invokeContext,
                        valiFlagKey);
                }
            }
            syncLog.setRowsUpserted(totalUpserted);
            syncLog.setFinishedAt(LocalDateTime.now());
            if (totalUpserted == 0) {
                int medTypeProbeSize = fetch1901Page(
                    contractBusNo, PlatformDictConstants.DICT_TYPE_MED_TYPE, "100000", "", 1, 10, invokeContext,
                    PlatformDictConstants.VALI_FLAG_KEY_SPEC).size();
                String hint = buildZeroRowsHint(requestedDictType, queryAdmdvs, medTypeProbeSize);
                syncLog.setStatus(PlatformDictConstants.SYNC_STATUS_FAILED);
                syncLog.setErrorMessage(hint);
                syncLogRepository.save(syncLog);
                failurePersisted = true;
                throw new BusinessException(ChsErrorCodes.CONTRACT_INVALID, hint);
            }
            syncLog.setStatus(PlatformDictConstants.SYNC_STATUS_SUCCESS);
            syncLogRepository.save(syncLog);
            log.info("[platform-dict][1901] dictType={} storageDictType={} queryAdmdvs={} rowsUpserted={}",
                dictType, storageDictType, queryAdmdvs, totalUpserted);
            return new PlatformDictSyncResultDto()
                .setDictType(storageDictType)
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
                syncLogRepository.save(syncLog);
            }
            throw ex;
        }
    }

    @Override
    public PlatformDictLabelsDto lookupDictLabels(LookupPlatformDictLabelsRequest request) {
        String dictType = resolveDictType(request != null ? request.getDictType() : null);
        List<String> values = request != null && request.getDictValues() != null
            ? request.getDictValues()
            : List.of();
        Long tenantId = TenantUtils.getCurrentTenantId();
        Map<String, String> labels = lookupService.lookupLabels(tenantId, dictType, values);
        return new PlatformDictLabelsDto()
            .setDictType(dictType)
            .setLabelsByValue(labels);
    }

    @Override
    public PlatformDictSyncLogDto getLastSyncLog(String dictType) {
        String resolved = resolveDictType(dictType);
        Long tenantId = TenantUtils.getCurrentTenantId();
        return syncLogRepository
            .findFirstByTenantIdAndDictTypeAndIsDeletedFalseOrderByStartedAtDesc(tenantId, resolved)
            .map(this::toSyncLogDto)
            .orElse(null);
    }

    @Override
    public PagedResult<PlatformDictItemDto> queryPlatformDictPage(QueryPlatformDictPageRequest request) {
        if (request == null) {
            throw new BusinessException(ChsErrorCodes.CONTRACT_INVALID, "查询请求不能为空");
        }
        String dictType = resolveDictType(request.getDictType());
        int pageNo = request.getPageNo() != null && request.getPageNo() > 0 ? request.getPageNo() : 1;
        int pageSize = request.getPageSize() != null && request.getPageSize() > 0
            ? Math.min(request.getPageSize(), 500)
            : 50;
        Long tenantId = TenantUtils.getCurrentTenantId();
        Page<YbPlatformDictItem> page = pageQueryService.page(
            tenantId, dictType, request.getKeyword(), pageNo, pageSize);
        List<PlatformDictItemDto> rows = new ArrayList<>(page.getContent().size());
        for (YbPlatformDictItem item : page.getContent()) {
            rows.add(toItemDto(item));
        }
        PagedResult<PlatformDictItemDto> result = new PagedResult<>();
        result.setContent(rows);
        result.setTotalCount(page.getTotalElements());
        result.setPageNo(page.getNumber() + 1);
        result.setPageSize(page.getSize());
        return result;
    }

    private PlatformDictItemDto toItemDto(YbPlatformDictItem item) {
        return new PlatformDictItemDto()
            .setId(item.getId())
            .setDictType(item.getDictType())
            .setDictValue(item.getDictValue())
            .setDictLabel(item.getDictLabel())
            .setParentValue(item.getParentValue())
            .setSortOrder(item.getSortOrder())
            .setValiFlag(item.getValiFlag())
            .setQueryAdmdvs(item.getQueryAdmdvs())
            .setSyncedAt(item.getSyncedAt());
    }

    private int pullAndUpsert1901(
            String contractBusNo,
            Long contractId,
            Long tenantId,
            Long operatorId,
            String apiDictType,
            String storageDictType,
            String queryAdmdvs,
            String rootParent,
            boolean recursive,
            int pageSize,
            InvokeContext invokeContext,
            String valiFlagKey) {
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
                List<UpsertPlatformDictRowCommand> rows = fetch1901Page(
                    contractBusNo, apiDictType, queryAdmdvs, parentValue, pageNum, pageSize, invokeContext,
                    valiFlagKey);
                if (rows.isEmpty()) {
                    break;
                }
                applyStorageDictType(rows, storageDictType);
                total += bulkUpsertRepository.bulkUpsert(
                    tenantId, operatorId, contractId, queryAdmdvs, syncedAt, rows);
                if (recursiveEnabled && pageNum == 1 && parentValue.isEmpty()) {
                    boolean hierarchical = rows.stream()
                        .anyMatch(row -> StringUtils.isNotBlank(row.getParentValue()));
                    if (!hierarchical) {
                        recursiveEnabled = false;
                    }
                }
                if (recursiveEnabled) {
                    for (UpsertPlatformDictRowCommand row : rows) {
                        if (!parentValue.equals(normalizeParent(row.getParentValue()))) {
                            continue;
                        }
                        if (StringUtils.isNotBlank(row.getDictValue())) {
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

    private static String normalizeParent(String parentValue) {
        return StringUtils.defaultString(parentValue, "").trim();
    }

    private List<UpsertPlatformDictRowCommand> fetch1901Page(
            String contractBusNo,
            String dictType,
            String queryAdmdvs,
            String parentValue,
            int pageNum,
            int pageSize,
            InvokeContext invokeContext,
            String valiFlagKey) {
        JSONObject data = new JSONObject();
        data.put("type", dictType);
        data.put("parentValue", StringUtils.defaultString(parentValue, ""));
        data.put("admdvs", queryAdmdvs);
        data.put("date", LocalDate.now().toString());
        String effectiveValiFlagKey = StringUtils.isNotBlank(valiFlagKey)
            ? valiFlagKey
            : PlatformDictConstants.VALI_FLAG_KEY_SPEC;
        data.put(effectiveValiFlagKey, "1");
        data.put("page_num", pageNum);
        data.put("page_size", pageSize);
        JSONObject input = new JSONObject();
        input.put("data", data);

        InvokeContext pageContext = invokeContext.toBuilder()
            .insuplcAdmdvs(queryAdmdvs)
            .build();
        ResultBody body = gatewayClient.invoke(contractBusNo, ChsInfNo.DICT_QUERY, input, pageContext);
        ChsPlatformResultSupport.requireSuccess(body, ChsInfNo.DICT_QUERY);
        JSONArray list = ChsOutputParser.parse1901List(body);
        List<UpsertPlatformDictRowCommand> rows = new ArrayList<>(list.size());
        for (int i = 0; i < list.size(); i++) {
            JSONObject jo = list.getJSONObject(i);
            if (jo == null) {
                continue;
            }
            String value = firstNonBlank(jo, "value", "dict_value", "admdvs", "code");
            String label = firstNonBlank(jo, "label", "dict_label", "admdvs_name", "name");
            if (StringUtils.isBlank(value) || StringUtils.isBlank(label)) {
                continue;
            }
            UpsertPlatformDictRowCommand row = new UpsertPlatformDictRowCommand()
                .setDictType(dictType)
                .setDictValue(value.trim())
                .setDictLabel(label.trim())
                .setParentValue(firstNonBlank(jo, "parentValue", "parent_value"))
                .setValiFlag(firstNonBlank(jo, "Vali_Flag", "vali_flag"));
            Integer sort = jo.getInteger("sort");
            if (sort == null) {
                sort = jo.getInteger("sort_order");
            }
            row.setSortOrder(sort);
            Long version = jo.getLong("version");
            if (version == null) {
                version = jo.getLong("platform_version");
            }
            row.setPlatformVersion(version);
            rows.add(row);
        }
        return rows;
    }

    private Dict1901ProbeCandidate probe1901Context(
            String contractBusNo,
            String dictType,
            String queryAdmdvs,
            String rootParent,
            int pageSize,
            InvokeContext invokeContext) {
        String contractAdmVs = contractPropertiesPort.findAdmVs(contractBusNo).orElse(null);
        List<Dict1901ProbeCandidate> candidates = build1901ContextProbeCandidates(
            dictType, queryAdmdvs, rootParent, contractAdmVs);
        for (Dict1901ProbeCandidate candidate : candidates) {
            List<UpsertPlatformDictRowCommand> rows;
            try {
                rows = fetch1901Page(
                    contractBusNo,
                    dictType,
                    candidate.queryAdmdvs(),
                    candidate.parentValue(),
                    1,
                    pageSize,
                    invokeContext,
                    candidate.valiFlagKey());
            } catch (RuntimeException ex) {
                continue;
            }
            if (!rows.isEmpty()) {
                return candidate;
            }
        }
        return null;
    }

    private static List<Dict1901ProbeCandidate> build1901ContextProbeCandidates(
            String dictType,
            String queryAdmdvs,
            String rootParent,
            String contractAdmVs) {
        List<Dict1901ProbeCandidate> candidates = new ArrayList<>();
        for (String valiFlagKey : List.of(
            PlatformDictConstants.VALI_FLAG_KEY_SPEC,
            PlatformDictConstants.VALI_FLAG_KEY)) {
            addProbeCandidate(candidates, dictType, "100000", rootParent, valiFlagKey);
            addProbeCandidate(candidates, dictType, "220000", rootParent, valiFlagKey);
            addProbeCandidate(candidates, dictType, "229900", rootParent, valiFlagKey);
            addProbeCandidate(candidates, dictType, queryAdmdvs, rootParent, valiFlagKey);
            addProbeCandidate(candidates, dictType, queryAdmdvs, "", valiFlagKey);
            if (StringUtils.isNotBlank(contractAdmVs)) {
                String admVs = contractAdmVs.trim();
                addProbeCandidate(candidates, dictType, admVs, rootParent, valiFlagKey);
                addProbeCandidate(candidates, dictType, admVs, "", valiFlagKey);
            }
        }
        return candidates;
    }

    private static void applyStorageDictType(List<UpsertPlatformDictRowCommand> rows, String storageDictType) {
        if (rows == null || rows.isEmpty() || StringUtils.isBlank(storageDictType)) {
            return;
        }
        String resolved = storageDictType.trim();
        for (UpsertPlatformDictRowCommand row : rows) {
            row.setDictType(resolved);
        }
    }

    private static String buildZeroRowsHint(String dictType, String queryAdmdvs, int medTypeProbeSize) {
        String base = "1901 未返回可入库字典（type=" + dictType
            + "，admdvs=" + queryAdmdvs + "）。type 须取自基线规范「字典表」括号内英文代码（如 med_type、insutype）；"
            + "国标字典表不含医保区划 type。";
        if (medTypeProbeSize > 0) {
            return base + " 链路正常（med_type 可返回 " + medTypeProbeSize
                + " 条，可先同步 med_type 验证入库）。";
        }
        return base + " med_type 探测也为空，请确认已签到且测试环境 1901 已开通。";
    }

    private static void addProbeCandidate(
            List<Dict1901ProbeCandidate> candidates,
            String dictType,
            String queryAdmdvs,
            String parentValue,
            String valiFlagKey) {
        if (StringUtils.isBlank(dictType) || StringUtils.isBlank(queryAdmdvs)) {
            return;
        }
        Dict1901ProbeCandidate candidate = new Dict1901ProbeCandidate(
            queryAdmdvs.trim(),
            StringUtils.defaultString(parentValue, ""),
            valiFlagKey);
        if (candidates.stream().anyMatch(candidate::equals)) {
            return;
        }
        candidates.add(candidate);
    }

    private record Dict1901ProbeCandidate(
            String queryAdmdvs,
            String parentValue,
            String valiFlagKey) {
    }

    private static String firstNonBlank(JSONObject jo, String... keys) {
        for (String key : keys) {
            String v = jo.getString(key);
            if (StringUtils.isNotBlank(v)) {
                return v.trim();
            }
        }
        return null;
    }

    private PlatformDictSyncLogDto toSyncLogDto(YbPlatformDictSyncLog log) {
        return new PlatformDictSyncLogDto()
            .setDictType(log.getDictType())
            .setQueryAdmdvs(log.getQueryAdmdvs())
            .setRowsUpserted(log.getRowsUpserted())
            .setStatus(log.getStatus())
            .setErrorMessage(log.getErrorMessage())
            .setStartedAt(log.getStartedAt())
            .setFinishedAt(log.getFinishedAt());
    }

    private String resolveDictType(String dictType) {
        if (StringUtils.isNotBlank(dictType)) {
            return dictType.trim();
        }
        return PlatformDictConstants.DEFAULT_DICT_TYPE;
    }

    private String resolveQueryAdmdvs(String queryAdmdvs, String contractBusNo) {
        if (StringUtils.isNotBlank(queryAdmdvs)) {
            return queryAdmdvs.trim();
        }
        String admVs = contractPropertiesPort.findAdmVs(contractBusNo).orElse(null);
        if (StringUtils.isNotBlank(admVs)) {
            return admVs.trim();
        }
        return DEFAULT_QUERY_ADMDVS;
    }

    private int resolvePageSize(Integer pageSize) {
        if (pageSize != null && pageSize > 0) {
            return Math.min(pageSize, 1000);
        }
        return PlatformDictConstants.DEFAULT_PAGE_SIZE;
    }

    private InvokeContext buildInvokeContext(Long operatorId) {
        InvokeContext partial = InvokeContext.builder()
            .operatorId(String.valueOf(operatorId))
            .build();
        return InvokeContext.builder()
            .operatorId(signCredentialResolver.resolveOperatorId(partial))
            .build();
    }
}
