/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.campus.domain.publicexpenseprofile.service;

import com.zxplat.plugin.campus.domain.campuscollege.repository.CampusCollegeRepository;
import com.zxplat.plugin.campus.domain.campusdepartment.repository.CampusDepartmentRepository;
import com.zxplat.plugin.campus.domain.campussite.repository.CampusSiteRepository;
import com.zxplat.plugin.campus.domain.gfswitch.entity.GfSwitch;
import com.zxplat.plugin.campus.domain.gfswitch.repository.GfSwitchRepository;
import com.zxplat.plugin.campus.domain.patientstaff.repository.PatientStaffRepository;
import com.zxplat.plugin.campus.domain.patientstudent.entity.PatientStudent;
import com.zxplat.plugin.campus.domain.patientstudent.repository.PatientStudentRepository;
import com.zxplat.plugin.campus.domain.publicexpenseprofile.model.EligibilityResult;
import com.zxplat.plugin.campus.domain.publicexpenseprofile.repository.PublicExpenseProfileRepository;
import com.zxplat.plugin.campus.domain.shared.CampusDomainConstants;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.Mockito.when;

/**
 * 公费资格校验关键：无档案、总开关关闭。
 */
@ExtendWith(MockitoExtension.class)
class PublicExpenseEligibilityDomainServiceTest {

    @Mock
    private GfSwitchRepository gfSwitchRepository;
    @Mock
    private PublicExpenseProfileRepository profileRepository;
    @Mock
    private PatientStudentRepository studentRepository;
    @Mock
    private PatientStaffRepository staffRepository;
    @Mock
    private CampusCollegeRepository collegeRepository;
    @Mock
    private CampusSiteRepository campusSiteRepository;
    @Mock
    private CampusDepartmentRepository departmentRepository;

    @InjectMocks
    private PublicExpenseEligibilityDomainService eligibilityDomainService;

    @Test
    void check_failsWhenSubjectNotFound() {
        when(studentRepository.findByTenantIdAndPatientIdAndIsDeletedFalse(1L, 100L))
                .thenReturn(Optional.empty());
        when(staffRepository.findByTenantIdAndPatientIdAndIsDeletedFalse(1L, 100L))
                .thenReturn(Optional.empty());

        EligibilityResult result = eligibilityDomainService.check(1L, 100L);
        assertFalse(result.isPassed());
        assertEquals("SUBJECT_NOT_FOUND", result.getReasonCode());
    }

    @Test
    void check_failsWhenMasterSwitchOff() {
        PatientStudent student = new PatientStudent();
        student.setId(10L);
        student.setTenantId(1L);
        student.setActiveFlag(Boolean.TRUE);
        student.setCampusCode("MAIN");
        when(studentRepository.findByTenantIdAndPatientIdAndIsDeletedFalse(1L, 100L))
                .thenReturn(Optional.of(student));

        GfSwitch masterOff = new GfSwitch();
        masterOff.setSwitchValue("off");
        when(gfSwitchRepository.findByTenantIdAndSwitchKeyAndIsDeletedFalse(
                1L, CampusDomainConstants.GF_SWITCH_KEY_MASTER))
                .thenReturn(Optional.of(masterOff));

        EligibilityResult result = eligibilityDomainService.check(1L, 100L);
        assertFalse(result.isPassed());
        assertEquals("SWITCH_OFF", result.getReasonCode());
    }
}
