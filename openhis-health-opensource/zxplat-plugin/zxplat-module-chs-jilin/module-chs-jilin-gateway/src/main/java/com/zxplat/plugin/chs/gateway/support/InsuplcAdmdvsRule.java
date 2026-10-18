/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.chs.gateway.support;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import com.zxplat.plugin.chs.domain.share.constants.ChsInfNo;
import com.zxplat.plugin.chs.gateway.client.InvokeContext;
import com.zxplat.plugin.chs.gateway.config.ContractConfig;

import java.util.Set;

/** insuplc_admdvs 报文头规则（对齐平台 吉林客户端） */
public final class InsuplcAdmdvsRule {

    private static final Set<String> USE_MDTRT_AREA = Set.of(
        ChsInfNo.SIGN_IN, ChsInfNo.PERSON_INFO,
        ChsInfNo.CLEAR_APPLY_JILIN, ChsInfNo.CLEAR_ORG_QUERY_JILIN,
        ChsInfNo.CLEAR_APPLY_REVOKE_JILIN, ChsInfNo.CLEAR_STATUS_QUERY_JILIN,
        ChsInfNo.CLEAR_CROSS_PROVINCE_RECON_JILIN,
        ChsInfNo.CATALOG_MAPPING_UPLOAD, ChsInfNo.CATALOG_MAPPING_REVOKE);

    private InsuplcAdmdvsRule() {
    }

    /** 按交易号与入参解析报文头 {@code insuplc_admdvs} */
    public static String resolve(String infNo, Object input, InvokeContext context, ContractConfig config) {
        if (context != null && context.getInsuplcAdmdvs() != null && !context.getInsuplcAdmdvs().isBlank()) {
            return context.getInsuplcAdmdvs();
        }
        if (USE_MDTRT_AREA.contains(infNo) && config != null && config.getAdmVs() != null) {
            return config.getAdmVs();
        }
        if (input != null) {
            JSONObject jo = (JSONObject) JSON.toJSON(input);
            JSONObject data = jo.getJSONObject("data");
            if (data != null) {
                String admdvs = data.getString("admdvs");
                if (admdvs != null && !admdvs.isBlank()) {
                    return admdvs.trim();
                }
            }
            String clrOptins = jo.getString("clr_optins");
            if (clrOptins != null && !clrOptins.isBlank()) {
                return clrOptins.trim();
            }
            String setlOptins = jo.getString("setl_optins");
            if (setlOptins != null && !setlOptins.isBlank()) {
                return setlOptins.trim();
            }
            String v = jo.getString("insuplcAdmdvs");
            if (v == null || v.isBlank()) {
                v = jo.getString("insu_admdvs");
            }
            if (v != null && !v.isBlank()) {
                return v;
            }
        }
        if (config != null && config.getAdmVs() != null) {
            return config.getAdmVs();
        }
        return "";
    }
}
