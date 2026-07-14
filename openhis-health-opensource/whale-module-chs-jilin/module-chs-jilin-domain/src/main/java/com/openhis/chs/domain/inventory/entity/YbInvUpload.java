/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.chs.domain.inventory.entity;

import com.openhis.whale.core.domain.BaseAuditedEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/** 350x 进销存上报流水 */
@Getter
@Setter
@Entity
@Table(name = "yb_inv_upload")
public class YbInvUpload extends BaseAuditedEntity {

    @Column(name = "contract_id", nullable = false)
    private Long contractId;

    @Column(name = "contract_bus_no", nullable = false, length = 32)
    private String contractBusNo;

    @Column(name = "infno", nullable = false, length = 8)
    private String infno;

    @Column(name = "inv_data_type", length = 4)
    private String invDataType;

    @Column(name = "fixmedins_bchno", nullable = false, length = 30)
    private String fixmedinsBchno;

    @Column(name = "med_list_codg", length = 50)
    private String medListCodg;

    @Column(name = "source_type", nullable = false, length = 32)
    private String sourceType;

    @Column(name = "source_id")
    private Long sourceId;

    @Column(name = "source_line_id")
    private Long sourceLineId;

    @Column(name = "upload_status", nullable = false, length = 16)
    private String uploadStatus;

    @Column(name = "platform_ret_rslt", length = 8)
    private String platformRetRslt;

    @Column(name = "platform_msg", length = 2000)
    private String platformMsg;

    @Column(name = "msgid", length = 64)
    private String msgid;

    @Column(name = "request_json", columnDefinition = "TEXT")
    private String requestJson;

    @Column(name = "response_json", columnDefinition = "TEXT")
    private String responseJson;

    @Column(name = "retry_count", nullable = false)
    private Integer retryCount;

    @Column(name = "next_retry_at")
    private LocalDateTime nextRetryAt;

    @Column(name = "operator_id")
    private Long operatorId;
}
