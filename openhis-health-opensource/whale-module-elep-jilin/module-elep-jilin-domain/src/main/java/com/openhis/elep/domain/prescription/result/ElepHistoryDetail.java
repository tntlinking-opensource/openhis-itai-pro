/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.elep.domain.prescription.result;

import lombok.Data;
import lombok.experimental.Accessors;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** 历史处方详情（含 snapshot 结构化字段）。 */
@Data
@Accessors(chain = true)
public class ElepHistoryDetail {

    /** 电子处方主键。 */
    private Long prescriptionId;
    /** 医保处方号。 */
    private String hiRxno;
    /** 院内处方号。 */
    private String hospRxno;
    /** 续方源医保处方号。 */
    private String initRxno;
    /** 本地状态机 code。 */
    private String localStatus;
    /** 平台处方状态码。 */
    private String rxStasCodg;
    /** 平台使用状态码。 */
    private String rxUsedStasCodg;
    /** 复用标志。 */
    private String reptFlag;
    /** 最大复用次数。 */
    private Integer maxReptCnt;
    /** 已复用次数。 */
    private Integer reptdCnt;
    /** 有效截止时间。 */
    private Instant valiEndTime;
    /** 开方时间。 */
    private Instant prscTime;
    /** 开方医师姓名。 */
    private String prscDrName;
    /** 主诊断名称。 */
    private String mainDiagName;
    /** 药品摘要。 */
    private String drugSummary;
    /** 本地 PDF 路径。 */
    private String pdfFilePath;
    /** 是否允许续方（本期前端隐藏入口）。 */
    private boolean canContinuationRx;
    /** 本方医疗类别。 */
    private String medType;
    /** 慢病病种编码。 */
    private String diseCodg;
    /** 慢病病种名称。 */
    private String diseName;
    /** 有效天数。 */
    private String valiDays;
    /** 审方药师医保码；非空表示已审方。 */
    private String pharCode;
    /** 审方药师姓名。 */
    private String pharName;
    /** 挂号 map(11) 就诊号。 */
    private String mdtrtId;
    /** A0001 请求 JSON（再开真源）。 */
    private Map<String, Object> uploadRequest;
    private List<DrugLine> drugLines = new ArrayList<>();
    private DiagnosisInfo diagnosis;

    @Data
    @Accessors(chain = true)
    public static class DrugLine {

        /** 行号。 */
        private int lineNo;
        /** 医保药品编码。 */
        private String medListCodg;
        /** 药品名称。 */
        private String drugName;
        /** 规格。 */
        private String drugSpec;
        /** 剂型。 */
        private String drugDosform;
        /** 总量。 */
        private String drugCnt;
        /** 总量单位。 */
        private String drugDosunt;
        /** 用药途径代码。 */
        private String medcWayCodg;
        /** 用药途径。 */
        private String medcWayDscr;
        /** 用药天数。 */
        private String medcDays;
        /** 单次剂量。 */
        private String sinDoscnt;
        /** 单次剂量单位。 */
        private String sinDosunt;
        /** 频次编码。 */
        private String usedFrquCodg;
        /** 频次名称。 */
        private String usedFrquName;
        /** 用药开始时间。 */
        private String medcBegntime;
        /** 用药结束时间。 */
        private String medcEndtime;
        /** 生产厂家。 */
        private String prdrName;
        /** 药品商品名。 */
        private String drugProdname;
    }

    @Data
    @Accessors(chain = true)
    public static class DiagnosisInfo {

        /** 医保诊断编码。 */
        private String diagCode;
        /** 医保诊断名称。 */
        private String diagName;
    }
}
