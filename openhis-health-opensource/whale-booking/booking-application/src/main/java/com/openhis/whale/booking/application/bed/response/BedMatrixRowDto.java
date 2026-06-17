package com.openhis.whale.booking.application.bed.response;

import lombok.Getter;
import lombok.Setter;

import java.util.List;

/**
 * 床位矩阵行 DTO。
 */
@Getter
@Setter
public class BedMatrixRowDto {

    private Long bedLocationId;
    private String bedName;
    private List<BedMatrixCellDto> cells;
}
