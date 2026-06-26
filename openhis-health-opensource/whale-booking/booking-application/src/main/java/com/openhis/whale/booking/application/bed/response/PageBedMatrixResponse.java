package com.openhis.whale.booking.application.bed.response;

import lombok.Getter;
import lombok.Setter;

import java.util.List;

/**
 * 床位矩阵响应。
 */
@Getter
@Setter
public class PageBedMatrixResponse {

    /** 日期列头列表。 */
    private List<String> dates;

    /** 床位矩阵行列表。 */
    private List<BedMatrixRowDto> rows;
}
