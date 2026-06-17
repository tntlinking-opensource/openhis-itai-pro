package com.openhis.whale.booking.application.bed.response;

import lombok.Getter;
import lombok.Setter;

/**
 * 床位矩阵单元格 DTO。
 */
@Getter
@Setter
public class BedMatrixCellDto {

    private Long bedLocationId;
    private String bedName;
    private String date;
    private String operationalCode;
    private String displayStatus;
    private Long appointmentId;
    private String patientName;
}
