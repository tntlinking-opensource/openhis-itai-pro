/*
 * Copyright © 2026 OpenHIS Contributors
 */

package com.openhis.chs.domain.settlement.support;

import com.alibaba.fastjson2.JSONObject;
import com.openhis.chs.domain.inpatient.entity.YbInpatientFeeUpload;
import com.openhis.chs.domain.outpatient.entity.YbClinicFeeUpload;
import org.apache.commons.lang3.StringUtils;

/** 2204/2301 平台分割结果写入费用上传行。 */
public final class FeeUploadResultApplier {

    private FeeUploadResultApplier() {
    }

    public static void applyToClinicRow(YbClinicFeeUpload row, JSONObject platformRow) {
        if (row == null || platformRow == null) {
            return;
        }
        row.setChrgitmLv(platformRow.getString("chrgitm_lv"));
        row.setMedChrgitmType(platformRow.getString("med_chrgitm_type"));
        row.setSelfpayProp(FeeUploadResultIndexer.decimal(platformRow, "selfpay_prop"));
        row.setFulamtOwnpayAmt(FeeUploadResultIndexer.decimal(platformRow, "fulamt_ownpay_amt"));
        row.setOverlmtAmt(FeeUploadResultIndexer.decimal(platformRow, "overlmt_amt"));
        row.setPreselfpayAmt(FeeUploadResultIndexer.decimal(platformRow, "preselfpay_amt"));
        row.setInscpScpAmt(FeeUploadResultIndexer.decimal(platformRow, "inscp_scp_amt"));
        row.setLmtUsedFlag(platformRow.getString("lmt_used_flag"));
        row.setPlatformMemo(platformRow.getString("memo"));
        row.setPlatformResultJson(platformRow.toJSONString());
    }

    public static void applyToInpatientRow(YbInpatientFeeUpload row, JSONObject platformRow) {
        if (row == null || platformRow == null) {
            return;
        }
        row.setChrgitmLv(platformRow.getString("chrgitm_lv"));
        row.setMedChrgitmType(platformRow.getString("med_chrgitm_type"));
        row.setSelfpayProp(FeeUploadResultIndexer.decimal(platformRow, "selfpay_prop"));
        row.setFulamtOwnpayAmt(FeeUploadResultIndexer.decimal(platformRow, "fulamt_ownpay_amt"));
        row.setOverlmtAmt(FeeUploadResultIndexer.decimal(platformRow, "overlmt_amt"));
        row.setPreselfpayAmt(FeeUploadResultIndexer.decimal(platformRow, "preselfpay_amt"));
        row.setInscpScpAmt(FeeUploadResultIndexer.decimal(platformRow, "inscp_scp_amt"));
        row.setLmtUsedFlag(platformRow.getString("lmt_used_flag"));
        row.setPlatformMemo(platformRow.getString("memo"));
        row.setPlatformResultJson(platformRow.toJSONString());
    }

    public static void applyFallbackJson(YbInpatientFeeUpload row, String outputJson) {
        if (row == null || StringUtils.isBlank(outputJson)) {
            return;
        }
        if (StringUtils.isBlank(row.getPlatformResultJson())) {
            row.setPlatformResultJson(outputJson);
        }
    }
}
