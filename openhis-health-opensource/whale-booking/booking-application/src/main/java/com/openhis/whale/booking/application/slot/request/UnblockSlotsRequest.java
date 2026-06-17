package com.openhis.whale.booking.application.slot.request;

import jakarta.validation.constraints.NotEmpty;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

/**
 * 批量解封号源请求（3.3）。
 */
@Getter
@Setter
public class UnblockSlotsRequest {

    /** 号源主键列表。 */
    @NotEmpty
    private List<Long> slotIds;
}
