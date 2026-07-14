/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.chs.domain.catalog.entity;

import com.openhis.whale.core.domain.BaseAuditedEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/**
 * 3301/3302 目录对照上传审计（C3 使用，C1 建表占位） */
@Getter
@Setter
@Entity
@Table(name = "yb_catalog_upload_record")
public class YbCatalogUploadRecord extends BaseAuditedEntity {

    @Column(name = "contract_id", nullable = false)
    private Long contractId;

    @Column(name = "operation", nullable = false, length = 8)
    private String operation;

    @Column(name = "product_table", nullable = false, length = 64)
    private String productTable;

    @Column(name = "product_id", nullable = false)
    private Long productId;

    @Column(name = "list_type", nullable = false, length = 30)
    private String listType;

    @Column(name = "fixmedins_hilist_id", nullable = false, length = 30)
    private String fixmedinsHilistId;

    @Column(name = "med_list_codg", nullable = false, length = 30)
    private String medListCodg;

    @Column(name = "platform_success", nullable = false)
    private Boolean platformSuccess;

    @Column(name = "msgid", length = 64)
    private String msgid;

    @Column(name = "request_snapshot", columnDefinition = "TEXT")
    private String requestSnapshot;

    @Column(name = "response_snapshot", columnDefinition = "TEXT")
    private String responseSnapshot;

    @Column(name = "error_msg", length = 512)
    private String errorMsg;

    @Column(name = "operator_id")
    private Long operatorId;
}
