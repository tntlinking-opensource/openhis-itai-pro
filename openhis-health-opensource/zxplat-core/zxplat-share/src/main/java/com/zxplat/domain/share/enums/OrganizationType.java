/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.domain.share.enums;

/**
 * 机构层级类型（组织树节点类型）。
 *
 * <p>仅区分「医院」与「科室」两级组织形态：医院可为根或上级节点，科室挂载于医院之下。
 * 门诊/住院/药房等业务属性见 {@link OrganizationClassification}（{@code classEnum}）。</p>
 */
public enum OrganizationType implements EnumInterface {
    /**
     * 医院（组织上级节点，如总部、分院）。
     */
    HOSPITAL(0L, "HOSPITAL", "医院", "", 1L),
    /**
     * 科室（组织下级节点，归属医院）。
     */
    DEPARTMENT(0L, "DEPARTMENT", "科室", "", 2L);

    private final Long codeSystemId;
    private final String code;
    private final String display;
    private final String definition;
    private final Long sortOrder;

    OrganizationType(Long codeSystemId, String code, String display, String definition, Long sortOrder) {
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

    /**
     * 解析请求或库表中的层级类型：支持领域 code，或页面 sortOrder 数字字符串（1/2）。
     *
     * @param raw code 或 sortOrder 数字字符串
     * @return 层级类型或 null
     */
    public static OrganizationType resolveRaw(String raw) {
        if (raw == null || raw.trim().isEmpty()) {
            return null;
        }
        String normalized = raw.trim();
        for (OrganizationType item : values()) {
            if (item.code.equals(normalized)) {
                return item;
            }
        }
        try {
            long sortOrder = Long.parseLong(normalized);
            for (OrganizationType item : values()) {
                if (item.sortOrder != null && item.sortOrder.longValue() == sortOrder) {
                    return item;
                }
            }
        } catch (NumberFormatException ex) {
            return null;
        }
        return null;
    }

    /**
     * 判断是否为医院层级类型。
     */
    public static boolean isHospital(String typeCode) {
        return HOSPITAL == resolveRaw(typeCode);
    }
}
