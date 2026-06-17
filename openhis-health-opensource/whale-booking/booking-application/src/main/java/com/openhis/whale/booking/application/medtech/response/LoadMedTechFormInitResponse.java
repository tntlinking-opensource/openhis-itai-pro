package com.openhis.whale.booking.application.medtech.response;

import com.openhis.whale.core.application.dto.OptionDto;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

/**
 * 医技预约台初始化响应（7.1）。
 */
@Getter
@Setter
public class LoadMedTechFormInitResponse {

    private List<OptionDto> clinicLocations;

    private List<OptionDto> healthcareServices;
}
