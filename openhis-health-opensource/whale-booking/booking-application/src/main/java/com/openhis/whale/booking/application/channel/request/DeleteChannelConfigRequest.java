package com.openhis.whale.booking.application.channel.request;

import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class DeleteChannelConfigRequest {

    @NotNull
    private Long id;
}
