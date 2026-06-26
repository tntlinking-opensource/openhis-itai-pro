package com.openhis.whale.booking.application.register.response;

import lombok.Getter;
import lombok.Setter;

/**
 * 加入候补响应。
 */
@Getter
@Setter
public class JoinWaitlistResponse {

    /** 候补条目主键。 */
    private Long waitlistEntryId;

    /** 关联号源主键。 */
    private Long slotId;

    /** 当前排队位次（1-based）。 */
    private Integer queuePosition;

    /** 该号源候补队列总人数。 */
    private Long waitingCount;
}
