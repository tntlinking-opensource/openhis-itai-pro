package com.openhis.whale.booking.contracts.dto;

import lombok.Getter;
import lombok.Setter;

/** 可约号源类型选项（IF-REG-01 收窄）。 */
@Getter
@Setter
public class BookableServiceOption {

    private Long healthcareServiceId;
    private String serviceTypeName;
}
