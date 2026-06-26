package com.openhis.whale.booking.application.healthcheckup.response;

import lombok.Getter;
import lombok.Setter;

/**
 * 团检名单 DTO（6.x 旧版 API 别名，对应 GroupCheckupRosterDto）。
 */
@Getter
@Setter
public class GroupRosterDto {

    /** 团检名单主键。 */
    private Long id;

    /** 团检批次主键。 */
    private Long batchId;

    /** 姓名。 */
    private String name;

    /** 脱敏身份证号。 */
    private String idCardNoMasked;

    /** 学号。 */
    private String studentNo;

    /** 脱敏手机号。 */
    private String phoneMasked;

    /** 预约状态展示文本。 */
    private String bookingStatusView;

    /** 预约业务流水号。 */
    private String appointmentBusNo;
}
