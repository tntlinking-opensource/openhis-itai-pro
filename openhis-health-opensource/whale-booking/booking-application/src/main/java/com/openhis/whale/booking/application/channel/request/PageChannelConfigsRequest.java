package com.openhis.whale.booking.application.channel.request;

import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class PageChannelConfigsRequest {

    private Long organizationId;
    private String keyword;

    @NotNull
    private Integer pageNo;

    @NotNull
    private Integer pageSize;
}
