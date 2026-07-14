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

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 国家医保目录镜像行（1312 查询 / 1301 解析入库） */
@Getter
@Setter
@Entity
@Table(name = "yb_national_catalog_item")
public class YbNationalCatalogItem extends BaseAuditedEntity {

    @Column(name = "contract_id", nullable = false)
    private Long contractId;

    @Column(name = "list_type", nullable = false, length = 30)
    private String listType;

    @Column(name = "hilist_code", nullable = false, length = 30)
    private String hilistCode;

    @Column(name = "hilist_name", length = 200)
    private String hilistName;

    /** 收费项目等级1/02/03 或平台中文「甲类」等，入库前规范化） */
    @Column(name = "chrgitm_lv", length = 16)
    private String chrgitmLv;

    @Column(name = "lmt_used_flag", length = 16)
    private String lmtUsedFlag;

    @Column(name = "begndate")
    private LocalDate begndate;

    @Column(name = "enddate")
    private LocalDate enddate;

    @Column(name = "vali_flag", length = 16)
    private String valiFlag;

    @Column(name = "spec", length = 200)
    private String spec;

    @Column(name = "dosform", length = 200)
    private String dosform;

    @Column(name = "aprvno", length = 30)
    private String aprvno;

    @Column(name = "prodentp_name", length = 200)
    private String prodentpName;

    @Column(name = "pinyin", length = 30)
    private String pinyin;

    @Column(name = "raw_json", columnDefinition = "TEXT")
    private String rawJson;

    @Column(name = "source_infno", length = 16)
    private String sourceInfno;

    @Column(name = "updt_time")
    private LocalDateTime updtTime;

    /** 1301 来源文件查询号；1312 行一般为 */
    @Column(name = "source_file_qury_no", length = 40)
    private String sourceFileQuryNo;

    /** 1301 TXT 行号（从 1 起）312 行为 */
    @Column(name = "source_line_no")
    private Integer sourceLineNo;
}
