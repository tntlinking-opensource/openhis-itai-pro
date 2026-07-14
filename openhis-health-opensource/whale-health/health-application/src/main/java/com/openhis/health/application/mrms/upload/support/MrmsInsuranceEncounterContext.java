/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.mrms.upload.support;

import lombok.Getter;
import lombok.Setter;

/**
 * MRMS 4401 组装用医保就诊上下文（由 {@link MrmsInsuranceEncounterLoader} 填充）。
 */
@Getter
@Setter
public class MrmsInsuranceEncounterContext {

    /** 医保就诊 ID（4401 baseinfo.mdtrt_id）。 */
    private String mdtrtId;

    /** 医保人员编号（4401 baseinfo.psn_no）。 */
    private String psnNo;

    /** 就诊流水号（4401 mdtrt_sn，部分场景由 fixmedins+iptNo 拼接）。 */
    private String mdtrtSn;

    /** 定点医药机构编号。 */
    private String fixmedinsCode;

    /** 定点医药机构名称。 */
    private String fixmedinsName;

    /** 参保地医保区划。 */
    private String insuplcAdmdvs;
}
