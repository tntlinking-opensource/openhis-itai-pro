/*
 * Copyright © 2026 OpenHIS Contributors
 */

package com.openhis.health.integration.insurance.benefit.result;

import lombok.Data;
import lombok.experimental.Accessors;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/** 参保人信息查询结果（平台 1101 语义的中性封装）。 */
@Data
@Accessors(chain = true)
public class InsurancePersonQueryResult {

    private String psnNo;
    private String insuplcAdmdvs;
    private String insutype;
    private BigDecimal balc;
    private String psnName;
    private String certno;
    private String gend;
    private String brdy;
    private List<InsuranceCoverageLineSnapshot> coverageLines = new ArrayList<>();
    private List<InsuranceIdentityLineSnapshot> identityLines = new ArrayList<>();
}
