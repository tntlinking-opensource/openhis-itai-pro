package com.openhis.whale.booking.contracts.query;

import lombok.Getter;
import lombok.Setter;

/**
 * 患者检索入参（4.7）。
 */
@Getter
@Setter
public class BookingPatientSearchQuery {

    /** 关键字（姓名/证件/业务号）。 */
    private String keyword;

    /** 页码（1-based）。 */
    private Integer pageNo;

    /** 页大小。 */
    private Integer pageSize;
}
