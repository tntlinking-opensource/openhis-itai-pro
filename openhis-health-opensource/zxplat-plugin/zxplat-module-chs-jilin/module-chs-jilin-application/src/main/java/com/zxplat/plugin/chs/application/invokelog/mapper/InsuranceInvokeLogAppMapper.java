/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.chs.application.invokelog.mapper;

import static com.zxplat.framework.core.application.util.StringUtils.isEmpty;

import com.zxplat.plugin.chs.application.invokelog.response.InvokeLogDetailResult;
import com.zxplat.plugin.chs.application.invokelog.response.InvokeLogRowResult;
import com.zxplat.plugin.chs.domain.invoke.entity.YbInvokeLog;

/** 医保调用日志实体 ↔ UI 结果映射。 */
public final class InsuranceInvokeLogAppMapper {

    /** 列表摘要最大字符数 */
    public static final int PREVIEW_MAX_CHARS = 200;

    private static final String SUCCESS_INFCODE = "0";

    private InsuranceInvokeLogAppMapper() {
    }

    public static InvokeLogRowResult toRowResult(
        YbInvokeLog row, String encounterBusNo, String patientName) {
        if (row == null) {
            return null;
        }
        return new InvokeLogRowResult()
            .setId(row.getId())
            .setCreatedAt(row.getCreatedAt())
            .setInfno(row.getInfno())
            .setInfname(row.getInfname())
            .setMsgid(row.getMsgid())
            .setInfcode(row.getInfcode())
            .setSuccess(isSuccess(row.getInfcode()))
            .setErrMsg(row.getErrMsg())
            .setDurationMs(row.getDurationMs())
            .setContractId(row.getContractId())
            .setContractBusNo(row.getContractBusNo())
            .setEncounterId(row.getEncounterId())
            .setEncounterBusNo(encounterBusNo)
            .setPatientName(patientName)
            .setChrgBchno(row.getChrgBchno())
            .setPaymentDraftId(row.getPaymentDraftId())
            .setPreSettleId(row.getPreSettleId())
            .setSettlementRecordId(row.getSettlementRecordId())
            .setRequestPreview(preview(row.getRequestJson()))
            .setResponsePreview(preview(row.getResponseJson()));
    }

    public static InvokeLogDetailResult toDetailResult(
        YbInvokeLog row, String encounterBusNo, String patientName) {
        if (row == null) {
            return null;
        }
        return new InvokeLogDetailResult()
            .setId(row.getId())
            .setCreatedAt(row.getCreatedAt())
            .setInfno(row.getInfno())
            .setInfname(row.getInfname())
            .setMsgid(row.getMsgid())
            .setInfcode(row.getInfcode())
            .setSuccess(isSuccess(row.getInfcode()))
            .setErrMsg(row.getErrMsg())
            .setDurationMs(row.getDurationMs())
            .setContractId(row.getContractId())
            .setContractBusNo(row.getContractBusNo())
            .setEncounterId(row.getEncounterId())
            .setEncounterBusNo(encounterBusNo)
            .setPatientName(patientName)
            .setChrgBchno(row.getChrgBchno())
            .setPaymentDraftId(row.getPaymentDraftId())
            .setPreSettleId(row.getPreSettleId())
            .setSettlementRecordId(row.getSettlementRecordId())
            .setRequestJson(row.getRequestJson())
            .setResponseJson(row.getResponseJson());
    }

    public static boolean isSuccess(String infcode) {
        return SUCCESS_INFCODE.equals(infcode);
    }

    public static String preview(String text) {
        if (isEmpty(text)) {
            return null;
        }
        String trimmed = text.trim();
        if (trimmed.length() <= PREVIEW_MAX_CHARS) {
            return trimmed;
        }
        return trimmed.substring(0, PREVIEW_MAX_CHARS) + "…";
    }
}
