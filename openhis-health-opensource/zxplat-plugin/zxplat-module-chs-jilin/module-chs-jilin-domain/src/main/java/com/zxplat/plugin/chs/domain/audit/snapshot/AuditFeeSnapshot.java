/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.chs.domain.audit.snapshot;

import lombok.Data;
import lombok.experimental.Accessors;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * 明细审核与费用上传共享的就诊费用快照（中性模型）。
 *
 * <p>由 ContextLoader 一次装载后，供 3101 树与 2204/2301 feedetail 投影，避免平行二次扫库。</p>
 */
@Data
@Accessors(chain = true)
public class AuditFeeSnapshot {

    /** HIS 就诊 ID */
    private Long encounterId;
    /** 医保就诊 ID */
    private String mdtrtId;
    /** 人员编号（作 patn_id） */
    private String psnNo;
    /** 姓名 */
    private String patnName;
    /** 性别码 */
    private String gend;
    /** 出生日期 yyyy-MM-dd */
    private String brdy;
    /** 统筹区 / 参保地 */
    private String poolarea;
    /** 险种 */
    private String insutype;
    /** 医疗类别 */
    private String medType;
    /** 就诊类型（门诊/住院） */
    private String medMdtrtType;
    /** 医疗费总额 */
    private BigDecimal medfeeSumamt;

    /** 机构编码 */
    private String medinsId;
    /** 机构名称 */
    private String medinsName;
    /** 机构医保区划 */
    private String medinsAdmdvs;
    /** 机构类型 */
    private String medinsType;
    /** 机构等级 */
    private String medinsLv;

    /** 入院/就诊时间 */
    private String admDate;
    /** 出院时间 */
    private String dscgDate;
    /** 主诊编码 */
    private String dscgMainDiseCodg;
    /** 主诊名称 */
    private String dscgMainDiseName;
    /** 医师编码 */
    private String drCodg;
    /** 医师姓名（统计冗余） */
    private String drName;
    /** 入院科室编码 */
    private String admDeptCodg;
    /** 入院科室名称 */
    private String admDeptName;
    /** 出院科室编码 */
    private String dscgDeptCodg;
    /** 出院科室名称 */
    private String dscgDeptName;

    /** 诊断行 */
    private List<AuditFeeDiagnoseLine> diagnoses = new ArrayList<>();
    /** 费用/医嘱行 */
    private List<AuditFeeOrderLine> orders = new ArrayList<>();
}
