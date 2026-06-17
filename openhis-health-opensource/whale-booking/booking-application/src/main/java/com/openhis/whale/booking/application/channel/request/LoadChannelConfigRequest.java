package com.openhis.whale.booking.application.channel.request;

import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class LoadChannelConfigRequest {

    @NotNull
    private Long id;
}
