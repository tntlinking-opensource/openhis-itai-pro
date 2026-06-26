/*
 * Copyright © 2026 OpenHIS Contributors
 */

package com.openhis.elep.domain.prescription.dto;

import lombok.Data;
import lombok.experimental.Accessors;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** 历史处方详情（含 snapshot 结构化字段）。 */
@Data
@Accessors(chain = true)
public class ElepHistoryDetailDto {

    private Long prescriptionId;
    private String hiRxno;
    private String hospRxno;
    private String initRxno;
    private String localStatus;
    private String rxStasCodg;
    private String rxUsedStasCodg;
    private String reptFlag;
    private Integer maxReptCnt;
    private Integer reptdCnt;
    private Instant valiEndTime;
    private Instant prscTime;
    private String prscDrName;
    private String mainDiagName;
    private String drugSummary;
    private String pdfFilePath;
    private boolean canContinuationRx;
    /** A0001 请求 JSON（再开真源）。 */
    private Map<String, Object> uploadRequest;
    private List<DrugLine> drugLines = new ArrayList<>();
    private DiagnosisInfo diagnosis;

    @Data
    @Accessors(chain = true)
    public static class DrugLine {

        private int lineNo;
        private String medListCodg;
        private String drugName;
        private String drugSpec;
    }

    @Data
    @Accessors(chain = true)
    public static class DiagnosisInfo {

        private String diagCode;
        private String diagName;
    }
}
