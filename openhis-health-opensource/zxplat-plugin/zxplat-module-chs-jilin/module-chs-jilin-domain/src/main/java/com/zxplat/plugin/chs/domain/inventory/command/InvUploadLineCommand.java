/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.chs.domain.inventory.command;

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
    /** 机构批次流水号：对应业务单据 busNo（发药取发放行；库房类取单据头；非生产批号） */
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
    /**
     * 3505 零售单据号：= 省平台 {@code feedetl_sn}（收费项 busNo，否则收费项 id）；非发放单 busNo。
     * <p>3506 报文无此字段，勿写入平台。</p>
     */
    private String rtalDocno;
    /** 外购处方标志：院内发药固定非外购 */
    private String rxCircFlag;
    private BigDecimal finlTrnsPric;
    /**
     * 3506：退药单 busNo 暂存（伴生 3502 批次用此字段；平台 3506 定位仍用 {@link #fixmedinsBchno}=原销售批次）。
     */
    private String originalFixmedinsBchno;
    /**
     * 3506 对单：商品销售流水号 {@code medins_prod_sel_no}（吉林=3511 平台 {@code medinsProdSelNo}）。
     */
    private String medinsProdSelNo;
    /** 3505/3506：人员编号 */
    private String psnNo;
    /** 3505/3506：人员证件类型 */
    private String psnCertType;
    /** 3505/3506：证件号码 */
    private String certno;
    /** 3505/3506：人员姓名 */
    private String psnName;
    /**
     * 上报前业务校验失败原因（非平台字段；如配药人与发药人同一人）。
     * <p>有值时编排层记 FAILED，不调用平台。</p>
     */
    private String preUploadFailureMessage;
    /** 3503/3504：随货单号 */
    private String dyntNo;
    /** 3503/3504：供应商名称 */
    private String splerName;
    /** 3503/3504：供应商许可证号 */
    private String splerPmtno;
    /** 3503/3504：生产厂家名称 */
    private String prodentpName;
    /**
     * 3503/3504：批准文号（真源药品主数据 {@code approval_number}）。
     * <p>业务未填时 Loader / 出站组装回落 {@link com.zxplat.plugin.chs.domain.share.constants.ChsInventoryConstants#DEFAULT_APRVNO}。</p>
     */
    private String aprvno;
    /** 3503/3504：采购/退货数量 */
    private BigDecimal purcRetnCnt;
    /** 3503/3504：采购发票编码（可选） */
    private String purcInvoCodg;
    /**
     * 3503/3504：采购发票号（真源 {@code wkf_supply_request.invoice_no}）。
     * <p>业务未填时 Loader 回落 {@link com.zxplat.plugin.chs.domain.share.constants.ChsInventoryConstants#DEFAULT_PURC_INVO_NO}。</p>
     */
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
    /** 3502：变更数量（操作单位；入库类正、出库类负，见 {@code InvChgType#toSignedCnt}） */
    private BigDecimal cnt;
    /** 3502：单价 */
    private BigDecimal pric;
    /** 3502：库存变更时间 yyyy-MM-dd HH:mm:ss */
    private String invChgTime;
    /** 3502：库存变更经办人姓名 */
    private String invChgOpterName;
}
