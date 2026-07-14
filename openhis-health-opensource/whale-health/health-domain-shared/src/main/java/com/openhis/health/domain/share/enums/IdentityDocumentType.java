/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.domain.share.enums;

/**
 * 身份证件类型枚举（患者/联系人身份核验与建档时的证件种类）。
 */
public enum IdentityDocumentType implements EnumInterface {
    /** 居民身份证（IdentityDocumentType，编码 {@code RESIDENT_ID_CARD}）。 */
    RESIDENT_ID_CARD(0L, "RESIDENT_ID_CARD", "居民身份证", "", 1L),
    /** 居民户口簿（IdentityDocumentType，编码 {@code HOUSEHOLD_REGISTER}）。 */
    HOUSEHOLD_REGISTER(0L, "HOUSEHOLD_REGISTER", "居民户口簿", "", 2L),
    /** 护照（IdentityDocumentType，编码 {@code passport}）。 */
    PASSPORT(0L, "passport", "护照", "", 3L),
    /** 军官证（IdentityDocumentType，编码 {@code OFFICER_CERTIFICATE}）。 */
    OFFICER_CERTIFICATE(0L, "OFFICER_CERTIFICATE", "军官证", "", 4L),
    /** 驾驶证（IdentityDocumentType，编码 {@code DRIVING_LICENSE}）。 */
    DRIVING_LICENSE(0L, "DRIVING_LICENSE", "驾驶证", "", 5L),
    /** 港澳居民来往内地通行证（IdentityDocumentType，编码 {@code HONGKONG_MACAO_PASS}）。 */
    HONGKONG_MACAO_PASS(0L, "HONGKONG_MACAO_PASS", "港澳居民来往内地通行证", "", 6L),
    /** 台湾居民来往内地通行证（IdentityDocumentType，编码 {@code TAIWAN_PASS}）。 */
    TAIWAN_PASS(0L, "TAIWAN_PASS", "台湾居民来往内地通行证", "", 7L),
    /** 其他身份证件（IdentityDocumentType，编码 {@code other}）。 */
    OTHER(0L, "other", "其他身份证件", "", 99L);

    private final Long codeSystemId;
    private final String code;
    private final String display;
    private final String definition;
    private final Long sortOrder;

    /**
     * 枚举构造：绑定编码系统、持久化 code、展示名与排序。
     */
    IdentityDocumentType(Long codeSystemId, String code, String display, String definition, Long sortOrder) {
        this.codeSystemId = codeSystemId;
        this.code = code;
        this.display = display;
        this.definition = definition;
        this.sortOrder = sortOrder;
    }

    public Long getCodeSystemId() {
        return codeSystemId;
    }

    public String getCode() {
        return code;
    }

    public String getDisplay() {
        return display;
    }

    public String getDefinition() {
        return definition;
    }

    public Long getSortOrder() {
        return sortOrder;
    }
}
