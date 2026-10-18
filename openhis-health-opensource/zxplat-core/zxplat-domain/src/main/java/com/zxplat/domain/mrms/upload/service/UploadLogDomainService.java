/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.domain.mrms.upload.service;

import com.zxplat.domain.mrms.caserecord.entity.CaseRecord;
import com.zxplat.domain.mrms.caserecord.repository.CaseRecordRepository;
import com.zxplat.domain.mrms.upload.entity.UploadLog;
import com.zxplat.domain.mrms.upload.repository.UploadLogRepository;
import com.zxplat.domain.share.enums.ChsUploadStatus;
import com.zxplat.framework.core.application.exception.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

import static com.zxplat.framework.core.application.util.StringUtils.trimToNull;

/**
 * 4401 上传日志（{@code mrms_upload_log}）写侧，并同步更新病案主档上传冗余字段。
 *
 * <p>写侧跨聚合：更新主档 {@code chs_upload_*} 冗余列与 DM 写契约一致，经 {@link CaseRecordRepository} 同一 UoW 落库。</p>
 */
@Service
@RequiredArgsConstructor
public class UploadLogDomainService {

    private final UploadLogRepository uploadLogRepository;
    private final CaseRecordRepository caseRecordRepository;

    /**
     * 按主键加载未删除上传日志。
     *
     * @param id 日志主键
     * @return 上传日志
     */
    public UploadLog requireById(Long id) {
        return uploadLogRepository.findByIdAndIsDeletedFalse(id)
                .orElseThrow(() -> new EntityNotFoundException("上传日志", id));
    }

    /**
     * 按病案列出历史上传日志。
     *
     * @param caseRecordId 病案主档主键
     * @return 日志列表，按创建时间倒序
     */
    public List<UploadLog> listByCaseRecord(Long caseRecordId) {
        return uploadLogRepository.findByCaseRecordIdAndIsDeletedFalseOrderByCreatedAtDesc(caseRecordId);
    }

    /**
     * 追加一条 4401 调用日志（成功/失败均落库留痕）。
     *
     * @param tenantId 租户主键
     * @param caseRecordId 病案主档主键
     * @param contractBusNo 医保合约业务流水号
     * @param contractId 合约主键
     * @param infno 接口编号
     * @param statusCode 本次调用结果 status code
     * @param requestDigest 请求摘要（脱敏）
     * @param infcode 平台返回码
     * @param errMsg 平台错误信息
     * @param responseSnapshot 响应快照（脱敏）
     * @param operatorId 操作人
     * @return 持久化后的日志
     */
    @Transactional(rollbackFor = Exception.class)
    public UploadLog appendLog(
            Long tenantId,
            Long caseRecordId,
            String contractBusNo,
            Long contractId,
            String infno,
            String statusCode,
            String requestDigest,
            String infcode,
            String errMsg,
            String responseSnapshot,
            Long operatorId) {
        // Cmd：组装上传日志实体（成功/失败均留痕）
        UploadLog log = new UploadLog();
        log.init(
                tenantId,
                caseRecordId,
                contractBusNo,
                contractId,
                infno,
                statusCode,
                requestDigest,
                infcode,
                errMsg,
                responseSnapshot,
                operatorId);
        // 持久化
        return uploadLogRepository.save(log);
    }

    /**
     * 回写病案主档 4401 上传冗余状态（列表/重试入口展示用）。
     *
     * @param caseRecordId 病案主档主键
     * @param status 上传状态枚举
     * @param errorMessage 失败时的错误摘要，成功可为 null
     * @param operatorId 操作人
     */
    @Transactional(rollbackFor = Exception.class)
    public void updateCaseRecordUploadStatus(
            Long caseRecordId,
            ChsUploadStatus status,
            String errorMessage,
            Long operatorId) {
        // Pre：加载主档（跨聚合写冗余列，同事务）
        CaseRecord record = caseRecordRepository.findByIdAndIsDeletedFalse(caseRecordId)
                .orElseThrow(() -> new EntityNotFoundException("病案主档", caseRecordId));
        record.setChsUploadStatusCode(status.getCode());
        record.setChsUploadAt(LocalDateTime.now());
        record.setChsUploadError(trimToNull(errorMessage));
        record.setUpdatedAt(LocalDateTime.now());
        record.setUpdatedBy(operatorId);
        caseRecordRepository.save(record);
    }

    /**
     * 回写病案主档结算清单上传状态（4101A/4102）。
     *
     * @param caseRecordId 病案主档主键
     * @param settleUploadStatusCode {@code 0}/{@code 1}/{@code 2}
     * @param setlListId 平台清单流水号，可为 null（保留原值时传 null 且 clearSetlListId=false）
     * @param clearSetlListId 是否清空 setl_list_id
     * @param errorMessage 错误摘要
     * @param operatorId 操作人
     */
    @Transactional(rollbackFor = Exception.class)
    public void updateCaseRecordSettleUploadStatus(
            Long caseRecordId,
            String settleUploadStatusCode,
            String setlListId,
            boolean clearSetlListId,
            String errorMessage,
            Long operatorId) {
        CaseRecord record = caseRecordRepository.findByIdAndIsDeletedFalse(caseRecordId)
                .orElseThrow(() -> new EntityNotFoundException("病案主档", caseRecordId));
        record.setSettleUploadStatusCode(trimToNull(settleUploadStatusCode));
        // 成功回写清单号；撤回审核时按需清空
        if (clearSetlListId) {
            record.setSetlListId(null);
        } else if (trimToNull(setlListId) != null) {
            record.setSetlListId(trimToNull(setlListId));
        }
        record.setSettleUploadAt(LocalDateTime.now());
        record.setSettleUploadError(trimToNull(errorMessage));
        record.setUpdatedAt(LocalDateTime.now());
        record.setUpdatedBy(operatorId);
        caseRecordRepository.save(record);
    }
}
