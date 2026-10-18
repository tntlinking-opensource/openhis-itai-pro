/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.elep.application.prescription.support;

import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import com.zxplat.plugin.elep.domain.prescription.command.ElepDiagnosisLine;
import com.zxplat.plugin.elep.domain.prescription.command.ElepRxDrugLine;
import com.zxplat.plugin.elep.domain.prescription.constants.ElepPrescriptionConstants;
import com.zxplat.plugin.elep.domain.prescription.entity.YbElepPrescription;
import com.zxplat.plugin.elep.domain.prescription.result.ElepEncounterContextResult;
import com.zxplat.framework.core.application.exception.BusinessException;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;

import static com.zxplat.framework.core.application.util.StringUtils.isEmpty;
import static com.zxplat.framework.core.application.util.StringUtils.trimToNull;

/**
 * 按 3-03 A0001 组装预核验报文：HIS 上下文只读 + 本页诊断/药品，不读院内医嘱。
 */
@Component
public class ElepA0001BodyAssembler {

    /**
     * 保存草稿时写入快照的拟报文（尚无审方药师也可落库）。
     *
     * @param prescription 主表
     * @param context      挂号/医师上下文
     * @param diagnosis    本页诊断
     * @param drugLines    本页药品
     * @return A0001 结构 JSON
     */
    public JSONObject assembleDraftBody(
            YbElepPrescription prescription,
            ElepEncounterContextResult context,
            ElepDiagnosisLine diagnosis,
            List<ElepRxDrugLine> drugLines) {
        JSONArray drugArray = assembleDrugLines(prescription, drugLines);
        JSONObject body = assembleHeader(prescription, context, diagnosis, drugArray);
        body.put("mdtrtinfo", assembleMdtrtinfo(prescription, context, diagnosis));
        body.put("diseinfo", assembleDiseinfo(prescription, context, diagnosis));
        body.put("rxdrugdetail", drugArray);
        return body;
    }

    /**
     * 预核验前刷新挂号上下文并校验慢病病种。
     *
     * @param prescription 主表
     * @param context      最新只读上下文
     * @param draftBody    快照中的拟报文
     * @return 可出站的 A0001 JSON
     */
    public JSONObject refreshForPreCheck(
            YbElepPrescription prescription,
            ElepEncounterContextResult context,
            JSONObject draftBody) {
        if (draftBody == null) {
            throw new BusinessException("ELEP_SNAPSHOT_REQUIRED", "缺少草稿快照，请先保存");
        }
        JSONObject body = JSONObject.parseObject(draftBody.toJSONString());
        ElepDiagnosisLine diagnosis = toDiagnosis(body.getJSONObject("diseinfo"));
        JSONArray drugArray = body.getJSONArray("rxdrugdetail");
        JSONObject header = assembleHeader(prescription, context, diagnosis, drugArray);
        for (String key : header.keySet()) {
            body.put(key, header.get(key));
        }
        body.put("mdtrtinfo", assembleMdtrtinfo(prescription, context, diagnosis));
        body.put("diseinfo", assembleDiseinfo(prescription, context, diagnosis));
        if (drugArray != null) {
            body.put("rxdrugdetail", drugArray);
        }
        requireChronicDisease(prescription);
        requireDrugLines(body.getJSONArray("rxdrugdetail"));
        return body;
    }

    /**
     * 组装处方头字段。
     *
     * @param prescription 主表
     * @param context      上下文
     * @param diagnosis    诊断
     * @param drugArray    药品行（用于类目数）
     * @return 头 JSON
     */
    private JSONObject assembleHeader(
            YbElepPrescription prescription,
            ElepEncounterContextResult context,
            ElepDiagnosisLine diagnosis,
            JSONArray drugArray) {
        JSONObject body = new JSONObject();
        body.put("mdtrtCertType", firstNonEmpty(
                context.getMdtrtCertType(), ElepPrescriptionConstants.MDTRT_CERT_TYPE_ID_CARD));
        body.put("mdtrtCertNo", context.getMdtrtCertNo());
        body.put("bizTypeCode", ElepPrescriptionConstants.BIZ_TYPE_FIXMEDINS);
        body.put("hospRxno", prescription.getHospRxno());
        String rxType = trimToNull(prescription.getRxTypeCode());
        if (rxType == null) {
            rxType = ElepPrescriptionConstants.RX_TYPE_WESTERN;
        }
        body.put("rxTypeCode", rxType);
        String prscTime = LocalDateTime.now().format(ElepPrescriptionConstants.PRSC_TIME_FORMATTER);
        if (prescription.getPrscTime() != null) {
            prscTime = LocalDateTime.ofInstant(prescription.getPrscTime(), ZoneId.systemDefault())
                    .format(ElepPrescriptionConstants.PRSC_TIME_FORMATTER);
        }
        body.put("prscTime", prscTime);
        int drugCnt = 0;
        if (drugArray != null) {
            drugCnt = drugArray.size();
        }
        body.put("rxDrugCnt", String.valueOf(drugCnt));
        String valiDays = trimToNull(prescription.getValiDays());
        if (valiDays == null) {
            valiDays = ElepPrescriptionConstants.DEFAULT_VALI_DAYS;
        }
        body.put("valiDays", valiDays);
        int days = parsePositiveDays(valiDays);
        LocalDateTime endTime = LocalDateTime.parse(prscTime, ElepPrescriptionConstants.PRSC_TIME_FORMATTER)
                .plusDays(days);
        body.put("valiEndTime", endTime.format(ElepPrescriptionConstants.PRSC_TIME_FORMATTER));
        body.put("prescriptionNo", prescription.getHospRxno());
        if (!isEmpty(prescription.getInitRxno())) {
            body.put("initRxno", prescription.getInitRxno());
        }
        if (diagnosis != null) {
            body.put("maindiagCode", trimToNull(diagnosis.getDiagCode()));
            body.put("maindiagName", trimToNull(diagnosis.getDiagName()));
        }
        return body;
    }

    /**
     * 组装 mdtrtinfo（慢病 medType/diseCodg 只写本报文）。
     *
     * @param prescription 主表
     * @param context      上下文
     * @param diagnosis    诊断
     * @return 就诊信息节点
     */
    private JSONObject assembleMdtrtinfo(
            YbElepPrescription prescription,
            ElepEncounterContextResult context,
            ElepDiagnosisLine diagnosis) {
        JSONObject mdtrt = new JSONObject();
        mdtrt.put("fixmedinsName", context.getFixmedinsName());
        mdtrt.put("fixmedinsCode", context.getFixmedinsCode());
        mdtrt.put("mdtrtId", context.getMdtrtId());
        String medType = trimToNull(prescription.getMedType());
        if (medType == null) {
            medType = ElepPrescriptionConstants.MED_TYPE_GENERAL_OUTPATIENT;
        }
        mdtrt.put("medType", medType);
        mdtrt.put("iptOtpNo", firstNonEmpty(context.getIptOtpNo(), context.getEncounterBusNo()));
        mdtrt.put("otpIptFlag", context.getOtpIptFlag());
        mdtrt.put("psnNo", context.getPsnNo());
        mdtrt.put("patnName", context.getPatientName());
        mdtrt.put("psnCertType", context.getPsnCertType());
        mdtrt.put("certno", context.getIdCard());
        mdtrt.put("patnAge", context.getPatnAge());
        mdtrt.put("gend", context.getGend());
        mdtrt.put("prscDeptName", context.getDeptName());
        mdtrt.put("prscDeptCode", context.getDeptYbNo());
        mdtrt.put("drCode", context.getDoctorYbNo());
        mdtrt.put("prscDrName", context.getDoctorName());
        mdtrt.put("drProfttlCodg", context.getDoctorTitleCode());
        mdtrt.put("drProfttlName", context.getDoctorTitleName());
        mdtrt.put("drDeptCode", firstNonEmpty(context.getDoctorDeptYbNo(), context.getDeptYbNo()));
        mdtrt.put("drDeptName", firstNonEmpty(context.getDoctorDeptName(), context.getDeptName()));
        mdtrt.put("mdtrtTime", context.getMdtrtTime());
        mdtrt.put("prescriptionNo", prescription.getHospRxno());
        if (ElepPrescriptionConstants.isChronicMedType(medType)) {
            mdtrt.put("diseCodg", prescription.getDiseCodg());
            mdtrt.put("diseName", prescription.getDiseName());
            mdtrt.put("spDiseFlag", ElepPrescriptionConstants.SP_DISE_FLAG_YES);
        } else {
            mdtrt.put("spDiseFlag", ElepPrescriptionConstants.SP_DISE_FLAG_NO);
        }
        if (diagnosis != null) {
            mdtrt.put("maindiagCode", trimToNull(diagnosis.getDiagCode()));
            mdtrt.put("maindiagName", trimToNull(diagnosis.getDiagName()));
        }
        return mdtrt;
    }

    /**
     * 组装 diseinfo（不写就诊诊断表）。
     *
     * @param prescription 主表
     * @param context      上下文
     * @param diagnosis    本页诊断
     * @return 诊断节点
     */
    private JSONObject assembleDiseinfo(
            YbElepPrescription prescription,
            ElepEncounterContextResult context,
            ElepDiagnosisLine diagnosis) {
        JSONObject dise = new JSONObject();
        if (diagnosis == null) {
            return dise;
        }
        dise.put("diagType", ElepPrescriptionConstants.DIAG_TYPE_WESTERN);
        dise.put("diagSrtNo", ElepPrescriptionConstants.DIAG_SRT_NO_MAIN);
        dise.put("diagCode", trimToNull(diagnosis.getDiagCode()));
        dise.put("diagName", trimToNull(diagnosis.getDiagName()));
        dise.put("diagDept", context.getDeptName());
        dise.put("diagDrNo", context.getDoctorYbNo());
        dise.put("diagDrName", context.getDoctorName());
        dise.put("diagTime", context.getMdtrtTime());
        dise.put("maindiagFlag", ElepPrescriptionConstants.MAINDIAG_FLAG_YES);
        dise.put("prescriptionNo", prescription.getHospRxno());
        return dise;
    }

    /**
     * 组装药品明细。
     *
     * @param prescription 主表
     * @param drugLines    本页药品
     * @return 明细数组
     */
    private JSONArray assembleDrugLines(YbElepPrescription prescription, List<ElepRxDrugLine> drugLines) {
        JSONArray array = new JSONArray();
        if (drugLines == null) {
            return array;
        }
        String prscTime = LocalDateTime.now().format(ElepPrescriptionConstants.PRSC_TIME_FORMATTER);
        for (ElepRxDrugLine line : drugLines) {
            // 空行跳过，不占 rxdrugdetail 下标
            if (line == null) {
                continue;
            }
            JSONObject row = new JSONObject();
            row.put("medListCodg", trimToNull(line.getMedListCodg()));
            row.put("drugGenname", firstNonEmpty(line.getDrugGenname(), line.getDrugName()));
            row.put("drugName", firstNonEmpty(line.getDrugName(), line.getDrugGenname()));
            row.put("drugSpec", trimToNull(line.getDrugSpec()));
            row.put("drugDosform", trimToNull(line.getDrugDosform()));
            row.put("drugProdname", firstNonEmpty(line.getDrugProdname(), line.getDrugName()));
            row.put("prdrName", trimToNull(line.getPrdrName()));
            String itemType = trimToNull(line.getRxItemTypeCode());
            // 本页只开西药/中成药时默认西药分类 11
            if (itemType == null) {
                itemType = ElepPrescriptionConstants.RX_ITEM_TYPE_WESTERN;
            }
            row.put("rxItemTypeCode", itemType);
            String itemName = trimToNull(line.getRxItemTypeName());
            if (itemName == null) {
                itemName = ElepPrescriptionConstants.RX_ITEM_TYPE_WESTERN_NAME;
            }
            row.put("rxItemTypeName", itemName);
            row.put("drugCnt", firstNonEmpty(line.getDrugCnt(), "1"));
            row.put("drugDosunt", trimToNull(line.getDrugDosunt()));
            String wayDscr = trimToNull(line.getMedcWayDscr());
            row.put("medcWayDscr", wayDscr);
            // 3-03 允许院内内部途径代码；医生只填描述时用描述充当编码
            row.put("medcWayCodg", firstNonEmpty(line.getMedcWayCodg(), wayDscr));
            String days = firstNonEmpty(line.getMedcDays(), ElepPrescriptionConstants.DEFAULT_VALI_DAYS);
            row.put("medcDays", days);
            row.put("sinDoscnt", trimToNull(line.getSinDoscnt()));
            row.put("sinDosunt", trimToNull(line.getSinDosunt()));
            String frquName = trimToNull(line.getUsedFrquName());
            row.put("usedFrquName", frquName);
            row.put("usedFrquCodg", firstNonEmpty(line.getUsedFrquCodg(), frquName));
            row.put("hospApprFlag", ElepPrescriptionConstants.HOSP_APPR_FLAG_DEFAULT);
            row.put("hospPrepFlag", ElepPrescriptionConstants.FLAG_NO);
            row.put("urgtFlag", ElepPrescriptionConstants.FLAG_NO);
            row.put("basMednFlag", ElepPrescriptionConstants.FLAG_NO);
            row.put("impDrugFlag", ElepPrescriptionConstants.FLAG_NO);
            row.put("otcFlag", ElepPrescriptionConstants.FLAG_NO);
            // 首行记为主要用药，满足 3-03 mainMedcFlag 必填
            if (array.isEmpty()) {
                row.put("mainMedcFlag", ElepPrescriptionConstants.FLAG_YES);
            } else {
                row.put("mainMedcFlag", ElepPrescriptionConstants.FLAG_NO);
            }
            putMedcWindow(row, line, prscTime, days);
            row.put("prescriptionNo", prescription.getHospRxno());
            array.add(row);
        }
        return array;
    }

    /**
     * 慢病必须已填病种，否则平台预核验会失败。
     *
     * @param prescription 主表
     */
    private void requireChronicDisease(YbElepPrescription prescription) {
        String medType = trimToNull(prescription.getMedType());
        if (!ElepPrescriptionConstants.isChronicMedType(medType)) {
            return;
        }
        if (isEmpty(prescription.getDiseCodg()) || isEmpty(prescription.getDiseName())) {
            throw new BusinessException("ELEP_DISE_REQUIRED", "慢病电子处方须选择病种");
        }
    }

    /**
     * 预核验至少一行药品，且西药/中成药必填用法、频次、单次用量、服药起止时间。
     *
     * @param drugLines 明细
     */
    private void requireDrugLines(JSONArray drugLines) {
        if (drugLines == null || drugLines.isEmpty()) {
            throw new BusinessException("ELEP_DRUG_REQUIRED", "请至少选择一行医保药品");
        }
        for (int i = 0; i < drugLines.size(); i++) {
            JSONObject line = drugLines.getJSONObject(i);
            // 空对象不计入必填校验
            if (line == null) {
                continue;
            }
            int displayNo = i + 1;
            requireLineText(line, "medListCodg", displayNo, "医保药品编码");
            requireLineText(line, "medcWayDscr", displayNo, "用法");
            requireLineText(line, "usedFrquName", displayNo, "频次");
            requireLineText(line, "sinDoscnt", displayNo, "单次用量");
            requireLineText(line, "sinDosunt", displayNo, "单次单位");
            requireLineText(line, "medcBegntime", displayNo, "服药开始时间");
            requireLineText(line, "medcEndtime", displayNo, "服药结束时间");
        }
    }

    /**
     * 校验药品行必填文本（3-03 rxdrugdetail 西药/中成药）。
     *
     * @param line      明细 JSON
     * @param key       字段
     * @param displayNo 行号（从 1 起）
     * @param label     中文名
     */
    private void requireLineText(JSONObject line, String key, int displayNo, String label) {
        // 3-03 西药/中成药必填项空则预核验必失败
        if (!isEmpty(line.getString(key))) {
            return;
        }
        throw new BusinessException(
                "ELEP_DRUG_FIELD_REQUIRED",
                "第" + displayNo + "行药品缺少" + label);
    }

    /**
     * 写入服药起止时间：优先本页填写，缺省按开方时间 + 用药天数。
     *
     * @param row      明细 JSON
     * @param line     本页药品
     * @param prscTime 开方时间
     * @param days     用药天数
     */
    private void putMedcWindow(JSONObject row, ElepRxDrugLine line, String prscTime, String days) {
        String beginTime = trimToNull(line.getMedcBegntime());
        // 本页未填开始时间时，用开方时间，避免预核验缺 medcBegntime
        if (beginTime == null) {
            beginTime = prscTime;
        }
        row.put("medcBegntime", beginTime);
        String endTimeText = trimToNull(line.getMedcEndtime());
        // 医生已填结束时间则原样上传
        if (endTimeText != null) {
            row.put("medcEndtime", endTimeText);
            return;
        }
        int medcDays = parsePositiveDays(days);
        LocalDateTime begin;
        try {
            begin = LocalDateTime.parse(beginTime, ElepPrescriptionConstants.PRSC_TIME_FORMATTER);
        } catch (java.time.format.DateTimeParseException ex) {
            // 非法时间串回落到当前时刻再加天数
            begin = LocalDateTime.now();
        }
        row.put(
                "medcEndtime",
                begin.plusDays(medcDays).format(ElepPrescriptionConstants.PRSC_TIME_FORMATTER));
    }

    /**
     * 从快照诊断节点还原本页诊断。
     *
     * @param diseinfo 诊断 JSON
     * @return 诊断行；空节点返回 null
     */
    private ElepDiagnosisLine toDiagnosis(JSONObject diseinfo) {
        if (diseinfo == null) {
            return null;
        }
        String code = trimToNull(diseinfo.getString("diagCode"));
        String name = trimToNull(diseinfo.getString("diagName"));
        if (code == null && name == null) {
            return null;
        }
        return new ElepDiagnosisLine().setDiagCode(code).setDiagName(name);
    }

    /**
     * 解析处方/用药天数（业务数值，非枚举 code）。
     *
     * @param days 天数串
     * @return 正整数天数
     */
    private static int parsePositiveDays(String days) {
        try {
            int value = Integer.parseInt(days);
            if (value <= 0) {
                return 3;
            }
            return value;
        } catch (NumberFormatException ex) {
            return 3;
        }
    }

    /**
     * 取第一个非空字符串。
     *
     * @param first  优先值
     * @param second 回落值
     * @return 非空或 null
     */
    private static String firstNonEmpty(String first, String second) {
        String left = trimToNull(first);
        if (left != null) {
            return left;
        }
        return trimToNull(second);
    }
}
