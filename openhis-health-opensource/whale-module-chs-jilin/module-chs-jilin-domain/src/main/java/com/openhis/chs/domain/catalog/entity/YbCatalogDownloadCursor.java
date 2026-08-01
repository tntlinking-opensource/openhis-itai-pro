/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.chs.domain.catalog.entity;

import com.openhis.whale.core.domain.BaseAuditedEntity;
import com.openhis.whale.core.domain.IExcludeFromAudit;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * 医保目录下载游标（1301/1305 等文件型接口）。
 * <p>每版本快照追加，排除框架审计。</p>
 */
@Getter
@Setter
@Entity
@Table(name = "yb_catalog_download_cursor")
public class YbCatalogDownloadCursor extends BaseAuditedEntity implements IExcludeFromAudit {

    @Column(name = "contract_id", nullable = false)
    private Long contractId;

    @Column(name = "infno", nullable = false, length = 16)
    private String infno;

    /** 下次 1301 data.ver；库表最64（V5）；请求平台为整串版本名称（OpenHIS 一致） */
    @Column(name = "local_ver", nullable = false, length = 64)
    private String localVer = "0";

    @Column(name = "last_file_qury_no", length = 40)
    private String lastFileQuryNo;

    @Column(name = "last_filename", length = 200)
    private String lastFilename;

    @Column(name = "last_success_at")
    private LocalDateTime lastSuccessAt;
}
