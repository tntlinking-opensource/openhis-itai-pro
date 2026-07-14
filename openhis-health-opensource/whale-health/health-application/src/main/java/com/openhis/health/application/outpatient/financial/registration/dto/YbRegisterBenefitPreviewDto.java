/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.outpatient.financial.registration.dto;

import lombok.Data;
import lombok.experimental.Accessors;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/** 医保挂号待遇预览。 */
@Data
@Accessors(chain = true)
public class YbRegisterBenefitPreviewDto {

    private String psnNo;
    private String psnName;
    private String certno;
    private String gend;
    private String brdy;
    private String insuplcAdmdvs;
    private String insutype;
    private BigDecimal balc;
    private List<YbInsuInfoPreviewDto> insuinfoList = new ArrayList<>();
    /** 2001 待遇检查结果。 */
    private List<YbTreatmentInfoPreviewDto> treatmentInfoList = new ArrayList<>();
    /** 是否存在不享受待遇的基金项（trt_enjymnt_flag != 1）。 */
    private Boolean treatmentBlocked;
    /** 5301 慢特病备案列表。 */
    private List<YbOpspFilingPreviewDto> opspFilingList = new ArrayList<>();
    /** 参保地 vs 就医地对比提示。 */
    private YbRegisterBenefitAreaHintDto areaHint;
    /** 5206 人员累计信息。 */
    private List<YbCumulativeInfoPreviewDto> cumulativeInfoList = new ArrayList<>();
}
