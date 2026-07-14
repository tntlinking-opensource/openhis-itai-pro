/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.elep.application.prescription.support;

import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import com.openhis.elep.domain.prescription.result.ElepHistoryDetail;
import com.openhis.elep.domain.prescription.entity.YbElepPrescription;
import com.openhis.elep.domain.prescription.entity.YbElepPrescriptionSnapshot;
import org.apache.commons.lang3.StringUtils;


public final class ElepHistoryDetailMapper {

    private ElepHistoryDetailMapper() {
    }

    @SuppressWarnings("unchecked")
    public static ElepHistoryDetail toDetail(
            YbElepPrescription rx,
            YbElepPrescriptionSnapshot snapshot) {
        ElepHistoryDetail dto = new ElepHistoryDetail()
                .setPrescriptionId(rx.getId())
                .setHiRxno(rx.getHiRxno())
                .setHospRxno(rx.getHospRxno())
                .setInitRxno(rx.getInitRxno())
                .setLocalStatus(rx.getLocalStatus())
                .setRxStasCodg(rx.getRxStasCodg())
                .setRxUsedStasCodg(rx.getRxUsedStasCodg())
                .setReptFlag(rx.getReptFlag())
                .setMaxReptCnt(rx.getMaxReptCnt())
                .setReptdCnt(rx.getReptdCnt())
                .setValiEndTime(rx.getValiEndTime())
                .setPrscTime(rx.getPrscTime())
                .setPrscDrName(rx.getPrscDrName())
                .setMainDiagName(rx.getMainDiagName())
                .setDrugSummary(rx.getDrugSummary())
                .setCanContinuationRx(ElepPrescriptionContinuationRules.canContinuation(rx));
        if (snapshot != null) {
            dto.setPdfFilePath(snapshot.getPdfFilePath());
            if (StringUtils.isNotBlank(snapshot.getUploadRequestJson())) {
                JSONObject body = JSONObject.parseObject(snapshot.getUploadRequestJson());
                dto.setUploadRequest(body);
                parseDrugLines(body, dto);
                parseDiagnosis(body, dto);
            }
        }
        return dto;
    }

    private static void parseDrugLines(JSONObject body, ElepHistoryDetail dto) {
        JSONArray lines = body.getJSONArray("rxdrugdetail");
        if (lines == null) {
            return;
        }
        for (int i = 0; i < lines.size(); i++) {
            JSONObject line = lines.getJSONObject(i);
            if (line == null) {
                continue;
            }
            dto.getDrugLines().add(new ElepHistoryDetail.DrugLine()
                    .setLineNo(i + 1)
                    .setMedListCodg(line.getString("medListCodg"))
                    .setDrugName(line.getString("drugName"))
                    .setDrugSpec(line.getString("drugSpec")));
        }
    }

    private static void parseDiagnosis(JSONObject body, ElepHistoryDetail dto) {
        JSONObject diseinfo = body.getJSONObject("diseinfo");
        if (diseinfo == null) {
            return;
        }
        dto.setDiagnosis(new ElepHistoryDetail.DiagnosisInfo()
                .setDiagCode(diseinfo.getString("diagCode"))
                .setDiagName(diseinfo.getString("diagName")));
    }
}
