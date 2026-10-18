/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.chs.bridge.inventory.workflow;

import static com.zxplat.framework.core.application.util.StringUtils.isEmpty;
import static com.zxplat.framework.core.application.util.StringUtils.trimToNull;

import com.zxplat.plugin.chs.domain.inventory.access.InventoryCompareAccess;
import com.zxplat.plugin.chs.domain.inventory.access.Inv35xxQueryInvoker;
import com.zxplat.plugin.chs.domain.share.constants.ChsInventoryConstants;
import com.zxplat.plugin.chs.domain.inventory.entity.YbInvUpload;
import com.zxplat.plugin.chs.domain.inventory.queries.CompareLocalVsPlatformQuery;
import com.zxplat.plugin.chs.domain.inventory.queries.ListLocalInvUploadRecordsQuery;
import com.zxplat.plugin.chs.domain.inventory.repository.InvUploadTraceCompareRow;
import com.zxplat.plugin.chs.domain.inventory.repository.YbInvUploadTraceLookup;
import com.zxplat.plugin.chs.domain.inventory.result.CompareLocalVsPlatformResult;
import com.zxplat.plugin.chs.domain.inventory.result.InvCompareRowResult;
import com.zxplat.plugin.chs.domain.inventory.result.LocalInvUploadResult;
import com.zxplat.plugin.chs.domain.inventory.result.LocalInvUploadTraceCompareResult;
import com.zxplat.plugin.chs.domain.inventory.result.PlatformInvQueryResult;
import com.zxplat.plugin.chs.domain.inventory.service.YbInvUploadDomainService;
import com.zxplat.plugin.chs.domain.share.constants.ChsErrorCodes;
import com.zxplat.plugin.chs.domain.share.constants.ChsInfNo;
import com.zxplat.framework.core.application.exception.BusinessException;
import com.zxplat.framework.core.infrastructure.multitenancy.TenantUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 本地 vs 平台进销存差异比对 bridge 编排实现（P14）。
 *
 * <p><b>职责：</b>读取本地 yb_inv_upload/追溯流水，调用 {@link com.zxplat.plugin.chs.domain.inventory.access.Inv35xxQueryInvoker} 做行级差异比对。</p>
 * <p><b>实现端口：</b>{@link com.zxplat.plugin.chs.domain.inventory.access.InventoryCompareAccess}</p>
 * <p><b>调用方：</b>{@code InsuranceInventoryAppService}</p>
 * <p><b>架构边界：</b>bridge 层组装 gateway 出站上下文；application 只依赖 domain 端口。</p>
 * <p><b>迁移阶段：</b>P14</p>
 */
@Component
@RequiredArgsConstructor
public class InventoryCompareWorkflow implements InventoryCompareAccess {

    private final Inv35xxQueryInvoker inv35xxQueryInvoker;
    private final YbInvUploadDomainService uploadDomainService;
    private final YbInvUploadTraceLookup uploadTraceLookup;

    public CompareLocalVsPlatformResult compare(CompareLocalVsPlatformQuery query) {
        CompareLocalVsPlatformQuery safeQuery = query == null ? new CompareLocalVsPlatformQuery() : query;
        String compareQueryInfno = requireCompareQueryInfno(safeQuery);
        if (ChsInfNo.INVENTORY_INBOUND_TRACE_QUERY.equals(compareQueryInfno)
            || ChsInfNo.INVENTORY_SALE_TRACE_QUERY.equals(compareQueryInfno)) {
            return compareByTrace(compareQueryInfno, safeQuery);
        }
        String localUploadInfno = mapPlatformQueryToLocalUploadInfno(compareQueryInfno);
        PlatformInvQueryResult platformResult = queryPlatformByCompareInfno(compareQueryInfno, safeQuery);
        List<Map<String, Object>> platformRows = platformResult.getRows() != null
            ? platformResult.getRows() : List.of();
        List<LocalInvUploadResult> localRows = filterLocalRowsForCompare(
            listLocalUploadRecords(new ListLocalInvUploadRecordsQuery().setInfno(localUploadInfno)),
            safeQuery);
        return buildCompareResult(compareQueryInfno, localUploadInfno, platformRows, localRows, null);
    }

    private CompareLocalVsPlatformResult compareByTrace(
        String compareQueryInfno,
        CompareLocalVsPlatformQuery query
    ) {
        List<String> localInfnoList = resolveTraceLocalInfnoList(compareQueryInfno);
        PlatformInvQueryResult platformResult = queryPlatformByCompareInfno(compareQueryInfno, query);
        List<Map<String, Object>> platformRows = platformResult.getRows() != null
            ? platformResult.getRows() : List.of();
        List<LocalInvUploadTraceCompareResult> localRows = filterLocalTraceRowsForCompare(
            listLocalTraceRowsForCompare(localInfnoList), query);
        return buildCompareResult(
            compareQueryInfno,
            mapPlatformQueryToLocalUploadInfno(compareQueryInfno),
            platformRows,
            null,
            localRows);
    }

    private CompareLocalVsPlatformResult buildCompareResult(
        String compareQueryInfno,
        String localUploadInfno,
        List<Map<String, Object>> platformRows,
        List<LocalInvUploadResult> localUploadRows,
        List<LocalInvUploadTraceCompareResult> localTraceRows
    ) {
        CompareLocalVsPlatformResult result = new CompareLocalVsPlatformResult();
        result.setCompareQueryInfno(compareQueryInfno);
        result.setLocalUploadInfno(localUploadInfno);
        result.setPlatformRowCount(platformRows.size());

        if (localTraceRows != null) {
            result.setLocalRowCount(localTraceRows.size());
            Map<String, LocalInvUploadTraceCompareResult> localByKey =
                indexLocalTraceRowsByCompareKey(localTraceRows);
            Map<String, Map<String, Object>> platformByKey = indexPlatformTraceRowsByCompareKey(platformRows);
            Set<String> allKeys = new LinkedHashSet<>();
            allKeys.addAll(localByKey.keySet());
            allKeys.addAll(platformByKey.keySet());
            for (String key : allKeys) {
                InvCompareRowResult rowDto = buildTraceCompareRow(
                    key, localByKey.get(key), platformByKey.get(key));
                result.getRows().add(rowDto);
                accumulateDiffCount(result, rowDto.getDiffType());
            }
            return result;
        }

        result.setLocalRowCount(localUploadRows.size());
        Map<String, LocalInvUploadResult> localByKey = indexLocalRowsByCompareKey(localUploadRows);
        Map<String, Map<String, Object>> platformByKey = indexPlatformRowsByCompareKey(platformRows);
        Set<String> allKeys = new LinkedHashSet<>();
        allKeys.addAll(localByKey.keySet());
        allKeys.addAll(platformByKey.keySet());
        for (String key : allKeys) {
            InvCompareRowResult rowDto = buildCompareRow(key, localByKey.get(key), platformByKey.get(key));
            result.getRows().add(rowDto);
            accumulateDiffCount(result, rowDto.getDiffType());
        }
        return result;
    }

    private void accumulateDiffCount(CompareLocalVsPlatformResult result, String diffType) {
        if (ChsInventoryConstants.COMPARE_DIFF_MATCH.equals(diffType)) {
            result.setMatchCount(result.getMatchCount() + 1);
        } else if (ChsInventoryConstants.COMPARE_DIFF_LOCAL_ONLY.equals(diffType)) {
            result.setLocalOnlyCount(result.getLocalOnlyCount() + 1);
        } else if (ChsInventoryConstants.COMPARE_DIFF_PLATFORM_ONLY.equals(diffType)) {
            result.setPlatformOnlyCount(result.getPlatformOnlyCount() + 1);
        }
    }

    private List<LocalInvUploadResult> listLocalUploadRecords(ListLocalInvUploadRecordsQuery query) {
        Long tenantId = TenantUtils.getCurrentTenantId();
        ListLocalInvUploadRecordsQuery safeQuery = query == null ? new ListLocalInvUploadRecordsQuery() : query;
        List<YbInvUpload> rows = uploadDomainService.listUploadRecords(safeQuery, tenantId);
        List<LocalInvUploadResult> dtos = new ArrayList<>(rows.size());
        for (YbInvUpload row : rows) {
            dtos.add(uploadDomainService.toLocalDto(row));
        }
        return dtos;
    }

    private List<LocalInvUploadTraceCompareResult> listLocalTraceRowsForCompare(List<String> infnoList) {
        if (infnoList == null || infnoList.isEmpty()) {
            return List.of();
        }
        Long tenantId = TenantUtils.getCurrentTenantId();
        PageRequest page = PageRequest.of(0, ChsInventoryConstants.LOCAL_UPLOAD_LIST_DEFAULT_LIMIT);
        List<InvUploadTraceCompareRow> pairs = uploadTraceLookup.findTraceRowsForCompare(tenantId, infnoList, page);
        List<LocalInvUploadTraceCompareResult> rows = new ArrayList<>(pairs.size());
        for (InvUploadTraceCompareRow pair : pairs) {
            if (pair == null) {
                continue;
            }
            LocalInvUploadTraceCompareResult dto = new LocalInvUploadTraceCompareResult();
            dto.setTraceId(pair.getTraceId());
            dto.setDrugTracCodg(pair.getDrugTracCodg());
            dto.setUploadId(pair.getUploadId());
            dto.setContractBusNo(pair.getContractBusNo());
            dto.setInfno(pair.getInfno());
            dto.setFixmedinsBchno(pair.getFixmedinsBchno());
            dto.setMedListCodg(pair.getMedListCodg());
            dto.setUploadStatus(pair.getUploadStatus());
            if (pair.getCreatedAt() != null) {
                dto.setCreatedAt(pair.getCreatedAt().toString());
            }
            rows.add(dto);
        }
        return rows;
    }

    private PlatformInvQueryResult queryPlatformByCompareInfno(
        String compareQueryInfno,
        CompareLocalVsPlatformQuery query
    ) {
        if (ChsInfNo.INVENTORY_STOCK_QUERY.equals(compareQueryInfno)) {
            return inv35xxQueryInvoker.query3508(query);
        }
        if (ChsInfNo.INVENTORY_CHANGE_QUERY.equals(compareQueryInfno)) {
            return inv35xxQueryInvoker.query3509(query);
        }
        if (ChsInfNo.INVENTORY_PURCHASE_QUERY.equals(compareQueryInfno)) {
            return inv35xxQueryInvoker.query3510(query);
        }
        if (ChsInfNo.INVENTORY_INBOUND_TRACE_QUERY.equals(compareQueryInfno)) {
            return inv35xxQueryInvoker.query3512(query);
        }
        if (ChsInfNo.INVENTORY_SALE_TRACE_QUERY.equals(compareQueryInfno)) {
            return inv35xxQueryInvoker.query3513(query);
        }
        return inv35xxQueryInvoker.query3511(query);
    }

    private static List<String> resolveTraceLocalInfnoList(String compareQueryInfno) {
        if (ChsInfNo.INVENTORY_INBOUND_TRACE_QUERY.equals(compareQueryInfno)) {
            return List.of(ChsInfNo.INVENTORY_CHANGE, ChsInfNo.INVENTORY_PURCHASE);
        }
        if (ChsInfNo.INVENTORY_SALE_TRACE_QUERY.equals(compareQueryInfno)) {
            return List.of(ChsInfNo.INVENTORY_SALE, ChsInfNo.INVENTORY_SALE_RETURN);
        }
        return List.of();
    }

    private static String requireCompareQueryInfno(CompareLocalVsPlatformQuery query) {
        if (query == null || isEmpty(query.getCompareQueryInfno())) {
            throw new BusinessException(ChsErrorCodes.CONTRACT_INVALID, "compareQueryInfno 不能为空（3508–3513）");
        }
        String infno = query.getCompareQueryInfno().trim();
        if (!ChsInfNo.INVENTORY_STOCK_QUERY.equals(infno)
            && !ChsInfNo.INVENTORY_CHANGE_QUERY.equals(infno)
            && !ChsInfNo.INVENTORY_PURCHASE_QUERY.equals(infno)
            && !ChsInfNo.INVENTORY_SALE_QUERY.equals(infno)
            && !ChsInfNo.INVENTORY_INBOUND_TRACE_QUERY.equals(infno)
            && !ChsInfNo.INVENTORY_SALE_TRACE_QUERY.equals(infno)) {
            throw new BusinessException(ChsErrorCodes.CONTRACT_INVALID, "compareQueryInfno 仅支持 3508–3513");
        }
        return infno;
    }

    private static String mapPlatformQueryToLocalUploadInfno(String compareQueryInfno) {
        if (ChsInfNo.INVENTORY_STOCK_QUERY.equals(compareQueryInfno)) {
            return ChsInfNo.INVENTORY_STOCKTAKE;
        }
        if (ChsInfNo.INVENTORY_CHANGE_QUERY.equals(compareQueryInfno)) {
            return ChsInfNo.INVENTORY_CHANGE;
        }
        if (ChsInfNo.INVENTORY_PURCHASE_QUERY.equals(compareQueryInfno)) {
            return ChsInfNo.INVENTORY_PURCHASE;
        }
        if (ChsInfNo.INVENTORY_SALE_QUERY.equals(compareQueryInfno)) {
            return ChsInfNo.INVENTORY_SALE;
        }
        if (ChsInfNo.INVENTORY_INBOUND_TRACE_QUERY.equals(compareQueryInfno)) {
            return ChsInfNo.INVENTORY_CHANGE + "," + ChsInfNo.INVENTORY_PURCHASE;
        }
        if (ChsInfNo.INVENTORY_SALE_TRACE_QUERY.equals(compareQueryInfno)) {
            return ChsInfNo.INVENTORY_SALE + "," + ChsInfNo.INVENTORY_SALE_RETURN;
        }
        throw new BusinessException(ChsErrorCodes.CONTRACT_INVALID, "无法映射 compareQueryInfno: " + compareQueryInfno);
    }

    private List<LocalInvUploadResult> filterLocalRowsForCompare(
        List<LocalInvUploadResult> rows,
        CompareLocalVsPlatformQuery query
    ) {
        if (rows == null || rows.isEmpty()) {
            return List.of();
        }
        CompareFilterCriteria criteria = extractCompareFilterCriteria(query);
        List<LocalInvUploadResult> filtered = new ArrayList<>();
        for (LocalInvUploadResult row : rows) {
            if (row == null) {
                continue;
            }
            if (criteria.contractBusNo() != null
                && !criteria.contractBusNo().equals(trimToNull(row.getContractBusNo()))) {
                continue;
            }
            if (criteria.fixmedinsBchno() != null
                && !criteria.fixmedinsBchno().equals(trimToNull(row.getFixmedinsBchno()))) {
                continue;
            }
            if (criteria.medListCodg() != null
                && !criteria.medListCodg().equals(trimToNull(row.getMedListCodg()))) {
                continue;
            }
            if (!withinCompareDateRange(row.getCreatedAt(), criteria.beginDate(), criteria.endDate())) {
                continue;
            }
            filtered.add(row);
        }
        return filtered;
    }

    private List<LocalInvUploadTraceCompareResult> filterLocalTraceRowsForCompare(
        List<LocalInvUploadTraceCompareResult> rows,
        CompareLocalVsPlatformQuery query
    ) {
        if (rows == null || rows.isEmpty()) {
            return List.of();
        }
        CompareFilterCriteria criteria = extractCompareFilterCriteria(query);
        List<LocalInvUploadTraceCompareResult> filtered = new ArrayList<>();
        for (LocalInvUploadTraceCompareResult row : rows) {
            if (row == null) {
                continue;
            }
            if (criteria.contractBusNo() != null
                && !criteria.contractBusNo().equals(trimToNull(row.getContractBusNo()))) {
                continue;
            }
            if (criteria.fixmedinsBchno() != null
                && !criteria.fixmedinsBchno().equals(trimToNull(row.getFixmedinsBchno()))) {
                continue;
            }
            if (criteria.medListCodg() != null
                && !criteria.medListCodg().equals(trimToNull(row.getMedListCodg()))) {
                continue;
            }
            if (criteria.drugTracCodg() != null
                && !criteria.drugTracCodg().equals(trimToNull(row.getDrugTracCodg()))) {
                continue;
            }
            if (!withinCompareDateRange(row.getCreatedAt(), criteria.beginDate(), criteria.endDate())) {
                continue;
            }
            filtered.add(row);
        }
        return filtered;
    }

    private record CompareFilterCriteria(
        String contractBusNo,
        String fixmedinsBchno,
        String medListCodg,
        String drugTracCodg,
        LocalDate beginDate,
        LocalDate endDate
    ) {
    }

    private static CompareFilterCriteria extractCompareFilterCriteria(CompareLocalVsPlatformQuery query) {
        CompareLocalVsPlatformQuery safe = query == null ? new CompareLocalVsPlatformQuery() : query;
        return new CompareFilterCriteria(
            trimToNull(safe.getContractBusNo()),
            trimToNull(safe.getFixmedinsBchno()),
            trimToNull(safe.getMedListCodg()),
            trimToNull(safe.getDrugTracCodg()),
            parseCompareDate(safe.getBegndate()),
            parseCompareDate(safe.getEnddate())
        );
    }

    private static LocalDate parseCompareDate(String value) {
        if (isEmpty(value)) {
            return null;
        }
        String trimmed = value.trim();
        if (trimmed.length() >= 10) {
            trimmed = trimmed.substring(0, 10);
        }
        return LocalDate.parse(trimmed, DateTimeFormatter.ISO_LOCAL_DATE);
    }

    private static boolean withinCompareDateRange(String createdAt, LocalDate beginDate, LocalDate endDate) {
        if (beginDate == null && endDate == null) {
            return true;
        }
        if (isEmpty(createdAt)) {
            return false;
        }
        LocalDate createdDate;
        try {
            if (createdAt.length() >= 10) {
                createdDate = LocalDate.parse(createdAt.substring(0, 10), DateTimeFormatter.ISO_LOCAL_DATE);
            } else {
                createdDate = LocalDate.parse(createdAt.trim(), DateTimeFormatter.ISO_LOCAL_DATE);
            }
        } catch (RuntimeException ex) {
            return false;
        }
        if (beginDate != null && createdDate.isBefore(beginDate)) {
            return false;
        }
        return endDate == null || !createdDate.isAfter(endDate);
    }

    private static Map<String, LocalInvUploadResult> indexLocalRowsByCompareKey(List<LocalInvUploadResult> rows) {
        Map<String, LocalInvUploadResult> indexed = new LinkedHashMap<>();
        for (LocalInvUploadResult row : rows) {
            String key = buildCompareKey(row.getFixmedinsBchno(), row.getMedListCodg());
            if (key == null) {
                continue;
            }
            indexed.putIfAbsent(key, row);
        }
        return indexed;
    }

    private static Map<String, Map<String, Object>> indexPlatformRowsByCompareKey(List<Map<String, Object>> rows) {
        Map<String, Map<String, Object>> indexed = new LinkedHashMap<>();
        for (Map<String, Object> row : rows) {
            if (row == null || row.isEmpty()) {
                continue;
            }
            String key = buildCompareKey(
                readCompareRowField(row, "fixmedinsBchno", "fixmedins_bchno"),
                readCompareRowField(row, "medListCodg", "med_list_codg"));
            if (key == null) {
                continue;
            }
            indexed.putIfAbsent(key, row);
        }
        return indexed;
    }

    private static Map<String, LocalInvUploadTraceCompareResult> indexLocalTraceRowsByCompareKey(
        List<LocalInvUploadTraceCompareResult> rows
    ) {
        Map<String, LocalInvUploadTraceCompareResult> indexed = new LinkedHashMap<>();
        for (LocalInvUploadTraceCompareResult row : rows) {
            String key = buildTraceCompareKey(row.getDrugTracCodg());
            if (key == null) {
                continue;
            }
            indexed.putIfAbsent(key, row);
        }
        return indexed;
    }

    private static Map<String, Map<String, Object>> indexPlatformTraceRowsByCompareKey(List<Map<String, Object>> rows) {
        Map<String, Map<String, Object>> indexed = new LinkedHashMap<>();
        for (Map<String, Object> row : rows) {
            if (row == null || row.isEmpty()) {
                continue;
            }
            String key = buildTraceCompareKey(readCompareRowField(row, "drugTracCodg", "drug_trac_codg"));
            if (key == null) {
                continue;
            }
            indexed.putIfAbsent(key, row);
        }
        return indexed;
    }

    private static InvCompareRowResult buildCompareRow(
        String key,
        LocalInvUploadResult localRow,
        Map<String, Object> platformRow
    ) {
        InvCompareRowResult rowDto = new InvCompareRowResult();
        rowDto.setCompareKey(key);
        String[] parts = key.split("\\|", 2);
        rowDto.setFixmedinsBchno(parts.length > 0 ? parts[0] : null);
        rowDto.setMedListCodg(parts.length > 1 ? parts[1] : null);
        if (localRow != null && platformRow != null) {
            rowDto.setDiffType(ChsInventoryConstants.COMPARE_DIFF_MATCH);
            rowDto.setLocalUploadId(localRow.getId());
            rowDto.setLocalUploadStatus(localRow.getUploadStatus());
            rowDto.setLocalCreatedAt(localRow.getCreatedAt());
            rowDto.setPlatformRow(new HashMap<>(platformRow));
        } else if (localRow != null) {
            rowDto.setDiffType(ChsInventoryConstants.COMPARE_DIFF_LOCAL_ONLY);
            rowDto.setLocalUploadId(localRow.getId());
            rowDto.setLocalUploadStatus(localRow.getUploadStatus());
            rowDto.setLocalCreatedAt(localRow.getCreatedAt());
        } else {
            rowDto.setDiffType(ChsInventoryConstants.COMPARE_DIFF_PLATFORM_ONLY);
            rowDto.setPlatformRow(new HashMap<>(platformRow));
            rowDto.setFixmedinsBchno(readCompareRowField(platformRow, "fixmedinsBchno", "fixmedins_bchno"));
            rowDto.setMedListCodg(readCompareRowField(platformRow, "medListCodg", "med_list_codg"));
        }
        return rowDto;
    }

    private static InvCompareRowResult buildTraceCompareRow(
        String key,
        LocalInvUploadTraceCompareResult localRow,
        Map<String, Object> platformRow
    ) {
        InvCompareRowResult rowDto = new InvCompareRowResult();
        rowDto.setCompareKey(key);
        rowDto.setDrugTracCodg(key);
        if (localRow != null && platformRow != null) {
            rowDto.setDiffType(ChsInventoryConstants.COMPARE_DIFF_MATCH);
            rowDto.setLocalUploadId(localRow.getUploadId());
            rowDto.setLocalUploadStatus(localRow.getUploadStatus());
            rowDto.setLocalCreatedAt(localRow.getCreatedAt());
            rowDto.setFixmedinsBchno(localRow.getFixmedinsBchno());
            rowDto.setMedListCodg(localRow.getMedListCodg());
            rowDto.setPlatformRow(new HashMap<>(platformRow));
        } else if (localRow != null) {
            rowDto.setDiffType(ChsInventoryConstants.COMPARE_DIFF_LOCAL_ONLY);
            rowDto.setLocalUploadId(localRow.getUploadId());
            rowDto.setLocalUploadStatus(localRow.getUploadStatus());
            rowDto.setLocalCreatedAt(localRow.getCreatedAt());
            rowDto.setFixmedinsBchno(localRow.getFixmedinsBchno());
            rowDto.setMedListCodg(localRow.getMedListCodg());
        } else {
            rowDto.setDiffType(ChsInventoryConstants.COMPARE_DIFF_PLATFORM_ONLY);
            rowDto.setPlatformRow(new HashMap<>(platformRow));
            rowDto.setFixmedinsBchno(readCompareRowField(platformRow, "fixmedinsBchno", "fixmedins_bchno"));
            rowDto.setMedListCodg(readCompareRowField(platformRow, "medListCodg", "med_list_codg"));
        }
        return rowDto;
    }

    private static String buildCompareKey(String fixmedinsBchno, String medListCodg) {
        String bchno = trimToNull(fixmedinsBchno);
        if (bchno == null) {
            return null;
        }
        String med = trimToNull(medListCodg);
        if (med == null) {
            return bchno + "|";
        }
        return bchno + "|" + med;
    }

    private static String buildTraceCompareKey(String drugTracCodg) {
        return trimToNull(drugTracCodg);
    }

    private static String readCompareRowField(Map<String, Object> row, String camelKey, String snakeKey) {
        if (row == null || row.isEmpty()) {
            return null;
        }
        Object camelValue = row.get(camelKey);
        if (camelValue != null) {
            return trimToNull(String.valueOf(camelValue));
        }
        Object snakeValue = row.get(snakeKey);
        if (snakeValue != null) {
            return trimToNull(String.valueOf(snakeValue));
        }
        return null;
    }
}
