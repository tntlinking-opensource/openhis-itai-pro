/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.chs.domain.inventory.command;

import lombok.Data;
import lombok.experimental.Accessors;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/** 3503–3506 单行组装输入（bridge 填充，application 消费） */
@Data
@Accessors(chain = true)
public class InvUploadLineCommand {

    private Long sourceLineId;
    private String tracePolicy;
    private String medListCodg;
    private String fixmedinsHilistId;
    private String fixmedinsHilistName;
    private String fixmedinsBchno;
    private String manuLotnum;
    private String manuDate;
    private String expyEnd;
    private String rxFlag;
    private String trdnFlag;
    private List<String> drugTracCodgs = new ArrayList<>();
    private BigDecimal selRetnCnt;
    private String selRetnTime;
    private String selRetnOpterName;
    private String prscDrName;
    private String pharName;
    private String pharPracCertNo;
    private String hiFeesetlType;
    private String setlId;
    private String mdtrtSn;
    private String mdtrtSetlType;
    private String rtalDocno;
    private BigDecimal finlTrnsPric;
    /** 3506：原销售上报 fixmedins_bchno（可空） */
    private String originalFixmedinsBchno;
    /** 3506：原商品销售流水号（关联 3505） */
    private String medinsProdSelNo;
    /** 3505/3506：人员编号 */
    private String psnNo;
    /** 3505/3506：人员证件类型 */
    private String psnCertType;
    /** 3505/3506：证件号码 */
    private String certno;
    /** 3505/3506：人员姓名 */
    private String psnName;
    /** 3503/3504：随货单号 */
    private String dyntNo;
    /** 3503/3504：供应商名称 */
    private String splerName;
    /** 3503/3504：供应商许可证号 */
    private String splerPmtno;
    /** 3503/3504：生产厂家名称 */
    private String prodentpName;
    /** 3503/3504：批准文号 */
    private String aprvno;
    /** 3503/3504：采购/退货数量 */
    private BigDecimal purcRetnCnt;
    /** 3503/3504：采购发票编码 */
    private String purcInvoCodg;
    /** 3503/3504：采购发票号 */
    private String purcInvoNo;
    /** 3503/3504：采购/退货入库时间 */
    private String purcRetnStoinTime;
    /** 3503/3504：采购/退货经办人姓名 */
    private String purcRetnOpterName;
    /** 3503/3504：商品赠送标志（0 否 / 1 是） */
    private String prodGeayFlag;
    /** 3503/3504：备注 */
    private String memo;
    /** 3503/3504：原商品采购流水号（关联 3503） */
    private String medinsProdPurcNo;

    /** 3501：盘存日期 yyyy-MM-dd */
    private String invdate;
    /** 3501：库存数量（最小包装单位） */
    private BigDecimal invCnt;

    /** 3502：库存变更类型（103 盘盈 / 104 盘损等） */
    private String invChgType;
    /** 3502：变更数量（最小包装单位，正数） */
    private BigDecimal cnt;
    /** 3502：单价 */
    private BigDecimal pric;
    /** 3502：库存变更时间 yyyy-MM-dd HH:mm:ss */
    private String invChgTime;
    /** 3502：库存变更经办人姓名 */
    private String invChgOpterName;
}
