/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.chs.gateway.scheme.inventory.assembler;

import static com.openhis.whale.core.application.util.StringUtils.isNotEmpty;

import static com.openhis.whale.core.application.util.StringUtils.isEmpty;
import com.openhis.chs.gateway.scheme.inventory.model.Inv3501Input;
import com.openhis.chs.gateway.scheme.inventory.model.Inv3502Input;
import com.openhis.chs.gateway.scheme.inventory.model.Inv3503Input;
import com.openhis.chs.gateway.scheme.inventory.model.Inv3504Input;
import com.openhis.chs.gateway.scheme.inventory.model.Inv3505Input;
import com.openhis.chs.gateway.scheme.inventory.model.Inv3506Input;
import com.openhis.chs.gateway.scheme.inventory.model.Inv3507Input;
import com.openhis.chs.gateway.scheme.inventory.model.InvDrugtracinfo;
import com.openhis.chs.gateway.scheme.inventory.model.InvInvinfo;
import com.openhis.chs.gateway.scheme.inventory.model.InvPlatformQueryInput;
import com.openhis.chs.gateway.scheme.inventory.model.InvPurcinfo;
import com.openhis.chs.gateway.scheme.inventory.model.InvSelinfo;
import com.openhis.chs.domain.inventory.command.InvUploadLineCommand;
import com.openhis.chs.domain.share.enums.InvChgType;
import com.openhis.chs.domain.inventory.queries.QueryPlatformInvQuery;

import java.util.ArrayList;
import java.util.List;

/** 3501507 上传 3508513 查询平台报文组装 */
public final class InvPlatformInputAssembler {

    private InvPlatformInputAssembler() {
    }

    /** 505】销售上input.selinfo（含 drugtracinfo 追溯码） */
    public static Inv3505Input assemble3505(InvUploadLineCommand line) {
        InvSelinfo selinfo = buildSelinfo3505(line);
        List<InvDrugtracinfo> drugtracinfo = buildDrugtracinfo(line);
        if (!drugtracinfo.isEmpty()) {
            selinfo.setDrugtracinfo(drugtracinfo);
        }
        return new Inv3505Input().setSelinfo(selinfo);
    }

    /** 506】销售退货上input.selinfo */
    public static Inv3506Input assemble3506(InvUploadLineCommand line) {
        InvSelinfo selinfo = buildSelinfo3506(line);
        List<InvDrugtracinfo> drugtracinfo = buildDrugtracinfo(line);
        if (!drugtracinfo.isEmpty()) {
            selinfo.setDrugtracinfo(drugtracinfo);
        }
        return new Inv3506Input().setSelinfo(selinfo);
    }

    /** 503】采购上input.purcinfo */
    public static Inv3503Input assemble3503(InvUploadLineCommand line) {
        return new Inv3503Input().setPurcinfo(buildPurcinfo3503(line));
    }

    /** 504】采购退货上input.purcinfo（含 medins_prod_purc_no） */
    public static Inv3504Input assemble3504(InvUploadLineCommand line) {
        InvPurcinfo purcinfo = buildPurcinfo3503(line);
        setIfNotBlank(purcinfo::setMedinsProdPurcNo, line.getMedinsProdPurcNo());
        return new Inv3504Input().setPurcinfo(purcinfo);
    }

    /** 501】盘存上input.invinfo */
    public static Inv3501Input assemble3501(InvUploadLineCommand line) {
        InvInvinfo invinfo = new InvInvinfo();
        setIfNotBlank(invinfo::setMedListCodg, line.getMedListCodg());
        setIfNotBlank(invinfo::setFixmedinsHilistId, line.getFixmedinsHilistId());
        setIfNotBlank(invinfo::setFixmedinsHilistName, line.getFixmedinsHilistName());
        setIfNotBlank(invinfo::setRxFlag, line.getRxFlag());
        setIfNotBlank(invinfo::setInvdate, line.getInvdate());
        if (line.getInvCnt() != null) {
            invinfo.setInvCnt(line.getInvCnt());
        } else if (line.getPurcRetnCnt() != null) {
            invinfo.setInvCnt(line.getPurcRetnCnt());
        }
        setIfNotBlank(invinfo::setManuLotnum, line.getManuLotnum());
        setIfNotBlank(invinfo::setFixmedinsBchno, line.getFixmedinsBchno());
        setIfNotBlank(invinfo::setManuDate, line.getManuDate());
        setIfNotBlank(invinfo::setExpyEnd, line.getExpyEnd());
        setIfNotBlank(invinfo::setMemo, line.getMemo());
        return new Inv3501Input().setInvinfo(invinfo);
    }

    /** 【3502】库存变更 input.invinfo（含 inv_chg_type、有符号 cnt、drugtracinfo）。 */
    public static Inv3502Input assemble3502(InvUploadLineCommand line) {
        if (line == null) {
            throw new IllegalArgumentException("3502 上报行不能为空");
        }
        if (isEmpty(line.getInvChgType())) {
            throw new IllegalArgumentException("3502 inv_chg_type 不能为空");
        }
        InvChgType invChgType = InvChgType.requireCode(line.getInvChgType());
        // Gate：cnt 符号须与 inv_chg_type 方向一致（入库正、出库负）
        invChgType.validateQtyDirection(line.getCnt());
        InvInvinfo invinfo = new InvInvinfo();
        setIfNotBlank(invinfo::setMedListCodg, line.getMedListCodg());
        invinfo.setInvChgType(invChgType.getCode());
        setIfNotBlank(invinfo::setFixmedinsHilistId, line.getFixmedinsHilistId());
        setIfNotBlank(invinfo::setFixmedinsHilistName, line.getFixmedinsHilistName());
        setIfNotBlank(invinfo::setFixmedinsBchno, line.getFixmedinsBchno());
        if (line.getPric() != null) {
            invinfo.setPric(line.getPric());
        } else if (line.getFinlTrnsPric() != null) {
            invinfo.setPric(line.getFinlTrnsPric());
        }
        if (line.getCnt() != null) {
            invinfo.setCnt(line.getCnt());
        }
        setIfNotBlank(invinfo::setRxFlag, line.getRxFlag());
        // 批号/效期：与初始化、库房 3502 Loader 写入的行字段对齐出站
        setIfNotBlank(invinfo::setManuLotnum, line.getManuLotnum());
        setIfNotBlank(invinfo::setManuDate, line.getManuDate());
        setIfNotBlank(invinfo::setExpyEnd, line.getExpyEnd());
        setIfNotBlank(invinfo::setInvChgTime, line.getInvChgTime());
        setIfNotBlank(invinfo::setInvChgOpterName, line.getInvChgOpterName());
        setIfNotBlank(invinfo::setMemo, line.getMemo());
        setIfNotBlank(invinfo::setTrdnFlag, line.getTrdnFlag());
        List<InvDrugtracinfo> drugtracinfo = buildDrugtracinfo(line);
        if (!drugtracinfo.isEmpty()) {
            invinfo.setDrugtracinfo(drugtracinfo);
        }
        return new Inv3502Input().setInvinfo(invinfo);
    }

    /** 507】删除进销存数input.data（fixmedins_bchno + inv_data_type） */
    public static Inv3507Input assemble3507(String fixmedinsBchno, String invDataType) {
        Inv3507Input.Inv3507Data data = new Inv3507Input.Inv3507Data();
        setIfNotBlank(data::setFixmedinsBchno, fixmedinsBchno);
        setIfNotBlank(data::setInvDataType, invDataType);
        Inv3507Input input = new Inv3507Input();
        input.setData(data);
        return input;
    }

    /** 508513】平台进销存查询通用 input.data（按 infno 区分查询类型） */
    public static InvPlatformQueryInput assemblePlatformQuery(QueryPlatformInvQuery request, String fixmedinsCode) {
        InvPlatformQueryInput.InvPlatformQueryData data = new InvPlatformQueryInput.InvPlatformQueryData();
        setIfNotBlank(data::setFixmedinsCode, fixmedinsCode);
        setIfNotBlank(data::setMedinsListCodg, request.getMedinsListCodg());
        setIfNotBlank(data::setFixmedinsBchno, request.getFixmedinsBchno());
        setIfNotBlank(data::setBegndate, request.getBegndate());
        setIfNotBlank(data::setEnddate, request.getEnddate());
        setIfNotBlank(data::setMedListCodg, request.getMedListCodg());
        setIfNotBlank(data::setMedinsProdSelNo, request.getMedinsProdSelNo());
        setIfNotBlank(data::setMedinsProdPurcNo, request.getMedinsProdPurcNo());
        setIfNotBlank(data::setMedinsProdInvNo, request.getMedinsProdInvNo());
        setIfNotBlank(data::setPsnNo, request.getPsnNo());
        setIfNotBlank(data::setCertno, request.getCertno());
        setIfNotBlank(data::setMdtrtId, request.getMdtrtId());
        setIfNotBlank(data::setDrugTracCodg, request.getDrugTracCodg());
        setIfNotBlank(data::setValiFlag, request.getValiFlag());
        InvPlatformQueryInput input = new InvPlatformQueryInput();
        input.setData(data);
        return input;
    }

    private static InvSelinfo buildSelinfo3505(InvUploadLineCommand line) {
        InvSelinfo selinfo = new InvSelinfo();
        setIfNotBlank(selinfo::setMedListCodg, line.getMedListCodg());
        setIfNotBlank(selinfo::setFixmedinsHilistId, line.getFixmedinsHilistId());
        setIfNotBlank(selinfo::setFixmedinsHilistName, line.getFixmedinsHilistName());
        setIfNotBlank(selinfo::setFixmedinsBchno, line.getFixmedinsBchno());
        setIfNotBlank(selinfo::setPrscDrName, line.getPrscDrName());
        setIfNotBlank(selinfo::setPharName, line.getPharName());
        setIfNotBlank(selinfo::setPharPracCertNo, line.getPharPracCertNo());
        setIfNotBlank(selinfo::setHiFeesetlType, line.getHiFeesetlType());
        setIfNotBlank(selinfo::setSetlId, line.getSetlId());
        setIfNotBlank(selinfo::setMdtrtSn, line.getMdtrtSn());
        setIfNotBlank(selinfo::setManuLotnum, line.getManuLotnum());
        setIfNotBlank(selinfo::setManuDate, line.getManuDate());
        setIfNotBlank(selinfo::setExpyEnd, line.getExpyEnd());
        setIfNotBlank(selinfo::setRxFlag, line.getRxFlag());
        setIfNotBlank(selinfo::setTrdnFlag, line.getTrdnFlag());
        if (line.getFinlTrnsPric() != null) {
            selinfo.setFinlTrnsPric(line.getFinlTrnsPric());
        }
        setIfNotBlank(selinfo::setRtalDocno, line.getRtalDocno());
        setIfNotBlank(selinfo::setRxCircFlag, line.getRxCircFlag());
        if (line.getSelRetnCnt() != null) {
            selinfo.setSelRetnCnt(line.getSelRetnCnt());
        }
        setIfNotBlank(selinfo::setSelRetnTime, line.getSelRetnTime());
        setIfNotBlank(selinfo::setSelRetnOpterName, line.getSelRetnOpterName());
        setIfNotBlank(selinfo::setMdtrtSetlType, line.getMdtrtSetlType());
        setIfNotBlank(selinfo::setPsnNo, line.getPsnNo());
        setIfNotBlank(selinfo::setPsnCertType, line.getPsnCertType());
        setIfNotBlank(selinfo::setCertno, line.getCertno());
        setIfNotBlank(selinfo::setPsnName, line.getPsnName());
        return selinfo;
    }

    private static InvSelinfo buildSelinfo3506(InvUploadLineCommand line) {
        InvSelinfo selinfo = new InvSelinfo();
        setIfNotBlank(selinfo::setMedListCodg, line.getMedListCodg());
        setIfNotBlank(selinfo::setFixmedinsHilistId, line.getFixmedinsHilistId());
        setIfNotBlank(selinfo::setFixmedinsHilistName, line.getFixmedinsHilistName());
        setIfNotBlank(selinfo::setFixmedinsBchno, line.getFixmedinsBchno());
        setIfNotBlank(selinfo::setSetlId, line.getSetlId());
        setIfNotBlank(selinfo::setMdtrtSn, line.getMdtrtSn());
        setIfNotBlank(selinfo::setManuLotnum, line.getManuLotnum());
        setIfNotBlank(selinfo::setManuDate, line.getManuDate());
        setIfNotBlank(selinfo::setExpyEnd, line.getExpyEnd());
        setIfNotBlank(selinfo::setRxFlag, line.getRxFlag());
        setIfNotBlank(selinfo::setTrdnFlag, line.getTrdnFlag());
        if (line.getFinlTrnsPric() != null) {
            selinfo.setFinlTrnsPric(line.getFinlTrnsPric());
        }
        if (line.getSelRetnCnt() != null) {
            selinfo.setSelRetnCnt(line.getSelRetnCnt());
        }
        setIfNotBlank(selinfo::setSelRetnTime, line.getSelRetnTime());
        setIfNotBlank(selinfo::setSelRetnOpterName, line.getSelRetnOpterName());
        // 3506 无 rtal_docno；对单字段为 medins_prod_sel_no（吉林=3511 平台销售流水）
        setIfNotBlank(selinfo::setPsnNo, line.getPsnNo());
        setIfNotBlank(selinfo::setPsnCertType, line.getPsnCertType());
        setIfNotBlank(selinfo::setCertno, line.getCertno());
        setIfNotBlank(selinfo::setPsnName, line.getPsnName());
        setIfNotBlank(selinfo::setMedinsProdSelNo, line.getMedinsProdSelNo());
        return selinfo;
    }

    private static InvPurcinfo buildPurcinfo3503(InvUploadLineCommand line) {
        InvPurcinfo purcinfo = new InvPurcinfo();
        setIfNotBlank(purcinfo::setMedListCodg, line.getMedListCodg());
        setIfNotBlank(purcinfo::setFixmedinsHilistId, line.getFixmedinsHilistId());
        setIfNotBlank(purcinfo::setFixmedinsHilistName, line.getFixmedinsHilistName());
        setIfNotBlank(purcinfo::setDyntNo, line.getDyntNo());
        setIfNotBlank(purcinfo::setFixmedinsBchno, line.getFixmedinsBchno());
        setIfNotBlank(purcinfo::setSplerName, line.getSplerName());
        setIfNotBlank(purcinfo::setSplerPmtno, line.getSplerPmtno());
        setIfNotBlank(purcinfo::setManuLotnum, line.getManuLotnum());
        setIfNotBlank(purcinfo::setProdentpName, line.getProdentpName());
        setIfNotBlank(purcinfo::setAprvno, line.getAprvno());
        setIfNotBlank(purcinfo::setManuDate, line.getManuDate());
        setIfNotBlank(purcinfo::setExpyEnd, line.getExpyEnd());
        if (line.getFinlTrnsPric() != null) {
            purcinfo.setFinlTrnsPric(line.getFinlTrnsPric());
        }
        if (line.getPurcRetnCnt() != null) {
            purcinfo.setPurcRetnCnt(line.getPurcRetnCnt());
        }
        setIfNotBlank(purcinfo::setPurcInvoCodg, line.getPurcInvoCodg());
        setIfNotBlank(purcinfo::setPurcInvoNo, line.getPurcInvoNo());
        setIfNotBlank(purcinfo::setRxFlag, line.getRxFlag());
        setIfNotBlank(purcinfo::setPurcRetnStoinTime, line.getPurcRetnStoinTime());
        setIfNotBlank(purcinfo::setPurcRetnOpterName, line.getPurcRetnOpterName());
        setIfNotBlank(purcinfo::setProdGeayFlag, line.getProdGeayFlag());
        setIfNotBlank(purcinfo::setMemo, line.getMemo());
        return purcinfo;
    }

    private static List<InvDrugtracinfo> buildDrugtracinfo(InvUploadLineCommand line) {
        List<InvDrugtracinfo> list = new ArrayList<>();
        if (line.getDrugTracCodgs() == null || line.getDrugTracCodgs().isEmpty()) {
            return list;
        }
        for (String code : line.getDrugTracCodgs()) {
            if (isEmpty(code)) {
                continue;
            }
            list.add(new InvDrugtracinfo().setDrugTracCodg(code.trim()));
        }
        return list;
    }

    private static void setIfNotBlank(java.util.function.Consumer<String> setter, String value) {
        if (isNotEmpty(value)) {
            setter.accept(value.trim());
        }
    }
}
