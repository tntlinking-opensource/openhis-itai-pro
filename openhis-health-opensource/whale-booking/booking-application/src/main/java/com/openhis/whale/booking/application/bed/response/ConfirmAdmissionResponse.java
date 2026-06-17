package com.openhis.whale.booking.application.bed.response;

import lombok.Getter;
import lombok.Setter;

/**
 * 入院确认响应（8.5）。
 */
@Getter
@Setter
public class ConfirmAdmissionResponse {

    private Long appointmentId;

    private Long bedLocationId;
}
