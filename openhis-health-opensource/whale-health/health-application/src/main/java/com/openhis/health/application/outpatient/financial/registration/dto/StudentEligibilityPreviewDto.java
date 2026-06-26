/*
 * Copyright © 2026 OpenHIS Contributors
 */

package com.openhis.health.application.outpatient.financial.registration.dto;

import com.openhis.health.domain.administration.patient.queries.model.StudentPublicExpenseEligibilityQueryModel;
import lombok.Data;
import lombok.experimental.Accessors;

/** 学生公费资格预览。 */
@Data
@Accessors(chain = true)
public class StudentEligibilityPreviewDto {

    private Boolean eligible;
    private String reasonCode;
    private String reasonMessage;
    private Long patientId;
    private Long studentId;

    public static StudentEligibilityPreviewDto from(StudentPublicExpenseEligibilityQueryModel model) {
        StudentEligibilityPreviewDto dto = new StudentEligibilityPreviewDto();
        dto.setEligible(model.getEligible());
        dto.setReasonCode(model.getReasonCode());
        dto.setReasonMessage(model.getReasonMessage());
        dto.setPatientId(model.getPatientId());
        dto.setStudentId(model.getStudentId());
        return dto;
    }
}
