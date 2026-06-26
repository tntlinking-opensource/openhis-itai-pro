/*
 * Copyright © 2026 OpenHIS Contributors
 */

package com.openhis.elep.domain.prescription.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;

import java.math.BigDecimal;
import java.util.Date;
import java.util.List;

/** PDF 模板填充字段（对齐 ybapp {@code PDFInputDto}）。 */
@Data
@Accessors(chain = true)
public class ElepPdfInputDto {

    private String rxTraceCode;
    private String orgName;
    private String iptOtpNo;
    private String prescriptionNo;
    private String locationName;
    private String patnName;
    private String gender;
    private BigDecimal patnAge;
    private String hiFeesetlType;
    private Date prscTime;
    private String diagName;
    private BigDecimal valiDays;
    private String prscDrName;
    private String pharName;
    private String disRevPharName;
    private String checkPharName;
    private String reason;
    private List<MedDetail> medDetailList;

    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    public static class MedDetail {

        private String medName;
        private String drugSpec;
        private String sinDoscnt;
        private String sinDosunt;
        private String usedFrquCodg;
        private String medWay;
    }
}
