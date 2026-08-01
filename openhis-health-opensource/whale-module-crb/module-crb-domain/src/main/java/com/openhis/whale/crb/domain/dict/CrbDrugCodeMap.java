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
 * 药品国家码对照（crb_drug_code_map）。
 */
@Entity
@Table(name = "crb_drug_code_map")
@Getter
@Setter
public class CrbDrugCodeMap extends CrbTenantRowEntity {

    /** 院内药品业务码。 */
    @Column(name = "med_bus_no", nullable = false, length = 64)
    private String medBusNo;

    /** 院内药品名称（展示）。 */
    @Column(name = "med_name", length = 256)
    private String medName;

    /** 国家药品码；空表示未对照。 */
    @Column(name = "national_drug_code", length = 64)
    private String nationalDrugCode;

    /** 国家药品名称；与 nationalDrugCode 成对。 */
    @Column(name = "national_drug_name", length = 256)
    private String nationalDrugName;

    /**
     * 校验国家码与国家名称成对出现或同时为空。
     *
     * @param nationalDrugCode 国家药品码
     * @param nationalDrugName 国家药品名称
     */
    public static void assertCodeNamePair(String nationalDrugCode, String nationalDrugName) {
        String code = trimToNull(nationalDrugCode);
        String name = trimToNull(nationalDrugName);
        boolean codeBlank = code == null;
        boolean nameBlank = name == null;
        // 国家码与名称须同时有值或同时为空
        if (codeBlank != nameBlank) {
            throw new IllegalArgumentException(CrbDictConstants.MSG_CODE_NAME_PAIR_REQUIRED);
        }
    }

    /**
     * 是否已填写国家药品码（非空且非空白）。
     *
     * @return 已对照则 true
     */
    public boolean isMapped() {
        return trimToNull(nationalDrugCode) != null;
    }
}
