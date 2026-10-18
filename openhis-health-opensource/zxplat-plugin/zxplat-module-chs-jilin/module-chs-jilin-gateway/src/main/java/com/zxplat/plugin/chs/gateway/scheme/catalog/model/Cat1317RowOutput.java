/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.chs.gateway.scheme.catalog.model;

import lombok.Data;

/**
 * 【1317】医药机构目录匹配信息查询单行 output。
 * <p>字段对齐平台规范；落库与回写使用核心字段，其余保留备查。</p>
 */
@Data
public class Cat1317RowOutput {

    /** 定点医药机构编号 */
    private String fixmedinsCode;
    /** 定点医药机构目录编号（院内编码） */
    private String medinsListCodg;
    /** 定点医药机构目录名称 */
    private String medinsListName;
    /** 参保机构医保区划 */
    private String insuAdmdvs;
    /** 目录类别 */
    private String listType;
    /** 医疗目录编码（国家医保码） */
    private String medListCodg;
    /** 开始日期 */
    private String begndate;
    /** 结束日期 */
    private String enddate;
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
    /** 唯一记录号 */
    private String rid;
    /** 更新时间 */
    private String updtTime;
    /** 创建人 */
    private String crterId;
    /** 创建人姓名 */
    private String crterName;
    /** 创建时间 */
    private String crteTime;
    /** 创建机构 */
    private String crteOptinsNo;
    /** 经办人 */
    private String opterId;
    /** 经办人姓名 */
    private String opterName;
    /** 经办时间 */
    private String optTime;
    /** 经办机构 */
    private String optinsNo;
    /** 统筹区 */
    private String poolareaNo;
}
