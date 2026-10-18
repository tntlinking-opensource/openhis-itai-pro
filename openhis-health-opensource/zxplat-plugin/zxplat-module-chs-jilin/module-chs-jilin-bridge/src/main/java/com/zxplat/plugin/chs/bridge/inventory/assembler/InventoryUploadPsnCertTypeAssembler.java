/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.chs.bridge.inventory.assembler;

import static com.zxplat.framework.core.application.util.StringUtils.isEmpty;
import static com.zxplat.framework.core.application.util.StringUtils.isNotEmpty;

import com.zxplat.plugin.chs.domain.share.enums.PsnCertType;
import com.zxplat.domain.administration.patient.entity.Patient;
import com.zxplat.domain.administration.patient.entity.PatientIdentifier;
import com.zxplat.domain.share.constants.GeneralSharedConstants;
import com.zxplat.domain.share.enums.IdentifierState;
import com.zxplat.domain.share.enums.IdentityDocumentType;

import java.util.List;

/**
 * 3505 {@code psn_cert_type}：跟患者主档证件类型映射为平台码。
 *
 * <p>主表有身份证号 → {@code 01}；否则取常用/官方非身份证标识的 {@code type_code} 映射。</p>
 */
public final class InventoryUploadPsnCertTypeAssembler {

    private InventoryUploadPsnCertTypeAssembler() {
    }

    /**
     * @param patient     患者主档
     * @param identifiers 非身份证标识列表（可为 null）
     * @return 平台 {@code psn_cert_type}；无法解析时 null
     */
    public static String resolve(Patient patient, List<PatientIdentifier> identifiers) {
        // 主表身份证：平台居民身份证
        if (patient != null && isNotEmpty(patient.getIdCard())) {
            return PsnCertType.ID_CARD.getCode();
        }
        PatientIdentifier picked = pickPreferredIdentifier(identifiers);
        if (picked == null || isEmpty(picked.getTypeCode())) {
            return null;
        }
        return mapHospitalTypeCodeToPlatform(picked.getTypeCode().trim());
    }

    /**
     * 与 {@link #resolve} 同源：主表身份证号，或优选非身份证标识号码。
     *
     * @param patient     患者
     * @param identifiers 非身份证标识
     * @return 证件号码；无则 null
     */
    public static String resolveCertno(Patient patient, List<PatientIdentifier> identifiers) {
        if (patient != null && isNotEmpty(patient.getIdCard())) {
            return patient.getIdCard().trim();
        }
        PatientIdentifier picked = pickPreferredIdentifier(identifiers);
        if (picked == null || isEmpty(picked.getIdentifierNo())) {
            return null;
        }
        return picked.getIdentifierNo().trim();
    }

    /**
     * 优选官方/常用非身份证标识。
     *
     * @param identifiers 标识列表
     * @return 优选行；无则 null
     */
    public static PatientIdentifier pickPreferredIdentifier(List<PatientIdentifier> identifiers) {
        if (identifiers == null || identifiers.isEmpty()) {
            return null;
        }
        PatientIdentifier official = null;
        PatientIdentifier usual = null;
        PatientIdentifier fallback = null;
        for (PatientIdentifier row : identifiers) {
            if (row == null || isEmpty(row.getTypeCode())) {
                continue;
            }
            if (fallback == null) {
                fallback = row;
            }
            if (IdentifierState.OFFICIAL.equals(row.getStateEnum())) {
                official = row;
                break;
            }
            if (usual == null && IdentifierState.USUAL.equals(row.getStateEnum())) {
                usual = row;
            }
        }
        if (official != null) {
            return official;
        }
        if (usual != null) {
            return usual;
        }
        return fallback;
    }

    /**
     * 院内证件类型 → 医保平台人员证件类型码。
     *
     * @param hospitalTypeCode 院内 type_code
     * @return 平台码；未知类型原样回传（已是两位数字码时）
     */
    private static String mapHospitalTypeCodeToPlatform(String hospitalTypeCode) {
        if (GeneralSharedConstants.ID_CARD_TYPE_CODE.equals(hospitalTypeCode)
            || IdentityDocumentType.RESIDENT_ID_CARD.getCode().equals(hospitalTypeCode)) {
            return PsnCertType.ID_CARD.getCode();
        }
        if (IdentityDocumentType.HOUSEHOLD_REGISTER.getCode().equals(hospitalTypeCode)) {
            return "02";
        }
        if (IdentityDocumentType.PASSPORT.getCode().equalsIgnoreCase(hospitalTypeCode)) {
            return "03";
        }
        if (IdentityDocumentType.OFFICER_CERTIFICATE.getCode().equals(hospitalTypeCode)) {
            return "04";
        }
        if (IdentityDocumentType.DRIVING_LICENSE.getCode().equals(hospitalTypeCode)) {
            return "05";
        }
        if (IdentityDocumentType.HONGKONG_MACAO_PASS.getCode().equals(hospitalTypeCode)) {
            return "06";
        }
        if (IdentityDocumentType.TAIWAN_PASS.getCode().equals(hospitalTypeCode)) {
            return "07";
        }
        if (IdentityDocumentType.OTHER.getCode().equalsIgnoreCase(hospitalTypeCode)) {
            return "99";
        }
        // 已是平台两位数字码则原样
        if (hospitalTypeCode.matches("\\d{2}")) {
            return hospitalTypeCode;
        }
        return null;
    }
}
