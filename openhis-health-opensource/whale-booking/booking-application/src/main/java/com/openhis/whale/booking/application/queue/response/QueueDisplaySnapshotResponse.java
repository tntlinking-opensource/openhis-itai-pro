package com.openhis.whale.booking.application.queue.response;

import lombok.Getter;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
public class QueueDisplaySnapshotResponse {

    private String displayTitle;
    private Integer refreshSeconds;
    private CallToSeeResponse currentCall;
    private List<QueueListItemDto> waitingList = new ArrayList<>();
}
