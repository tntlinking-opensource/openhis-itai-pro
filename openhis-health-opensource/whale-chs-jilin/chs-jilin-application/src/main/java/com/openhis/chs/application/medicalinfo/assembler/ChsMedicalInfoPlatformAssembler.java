/*
 * Copyright © 2026 OpenHIS Contributors
 */

package com.openhis.chs.application.medicalinfo.assembler;

import com.alibaba.fastjson2.JSONObject;
import com.openhis.chs.domain.medicalinfo.dto.request.QueryCum5206Request;
import com.openhis.chs.domain.medicalinfo.dto.request.QueryDise5202Request;
import com.openhis.chs.domain.medicalinfo.dto.request.QueryFee5204Request;
import com.openhis.chs.domain.medicalinfo.dto.request.QueryMdtrt5201Request;
import com.openhis.chs.domain.medicalinfo.dto.request.QueryOpspDrug5205Request;
import com.openhis.chs.domain.medicalinfo.dto.request.QueryOpspFiling5301Request;
import com.openhis.chs.domain.medicalinfo.dto.request.QuerySetl5203Request;
import org.apache.commons.lang3.StringUtils;

/**
 * 5201–5206、5301 平台报文 input.data 节点组装。
 *
 * <p>由 {@link com.openhis.chs.application.medicalinfo.ChsMedicalInfoQueryAppService} 调用，
 * 输出 JSONObject 直接作为 {@code gatewayClient.invoke} 的 input 参数。</p>
 */
public final class ChsMedicalInfoPlatformAssembler {

    private ChsMedicalInfoPlatformAssembler() {
    }

    /** 【5201】就诊信息查询：psn_no + 起止时间 + med_type（缺省 11）。 */
    public static JSONObject assemble5201(QueryMdtrt5201Request request) {
        JSONObject data = new JSONObject();
        data.put("psn_no", request.getPsnNo());
        data.put("begntime", request.getBegntime());
        data.put("endtime", request.getEndtime());
        data.put("med_type", StringUtils.defaultIfBlank(request.getMedType(), "11"));
        putIfNotBlank(data, "mdtrt_id", request.getMdtrtId());
        return wrapData(data);
    }

    /** 【5202】诊断信息查询：mdtrt_id + psn_no。 */
    public static JSONObject assemble5202(QueryDise5202Request request) {
        JSONObject data = new JSONObject();
        data.put("mdtrt_id", request.getMdtrtId());
        data.put("psn_no", request.getPsnNo());
        return wrapData(data);
    }

    /** 【5203】结算信息查询：psn_no + mdtrt_id + setl_id。 */
    public static JSONObject assemble5203(QuerySetl5203Request request) {
        JSONObject data = new JSONObject();
        data.put("psn_no", request.getPsnNo());
        data.put("setl_id", request.getSetlId());
        data.put("mdtrt_id", request.getMdtrtId());
        return wrapData(data);
    }

    /** 【5204】费用明细查询：psn_no + mdtrt_id，setl_id 可选。 */
    public static JSONObject assemble5204(QueryFee5204Request request) {
        JSONObject data = new JSONObject();
        data.put("psn_no", request.getPsnNo());
        data.put("mdtrt_id", request.getMdtrtId());
        putIfNotBlank(data, "setl_id", request.getSetlId());
        return wrapData(data);
    }

    /** 【5205】门慢门特用药查询：psn_no + begntime，endtime 可选。 */
    public static JSONObject assemble5205(QueryOpspDrug5205Request request) {
        JSONObject data = new JSONObject();
        data.put("psn_no", request.getPsnNo());
        data.put("begntime", request.getBegntime());
        putIfNotBlank(data, "endtime", request.getEndtime());
        return wrapData(data);
    }

    /** 【5206】人员累计查询：psn_no，cum_ym 可选（yyyyMM）。 */
    public static JSONObject assemble5206(QueryCum5206Request request) {
        JSONObject data = new JSONObject();
        data.put("psn_no", request.getPsnNo());
        putIfNotBlank(data, "cum_ym", request.getCumYm());
        return wrapData(data);
    }

    /** 【5301】慢特病备案查询：psn_no。 */
    public static JSONObject assemble5301(QueryOpspFiling5301Request request) {
        JSONObject data = new JSONObject();
        data.put("psn_no", request.getPsnNo());
        return wrapData(data);
    }

    private static JSONObject wrapData(JSONObject data) {
        JSONObject input = new JSONObject();
        input.put("data", data);
        return input;
    }

    private static void putIfNotBlank(JSONObject target, String key, String value) {
        if (StringUtils.isNotBlank(value)) {
            target.put(key, value.trim());
        }
    }
}
