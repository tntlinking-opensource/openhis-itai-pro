/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.application.outpatient.financial.registration.dto;

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
    /**
     * 2001 待遇检查提示文案（跨省失败「暂不可查」或省内失败说明）；空表示无提示。
     */
    private String treatmentCheckHint;
    /**
     * 为 true 时前端应对待遇检查失败弹警告（省内）；跨省静默场景为 false。
     */
    private Boolean treatmentCheckAlert;
    /** 慢特病备案列表（5301 主查，5302A 补充慢/特标签）。 */
    private List<YbOpspFilingPreviewDto> opspFilingList = new ArrayList<>();
    /** 参保地 vs 就医地对比提示。 */
    private YbRegisterBenefitAreaHintDto areaHint;
    /** 5206 人员累计信息。 */
    private List<YbCumulativeInfoPreviewDto> cumulativeInfoList = new ArrayList<>();

    /**
     * 1101 异地失败切省通道后的实际合同 bus_no；未切换时为 null。
     * 前端应用此合同拉取后续 2001/5301，避免仍走市通道。
     */
    private String resolvedContractBusNo;

    /**
     * 1101 异地失败切省通道后的实际费用性质 id；未切换时为 null。
     */
    private String resolvedContractId;
}
