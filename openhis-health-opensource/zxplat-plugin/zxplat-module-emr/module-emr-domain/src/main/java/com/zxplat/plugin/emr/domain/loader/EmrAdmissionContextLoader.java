/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.emr.domain.loader;

import java.util.Map;

/**
 * 向 EMR 元数据解析上下文贡献患者/就诊等外部数据（由 HIS bridge 实现）。
 *
 * <p>键名与 EMR 元数据字段 {@code source_path} 对齐；诊断类键由 bridge 贡献器补充。</p>
 */
public interface EmrAdmissionContextLoader {

    /** 贡献患者主数据键值（如 {@code patient.name}）。 */
    void contributePatient(Map<String, String> target, String patientId);

    /** 贡献就诊主数据键值（如 {@code encounter.admitDate}）。 */
    void contributeEncounter(Map<String, String> target, String encounterId);

    /** 贡献医护主数据键值。 */
    void contributePractitioner(Map<String, String> target, Long practitionerId);

    /**
     * 贡献机构/医院名称（如 {@code org.name}）。
     * <p>自给定组织向上回溯至医院节点；若上下文已有 {@code org.name} 则可不覆盖。</p>
     */
    default void contributeOrganization(Map<String, String> target, Long organizationId) {
        // 默认无实现；HIS bridge 覆盖
    }
}
