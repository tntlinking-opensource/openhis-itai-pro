package com.openhis.whale.booking.contracts.query;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;

/** IF-REG-12：挂号台加载有效预约检索条件。 */
@Getter
@Setter
public class OutpatientBookingLookupQuery {

    /** 患者主键。 */
    private Long patientId;

    /** 预约单号（busNo）。 */
    private String appointmentBusNo;

    /** 挂号自然日；默认当日。 */
    private LocalDate registrationDate;
}
