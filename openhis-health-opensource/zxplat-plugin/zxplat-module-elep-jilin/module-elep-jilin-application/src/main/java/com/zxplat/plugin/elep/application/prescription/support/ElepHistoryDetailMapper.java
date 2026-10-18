/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.elep.application.prescription.support;

import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import com.zxplat.plugin.elep.domain.prescription.entity.YbElepPrescription;
import com.zxplat.plugin.elep.domain.prescription.entity.YbElepPrescriptionSnapshot;
import com.zxplat.plugin.elep.domain.prescription.result.ElepHistoryDetail;

import static com.zxplat.framework.core.application.util.StringUtils.isEmpty;

/**
 * 电子处方详情映射：主表 + 快照结构化字段，不透出密钥。
 */
public final class ElepHistoryDetailMapper {

    private ElepHistoryDetailMapper() {
    }

    /**
     * 组装医生站详情（含审方、慢病与药品用法）。
     *
     * @param rx       主表
     * @param snapshot 快照，可空
     * @return 详情
     */
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
                .setMedType(rx.getMedType())
                .setDiseCodg(rx.getDiseCodg())
                .setDiseName(rx.getDiseName())
                .setValiDays(rx.getValiDays())
                .setPharCode(rx.getPharCode())
                .setPharName(rx.getPharName())
                .setMdtrtId(rx.getMdtrtId())
                .setCanContinuationRx(ElepPrescriptionContinuationRules.canContinuation(rx));
        if (snapshot != null) {
            dto.setPdfFilePath(snapshot.getPdfFilePath());
            if (!isEmpty(snapshot.getUploadRequestJson())) {
                JSONObject body = JSONObject.parseObject(snapshot.getUploadRequestJson());
                dto.setUploadRequest(body);
                parseDrugLines(body, dto);
                parseDiagnosis(body, dto);
            }
        }
        return dto;
    }

    /**
     * 从快照解析药品行用法。
     *
     * @param body 拟 A0001 JSON
     * @param dto  详情
     */
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
                    .setDrugName(firstNonEmpty(line.getString("drugName"), line.getString("drugGenname")))
                    .setDrugSpec(line.getString("drugSpec"))
                    .setDrugDosform(line.getString("drugDosform"))
                    .setDrugCnt(line.getString("drugCnt"))
                    .setDrugDosunt(line.getString("drugDosunt"))
                    .setMedcWayCodg(line.getString("medcWayCodg"))
                    .setMedcWayDscr(line.getString("medcWayDscr"))
                    .setMedcDays(line.getString("medcDays"))
                    .setSinDoscnt(line.getString("sinDoscnt"))
                    .setSinDosunt(line.getString("sinDosunt"))
                    .setUsedFrquCodg(line.getString("usedFrquCodg"))
                    .setUsedFrquName(line.getString("usedFrquName"))
                    .setMedcBegntime(line.getString("medcBegntime"))
                    .setMedcEndtime(line.getString("medcEndtime"))
                    .setPrdrName(line.getString("prdrName"))
                    .setDrugProdname(line.getString("drugProdname")));
        }
    }

    /**
     * 从快照解析主诊断。
     *
     * @param body 拟 A0001 JSON
     * @param dto  详情
     */
    private static void parseDiagnosis(JSONObject body, ElepHistoryDetail dto) {
        JSONObject diseinfo = body.getJSONObject("diseinfo");
        if (diseinfo == null) {
            return;
        }
        dto.setDiagnosis(new ElepHistoryDetail.DiagnosisInfo()
                .setDiagCode(diseinfo.getString("diagCode"))
                .setDiagName(diseinfo.getString("diagName")));
    }

    /**
     * 取第一个非空串。
     *
     * @param first  优先
     * @param second 回落
     * @return 非空或 null
     */
    private static String firstNonEmpty(String first, String second) {
        if (!isEmpty(first)) {
            return first;
        }
        if (!isEmpty(second)) {
            return second;
        }
        return null;
    }
}
