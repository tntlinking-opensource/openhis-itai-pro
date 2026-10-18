/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.chs.bridge.outpatient.support;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import com.zxplat.framework.core.application.exception.BusinessException;
import com.zxplat.framework.core.infrastructure.multitenancy.TenantUtils;
import com.zxplat.plugin.chs.domain.outpatient.command.OpPreSettleCommand;
import com.zxplat.plugin.chs.domain.outpatient.entity.YbPersonCache;
import com.zxplat.plugin.chs.domain.outpatient.service.YbPersonCacheDomainService;
import com.zxplat.plugin.chs.domain.share.constants.ChsErrorCodes;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.Optional;

import static com.zxplat.framework.core.application.util.StringUtils.isEmpty;
import static com.zxplat.framework.core.application.util.StringUtils.isNotEmpty;

/**
 * 门诊医保读卡人与就诊患者一致性门禁。
 *
 * <p>收费预结算/2201 前调用：禁止把他人电子凭证或社保卡挂到当前就诊（串账）。
 * 比对优先身份证号，缺证号时回落姓名；均无法核验则拒绝（医保路径 fail-closed）。</p>
 */
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "zxplat.modules.chs.enabled", havingValue = "true", matchIfMissing = true)
public class ChsOutpatientPayerPatientMatchGate {

    private final YbPersonCacheDomainService personCacheDomainService;

    /**
     * 断言预结算上下文中的 {@code psnNo} 对应当日读卡人，且与就诊患者身份证/姓名一致。
     *
     * <p>无 {@code psnNo} 时跳过（签发事前提醒等可不传读卡）；有 {@code psnNo} 则必须通过。</p>
     *
     * @param context 已填就诊患者与读卡人员编号的预结算上下文
     * @throws BusinessException 读卡人与就诊患者不一致，或无法从当日缓存核验读卡人
     */
    public void assertPayerMatchesEncounterPatient(OpPreSettleCommand context) {
        if (context == null || isEmpty(context.getPsnNo())) {
            return;
        }
        String requestPsnNo = context.getPsnNo().trim();
        String patientIdCard = normalizeIdCard(context.getPatientCertNo());
        String patientName = normalizePersonName(context.getPatientName());
        // Gate：就诊侧至少要有身份证或姓名之一，否则无法核对读卡人
        if (isEmpty(patientIdCard) && isEmpty(patientName)) {
            throw new BusinessException(
                ChsErrorCodes.PAYER_PATIENT_MISMATCH,
                "当前就诊缺少患者身份证号与姓名，无法核验医保读卡人，请完善患者档案后重试");
        }
        Long tenantId = context.getTenantId();
        if (tenantId == null) {
            tenantId = TenantUtils.getCurrentTenantId();
        }
        Long contractId = context.getContractId();
        // Pre：优先用就诊身份证号命中当日缓存，期望 psn 必须等于请求 psn（防止扫了别人的码）
        if (isNotEmpty(patientIdCard) && contractId != null) {
            Optional<YbPersonCache> byPatientIdCard = personCacheDomainService
                .findValidByContractIdAndIdCard(contractId, patientIdCard, tenantId);
            if (byPatientIdCard.isEmpty()) {
                byPatientIdCard = personCacheDomainService
                    .findLatestByContractIdAndIdCard(contractId, patientIdCard, tenantId);
            }
            if (byPatientIdCard.isPresent()) {
                String expectedPsn = trimOrEmpty(byPatientIdCard.get().getPsnNo());
                // Gate：该患者今日已读过卡，请求人员编号必须就是该患者
                if (isNotEmpty(expectedPsn) && !expectedPsn.equals(requestPsnNo)) {
                    throw mismatchException(patientName, extractPsnName(byPatientIdCard.get()), requestPsnNo);
                }
            }
        }
        // Pre：用请求 psn 回查 1101 快照，再与就诊身份证/姓名比对（覆盖「患者今日未读卡、却扫了别人码」）
        Optional<YbPersonCache> byRequestPsn = Optional.empty();
        if (contractId != null) {
            byRequestPsn = personCacheDomainService
                .findValidByContractIdAndPsnNo(contractId, requestPsnNo, tenantId);
        }
        if (byRequestPsn.isEmpty()) {
            throw new BusinessException(
                ChsErrorCodes.PAYER_PATIENT_MISMATCH,
                "未找到本次读卡人员缓存，请重新读卡后再收费");
        }
        YbPersonCache payerCache = byRequestPsn.get();
        String payerCertno = extractCertno(payerCache);
        String payerName = extractPsnName(payerCache);
        // Gate：有身份证号时以证件为准（最强防串账）
        if (isNotEmpty(patientIdCard)) {
            String normalizedPayerCert = normalizeIdCard(payerCertno);
            if (isEmpty(normalizedPayerCert)) {
                throw new BusinessException(
                    ChsErrorCodes.PAYER_PATIENT_MISMATCH,
                    "读卡未返回身份证号，无法与就诊患者核验，请重新读卡");
            }
            if (!patientIdCard.equals(normalizedPayerCert)) {
                throw mismatchException(patientName, payerName, requestPsnNo);
            }
            return;
        }
        // Gate：无证号档案时回落姓名全等（去空白）
        if (isNotEmpty(patientName)) {
            String normalizedPayerName = normalizePersonName(payerName);
            if (isEmpty(normalizedPayerName) || !patientName.equals(normalizedPayerName)) {
                throw mismatchException(patientName, payerName, requestPsnNo);
            }
        }
    }

    /**
     * 断言平台就诊映射上的 {@code psn_no} 与当前请求/患者一致。
     *
     * <p>map 命中复用 mdtrt 时调用：若历史误把他人 psn 写入本就诊 map，禁止继续用错号结算。
     * 有本次读卡 {@code psnNo} 时走完整比对；签发门禁无读卡时仅按患者身份证期望 psn 核对 map。</p>
     *
     * @param context     预结算上下文（须已含患者证件/姓名）
     * @param mappedPsnNo map 行上的人员编号
     * @throws BusinessException map 人员与就诊患者或本次读卡人不一致
     */
    public void assertMappedPsnMatchesContext(OpPreSettleCommand context, String mappedPsnNo) {
        if (context == null || isEmpty(mappedPsnNo)) {
            return;
        }
        String mapPsn = mappedPsnNo.trim();
        // Gate：本次显式传了读卡 psn 时，不得复用他人 map，并做完整患者比对
        if (isNotEmpty(context.getPsnNo())) {
            if (!mapPsn.equals(context.getPsnNo().trim())) {
                throw new BusinessException(
                    ChsErrorCodes.PAYER_PATIENT_MISMATCH,
                    "本就诊已绑定的医保人员与本次读卡不一致，请重新读当前患者医保凭证后再收费");
            }
            this.assertPayerMatchesEncounterPatient(context);
            return;
        }
        // Pre：签发门禁常不传 psnNo——仅当患者有身份证且当日/历史缓存可解析期望 psn 时核对 map
        String patientIdCard = normalizeIdCard(context.getPatientCertNo());
        Long contractId = context.getContractId();
        Long tenantId = context.getTenantId();
        if (tenantId == null) {
            tenantId = TenantUtils.getCurrentTenantId();
        }
        if (isEmpty(patientIdCard) || contractId == null) {
            return;
        }
        Optional<YbPersonCache> byPatientIdCard = personCacheDomainService
            .findValidByContractIdAndIdCard(contractId, patientIdCard, tenantId);
        if (byPatientIdCard.isEmpty()) {
            byPatientIdCard = personCacheDomainService
                .findLatestByContractIdAndIdCard(contractId, patientIdCard, tenantId);
        }
        if (byPatientIdCard.isEmpty()) {
            return;
        }
        String expectedPsn = trimOrEmpty(byPatientIdCard.get().getPsnNo());
        // Gate：map 上挂的是别人的 psn（串账残留）则拒绝复用
        if (isNotEmpty(expectedPsn) && !expectedPsn.equals(mapPsn)) {
            throw new BusinessException(
                ChsErrorCodes.PAYER_PATIENT_MISMATCH,
                "本就诊医保平台映射人员与患者档案不一致，请重新读取当前患者医保凭证后再收费");
        }
    }

    /**
     * 组装串账拒绝异常（文案含就诊姓名与读卡姓名，便于款员当场发现扫错人）。
     *
     * @param patientName  就诊患者姓名
     * @param payerName    读卡返回姓名
     * @param requestPsnNo 请求人员编号（日志辅助）
     * @return 业务异常
     */
    private BusinessException mismatchException(String patientName, String payerName, String requestPsnNo) {
        String patientLabel = "当前患者";
        // Pre：有姓名时写入文案，便于款员当场识别扫错人
        if (isNotEmpty(patientName)) {
            patientLabel = patientName.trim();
        }
        String payerLabel = "读卡人员";
        if (isNotEmpty(payerName)) {
            payerLabel = payerName.trim();
        }
        return new BusinessException(
            ChsErrorCodes.PAYER_PATIENT_MISMATCH,
            "医保读卡人「" + payerLabel + "」与就诊患者「" + patientLabel
                + "」不一致，请重新读取当前患者的医保凭证后再收费（人员编号 "
                + trimOrEmpty(requestPsnNo) + "）");
    }

    /**
     * 从人员缓存快照解析身份证号（兼容 output.baseinfo / baseinfo / 顶层 certno）。
     *
     * @param cache 人员缓存行
     * @return 身份证号；解析不到则空串
     */
    private String extractCertno(YbPersonCache cache) {
        JSONObject root = parseSnapshot(cache);
        if (root == null) {
            return "";
        }
        String direct = root.getString("certno");
        if (isNotEmpty(direct)) {
            return direct.trim();
        }
        JSONObject baseinfo = root.getJSONObject("baseinfo");
        if (baseinfo != null && isNotEmpty(baseinfo.getString("certno"))) {
            return baseinfo.getString("certno").trim();
        }
        JSONObject output = root.getJSONObject("output");
        if (output != null) {
            JSONObject nested = output.getJSONObject("baseinfo");
            if (nested != null && isNotEmpty(nested.getString("certno"))) {
                return nested.getString("certno").trim();
            }
        }
        return "";
    }

    /**
     * 从人员缓存快照解析姓名。
     *
     * @param cache 人员缓存行
     * @return 姓名；解析不到则空串
     */
    private String extractPsnName(YbPersonCache cache) {
        JSONObject root = parseSnapshot(cache);
        if (root == null) {
            return "";
        }
        String direct = root.getString("psn_name");
        if (isEmpty(direct)) {
            direct = root.getString("psnName");
        }
        if (isNotEmpty(direct)) {
            return direct.trim();
        }
        JSONObject baseinfo = root.getJSONObject("baseinfo");
        if (baseinfo != null) {
            String name = baseinfo.getString("psn_name");
            if (isEmpty(name)) {
                name = baseinfo.getString("psnName");
            }
            if (isNotEmpty(name)) {
                return name.trim();
            }
        }
        JSONObject output = root.getJSONObject("output");
        if (output != null) {
            JSONObject nested = output.getJSONObject("baseinfo");
            if (nested != null) {
                String name = nested.getString("psn_name");
                if (isEmpty(name)) {
                    name = nested.getString("psnName");
                }
                if (isNotEmpty(name)) {
                    return name.trim();
                }
            }
        }
        return "";
    }

    /**
     * 解析人员缓存 snapshot_json。
     *
     * @param cache 缓存行
     * @return JSON 对象；空或非法时 null
     */
    private JSONObject parseSnapshot(YbPersonCache cache) {
        if (cache == null || isEmpty(cache.getSnapshotJson())) {
            return null;
        }
        try {
            return JSON.parseObject(cache.getSnapshotJson());
        } catch (Exception ex) {
            return null;
        }
    }

    /**
     * 规范化身份证号（去空白、大写）。
     *
     * @param idCard 原始证件号
     * @return 规范化值；空则 null
     */
    private String normalizeIdCard(String idCard) {
        if (isEmpty(idCard)) {
            return null;
        }
        return idCard.trim().toUpperCase();
    }

    /**
     * 规范化姓名（去首尾空白）。
     *
     * @param name 原始姓名
     * @return 规范化值；空则 null
     */
    private String normalizePersonName(String name) {
        if (isEmpty(name)) {
            return null;
        }
        return name.trim();
    }

    /**
     * 空安全 trim。
     *
     * @param value 原始字符串
     * @return trim 后；空输入返回空串
     */
    private String trimOrEmpty(String value) {
        if (isEmpty(value)) {
            return "";
        }
        return value.trim();
    }
}
