/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.elep.domain.prescription.result;

import lombok.Data;
import lombok.experimental.Accessors;

/**
 * 电子处方页只读就诊上下文。
 *
 * <p>不写 HIS/CHS；{@code allowed=false} 时前端禁止开方。</p>
 */
@Data
@Accessors(chain = true)
public class ElepEncounterContextResult {

    /** 是否允许开电子处方。 */
    private boolean allowed;

    /** 拦截原因（自费、缺 mdtrt_id、缺医师/科室医保码等）。 */
    private String blockReason;

    /** 就诊主键。 */
    private Long encounterId;

    /** 患者主键。 */
    private Long patientId;

    /** 门诊号 / 就诊业务号。 */
    private String encounterBusNo;

    /** 患者姓名。 */
    private String patientName;

    /** HIS 性别 code（MALE/FEMALE）。 */
    private String genderEnum;

    /** 平台性别 gend（1/2/9）。 */
    private String gend;

    /** 年龄（岁，整数串）。 */
    private String patnAge;

    /** 身份证号。 */
    private String idCard;

    /** 就诊科室名称。 */
    private String deptName;

    /** 就诊科室医保码 {@code adm_organization.yb_no}。 */
    private String deptYbNo;

    /** 开方医师姓名。 */
    private String doctorName;

    /** 开方医师国家医保码 {@code adm_practitioner.yb_no}。 */
    private String doctorYbNo;

    /** 医师职称编码。 */
    private String doctorTitleCode;

    /** 医师职称名称。 */
    private String doctorTitleName;

    /** 医师所属科室医保码。 */
    private String doctorDeptYbNo;

    /** 医师所属科室名称。 */
    private String doctorDeptName;

    /** 挂号 map(11) 的医保就诊号。 */
    private String mdtrtId;

    /** 挂号 map(11) 的人员编号。 */
    private String psnNo;

    /** 门诊/住院号。 */
    private String iptOtpNo;

    /** 就诊凭证类型（缺省身份证号 02）。 */
    private String mdtrtCertType;

    /** 就诊凭证编号。 */
    private String mdtrtCertNo;

    /** 人员证件类型。 */
    private String psnCertType;

    /** 门诊住院标识。 */
    private String otpIptFlag;

    /** 就诊时间（yyyy-MM-dd HH:mm:ss）。 */
    private String mdtrtTime;

    /** 定点机构编码（elep-contract）。 */
    private String fixmedinsCode;

    /** 定点机构名称。 */
    private String fixmedinsName;

    /**
     * 挂号 map 对应费用性质 bus_no。
     *
     * <p>电子处方不区分省医保/市医保/工伤，由本次医保挂号契约决定，前端不选手选。</p>
     */
    private String contractBusNo;

    /** 挂号 map 对应费用性质主键。 */
    private Long contractId;
}
