import type { OutpatientInfoItemDto } from '#/api/doctor-station/encounter.types';
import type { PatientInfoBarPayload } from '#/components/outpatient/patient-card.types';

import { computed, ref } from 'vue';

import { defineStore } from 'pinia';

/** 医生站侧栏/当前患者：与 IF-DOR-01 列表行 DTO 一致 */
export type DoctorStationPatient = OutpatientInfoItemDto;

export const useDoctorPatientStore = defineStore('doctorStationPatient', () => {
  const selectedPatient = ref<DoctorStationPatient | null>(null);

  const setSelectedPatient = (patient: DoctorStationPatient | null) => {
    selectedPatient.value = patient;
  };

  const currentPatientInfo = computed<PatientInfoBarPayload>(() => {
    const p = selectedPatient.value;
    if (!p) {
      return {
        encounterId: '',
        patientName: '',
        genderEnumText: undefined,
        genderEnum: undefined,
        ageText: '',
        phone: '',
        medicalRecordNo: '',
        idCard: '',
        address: '',
        serviceTypeName: '',
        startTime: '',
        statusEnum: undefined,
      };
    }
    return {
      encounterId: p.encounterId,
      patientName: p.patientName,
      genderEnumText: p.genderEnumText,
      genderEnum: p.genderEnum,
      ageText: p.ageText,
      phone: p.phone,
      medicalRecordNo: (p.encounterBusNo ?? '').trim() || (p.encounterId ?? '').trim(),
      idCard: (p.idCard ?? '').trim(),
      address: (p.address ?? '').trim(),
      serviceTypeName: p.serviceTypeName,
      startTime: p.startTime,
      statusEnum: p.statusEnum,
    };
  });

  return {
    selectedPatient,
    setSelectedPatient,
    currentPatientInfo,
  };
});
