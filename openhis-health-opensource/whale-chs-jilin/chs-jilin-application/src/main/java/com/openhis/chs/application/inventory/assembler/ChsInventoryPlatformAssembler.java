/*
 * Copyright © 2026 OpenHIS Contributors
 */

package com.openhis.chs.application.inventory.assembler;

import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import com.openhis.chs.domain.inventory.dto.InvUploadLineCommand;
import com.openhis.chs.domain.inventory.dto.request.QueryPlatformInvRequest;
import org.apache.commons.lang3.StringUtils;

/** 3501–3507 上传与 3508–3513 查询平台报文组装。 */
public final class ChsInventoryPlatformAssembler {

    private ChsInventoryPlatformAssembler() {
    }

    /** 【3505】销售上传 input.selinfo（含 drugtracinfo 追溯码）。 */
    public static JSONObject assemble3505(InvUploadLineCommand line) {
        JSONObject selinfo = new JSONObject();
        putIfNotBlank(selinfo, "med_list_codg", line.getMedListCodg());
        putIfNotBlank(selinfo, "fixmedins_hilist_id", line.getFixmedinsHilistId());
        putIfNotBlank(selinfo, "fixmedins_hilist_name", line.getFixmedinsHilistName());
        putIfNotBlank(selinfo, "fixmedins_bchno", line.getFixmedinsBchno());
        putIfNotBlank(selinfo, "prsc_dr_name", line.getPrscDrName());
        putIfNotBlank(selinfo, "phar_name", line.getPharName());
        putIfNotBlank(selinfo, "phar_prac_cert_no", line.getPharPracCertNo());
        putIfNotBlank(selinfo, "hi_feesetl_type", line.getHiFeesetlType());
        putIfNotBlank(selinfo, "setl_id", line.getSetlId());
        putIfNotBlank(selinfo, "mdtrt_sn", line.getMdtrtSn());
        putIfNotBlank(selinfo, "manu_lotnum", line.getManuLotnum());
        putIfNotBlank(selinfo, "manu_date", line.getManuDate());
        putIfNotBlank(selinfo, "expy_end", line.getExpyEnd());
        putIfNotBlank(selinfo, "rx_flag", line.getRxFlag());
        putIfNotBlank(selinfo, "trdn_flag", line.getTrdnFlag());
        if (line.getFinlTrnsPric() != null) {
            selinfo.put("finl_trns_pric", line.getFinlTrnsPric());
        }
        putIfNotBlank(selinfo, "rtal_docno", line.getRtalDocno());
        if (line.getSelRetnCnt() != null) {
            selinfo.put("sel_retn_cnt", line.getSelRetnCnt());
        }
        putIfNotBlank(selinfo, "sel_retn_time", line.getSelRetnTime());
        putIfNotBlank(selinfo, "sel_retn_opter_name", line.getSelRetnOpterName());
        putIfNotBlank(selinfo, "mdtrt_setl_type", line.getMdtrtSetlType());
        putIfNotBlank(selinfo, "psn_no", line.getPsnNo());
        putIfNotBlank(selinfo, "psn_cert_type", line.getPsnCertType());
        putIfNotBlank(selinfo, "certno", line.getCertno());
        putIfNotBlank(selinfo, "psn_name", line.getPsnName());
        JSONArray drugtracinfo = buildDrugtracinfo(line);
        if (!drugtracinfo.isEmpty()) {
            selinfo.put("drugtracinfo", drugtracinfo);
        }
        JSONObject input = new JSONObject();
        input.put("selinfo", selinfo);
        return input;
    }

    /** 【3506】销售退货上传 input.selinfo。 */
    public static JSONObject assemble3506(InvUploadLineCommand line) {
        JSONObject selinfo = new JSONObject();
        putIfNotBlank(selinfo, "med_list_codg", line.getMedListCodg());
        putIfNotBlank(selinfo, "fixmedins_hilist_id", line.getFixmedinsHilistId());
        putIfNotBlank(selinfo, "fixmedins_hilist_name", line.getFixmedinsHilistName());
        putIfNotBlank(selinfo, "fixmedins_bchno", line.getFixmedinsBchno());
        putIfNotBlank(selinfo, "setl_id", line.getSetlId());
        putIfNotBlank(selinfo, "mdtrt_sn", line.getMdtrtSn());
        putIfNotBlank(selinfo, "manu_lotnum", line.getManuLotnum());
        putIfNotBlank(selinfo, "manu_date", line.getManuDate());
        putIfNotBlank(selinfo, "expy_end", line.getExpyEnd());
        putIfNotBlank(selinfo, "rx_flag", line.getRxFlag());
        putIfNotBlank(selinfo, "trdn_flag", line.getTrdnFlag());
        if (line.getFinlTrnsPric() != null) {
            selinfo.put("finl_trns_pric", line.getFinlTrnsPric());
        }
        if (line.getSelRetnCnt() != null) {
            selinfo.put("sel_retn_cnt", line.getSelRetnCnt());
        }
        putIfNotBlank(selinfo, "sel_retn_time", line.getSelRetnTime());
        putIfNotBlank(selinfo, "sel_retn_opter_name", line.getSelRetnOpterName());
        putIfNotBlank(selinfo, "psn_no", line.getPsnNo());
        putIfNotBlank(selinfo, "psn_cert_type", line.getPsnCertType());
        putIfNotBlank(selinfo, "certno", line.getCertno());
        putIfNotBlank(selinfo, "psn_name", line.getPsnName());
        putIfNotBlank(selinfo, "medins_prod_sel_no", line.getMedinsProdSelNo());
        JSONArray drugtracinfo = buildDrugtracinfo(line);
        if (!drugtracinfo.isEmpty()) {
            selinfo.put("drugtracinfo", drugtracinfo);
        }
        JSONObject input = new JSONObject();
        input.put("selinfo", selinfo);
        return input;
    }

    /** 【3503】采购上传 input.purcinfo。 */
    public static JSONObject assemble3503(InvUploadLineCommand line) {
        JSONObject purcinfo = buildPurcinfo3503(line);
        JSONObject input = new JSONObject();
        input.put("purcinfo", purcinfo);
        return input;
    }

    /** 【3504】采购退货上传 input.purcinfo（含 medins_prod_purc_no）。 */
    public static JSONObject assemble3504(InvUploadLineCommand line) {
        JSONObject purcinfo = buildPurcinfo3503(line);
        putIfNotBlank(purcinfo, "medins_prod_purc_no", line.getMedinsProdPurcNo());
        JSONObject input = new JSONObject();
        input.put("purcinfo", purcinfo);
        return input;
    }

    /** 【3501】盘存上传 input.invinfo。 */
    public static JSONObject assemble3501(InvUploadLineCommand line) {
        JSONObject invinfo = new JSONObject();
        putIfNotBlank(invinfo, "med_list_codg", line.getMedListCodg());
        putIfNotBlank(invinfo, "fixmedins_hilist_id", line.getFixmedinsHilistId());
        putIfNotBlank(invinfo, "fixmedins_hilist_name", line.getFixmedinsHilistName());
        putIfNotBlank(invinfo, "rx_flag", line.getRxFlag());
        putIfNotBlank(invinfo, "invdate", line.getInvdate());
        if (line.getInvCnt() != null) {
            invinfo.put("inv_cnt", line.getInvCnt());
        } else if (line.getPurcRetnCnt() != null) {
            invinfo.put("inv_cnt", line.getPurcRetnCnt());
        }
        putIfNotBlank(invinfo, "manu_lotnum", line.getManuLotnum());
        putIfNotBlank(invinfo, "fixmedins_bchno", line.getFixmedinsBchno());
        putIfNotBlank(invinfo, "manu_date", line.getManuDate());
        putIfNotBlank(invinfo, "expy_end", line.getExpyEnd());
        putIfNotBlank(invinfo, "memo", line.getMemo());
        JSONObject input = new JSONObject();
        input.put("invinfo", invinfo);
        return input;
    }

    /** 【3502】库存变更上传 input.invinfo（含 inv_chg_type、drugtracinfo）。 */
    public static JSONObject assemble3502(InvUploadLineCommand line) {
        JSONObject invinfo = new JSONObject();
        putIfNotBlank(invinfo, "med_list_codg", line.getMedListCodg());
        putIfNotBlank(invinfo, "inv_chg_type", line.getInvChgType());
        putIfNotBlank(invinfo, "fixmedins_hilist_id", line.getFixmedinsHilistId());
        putIfNotBlank(invinfo, "fixmedins_hilist_name", line.getFixmedinsHilistName());
        putIfNotBlank(invinfo, "fixmedins_bchno", line.getFixmedinsBchno());
        if (line.getPric() != null) {
            invinfo.put("pric", line.getPric());
        } else if (line.getFinlTrnsPric() != null) {
            invinfo.put("pric", line.getFinlTrnsPric());
        }
        if (line.getCnt() != null) {
            invinfo.put("cnt", line.getCnt());
        }
        putIfNotBlank(invinfo, "rx_flag", line.getRxFlag());
        putIfNotBlank(invinfo, "inv_chg_time", line.getInvChgTime());
        putIfNotBlank(invinfo, "inv_chg_opter_name", line.getInvChgOpterName());
        putIfNotBlank(invinfo, "memo", line.getMemo());
        putIfNotBlank(invinfo, "trdn_flag", line.getTrdnFlag());
        JSONArray drugtracinfo = buildDrugtracinfo(line);
        if (!drugtracinfo.isEmpty()) {
            invinfo.put("drugtracinfo", drugtracinfo);
        }
        JSONObject input = new JSONObject();
        input.put("invinfo", invinfo);
        return input;
    }

    /** 【3507】删除进销存数据 input.data（fixmedins_bchno + inv_data_type）。 */
    public static JSONObject assemble3507(String fixmedinsBchno, String invDataType) {
        JSONObject data = new JSONObject();
        putIfNotBlank(data, "fixmedins_bchno", fixmedinsBchno);
        putIfNotBlank(data, "inv_data_type", invDataType);
        JSONObject input = new JSONObject();
        input.put("data", data);
        return input;
    }

    /** 【3508–3513】平台进销存查询通用 input.data（按 infno 区分查询类型）。 */
    public static JSONObject assemblePlatformQuery(QueryPlatformInvRequest request, String fixmedinsCode) {
        JSONObject data = new JSONObject();
        putIfNotBlank(data, "fixmedins_code", fixmedinsCode);
        putIfNotBlank(data, "medins_list_codg", request.getMedinsListCodg());
        putIfNotBlank(data, "fixmedins_bchno", request.getFixmedinsBchno());
        putIfNotBlank(data, "begndate", request.getBegndate());
        putIfNotBlank(data, "enddate", request.getEnddate());
        putIfNotBlank(data, "med_list_codg", request.getMedListCodg());
        putIfNotBlank(data, "medins_prod_sel_no", request.getMedinsProdSelNo());
        putIfNotBlank(data, "medins_prod_purc_no", request.getMedinsProdPurcNo());
        putIfNotBlank(data, "medins_prod_inv_no", request.getMedinsProdInvNo());
        putIfNotBlank(data, "psn_no", request.getPsnNo());
        putIfNotBlank(data, "certno", request.getCertno());
        putIfNotBlank(data, "mdtrt_id", request.getMdtrtId());
        putIfNotBlank(data, "drug_trac_codg", request.getDrugTracCodg());
        putIfNotBlank(data, "vali_flag", request.getValiFlag());
        JSONObject input = new JSONObject();
        input.put("data", data);
        return input;
    }

    private static JSONObject buildPurcinfo3503(InvUploadLineCommand line) {
        JSONObject purcinfo = new JSONObject();
        putIfNotBlank(purcinfo, "med_list_codg", line.getMedListCodg());
        putIfNotBlank(purcinfo, "fixmedins_hilist_id", line.getFixmedinsHilistId());
        putIfNotBlank(purcinfo, "fixmedins_hilist_name", line.getFixmedinsHilistName());
        putIfNotBlank(purcinfo, "dynt_no", line.getDyntNo());
        putIfNotBlank(purcinfo, "fixmedins_bchno", line.getFixmedinsBchno());
        putIfNotBlank(purcinfo, "spler_name", line.getSplerName());
        putIfNotBlank(purcinfo, "spler_pmtno", line.getSplerPmtno());
        putIfNotBlank(purcinfo, "manu_lotnum", line.getManuLotnum());
        putIfNotBlank(purcinfo, "prodentp_name", line.getProdentpName());
        putIfNotBlank(purcinfo, "aprvno", line.getAprvno());
        putIfNotBlank(purcinfo, "manu_date", line.getManuDate());
        putIfNotBlank(purcinfo, "expy_end", line.getExpyEnd());
        if (line.getFinlTrnsPric() != null) {
            purcinfo.put("finl_trns_pric", line.getFinlTrnsPric());
        }
        if (line.getPurcRetnCnt() != null) {
            purcinfo.put("purc_retn_cnt", line.getPurcRetnCnt());
        }
        putIfNotBlank(purcinfo, "purc_invo_codg", line.getPurcInvoCodg());
        putIfNotBlank(purcinfo, "purc_invo_no", line.getPurcInvoNo());
        putIfNotBlank(purcinfo, "rx_flag", line.getRxFlag());
        putIfNotBlank(purcinfo, "purc_retn_stoin_time", line.getPurcRetnStoinTime());
        putIfNotBlank(purcinfo, "purc_retn_opter_name", line.getPurcRetnOpterName());
        putIfNotBlank(purcinfo, "prod_geay_flag", line.getProdGeayFlag());
        putIfNotBlank(purcinfo, "memo", line.getMemo());
        return purcinfo;
    }

    private static JSONArray buildDrugtracinfo(InvUploadLineCommand line) {
        JSONArray array = new JSONArray();
        if (line.getDrugTracCodgs() == null || line.getDrugTracCodgs().isEmpty()) {
            return array;
        }
        for (String code : line.getDrugTracCodgs()) {
            if (StringUtils.isBlank(code)) {
                continue;
            }
            JSONObject node = new JSONObject();
            node.put("drug_trac_codg", code.trim());
            array.add(node);
        }
        return array;
    }

    private static void putIfNotBlank(JSONObject target, String key, String value) {
        if (StringUtils.isNotBlank(value)) {
            target.put(key, value.trim());
        }
    }
}
