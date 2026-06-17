package com.openhis.whale.booking.application.register.response;

import lombok.Getter;
import lombok.Setter;

/**
 * 加入候补响应。
 */
@Getter
@Setter
public class JoinWaitlistResponse {

    private Long waitlistEntryId;

    private Long slotId;

    private Integer queuePosition;

    private Long waitingCount;
}
