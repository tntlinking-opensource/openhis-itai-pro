package com.openhis.whale.booking.application.outpatient.response;

import lombok.Getter;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
public class GenerateFromPractitionerTemplateResponse {

    private List<Long> scheduleIds = new ArrayList<>();
}
