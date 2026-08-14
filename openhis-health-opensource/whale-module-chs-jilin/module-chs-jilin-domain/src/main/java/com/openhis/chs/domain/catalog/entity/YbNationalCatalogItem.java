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

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 国家医保目录镜像行（1312 查询 / 1301 解析入库）。
 * <p>量大，主路径走 JDBC bulk；JPA 路径亦排除框架审计。</p>
 */
@Getter
@Setter
@Entity
@Table(name = "yb_national_catalog_item")
public class YbNationalCatalogItem extends BaseAuditedEntity implements IExcludeFromAudit {

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

    /** 1301 最小包装数量（规范第 42 列 / raw_json 下标 41）。 */
    @Column(name = "min_pac_cnt", length = 20)
    private String minPacCnt;

    /** 1301 最小包装单位（规范第 43 列 / raw_json 下标 42）。 */
    @Column(name = "min_pacunt", length = 30)
    private String minPacunt;

    /** 1301 最小制剂单位（规范第 44 列 / raw_json 下标 43）。 */
    @Column(name = "min_prepunt", length = 30)
    private String minPrepunt;

    /** 1301 最小计价单位（规范第 49 列 / raw_json 下标 48）。 */
    @Column(name = "min_prcunt", length = 50)
    private String minPrcunt;
}
