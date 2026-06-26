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

    /** 床位地点主键。 */
    private Long bedLocationId;

    /** 床位名称。 */
    private String bedName;

    /** 矩阵单元格列表。 */
    private List<BedMatrixCellDto> cells;
}
