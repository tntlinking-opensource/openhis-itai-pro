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
 * 医药机构目录匹配镜像行（1317 查询入库）。
 * <p>唯一键：contract_id + tenant_id + medins_list_codg + med_list_codg + list_type。</p>
 */
@Getter
@Setter
@Entity
@Table(name = "yb_institution_catalog_match")
public class YbInstitutionCatalogMatch extends BaseAuditedEntity implements IExcludeFromAudit {

    /** 费用契约主键 */
    @Column(name = "contract_id", nullable = false)
    private Long contractId;

    /** 定点医药机构编号 */
    @Column(name = "fixmedins_code", length = 30)
    private String fixmedinsCode;

    /** 定点医药机构目录编号（院内 bus_no） */
    @Column(name = "medins_list_codg", nullable = false, length = 30)
    private String medinsListCodg;

    /** 定点医药机构目录名称 */
    @Column(name = "medins_list_name", length = 200)
    private String medinsListName;

    /** 参保机构医保区划 */
    @Column(name = "insu_admdvs", length = 6)
    private String insuAdmdvs;

    /** 目录类别（101/102/201/301） */
    @Column(name = "list_type", nullable = false, length = 30)
    private String listType;

    /** 医疗目录编码（国家医保码） */
    @Column(name = "med_list_codg", nullable = false, length = 30)
    private String medListCodg;

    /** 开始日期 */
    @Column(name = "begndate")
    private LocalDate begndate;

    /** 结束日期 */
    @Column(name = "enddate")
    private LocalDate enddate;

    /** 批准文号 */
    @Column(name = "aprvno", length = 30)
    private String aprvno;

    /** 剂型 */
    @Column(name = "dosform", length = 200)
    private String dosform;

    /** 除外内容 */
    @Column(name = "exct_cont", length = 2000)
    private String exctCont;

    /** 项目内涵 */
    @Column(name = "item_cont", length = 2000)
    private String itemCont;

    /** 计价单位 */
    @Column(name = "prcunt", length = 100)
    private String prcunt;

    /** 规格 */
    @Column(name = "spec", length = 200)
    private String spec;

    /** 包装规格 */
    @Column(name = "pacspec", length = 100)
    private String pacspec;

    /** 备注 */
    @Column(name = "memo", length = 500)
    private String memo;

    /** 有效标志 */
    @Column(name = "vali_flag", length = 16)
    private String valiFlag;

    /** 平台唯一记录号 */
    @Column(name = "rid", length = 40)
    private String rid;

    /** 平台更新时间 */
    @Column(name = "updt_time")
    private LocalDateTime updtTime;

    /** 统筹区 */
    @Column(name = "poolarea_no", length = 6)
    private String poolareaNo;
}
