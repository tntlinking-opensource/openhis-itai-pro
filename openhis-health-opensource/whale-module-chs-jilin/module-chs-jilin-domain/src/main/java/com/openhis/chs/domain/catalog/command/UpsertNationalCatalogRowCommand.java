/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.chs.domain.catalog.command;

import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 国家目录镜像 upsert 单行入参312/1301 解析后写入域层） */
@Data
public class UpsertNationalCatalogRowCommand {

    private String listType;
    private String hilistCode;
    private String hilistName;
    private String chrgitmLv;
    private String lmtUsedFlag;
    private LocalDate begndate;
    private LocalDate enddate;
    private String valiFlag;
    private String pinyin;
    private String spec;
    private String dosform;
    private String aprvno;
    private String prodentpName;
    private String sourceInfno;
    private LocalDateTime updtTime;
    private String rawJson;

    /** 1301 最小包装数量（规范第 42 列） */
    private String minPacCnt;

    /** 1301 最小包装单位（规范第 43 列） */
    private String minPacunt;

    /** 1301 最小制剂单位（规范第 44 列） */
    private String minPrepunt;

    /** 1301 最小计价单位（规范第 49 列） */
    private String minPrcunt;

    /** 1301 文件查询号（与游last_file_qury_no 一致） */
    private String sourceFileQuryNo;

    /** 1301 TXT 行号（从 1 起） */
    private Integer sourceLineNo;
}
