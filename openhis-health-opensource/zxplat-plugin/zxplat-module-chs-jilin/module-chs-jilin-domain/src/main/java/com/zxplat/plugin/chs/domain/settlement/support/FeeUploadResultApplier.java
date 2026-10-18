/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.chs.domain.settlement.support;

import com.alibaba.fastjson2.JSON;
import com.zxplat.plugin.chs.domain.inpatient.entity.YbInpatientFeeUpload;
import com.zxplat.plugin.chs.domain.outpatient.entity.YbClinicFeeUpload;
import com.zxplat.plugin.chs.domain.platform.model.ChsFeeUploadResultRow;

import java.math.BigDecimal;

/** 2204/2301 平台分割结果回填上传行 */
public final class FeeUploadResultApplier {

    private FeeUploadResultApplier() {
    }

    /**
     * 将平台分割结果回填门诊费用上传行。
     *
     * @param row         费用上传行
     * @param platformRow 平台 result 行
     */
    public static void applyToClinicRow(YbClinicFeeUpload row, ChsFeeUploadResultRow platformRow) {
        applyCommon(row, platformRow);
    }

    /**
     * 将平台分割结果回填住院费用上传行。
     *
     * @param row         费用上传行
     * @param platformRow 平台 result 行
     */
    public static void applyToInpatientRow(YbInpatientFeeUpload row, ChsFeeUploadResultRow platformRow) {
        applyCommon(row, platformRow);
    }

    /**
     * 平台响应无法逐行解析时，将原始 output JSON 回落写入上传行。
     *
     * @param row        住院费用上传行
     * @param outputJson 平台 output 原文
     */
    public static void applyFallbackJson(YbInpatientFeeUpload row, String outputJson) {
        if (row == null || outputJson == null || outputJson.isBlank()) {
            return;
        }
        if (row.getPlatformResultJson() == null || row.getPlatformResultJson().isBlank()) {
            row.setPlatformResultJson(outputJson.trim());
        }
    }

    /**
     * 将平台分割字段写入上传行公共字段。
     *
     * @param row         门诊或住院上传行
     * @param platformRow 平台 result 行
     */
    private static void applyCommon(Object row, ChsFeeUploadResultRow platformRow) {
        if (row == null || platformRow == null) {
            return;
        }
        setChrgitmLv(row, platformRow.getChrgitmLv());
        setMedChrgitmType(row, platformRow.getMedChrgitmType());
        setSelfpayProp(row, platformRow.getSelfpayProp());
        setFulamtOwnpayAmt(row, platformRow.getFulamtOwnpayAmt());
        setOverlmtAmt(row, platformRow.getOverlmtAmt());
        setPreselfpayAmt(row, platformRow.getPreselfpayAmt());
        setInscpScpAmt(row, platformRow.getInscpScpAmt());
        setLmtUsedFlag(row, platformRow.getLmtUsedFlag());
        setPlatformMemo(row, platformRow.getMemo());
        setPlatformResultJson(row, JSON.toJSONString(platformRow));
    }

    /**
     * 设置收费项目等级。
     *
     * @param row   上传行
     * @param value 平台值
     */
    private static void setChrgitmLv(Object row, String value) {
        if (row instanceof YbClinicFeeUpload clinic) {
            clinic.setChrgitmLv(value);
        } else if (row instanceof YbInpatientFeeUpload inpatient) {
            inpatient.setChrgitmLv(value);
        }
    }

    /**
     * 设置医疗收费项目类别。
     *
     * @param row   上传行
     * @param value 平台值
     */
    private static void setMedChrgitmType(Object row, String value) {
        if (row instanceof YbClinicFeeUpload clinic) {
            clinic.setMedChrgitmType(value);
        } else if (row instanceof YbInpatientFeeUpload inpatient) {
            inpatient.setMedChrgitmType(value);
        }
    }

    /**
     * 设置自付比例。
     *
     * @param row   上传行
     * @param value 平台值
     */
    private static void setSelfpayProp(Object row, BigDecimal value) {
        if (row instanceof YbClinicFeeUpload clinic) {
            clinic.setSelfpayProp(value);
        } else if (row instanceof YbInpatientFeeUpload inpatient) {
            inpatient.setSelfpayProp(value);
        }
    }

    /**
     * 设置全自费金额。
     *
     * @param row   上传行
     * @param value 平台值
     */
    private static void setFulamtOwnpayAmt(Object row, BigDecimal value) {
        if (row instanceof YbClinicFeeUpload clinic) {
            clinic.setFulamtOwnpayAmt(value);
        } else if (row instanceof YbInpatientFeeUpload inpatient) {
            inpatient.setFulamtOwnpayAmt(value);
        }
    }

    /**
     * 设置超限价金额。
     *
     * @param row   上传行
     * @param value 平台值
     */
    private static void setOverlmtAmt(Object row, BigDecimal value) {
        if (row instanceof YbClinicFeeUpload clinic) {
            clinic.setOverlmtAmt(value);
        } else if (row instanceof YbInpatientFeeUpload inpatient) {
            inpatient.setOverlmtAmt(value);
        }
    }

    /**
     * 设置先行自付金额。
     *
     * @param row   上传行
     * @param value 平台值
     */
    private static void setPreselfpayAmt(Object row, BigDecimal value) {
        if (row instanceof YbClinicFeeUpload clinic) {
            clinic.setPreselfpayAmt(value);
        } else if (row instanceof YbInpatientFeeUpload inpatient) {
            inpatient.setPreselfpayAmt(value);
        }
    }

    /**
     * 设置符合政策范围金额。
     *
     * @param row   上传行
     * @param value 平台值
     */
    private static void setInscpScpAmt(Object row, BigDecimal value) {
        if (row instanceof YbClinicFeeUpload clinic) {
            clinic.setInscpScpAmt(value);
        } else if (row instanceof YbInpatientFeeUpload inpatient) {
            inpatient.setInscpScpAmt(value);
        }
    }

    /**
     * 设置限价使用标志。
     *
     * @param row   上传行
     * @param value 平台值
     */
    private static void setLmtUsedFlag(Object row, String value) {
        if (row instanceof YbClinicFeeUpload clinic) {
            clinic.setLmtUsedFlag(value);
        } else if (row instanceof YbInpatientFeeUpload inpatient) {
            inpatient.setLmtUsedFlag(value);
        }
    }

    /**
     * 设置平台备注。
     *
     * @param row   上传行
     * @param value 平台值
     */
    private static void setPlatformMemo(Object row, String value) {
        if (row instanceof YbClinicFeeUpload clinic) {
            clinic.setPlatformMemo(value);
        } else if (row instanceof YbInpatientFeeUpload inpatient) {
            inpatient.setPlatformMemo(value);
        }
    }

    /**
     * 设置平台 result 原文 JSON。
     *
     * @param row   上传行
     * @param value JSON 字符串
     */
    private static void setPlatformResultJson(Object row, String value) {
        if (row instanceof YbClinicFeeUpload clinic) {
            clinic.setPlatformResultJson(value);
        } else if (row instanceof YbInpatientFeeUpload inpatient) {
            inpatient.setPlatformResultJson(value);
        }
    }
}
