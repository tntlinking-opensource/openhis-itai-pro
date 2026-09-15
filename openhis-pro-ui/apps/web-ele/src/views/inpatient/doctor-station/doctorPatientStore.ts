import { computed, ref } from 'vue';

import { defineStore } from 'pinia';

import type { InpatientDoctorStationPatientDto } from '#/api/inpatient/doctor-station/inpatient-doctor-station-encounter.types';
import type { PatientInfoBarPayload } from '#/components/outpatient/patient-card.types';

/** 住院医生站侧栏/当前患者：与 IF-IP-DOR-01 列表行 DTO 一致 */
export type InpatientDoctorStationPatient = InpatientDoctorStationPatientDto;

export const useInpatientDoctorPatientStore = defineStore('inpatientDoctorStationPatient', () => {
  const selectedPatient = ref<InpatientDoctorStationPatient | null>(null);

  /** 写入当前选中在院患者 */
  const setSelectedPatient = (patient: InpatientDoctorStationPatient | null) => {
    selectedPatient.value = patient;
  };

  const currentPatientInfo = computed<PatientInfoBarPayload>(() => {
    const p = selectedPatient.value;
    if (!p) {
      return {
        encounterId: '',
        patientName: '',
        genderEnumText: undefined,
        ageText: '',
        phone: '',
        medicalRecordNo: '',
        idCard: '',
        serviceTypeName: '',
        startTime: '',
        statusEnum: undefined,
      };
    }
    const wardBed = [(p.wardName ?? '').trim(), (p.bedName ?? '').trim()].filter(Boolean).join(' ');
    return {
      encounterId: p.encounterId,
      patientName: p.patientName,
      genderEnumText: p.genderEnumText,
      ageText: p.ageText,
      phone: '',
      medicalRecordNo: (p.encounterBusNo ?? '').trim(),
      idCard: (p.idCard ?? '').trim(),
      serviceTypeName: wardBed || (p.organizationName ?? '').trim(),
      startTime: p.admissionTime ?? p.startTime,
      statusEnum: p.statusEnum,
    };
  });

  return {
    selectedPatient,
    setSelectedPatient,
    currentPatientInfo,
  };
});
