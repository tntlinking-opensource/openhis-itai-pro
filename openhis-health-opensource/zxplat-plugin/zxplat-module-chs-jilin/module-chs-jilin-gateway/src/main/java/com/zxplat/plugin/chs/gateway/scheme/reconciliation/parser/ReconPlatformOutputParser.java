/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.chs.gateway.scheme.reconciliation.parser;

import static com.zxplat.framework.core.application.util.StringUtils.isEmpty;
import static com.zxplat.framework.core.application.util.StringUtils.isNotEmpty;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import com.zxplat.plugin.chs.domain.reconciliation.result.ClearOrg3206AResult;
import com.zxplat.plugin.chs.domain.reconciliation.result.ClearStatus3205ARowResult;
import com.zxplat.plugin.chs.domain.reconciliation.result.CrossProvinceReconRowResult;
import com.zxplat.plugin.chs.domain.reconciliation.result.ReconDetailResultRowResult;
import com.zxplat.plugin.chs.gateway.client.ResultBody;
import com.zxplat.plugin.chs.gateway.support.ChsGatewayOutputSupport;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/** 吉林对账清算平台 output 解析 */
public final class ReconPlatformOutputParser {

    private ReconPlatformOutputParser() {
    }

    public static ClearOrg3206AResult parse3206(ResultBody body) {
        JSONObject output = ChsGatewayOutputSupport.parseOutputObject(body);
        return new ClearOrg3206AResult()
            .setPages(output.getInteger("pages"))
            .setPageNum(output.getInteger("page_num"))
            .setPageSize(output.getInteger("page_size"))
            .setClrOptins(parseClrOptinsArray(output.get("clr_optins")));
    }

    public static String parse3203ClrAppyEvtId(ResultBody body) {
        JSONObject output = ChsGatewayOutputSupport.parseOutputObject(body);
        return output.getString("clr_appy_evt_id");
    }

    public static List<ClearStatus3205ARowResult> parse3205(ResultBody body) {
        JSONObject output = ChsGatewayOutputSupport.parseOutputObject(body);
        return parseClearStatusRows(output.get("clr_appys"));
    }

    public static JSONObject parse3201StmtInfo(ResultBody body) {
        JSONObject output = ChsGatewayOutputSupport.parseOutputObject(body);
        JSONObject stmtinfo = output.getJSONObject("stmtinfo");
        if (stmtinfo == null) {
            return output;
        }
        return stmtinfo;
    }

    public static JSONObject parse3202FileInfo(ResultBody body) {
        JSONObject output = ChsGatewayOutputSupport.parseOutputObject(body);
        JSONObject fileinfo = output.getJSONObject("fileinfo");
        if (fileinfo == null) {
            return output;
        }
        return fileinfo;
    }

    public static List<CrossProvinceReconRowResult> parse3209A(ResultBody body) {
        JSONObject output = ChsGatewayOutputSupport.parseOutputObject(body);
        return parseCrossProvinceRows(output.get("selt_list"));
    }

    public static List<ReconDetailResultRowResult> parse3202ResultZipText(String text) {
        List<ReconDetailResultRowResult> rows = new ArrayList<>();
        if (isEmpty(text)) {
            return rows;
        }
        for (String line : text.split("\\R")) {
            if (isEmpty(line)) {
                continue;
            }
            String[] cols = line.split("\t", -1);
            if (cols.length < 5) {
                continue;
            }
            ReconDetailResultRowResult row = new ReconDetailResultRowResult();
            row.setPsnNo(cols[0]);
            row.setMdtrtId(cols.length > 1 ? cols[1] : null);
            row.setSetlId(cols.length > 2 ? cols[2] : null);
            row.setMsgid(cols.length > 3 ? cols[3] : null);
            row.setStmtRslt(cols.length > 4 ? cols[4] : null);
            row.setRefdSetlFlag(cols.length > 5 ? cols[5] : null);
            row.setMemo(cols.length > 6 ? cols[6] : null);
            row.setMedfeeSumamt(parseDecimal(cols, 7));
            row.setFundPaySumamt(parseDecimal(cols, 8));
            row.setAcctPay(parseDecimal(cols, 9));
            rows.add(row);
        }
        return rows;
    }

    public static int countMismatch(List<ReconDetailResultRowResult> rows) {
        int count = 0;
        if (rows == null) {
            return 0;
        }
        for (ReconDetailResultRowResult row : rows) {
            if (row == null || !"0".equals(trimToEmpty(row.getStmtRslt()))) {
                count += 1;
            }
        }
        return count;
    }

    private static List<Map<String, Object>> parseClrOptinsArray(Object raw) {
        if (raw == null) {
            return Collections.emptyList();
        }
        if (raw instanceof JSONArray array) {
            List<Map<String, Object>> rows = new ArrayList<>();
            for (int i = 0; i < array.size(); i++) {
                Object item = array.get(i);
                if (item instanceof JSONObject jo) {
                    rows.add(jo);
                } else if (item instanceof Map<?, ?> map) {
                    @SuppressWarnings("unchecked")
                    Map<String, Object> cast = (Map<String, Object>) map;
                    rows.add(cast);
                }
            }
            return rows;
        }
        if (raw instanceof String text && isNotEmpty(text)) {
            try {
                return parseClrOptinsArray(JSON.parseArray(text));
            } catch (RuntimeException ignored) {
                return Collections.emptyList();
            }
        }
        return Collections.emptyList();
    }

    private static List<ClearStatus3205ARowResult> parseClearStatusRows(Object raw) {
        if (raw == null) {
            return Collections.emptyList();
        }
        List<JSONObject> objects = new ArrayList<>();
        if (raw instanceof JSONArray array) {
            for (int i = 0; i < array.size(); i++) {
                Object item = array.get(i);
                if (item instanceof JSONObject jo) {
                    objects.add(jo);
                }
            }
        } else if (raw instanceof JSONObject jo) {
            objects.add(jo);
        }
        List<ClearStatus3205ARowResult> rows = new ArrayList<>();
        for (JSONObject jo : objects) {
            ClearStatus3205ARowResult row = new ClearStatus3205ARowResult();
            row.setFixmedinsName(jo.getString("fixmedins_name"));
            row.setFixmedinsCode(jo.getString("fixmedins_code"));
            row.setClrStas(jo.getString("clr_stas"));
            row.setOptTime(jo.getString("opt_time"));
            row.setMedfeeSumamt(firstDecimal(jo, "Medfee_Sumamt", "medfee_sumamt"));
            row.setHiAgreSumfee(jo.getBigDecimal("hi_agre_sumfee"));
            row.setFundAppySum(jo.getBigDecimal("fund_appy_sum"));
            row.setCashPayamt(jo.getBigDecimal("cash_payamt"));
            row.setAcctPay(jo.getBigDecimal("acct_pay"));
            row.setBegndate(jo.getString("begndate"));
            row.setEnddate(jo.getString("enddate"));
            row.setClrYm(jo.getString("clr_ym"));
            row.setClrOptins(jo.getString("clr_optins"));
            row.setOpterId(jo.getString("opter_id"));
            row.setOpterName(jo.getString("opter_name"));
            row.setOptinsNo(jo.getString("optins_no"));
            row.setClrAppyEvtId(jo.getString("clr_appy_evt_id"));
            rows.add(row);
        }
        return rows;
    }

    private static List<CrossProvinceReconRowResult> parseCrossProvinceRows(Object raw) {
        if (raw == null) {
            return Collections.emptyList();
        }
        List<JSONObject> objects = new ArrayList<>();
        if (raw instanceof JSONArray array) {
            for (int i = 0; i < array.size(); i++) {
                Object item = array.get(i);
                if (item instanceof JSONObject jo) {
                    objects.add(jo);
                }
            }
        } else if (raw instanceof JSONObject jo) {
            objects.add(jo);
        } else if (raw instanceof String text && isNotEmpty(text)) {
            try {
                return parseCrossProvinceRows(JSON.parse(text));
            } catch (RuntimeException ignored) {
                return Collections.emptyList();
            }
        }
        List<CrossProvinceReconRowResult> rows = new ArrayList<>();
        for (JSONObject jo : objects) {
            rows.add(new CrossProvinceReconRowResult()
                .setSetlId(firstNonBlank(jo, "setlId", "setl_id"))
                .setInsuAdmdvs(firstNonBlank(jo, "insuAdmdvs", "insu_admdvs")));
        }
        return rows;
    }

    private static BigDecimal firstDecimal(JSONObject jo, String... keys) {
        for (String key : keys) {
            BigDecimal value = jo.getBigDecimal(key);
            if (value != null) {
                return value;
            }
        }
        return null;
    }

    private static String firstNonBlank(JSONObject jo, String... keys) {
        for (String key : keys) {
            String value = jo.getString(key);
            if (isNotEmpty(value)) {
                return value.trim();
            }
        }
        return null;
    }

    private static BigDecimal parseDecimal(String[] cols, int index) {
        if (cols.length <= index || isEmpty(cols[index])) {
            return null;
        }
        try {
            return new BigDecimal(cols[index].trim());
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private static String trimToEmpty(String value) {
        if (value == null) {
            return "";
        }
        return value.trim();
    }
}
