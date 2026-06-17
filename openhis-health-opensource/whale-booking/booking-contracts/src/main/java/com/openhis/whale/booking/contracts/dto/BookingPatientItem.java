package com.openhis.whale.booking.contracts.dto;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;

/**
 * 患者检索选项（4.7）。
 */
@Getter
@Setter
public class BookingPatientItem {

    /** 患者主键。 */
    private Long patientId;

    /** 患者业务号。 */
    private String patientBusNo;

    /** 姓名。 */
    private String name;

    /** 性别编码。 */
    private String genderCode;

    /** 出生日期。 */
    private LocalDate birthDate;

    /** 脱敏证件号。 */
    private String idCardNoMasked;

    /** 脱敏手机号。 */
    private String phoneMasked;
}
