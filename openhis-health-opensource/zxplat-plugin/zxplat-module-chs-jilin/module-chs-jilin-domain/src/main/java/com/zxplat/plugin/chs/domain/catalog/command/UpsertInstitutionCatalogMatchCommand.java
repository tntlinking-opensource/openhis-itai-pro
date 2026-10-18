/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.chs.domain.catalog.command;

import lombok.Data;
import lombok.experimental.Accessors;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 1317 镜像 upsert 单行入参。
 */
@Data
@Accessors(chain = true)
public class UpsertInstitutionCatalogMatchCommand {

    /** 定点医药机构编号 */
    private String fixmedinsCode;
    /** 院内目录编号 */
    private String medinsListCodg;
    /** 院内目录名称 */
    private String medinsListName;
    /** 参保区划 */
    private String insuAdmdvs;
    /** 目录类别 */
    private String listType;
    /** 国家医保目录编码 */
    private String medListCodg;
    /** 开始日期 */
    private LocalDate begndate;
    /** 结束日期 */
    private LocalDate enddate;
    /** 批准文号 */
    private String aprvno;
    /** 剂型 */
    private String dosform;
    /** 除外内容 */
    private String exctCont;
    /** 项目内涵 */
    private String itemCont;
    /** 计价单位 */
    private String prcunt;
    /** 规格 */
    private String spec;
    /** 包装规格 */
    private String pacspec;
    /** 备注 */
    private String memo;
    /** 有效标志 */
    private String valiFlag;
    /** 平台 rid */
    private String rid;
    /** 平台更新时间 */
    private LocalDateTime updtTime;
    /** 统筹区 */
    private String poolareaNo;
}
