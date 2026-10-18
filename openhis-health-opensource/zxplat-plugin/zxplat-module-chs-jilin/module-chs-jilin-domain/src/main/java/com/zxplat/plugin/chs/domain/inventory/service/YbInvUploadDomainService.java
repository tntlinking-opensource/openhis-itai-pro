/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.chs.domain.inventory.service;

import com.zxplat.plugin.chs.domain.share.constants.ChsInfNo;
import com.zxplat.plugin.chs.domain.share.enums.InvDataType;
import static com.zxplat.framework.core.application.util.StringUtils.isEmpty;
import static com.zxplat.framework.core.application.util.StringUtils.isNotEmpty;

import com.alibaba.fastjson2.JSON;
import com.zxplat.plugin.chs.domain.share.constants.ChsInventoryConstants;
import com.zxplat.plugin.chs.domain.inventory.config.ChsInventoryProperties;
import com.zxplat.plugin.chs.domain.inventory.command.InvUploadLineCommand;
import com.zxplat.plugin.chs.domain.inventory.queries.ListLocalInvUploadRecordsQuery;
import com.zxplat.plugin.chs.domain.inventory.result.LocalInvUploadResult;
import com.zxplat.plugin.chs.domain.inventory.entity.YbInvUpload;
import com.zxplat.plugin.chs.domain.inventory.entity.YbInvUploadTrace;
import com.zxplat.plugin.chs.domain.inventory.repository.YbInvUploadLookup;
import com.zxplat.plugin.chs.domain.inventory.repository.YbInvUploadRepository;
import com.zxplat.plugin.chs.domain.inventory.repository.YbInvUploadTraceRepository;
import com.zxplat.plugin.chs.domain.platform.model.InvUploadPlatformResult;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.zxplat.framework.core.infrastructure.multitenancy.TenantUtils;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * {@code yb_inv_upload} / {@code yb_inv_upload_trace} 聚合写服务。
 *
 * <p><b>聚合边界：</b>仅本聚合 Repository/Lookup（上报主表 + 追溯码子表）；不写 HIS 库存/发药表，
 * 不直接调医保网关。不变量摘要：SUCCESS 才落追溯码；同码租户内仅一条未删子行；
 * DELETED 不作幂等命中；重试退避由配置驱动。</p>
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
     * <p>{@code DELETED}（冲销标删）视为不存在，避免再清床/再结时幂等短路到旧行。</p>
     *
     * @param tenantId      租户主键
     * @param infno         医保接口编号（如 3501、3505）
     * @param fixmedinsBchno 定点医药机构批次流水号
     * @param sourceLineId  来源业务行主键
     * @return 已存在的上报流水，未找到或已 DELETED 时 empty
     */
    public Optional<YbInvUpload> findExistingLine(
            Long tenantId, String infno, String fixmedinsBchno, Long sourceLineId) {
        Optional<YbInvUpload> found =
            uploadRepository.findFirstByTenantIdAndInfnoAndFixmedinsBchnoAndSourceLineId(
                tenantId, infno, fixmedinsBchno, sourceLineId);
        // 已冲销标删的流水不作幂等命中：再清床/再结须新建上报行（多轮召回）
        if (found.isPresent()
            && ChsInventoryConstants.UPLOAD_STATUS_DELETED.equals(found.get().getUploadStatus())) {
            return Optional.empty();
        }
        return found;
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
        // 组装：无已有行则新建，重试计数从 0 起
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
        // 请求快照：null 表示本轮不覆盖已落库报文（如仅改状态）
        if (requestJson != null) {
            row.setRequestJson(requestJson);
        }
        row.setResponseJson(responseJson);
        row.setOperatorId(operatorId);
        row.setUpdatedAt(LocalDateTime.now());
        // 成功清空下次重试；失败且允许调度时写退避时间
        if (ChsInventoryConstants.UPLOAD_STATUS_SUCCESS.equals(uploadStatus)) {
            row.setNextRetryAt(null);
        } else if (scheduleRetry) {
            applyNextRetrySchedule(row);
        }
        // 持久化：flush 以便同事务立即用 id 写子表/出站
        return uploadRepository.saveAndFlush(row);
    }

    /**
     * 将成功上报行的追溯码写入 {@code yb_inv_upload_trace}。
     *
     * <p>同码在租户内仅允许一条未删明细（部分唯一索引 {@code uk_yb_inv_upload_trace_codg}）。
     * 发药 3502(107)/3505、退药 3506、退药伴生 3502(106) 会反复带同一码：写入前先软删旧归属，
     * 再挂到当前 upload，避免唯一冲突把伴生事务整笔打掉（历史表现为「有码退药无 106 审计」）。</p>
     *
     * @param upload   已 SUCCESS 的上报流水
     * @param line     含追溯码列表的上报行
     * @param tenantId 租户主键
     */
    @Transactional
    public void saveTraceRows(YbInvUpload upload, InvUploadLineCommand line, Long tenantId) {
        // 仅 SUCCESS 建档，避免 FAILED/SKIPPED 半成品污染比对表
        if (!ChsInventoryConstants.UPLOAD_STATUS_SUCCESS.equals(upload.getUploadStatus())) {
            return;
        }
        // 无码行无需写子表
        if (line.getDrugTracCodgs() == null || line.getDrugTracCodgs().isEmpty()) {
            return;
        }
        for (String code : line.getDrugTracCodgs()) {
            // 空串跳过，防止占位码写入唯一索引
            if (isEmpty(code)) {
                continue;
            }
            // Cmd：同码先软删旧行，再挂当前 upload，满足部分唯一索引
            saveTraceRow(upload.getId(), code.trim(), line.getTrdnFlag(), tenantId);
        }
    }

    /**
     * 从已存储的请求 JSON 中补建追溯码明细（用于重试成功但追溯码未落库的场景）。
     *
     * <p>3505/3506 读 {@code selinfo.drugtracinfo}；3502 读 {@code invinfo.drugtracinfo}。</p>
     *
     * @param row 上报流水实体
     */
    @Transactional
    public void rebuildTraceRowsFromStoredRequest(YbInvUpload row) {
        // 无主键无法挂子表
        if (row == null || row.getId() == null) {
            return;
        }
        // 仅 SUCCESS 建档，避免 FAILED 半成品污染比对表
        if (!ChsInventoryConstants.UPLOAD_STATUS_SUCCESS.equals(row.getUploadStatus())) {
            return;
        }
        // 已有追溯行则不重复插入
        if (!uploadTraceRepository.findAllByUploadIdOrderByIdAsc(row.getId()).isEmpty()) {
            return;
        }
        // 无请求快照无法解析码
        if (isEmpty(row.getRequestJson())) {
            return;
        }
        InvUploadPlatformResult input;
        try {
            input = JSON.parseObject(row.getRequestJson(), InvUploadPlatformResult.class);
        } catch (RuntimeException ex) {
            // 非法 JSON：跳过补建，不抛以免打断重试成功主路径
            return;
        }
        if (input == null) {
            return;
        }
        List<InvUploadPlatformResult.InvUploadDrugTrace> drugtracinfo = null;
        String trdnFlag = null;
        // 3505/3506：销售节点
        if (requiresPlatformRetRslt(row.getInfno())) {
            InvUploadPlatformResult.InvUploadSelinfo selinfo = input.getSelinfo();
            if (selinfo != null) {
                drugtracinfo = selinfo.getDrugtracinfo();
                trdnFlag = selinfo.getTrdnFlag();
            }
        } else if (ChsInfNo.INVENTORY_CHANGE.equals(row.getInfno())) {
            // 3502：变更节点（进货伴生入库追溯亦走此路径）
            InvUploadPlatformResult.InvUploadInvinfo invinfo = input.getInvinfo();
            if (invinfo != null) {
                drugtracinfo = invinfo.getDrugtracinfo();
                trdnFlag = invinfo.getTrdnFlag();
            }
        } else {
            return;
        }
        if (drugtracinfo == null || drugtracinfo.isEmpty()) {
            return;
        }
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
     * 上报报文中是否至少有一条追溯码存在于本租户 {@code cus_trace_number} 主档。
     *
     * @param row 上报流水
     * @return 主档有命中 true
     */
    public boolean hasAnyRequestDrugTracInMaster(YbInvUpload row) {
        if (row == null) {
            return false;
        }
        return uploadLookup.hasAnyRequestDrugTracInMaster(row.getTenantId(), row.getRequestJson());
    }

    /**
     * 按查询条件列出本地上报流水（多条件 AND：契约/交易号/来源/状态/批次号）。
     *
     * <p>排除「191 且主档无码」历史挂账；主档有码的 191 仍返回（BR-CHS-INV-011）。</p>
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
     * 运维补传拆批：覆盖 request_json 并同步批次号（与报文 invinfo.fixmedins_bchno 一致）。
     *
     * @param row             待补传流水
     * @param requestJson     拆后报文
     * @param fixmedinsBchno  拆后批次号（如原单号-C01）
     * @return 持久化后的流水
     */
    @Transactional
    public YbInvUpload updateRequestJsonAndBchno(
        YbInvUpload row,
        String requestJson,
        String fixmedinsBchno
    ) {
        // 前置校验：无主键则无法落库，原样返回
        if (row == null || row.getId() == null) {
            return row;
        }
        // 组装：报文与批次号须与出站 invinfo 一致，否则重放后流水对不上
        if (requestJson != null) {
            row.setRequestJson(requestJson);
        }
        if (isNotEmpty(fixmedinsBchno)) {
            row.setFixmedinsBchno(fixmedinsBchno.trim());
        }
        row.setUpdatedAt(LocalDateTime.now());
        // 持久化
        return uploadRepository.save(row);
    }

    /**
     * 运维补传拆批：从原 FAILED 流水克隆后续片为新 FAILED 行（独立幂等键）。
     *
     * @param source         原流水（首片将改写在原行上）
     * @param fixmedinsBchno 本片批次号
     * @param requestJson    本片报文
     * @return 新建流水
     */
    @Transactional
    public YbInvUpload createSiblingChunkRow(
        YbInvUpload source,
        String fixmedinsBchno,
        String requestJson
    ) {
        // 前置校验
        if (source == null) {
            throw new IllegalArgumentException("source 不能为空");
        }
        // 组装：同源契约/行号，仅批次与报文换片；状态置 FAILED 待本片出站
        YbInvUpload row = new YbInvUpload();
        row.setRetryCount(0);
        row.setCreatedAt(LocalDateTime.now());
        row.setTenantId(source.getTenantId());
        row.setContractId(source.getContractId());
        row.setContractBusNo(source.getContractBusNo());
        row.setInfno(source.getInfno());
        row.setInvDataType(source.getInvDataType());
        row.setFixmedinsBchno(fixmedinsBchno);
        row.setMedListCodg(source.getMedListCodg());
        row.setSourceType(source.getSourceType());
        row.setSourceId(source.getSourceId());
        row.setSourceLineId(source.getSourceLineId());
        row.setUploadStatus(ChsInventoryConstants.UPLOAD_STATUS_FAILED);
        row.setPlatformRetRslt(ChsInventoryConstants.PLATFORM_RET_RSLT_FAILED);
        row.setPlatformMsg("追溯码拆批待补传");
        row.setRequestJson(requestJson);
        row.setOperatorId(source.getOperatorId());
        row.setUpdatedAt(LocalDateTime.now());
        applyNextRetrySchedule(row);
        // 持久化：flush 以便后续同事务内立刻用 id 出站
        return uploadRepository.saveAndFlush(row);
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
     * 写入单条追溯码明细；若租户下同码仍有未删行则先软删再插入。
     *
     * @param uploadId     上报流水主键
     * @param drugTracCodg 药品追溯码（已 trim）
     * @param trdnFlag     拆零标志（0 整盒 / 1 拆零）
     * @param tenantId     租户主键
     */
    private void saveTraceRow(Long uploadId, String drugTracCodg, String trdnFlag, Long tenantId) {
        // Pre：释放同码未删占位，否则 uk_yb_inv_upload_trace_codg 拒绝插入
        List<YbInvUploadTrace> existing =
            uploadTraceRepository.findAllByTenantIdAndDrugTracCodgAndIsDeletedFalse(tenantId, drugTracCodg);
        LocalDateTime now = LocalDateTime.now();
        if (existing != null && !existing.isEmpty()) {
            for (YbInvUploadTrace old : existing) {
                // 已挂本 upload：只刷新拆零标志，不重复插
                if (old != null && uploadId.equals(old.getUploadId())) {
                    old.setTrdnFlag(trdnFlag);
                    old.setUpdatedAt(now);
                    uploadTraceRepository.save(old);
                    return;
                }
                if (old == null) {
                    continue;
                }
                old.setIsDeleted(true);
                old.setUpdatedAt(now);
                uploadTraceRepository.save(old);
            }
            // 软删须先落库，否则同事务内再 insert 仍撞部分唯一索引
            uploadTraceRepository.flush();
        }
        YbInvUploadTrace trace = new YbInvUploadTrace();
        trace.setUploadId(uploadId);
        trace.setDrugTracCodg(drugTracCodg);
        trace.setTrdnFlag(trdnFlag);
        trace.setTenantId(tenantId);
        trace.setCreatedAt(now);
        trace.setUpdatedAt(now);
        uploadTraceRepository.save(trace);
    }

    /**
     * 按退避策略计算并设置下次重试时间；已达最大重试次数时清空。
     *
     * @param row 上报流水实体
     */
    private void applyNextRetrySchedule(YbInvUpload row) {
        int count = safeRetryCount(row.getRetryCount());
        // 已达最大重试：清空下次时间，不再进自动捞取
        if (count >= inventoryProperties.getMaxRetry()) {
            row.setNextRetryAt(null);
            return;
        }
        List<Integer> backoffs = inventoryProperties.getRetryBackoffSeconds();
        // 未配置退避表：默认 60 秒后再试
        if (backoffs == null || backoffs.isEmpty()) {
            row.setNextRetryAt(LocalDateTime.now().plusSeconds(60));
            return;
        }
        int index = Math.min(count, backoffs.size() - 1);
        int seconds = backoffs.get(index);
        // 非法秒数回落 60，避免立刻风暴重试
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
        // 空或负数按从未重试处理
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
        // 盘存 → 3501
        if (InvDataType.STOCKTAKE.getCode().equals(invDataType)) {
            return List.of(ChsInfNo.INVENTORY_STOCKTAKE);
        }
        // 变更 → 3502
        if (InvDataType.INV_CHANGE.getCode().equals(invDataType)) {
            return List.of(ChsInfNo.INVENTORY_CHANGE);
        }
        // 采购类 → 3503/3504
        if (InvDataType.PURCHASE.getCode().equals(invDataType)) {
            return List.of(ChsInfNo.INVENTORY_PURCHASE, ChsInfNo.INVENTORY_PURCHASE_RETURN);
        }
        // 销售类 → 3505/3506
        if (InvDataType.SALE.getCode().equals(invDataType)) {
            return List.of(ChsInfNo.INVENTORY_SALE, ChsInfNo.INVENTORY_SALE_RETURN);
        }
        // 未知类型：不标删任何流水
        return List.of();
    }
}
