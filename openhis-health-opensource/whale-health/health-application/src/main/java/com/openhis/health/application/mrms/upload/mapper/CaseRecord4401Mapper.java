/*
 * Copyright © 2026 OpenHIS Contributors
 */

package com.openhis.health.application.mrms.upload.mapper;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import com.openhis.health.application.mrms.upload.support.Mrms4401DiagTypeMapping;
import com.openhis.health.application.mrms.upload.support.MrmsInsuranceEncounterContext;
import com.openhis.health.domain.mrms.caserecord.entity.CaseRecord;
import com.openhis.health.domain.mrms.caserecord.entity.CaseRecordHomepage;
import com.openhis.health.domain.mrms.caserecord.entity.HomepageDiagnosis;
import com.openhis.health.domain.mrms.caserecord.entity.HomepageProcedure;
import com.openhis.health.domain.mrms.caserecord.service.CaseRecordHomepageDomainService;
import com.openhis.health.domain.share.constants.GeneralSharedConstants;
import com.openhis.health.domain.share.constants.MrmsErrorCodes;
import com.openhis.health.integration.insurance.medcas.dto.MedcasHomepageBaseInfo;
import com.openhis.health.integration.insurance.medcas.dto.MedcasHomepageDiseInfo;
import com.openhis.health.integration.insurance.medcas.dto.MedcasHomepageIcuInfo;
import com.openhis.health.integration.insurance.medcas.dto.MedcasHomepageOprnInfo;
import com.openhis.health.integration.insurance.medcas.dto.MedcasHomepagePayload;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

import static com.openhis.whale.core.application.util.StringUtils.trimToNull;

/**
 * 病案首页 → 医保 4401 {@link MedcasHomepagePayload} 映射（upload IF {@code mapper}）。
 *
 * <p>读首页/子表经 {@link CaseRecordHomepageDomainService}（B.6.4）；费用等扩展字段取自
 * {@code extensionJson}；诊断类型码经 {@link Mrms4401DiagTypeMapping} 转换。</p>
 */
@Component
@RequiredArgsConstructor
public class CaseRecord4401Mapper {

    private final CaseRecordHomepageDomainService homepageDomainService;

    /**
     * 组装 4401 上传载荷。
     *
     * @param record 病案主档
     * @param insCtx 医保就诊上下文，可为 null
     * @return 4401 载荷
     */
    public MedcasHomepagePayload assemble(CaseRecord record, MrmsInsuranceEncounterContext insCtx) {
        CaseRecordHomepage homepage = homepageDomainService.requireExistingByCaseRecordId(record.getId());
        List<HomepageDiagnosis> diagnoses = homepageDomainService.listDiagnoses(record.getId());
        List<HomepageProcedure> procedures = homepageDomainService.listProcedures(record.getId());
        JSONObject ext = parseExtension(homepage.getExtensionJson());

        String fixmedinsCode = null;
        if (insCtx != null) {
            fixmedinsCode = trimToNull(insCtx.getFixmedinsCode());
        }
        String mdtrtSn = resolveMdtrtSn(insCtx, fixmedinsCode, homepage.getIptNo());
        String hmpgSn = trimToNull(homepage.getIptMedcasHmpgSn());

        MedcasHomepageBaseInfo base = new MedcasHomepageBaseInfo();
        base.setMdtrtSn(mdtrtSn);
        // 医保上下文存在时上送 mdtrt_id/psn_no/定点机构
        if (insCtx != null) {
            base.setMdtrtId(trimToNull(insCtx.getMdtrtId()));
            base.setPsnNo(trimToNull(insCtx.getPsnNo()));
            base.setFixmedinsCode(fixmedinsCode);
        }
        // 上下文无 psn_no 时从 extensionJson 回填
        if (trimToNull(base.getPsnNo()) == null) {
            base.setPsnNo(extGet(ext, "psn_no"));
        }
        if (record.getPatnIptCnt() != null) {
            base.setPatnIptCnt(String.valueOf(record.getPatnIptCnt()));
        }
        base.setIptNo(trimToNull(homepage.getIptNo()));
        base.setMedcasno(trimToNull(record.getMedcasno()));
        base.setPsnName(trimToNull(homepage.getPsnName()));
        base.setGend(trimToNull(homepage.getGendCode()));
        base.setBrdy(formatDate(homepage.getBrdy()));
        base.setNtly(trimToNull(homepage.getNtlyCode()));
        base.setNaty(trimToNull(homepage.getNatyCode()));
        base.setCertno(trimToNull(homepage.getCertno()));
        base.setPrfs(trimToNull(homepage.getPrfsCode()));
        base.setMrgStas(trimToNull(homepage.getMrgStasCode()));
        base.setCurrAddr(trimToNull(homepage.getCurrAddr()));
        base.setPsnTel(trimToNull(homepage.getPsnTel()));
        base.setConerName(trimToNull(homepage.getConerName()));
        base.setConerTel(trimToNull(homepage.getConerTel()));
        base.setAdmWayCode(trimToNull(homepage.getAdmWayCode()));
        base.setAdmCaty(trimToNull(homepage.getAdmCatyCode()));
        base.setDscgCaty(trimToNull(homepage.getDscgCatyCode()));
        base.setAdmDate(formatDateTime(homepage.getAdmTime()));
        base.setDscgDate(formatDateTime(homepage.getDscgTime()));
        if (homepage.getIptDays() != null) {
            base.setIptDays(String.valueOf(homepage.getIptDays()));
        }
        base.setDscgWay(trimToNull(homepage.getDscgWayCode()));
        base.setDeptdrtName(trimToNull(homepage.getDeptDirectorName()));
        base.setChfdrName(trimToNull(homepage.getChfdrName()));
        base.setAtddrName(trimToNull(homepage.getAtddrName()));
        base.setIptDrName(trimToNull(homepage.getIptDrName()));
        base.setRespNursName(trimToNull(homepage.getRespNursName()));
        base.setMedfeeSumamt(extGet(ext, "medfee_sumamt"));
        base.setSelfpayAmt(extGet(ext, "selfpay_amt"));
        base.setMedfeePaymtdCode(extGet(ext, "medfee_paymtd_code"));
        base.setValiFlag("1");

        MedcasHomepagePayload payload = new MedcasHomepagePayload();
        payload.setBaseinfo(base);
        payload.setDiseinfo(buildDiseinfo(diagnoses, hmpgSn, mdtrtSn, fixmedinsCode));
        payload.setOprninfo(buildOprninfo(procedures, hmpgSn, mdtrtSn, fixmedinsCode));
        payload.setIcuinfo(Collections.<MedcasHomepageIcuInfo>emptyList());
        return payload;
    }

    /**
     * 首页诊断子表 → 4401 diseinfo 行（跳过无编码行，按类型+序号排序）。
     *
     * @param rows 诊断实体
     * @param hmpgSn 4401 首页流水号
     * @param mdtrtSn 就诊流水号
     * @param fixmedinsCode 定点机构编码
     * @return 4401 诊断行列表
     */
    private List<MedcasHomepageDiseInfo> buildDiseinfo(
            List<HomepageDiagnosis> rows,
            String hmpgSn,
            String mdtrtSn,
            String fixmedinsCode) {
        if (rows == null || rows.isEmpty()) {
            return Collections.emptyList();
        }
        List<HomepageDiagnosis> sorted = new ArrayList<>(rows);
        sorted.sort(Comparator.comparing(HomepageDiagnosis::getDiagTypeCode)
                .thenComparing(HomepageDiagnosis::getSortNo));
        List<MedcasHomepageDiseInfo> result = new ArrayList<>();
        for (HomepageDiagnosis row : sorted) {
            // 无 ICD 编码行跳过，避免 4401 校验失败
            if (trimToNull(row.getDiagCode()) == null) {
                continue;
            }
            MedcasHomepageDiseInfo item = new MedcasHomepageDiseInfo();
            item.setIptMedcasHmpgSn(hmpgSn);
            item.setMdtrtSn(mdtrtSn);
            item.setFixmedinsCode(fixmedinsCode);
            item.setIptPatnDisediagTypeCode(Mrms4401DiagTypeMapping.mapTypeCode(row.getDiagTypeCode()));
            item.setMaindiagFlag(Mrms4401DiagTypeMapping.mapMainFlag(row.getDiagTypeCode(), row.getMaindiagFlag()));
            item.setDiagCode(trimToNull(row.getDiagCode()));
            item.setDiagName(trimToNull(row.getDiagName()));
            item.setAdmCondCode(trimToNull(row.getAdmCondCode()));
            item.setValiFlag("1");
            result.add(item);
        }
        return result;
    }

    /**
     * 首页手术子表 → 4401 oprninfo 行（跳过无编码行，按序号排序）。
     *
     * @param rows 手术实体
     * @param hmpgSn 4401 首页流水号
     * @param mdtrtSn 就诊流水号
     * @param fixmedinsCode 定点机构编码
     * @return 4401 手术行列表
     */
    private List<MedcasHomepageOprnInfo> buildOprninfo(
            List<HomepageProcedure> rows,
            String hmpgSn,
            String mdtrtSn,
            String fixmedinsCode) {
        if (rows == null || rows.isEmpty()) {
            return Collections.emptyList();
        }
        List<HomepageProcedure> sorted = new ArrayList<>(rows);
        sorted.sort(Comparator.comparing(HomepageProcedure::getSortNo));
        List<MedcasHomepageOprnInfo> result = new ArrayList<>();
        for (HomepageProcedure row : sorted) {
            // 无手术编码行跳过
            if (trimToNull(row.getOprnOprtCode()) == null) {
                continue;
            }
            MedcasHomepageOprnInfo item = new MedcasHomepageOprnInfo();
            item.setIptMedcasHmpgSn(hmpgSn);
            item.setMdtrtSn(mdtrtSn);
            item.setFixmedinsCode(fixmedinsCode);
            item.setOprnOprtDate(formatDate(row.getOprnDate()));
            item.setOprnOprtCode(trimToNull(row.getOprnOprtCode()));
            item.setOprnOprtName(trimToNull(row.getOprnOprtName()));
            item.setMainOprnFlag(trimToNull(row.getMainOprnFlag()));
            item.setValiFlag("1");
            result.add(item);
        }
        return result;
    }

    /**
     * 解析 4401 mdtrt_sn：优先医保上下文，否则 fixmedinsCode+iptNo 拼接。
     *
     * @param insCtx 医保就诊上下文，可为 null
     * @param fixmedinsCode 定点机构编码
     * @param iptNo 住院号
     * @return 就诊流水号，可能为 null
     */
    private String resolveMdtrtSn(MrmsInsuranceEncounterContext insCtx, String fixmedinsCode, String iptNo) {
        if (insCtx != null && trimToNull(insCtx.getMdtrtSn()) != null) {
            return insCtx.getMdtrtSn();
        }
        if (trimToNull(fixmedinsCode) != null && trimToNull(iptNo) != null) {
            return fixmedinsCode + iptNo;
        }
        return trimToNull(fixmedinsCode);
    }

    /**
     * 解析首页 extensionJson 为 JSONObject；失败时返回空对象。
     *
     * @param json extension JSON 字符串
     * @return 解析结果，永不为 null
     */
    private JSONObject parseExtension(String json) {
        if (trimToNull(json) == null) {
            return new JSONObject();
        }
        try {
            return JSON.parseObject(json);
        } catch (Exception ex) {
            return new JSONObject();
        }
    }

    /**
     * 从 extension JSON 读取字符串字段。
     *
     * @param ext extension 对象
     * @param key 字段键
     * @return 字符串值，缺失时 null
     */
    private String extGet(JSONObject ext, String key) {
        if (ext == null || !ext.containsKey(key)) {
            return null;
        }
        Object val = ext.get(key);
        if (val == null) {
            return null;
        }
        return String.valueOf(val);
    }

    /**
     * LocalDate 格式化为 4401 展示日期（yyyy-MM-dd）。
     *
     * @param date 日期
     * @return 格式化字符串，null 时 null
     */
    private String formatDate(LocalDate date) {
        if (date == null) {
            return null;
        }
        return date.format(GeneralSharedConstants.DATE_DISPLAY_FORMATTER);
    }

    /**
     * LocalDateTime 格式化为 4401 展示日期（yyyy-MM-dd）。
     *
     * @param dateTime 日期时间
     * @return 格式化字符串，null 时 null
     */
    private String formatDateTime(LocalDateTime dateTime) {
        if (dateTime == null) {
            return null;
        }
        return dateTime.format(GeneralSharedConstants.DATE_DISPLAY_FORMATTER);
    }
}
