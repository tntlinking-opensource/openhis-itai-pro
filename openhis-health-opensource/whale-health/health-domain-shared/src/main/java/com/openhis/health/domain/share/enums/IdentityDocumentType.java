/*
 * Copyright © 2026 OpenHIS Contributors
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 3 of the License, or (at your option) any later version.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library.  If not, see <https://www.gnu.org/licenses/>.
 */

package com.openhis.health.domain.share.enums;

/**
 * 身份证件类型枚举。
 */
public enum IdentityDocumentType implements EnumInterface {
    /**
     * RESIDENT_ID_CARD。
     */
    RESIDENT_ID_CARD(0L, "RESIDENT_ID_CARD", "居民身份证", "", 1L),
    /**
     * HOUSEHOLD_REGISTER。
     */
    HOUSEHOLD_REGISTER(0L, "HOUSEHOLD_REGISTER", "居民户口簿", "", 2L),
    /**
     * PASSPORT。
     */
    PASSPORT(0L, "passport", "护照", "", 3L),
    /**
     * OFFICER_CERTIFICATE。
     */
    OFFICER_CERTIFICATE(0L, "OFFICER_CERTIFICATE", "军官证", "", 4L),
    /**
     * DRIVING_LICENSE。
     */
    DRIVING_LICENSE(0L, "DRIVING_LICENSE", "驾驶证", "", 5L),
    /**
     * HONGKONG_MACAO_PASS。
     */
    HONGKONG_MACAO_PASS(0L, "HONGKONG_MACAO_PASS", "港澳居民来往内地通行证", "", 6L),
    /**
     * TAIWAN_PASS。
     */
    TAIWAN_PASS(0L, "TAIWAN_PASS", "台湾居民来往内地通行证", "", 7L),
    /**
     * OTHER。
     */
    OTHER(0L, "other", "其他身份证件", "", 99L);

    private final Long codeSystemId;
    private final String code;
    private final String display;
    private final String definition;
    private final Long sortOrder;

    /**
     * IdentityDocumentType。
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
