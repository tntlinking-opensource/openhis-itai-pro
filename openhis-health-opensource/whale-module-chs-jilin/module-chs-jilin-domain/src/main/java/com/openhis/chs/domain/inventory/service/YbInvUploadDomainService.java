/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.chs.domain.inventory.service;

import com.openhis.chs.domain.share.constants.ChsInfNo;
import com.openhis.chs.domain.share.enums.InvDataType;
import static com.openhis.whale.core.application.util.StringUtils.isEmpty;
import static com.openhis.whale.core.application.util.StringUtils.isNotEmpty;

import com.alibaba.fastjson2.JSON;
import com.openhis.chs.domain.share.constants.ChsInventoryConstants;
import com.openhis.chs.domain.inventory.config.ChsInventoryProperties;
import com.openhis.chs.domain.inventory.command.InvUploadLineCommand;
import com.openhis.chs.domain.inventory.queries.ListLocalInvUploadRecordsQuery;
import com.openhis.chs.domain.inventory.result.LocalInvUploadResult;
import com.openhis.chs.domain.inventory.entity.YbInvUpload;
import com.openhis.chs.domain.inventory.entity.YbInvUploadTrace;
import com.openhis.chs.domain.inventory.repository.YbInvUploadLookup;
import com.openhis.chs.domain.inventory.repository.YbInvUploadRepository;
import com.openhis.chs.domain.inventory.repository.YbInvUploadTraceRepository;
import com.openhis.chs.domain.platform.model.InvUploadPlatformResult;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.openhis.whale.core.infrastructure.multitenancy.TenantUtils;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * {@code yb_inv_upload} / {@code yb_inv_upload_trace} 持久化领域服务。
 */
@Service
@RequiredArgsConstructor
public class YbInvUploadDomainService {

    private final YbInvUploadRepository uploadRepository;
    private final YbInvUploadTraceRepository uploadTraceRepository;
    private final YbInvUploadLookup uploadLookup;
    private final ChsInventoryProperties inventoryProperties;

    /**
     * 按租户、接口编号、定点批次流水号与来源行主键查询已存在的上报流水。
     *
     * @param tenantId      租户主键
     * @param infno         医保接口编号（如 3501、3505）
     * @param fixmedinsBchno 定点医药机构批次流水号
     * @param sourceLineId  来源业务行主键
     * @return 已存在的上报流水，未找到时 empty
     */
    public Optional<YbInvUpload> findExistingLine(
            Long tenantId, String infno, String fixmedinsBchno, Long sourceLineId) {
        return uploadRepository.findFirstByTenantIdAndInfnoAndFixmedinsBchnoAndSourceLineId(
                tenantId, infno, fixmedinsBchno, sourceLineId);
    }

    /**
     * 新增或更新一条进销存上报流水，并按需调度下次重试时间。
     *
     * @param existing       已存在的上报流水，新建时传 {@code null}
     * @param contractId     医保契约主键
     * @param contractBusNo  医保契约业务编号
     * @param infno          医保接口编号
     * @param line           上报行命令（含批次号、目录编码、追溯码等）
     * @param sourceType     来源业务类型
     * @param sourceId       来源业务主键
     * @param uploadStatus   上报状态
     * @param retRslt        平台返回结果码
     * @param msgRslt        平台返回消息
     * @param requestJson    请求体 JSON 快照
     * @param responseJson   响应体 JSON 快照
     * @param operatorId     操作人主键
     * @param tenantId       租户主键
     * @param scheduleRetry  非成功状态时是否计算下次重试时间
     * @return 持久化后的上报流水
     */
    @Transactional
    public YbInvUpload upsertUploadRecord(
            YbInvUpload existing,
            Long contractId,
            String contractBusNo,
            String infno,
            InvUploadLineCommand line,
            String sourceType,
            Long sourceId,
            String uploadStatus,
            String retRslt,
            String msgRslt,
            String requestJson,
            String responseJson,
            Long operatorId,
            Long tenantId,
            boolean scheduleRetry) {
        YbInvUpload row = existing;
        if (row == null) {
            row = new YbInvUpload();
            row.setRetryCount(0);
            row.setCreatedAt(LocalDateTime.now());
            row.setTenantId(tenantId);
        }
        row.setContractId(contractId);
        row.setContractBusNo(contractBusNo);
        row.setInfno(infno);
        row.setFixmedinsBchno(line.getFixmedinsBchno());
        row.setMedListCodg(line.getMedListCodg());
        row.setSourceType(sourceType);
        row.setSourceId(sourceId);
        row.setSourceLineId(line.getSourceLineId());
        row.setUploadStatus(uploadStatus);
        row.setPlatformRetRslt(retRslt);
        row.setPlatformMsg(msgRslt);
        if (requestJson != null) {
            row.setRequestJson(requestJson);
        }
        row.setResponseJson(responseJson);
        row.setOperatorId(operatorId);
        row.setUpdatedAt(LocalDateTime.now());
        if (ChsInventoryConstants.UPLOAD_STATUS_SUCCESS.equals(uploadStatus)) {
            row.setNextRetryAt(null);
        } else if (scheduleRetry) {
            applyNextRetrySchedule(row);
        }
        return uploadRepository.saveAndFlush(row);
    }

    /**
     * 上报成功后，将行内追溯码列表持久化到 {@code yb_inv_upload_trace}。
     *
     * @param upload   已成功的上报流水
     * @param line     上报行命令（含追溯码列表）
     * @param tenantId 租户主键
     */
    @Transactional
    public void saveTraceRows(YbInvUpload upload, InvUploadLineCommand line, Long tenantId) {
        if (!ChsInventoryConstants.UPLOAD_STATUS_SUCCESS.equals(upload.getUploadStatus())) {
            return;
        }
        if (line.getDrugTracCodgs() == null || line.getDrugTracCodgs().isEmpty()) {
            return;
        }
        for (String code : line.getDrugTracCodgs()) {
            if (isEmpty(code)) {
                continue;
            }
            saveTraceRow(upload.getId(), code.trim(), line.getTrdnFlag(), tenantId);
        }
    }

    /**
     * 从已存储的请求 JSON 中补建追溯码明细（用于重试成功但追溯码未落库的场景）。
     *
     * @param row 上报流水实体
     */
    @Transactional
    public void rebuildTraceRowsFromStoredRequest(YbInvUpload row) {
        if (row == null || row.getId() == null) {
            return;
        }
        if (!ChsInventoryConstants.UPLOAD_STATUS_SUCCESS.equals(row.getUploadStatus())) {
            return;
        }
        if (!requiresPlatformRetRslt(row.getInfno())) {
            return;
        }
        if (!uploadTraceRepository.findAllByUploadIdOrderByIdAsc(row.getId()).isEmpty()) {
            return;
        }
        if (isEmpty(row.getRequestJson())) {
            return;
        }
        InvUploadPlatformResult input;
        try {
            input = JSON.parseObject(row.getRequestJson(), InvUploadPlatformResult.class);
        } catch (RuntimeException ex) {
            return;
        }
        InvUploadPlatformResult.InvUploadSelinfo selinfo = null;
        if (input != null) {
            selinfo = input.getSelinfo();
        }
        if (selinfo == null) {
            return;
        }
        List<InvUploadPlatformResult.InvUploadDrugTrace> drugtracinfo = selinfo.getDrugtracinfo();
        if (drugtracinfo == null || drugtracinfo.isEmpty()) {
            return;
        }
        String trdnFlag = selinfo.getTrdnFlag();
        Long tenantId = TenantUtils.getCurrentTenantId();
        if (row.getTenantId() != null) {
            tenantId = row.getTenantId();
        }
        for (InvUploadPlatformResult.InvUploadDrugTrace trace : drugtracinfo) {
            if (trace == null) {
                continue;
            }
            String code = trace.getDrugTracCodg();
            if (isEmpty(code)) {
                continue;
            }
            saveTraceRow(row.getId(), code.trim(), trdnFlag, tenantId);
        }
    }

    /**
     * 按定点批次流水号将匹配的成功上报流水标记为已删除（3507 本地状态同步）。
     *
     * @param tenantId     租户主键
     * @param fixmedinsBchno 定点医药机构批次流水号
     * @param invDataType  3507 商品数据类型（盘存/变更/采购/销售）
     */
    @Transactional
    public void markDeletedByBatchNo(Long tenantId, String fixmedinsBchno, String invDataType) {
        List<String> infnos = resolveInfnoByInvDataType(invDataType);
        if (infnos.isEmpty()) {
            return;
        }
        PageRequest page = PageRequest.of(0, ChsInventoryConstants.LOCAL_UPLOAD_LIST_DEFAULT_LIMIT);
        List<YbInvUpload> rows = uploadRepository.findAllByTenantIdOrderByIdDesc(tenantId, page);
        for (YbInvUpload row : rows) {
            if (row == null || !infnos.contains(row.getInfno())) {
                continue;
            }
            if (!fixmedinsBchno.equals(row.getFixmedinsBchno())) {
                continue;
            }
            if (!ChsInventoryConstants.UPLOAD_STATUS_SUCCESS.equals(row.getUploadStatus())) {
                continue;
            }
            row.setUploadStatus(ChsInventoryConstants.UPLOAD_STATUS_DELETED);
            row.setUpdatedAt(LocalDateTime.now());
            uploadRepository.save(row);
        }
    }

    /**
     * 清床汇总 3506 冲销成功后：将配对的成功 3505 标为 DELETED。
     *
     * <p>按原清床销售 {@code fixmedins_bchno} + {@code CLEAR_BED_SUMMARY} + SUCCESS 定位，
     * 不用 3506 行的退药 {@code source_line_id}/退药单号（与 3505 幂等键不同）。</p>
     *
     * @param tenantId       租户主键
     * @param fixmedinsBchno 原清床 3505 定点批次号（非退药单号）
     */
    @Transactional
    public void markClearBedSummarySaleDeletedAfterReverse(
        Long tenantId,
        String fixmedinsBchno
    ) {
        if (tenantId == null || isEmpty(fixmedinsBchno)) {
            return;
        }
        // 冲销行 source_line_id 为退药单，不能与 3505 行键混用；按销售批次 + 来源类型定位
        Optional<YbInvUpload> sale =
            uploadRepository.findFirstByTenantIdAndInfnoAndFixmedinsBchnoAndSourceTypeAndUploadStatusOrderByIdDesc(
                tenantId,
                ChsInfNo.INVENTORY_SALE,
                fixmedinsBchno.trim(),
                ChsInventoryConstants.SOURCE_TYPE_CLEAR_BED_SUMMARY,
                ChsInventoryConstants.UPLOAD_STATUS_SUCCESS);
        if (sale.isEmpty()) {
            return;
        }
        YbInvUpload row = sale.get();
        // 冲销成功：销售行本地标删除，再清床可重新出站 3505
        row.setUploadStatus(ChsInventoryConstants.UPLOAD_STATUS_DELETED);
        row.setUpdatedAt(LocalDateTime.now());
        uploadRepository.save(row);
    }

    /**
     * 按查询条件列出本地上报流水（多条件 AND：契约/交易号/来源/状态/批次号）。
     *
     * @param request  查询条件，可空
     * @param tenantId 租户主键
     * @return 上报流水列表
     */
    public List<YbInvUpload> listUploadRecords(ListLocalInvUploadRecordsQuery request, Long tenantId) {
        ListLocalInvUploadRecordsQuery safeRequest = new ListLocalInvUploadRecordsQuery();
        if (request != null) {
            safeRequest = request;
        }
        PageRequest page = PageRequest.of(0, ChsInventoryConstants.LOCAL_UPLOAD_LIST_DEFAULT_LIMIT);
        // Cmd：Jdbc AND 筛选，避免互斥优先级导致 UI 组合条件失效
        return uploadLookup.findLocalRecords(
            tenantId,
            safeRequest.getContractBusNo(),
            safeRequest.getInfno(),
            safeRequest.getSourceType(),
            safeRequest.getSourceId(),
            safeRequest.getUploadStatus(),
            safeRequest.getFixmedinsBchno(),
            safeRequest.getCreatedFrom(),
            safeRequest.getCreatedTo(),
            page);
    }

    /**
     * 按主键加载本租户上报流水。
     *
     * @param uploadId 上报流水主键
     * @param tenantId 租户主键
     * @return 上报流水，未找到时 empty
     */
    public Optional<YbInvUpload> findByIdForTenant(Long uploadId, Long tenantId) {
        if (uploadId == null || tenantId == null) {
            return Optional.empty();
        }
        Optional<YbInvUpload> row = uploadRepository.findById(uploadId);
        if (row.isEmpty()) {
            return Optional.empty();
        }
        YbInvUpload upload = row.get();
        if (!tenantId.equals(upload.getTenantId())) {
            return Optional.empty();
        }
        return Optional.of(upload);
    }

    /**
     * 运维补传前覆盖持久化的 request_json（不从业务表重建）。
     *
     * @param row         待补传的上报流水
     * @param requestJson 新的请求 JSON
     * @return 持久化后的上报流水
     */
    @Transactional
    public YbInvUpload updateRequestJson(YbInvUpload row, String requestJson) {
        if (row == null || row.getId() == null) {
            return row;
        }
        if (requestJson != null) {
            row.setRequestJson(requestJson);
            row.setUpdatedAt(LocalDateTime.now());
            return uploadRepository.save(row);
        }
        return row;
    }

    /**
     * 查询到达重试时间且未超最大重试次数的上报流水。
     *
     * @param tenantId  租户主键
     * @param batchSize 单次捞取条数上限
     * @return 可重试的上报流水列表
     */
    public List<YbInvUpload> findRetryable(Long tenantId, int batchSize) {
        return uploadLookup.findRetryable(
                tenantId,
                ChsInventoryConstants.RETRYABLE_UPLOAD_STATUSES,
                inventoryProperties.getMaxRetry(),
                LocalDateTime.now(),
                PageRequest.of(0, batchSize));
    }

    /**
     * 记录一次重试尝试，递增重试计数。
     *
     * @param row 待重试的上报流水
     * @return 已递增重试计数的流水实体（尚未持久化）
     */
    @Transactional
    public YbInvUpload recordRetryAttempt(YbInvUpload row) {
        row.setRetryCount(safeRetryCount(row.getRetryCount()) + 1);
        return row;
    }

    /**
     * 应用重试成功结果并持久化，必要时从请求 JSON 补建追溯码。
     *
     * @param row          上报流水
     * @param uploadStatus 新的上报状态
     * @param retRslt      平台返回结果码
     * @param msgRslt      平台返回消息
     * @param responseJson 响应体 JSON 快照
     * @return 持久化后的上报流水
     */
    @Transactional
    public YbInvUpload applyRetrySuccess(
            YbInvUpload row, String uploadStatus, String retRslt, String msgRslt, String responseJson) {
        row.setUploadStatus(uploadStatus);
        row.setPlatformRetRslt(retRslt);
        row.setPlatformMsg(msgRslt);
        row.setResponseJson(responseJson);
        row.setUpdatedAt(LocalDateTime.now());
        if (ChsInventoryConstants.UPLOAD_STATUS_SUCCESS.equals(uploadStatus)) {
            row.setNextRetryAt(null);
        } else {
            applyNextRetrySchedule(row);
        }
        YbInvUpload saved = uploadRepository.save(row);
        rebuildTraceRowsFromStoredRequest(saved);
        return saved;
    }

    /**
     * 应用重试失败结果，标记失败状态并调度下次重试。
     *
     * @param row          上报流水
     * @param errorMessage 失败原因
     * @return 持久化后的上报流水
     */
    @Transactional
    public YbInvUpload applyRetryFailure(YbInvUpload row, String errorMessage) {
        row.setUploadStatus(ChsInventoryConstants.UPLOAD_STATUS_FAILED);
        row.setPlatformRetRslt(ChsInventoryConstants.PLATFORM_RET_RSLT_FAILED);
        row.setPlatformMsg(errorMessage);
        // 本次出站未拿到响应体，清空旧 response 避免运维误读
        row.setResponseJson(null);
        row.setUpdatedAt(LocalDateTime.now());
        applyNextRetrySchedule(row);
        return uploadRepository.save(row);
    }

    /**
     * 将上报流水实体转换为本地查询 DTO。
     *
     * @param row 上报流水实体
     * @return 本地查询结果 DTO
     */
    public LocalInvUploadResult toLocalDto(YbInvUpload row) {
        LocalInvUploadResult dto = new LocalInvUploadResult();
        dto.setId(row.getId());
        dto.setContractBusNo(row.getContractBusNo());
        dto.setInfno(row.getInfno());
        dto.setFixmedinsBchno(row.getFixmedinsBchno());
        dto.setMedListCodg(row.getMedListCodg());
        dto.setSourceType(row.getSourceType());
        dto.setSourceId(row.getSourceId());
        dto.setSourceLineId(row.getSourceLineId());
        dto.setUploadStatus(row.getUploadStatus());
        dto.setPlatformRetRslt(row.getPlatformRetRslt());
        dto.setPlatformMsg(row.getPlatformMsg());
        dto.setRequestJson(row.getRequestJson());
        dto.setResponseJson(row.getResponseJson());
        dto.setRetryCount(row.getRetryCount());
        if (row.getCreatedAt() != null) {
            // 运维台展示：去掉 T/小数秒，固定到秒
            String raw = row.getCreatedAt().toString().replace('T', ' ');
            int dot = raw.indexOf('.');
            if (dot > 0) {
                dto.setCreatedAt(raw.substring(0, Math.min(dot, 19)));
            } else if (raw.length() >= 19) {
                dto.setCreatedAt(raw.substring(0, 19));
            } else {
                dto.setCreatedAt(raw);
            }
        }
        return dto;
    }

    /**
     * 持久化单条追溯码明细。
     *
     * @param uploadId     上报流水主键
     * @param drugTracCodg 药品追溯码
     * @param trdnFlag     拆零标志（0 整盒 / 1 拆零）
     * @param tenantId     租户主键
     */
    private void saveTraceRow(Long uploadId, String drugTracCodg, String trdnFlag, Long tenantId) {
        YbInvUploadTrace trace = new YbInvUploadTrace();
        trace.setUploadId(uploadId);
        trace.setDrugTracCodg(drugTracCodg);
        trace.setTrdnFlag(trdnFlag);
        trace.setTenantId(tenantId);
        trace.setCreatedAt(LocalDateTime.now());
        trace.setUpdatedAt(LocalDateTime.now());
        uploadTraceRepository.save(trace);
    }

    /**
     * 按退避策略计算并设置下次重试时间；已达最大重试次数时清空。
     *
     * @param row 上报流水实体
     */
    private void applyNextRetrySchedule(YbInvUpload row) {
        int count = safeRetryCount(row.getRetryCount());
        if (count >= inventoryProperties.getMaxRetry()) {
            row.setNextRetryAt(null);
            return;
        }
        List<Integer> backoffs = inventoryProperties.getRetryBackoffSeconds();
        if (backoffs == null || backoffs.isEmpty()) {
            row.setNextRetryAt(LocalDateTime.now().plusSeconds(60));
            return;
        }
        int index = Math.min(count, backoffs.size() - 1);
        int seconds = backoffs.get(index);
        if (seconds <= 0) {
            seconds = 60;
        }
        row.setNextRetryAt(LocalDateTime.now().plusSeconds(seconds));
    }

    /**
     * 将重试计数规范为非负整数，空值视为 0。
     *
     * @param retryCount 原始重试计数
     * @return 非负重试计数
     */
    private static int safeRetryCount(Integer retryCount) {
        if (retryCount == null || retryCount < 0) {
            return 0;
        }
        return retryCount;
    }

    /**
     * 判断该接口编号是否要求平台返回 retRslt（销售类 3505/3506）。
     *
     * @param infno 医保接口编号
     * @return 需要平台 retRslt 时 {@code true}
     */
    private static boolean requiresPlatformRetRslt(String infno) {
        return ChsInfNo.INVENTORY_SALE.equals(infno)
                || ChsInfNo.INVENTORY_SALE_RETURN.equals(infno);
    }

    /**
     * 将 3507 商品数据类型映射为对应的 infno 列表。
     *
     * @param invDataType 3507 商品数据类型
     * @return 关联的 infno 列表，未知类型时返回空列表
     */
    private static List<String> resolveInfnoByInvDataType(String invDataType) {
        if (InvDataType.STOCKTAKE.getCode().equals(invDataType)) {
            return List.of(ChsInfNo.INVENTORY_STOCKTAKE);
        }
        if (InvDataType.INV_CHANGE.getCode().equals(invDataType)) {
            return List.of(ChsInfNo.INVENTORY_CHANGE);
        }
        if (InvDataType.PURCHASE.getCode().equals(invDataType)) {
            return List.of(ChsInfNo.INVENTORY_PURCHASE, ChsInfNo.INVENTORY_PURCHASE_RETURN);
        }
        if (InvDataType.SALE.getCode().equals(invDataType)) {
            return List.of(ChsInfNo.INVENTORY_SALE, ChsInfNo.INVENTORY_SALE_RETURN);
        }
        return List.of();
    }
}
