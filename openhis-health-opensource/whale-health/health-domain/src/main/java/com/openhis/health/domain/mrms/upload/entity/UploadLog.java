/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.domain.mrms.upload.entity;

import com.openhis.whale.core.domain.BaseAuditedEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * 病案 4401 上传日志（{@code mrms_upload_log}），每次上传尝试一条记录。
 */
@Getter
@Setter
@Entity
@Table(name = "mrms_upload_log")
public class UploadLog extends BaseAuditedEntity {

    /** 关联病案主档 {@code mrms_case_record.id}。 */
    @Column(name = "case_record_id", nullable = false)
    private Long caseRecordId;

    /** 医保合同业务号（冗余，便于日志与合约对账）。 */
    @Column(name = "contract_bus_no", length = 64)
    private String contractBusNo;

    /** 医保合同主键 {@code chs_contract.id}。 */
    @Column(name = "contract_id")
    private Long contractId;

    /** 医保接口编号（4401 等）。 */
    @Column(name = "infno", nullable = false, length = 16)
    private String infno;

    /** 上传结果状态，存 {@link com.openhis.health.domain.share.enums.ChsUploadStatus} code。 */
    @Column(name = "status_code", nullable = false, length = 32)
    private String statusCode;

    /** 医保报文 msgid（成功时回写）。 */
    @Column(name = "msgid", length = 64)
    private String msgid;

    /** 请求体摘要（脱敏 hash，幂等与审计用）。 */
    @Column(name = "request_digest", length = 128)
    private String requestDigest;

    /** 医保响应快照 JSON（脱敏落库，排障用）。 */
    @Column(name = "response_snapshot", columnDefinition = "TEXT")
    private String responseSnapshot;

    /** 平台错误摘要（失败时写入，成功可为空）。 */
    @Column(name = "err_msg", length = 500)
    private String errMsg;

    /** 医保 infcode 回执码。 */
    @Column(name = "infcode", length = 16)
    private String infcode;

    /**
     * 写入一条上传日志（成功或失败均落库）。
     *
     * @param tenantId 租户
     * @param caseRecordId 病案主档
     * @param contractBusNo 合约业务号
     * @param contractId 合约主键
     * @param infno 接口编号
     * @param statusCode 上传状态 code
     * @param requestDigest 请求摘要
     * @param infcode 平台回执码
     * @param errMsg 错误信息
     * @param responseSnapshot 响应快照
     * @param operatorId 操作人
     */
    public void init(
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
        this.setTenantId(tenantId);
        this.caseRecordId = caseRecordId;
        this.contractBusNo = contractBusNo;
        this.contractId = contractId;
        this.infno = infno;
        this.statusCode = statusCode;
        this.requestDigest = requestDigest;
        this.infcode = infcode;
        this.errMsg = errMsg;
        this.responseSnapshot = responseSnapshot;
        this.setCreatedAt(LocalDateTime.now());
        this.setCreatedBy(operatorId);
        this.setIsDeleted(false);
    }
}
