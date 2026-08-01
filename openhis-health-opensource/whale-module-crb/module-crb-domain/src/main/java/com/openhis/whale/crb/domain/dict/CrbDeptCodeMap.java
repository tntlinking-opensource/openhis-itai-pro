/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.whale.crb.domain.dict;

import com.openhis.whale.crb.domain.support.CrbTenantRowEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import static com.openhis.whale.core.application.util.StringUtils.trimToNull;

/**
 * 科室国家码对照（crb_dept_code_map）。
 */
@Entity
@Table(name = "crb_dept_code_map")
@Getter
@Setter
public class CrbDeptCodeMap extends CrbTenantRowEntity {

    /** 院内科室业务码。 */
    @Column(name = "location_bus_no", nullable = false, length = 64)
    private String locationBusNo;

    /** 院内科室名称（展示）。 */
    @Column(name = "location_name", length = 256)
    private String locationName;

    /** 国家科室码；空表示未对照。 */
    @Column(name = "target_dept_code", length = 64)
    private String targetDeptCode;

    /** 国家科室名称；与 targetDeptCode 成对。 */
    @Column(name = "target_dept_name", length = 256)
    private String targetDeptName;

    /**
     * 校验国家码与国家名称成对出现或同时为空。
     *
     * @param targetDeptCode 国家科室码
     * @param targetDeptName 国家科室名称
     */
    public static void assertCodeNamePair(String targetDeptCode, String targetDeptName) {
        String code = trimToNull(targetDeptCode);
        String name = trimToNull(targetDeptName);
        boolean codeBlank = code == null;
        boolean nameBlank = name == null;
        // 国家码与名称须同时有值或同时为空
        if (codeBlank != nameBlank) {
            throw new IllegalArgumentException(CrbDictConstants.MSG_CODE_NAME_PAIR_REQUIRED);
        }
    }

    /**
     * 是否已填写国家科室码（非空且非空白）。
     *
     * @return 已对照则 true
     */
    public boolean isMapped() {
        return trimToNull(targetDeptCode) != null;
    }
}
