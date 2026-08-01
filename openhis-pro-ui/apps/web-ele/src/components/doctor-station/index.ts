export {
  ENSURE_CLINICAL_RECEPTION_KEY,
  type EnsureClinicalReceptionFn,
} from './clinical-reception-context';
export { useEnsureClinicalReception } from './use-ensure-clinical-reception';

// 须经 #/ 导入，院点 overrides 才会在 Vite resolve 时生效（相对路径不会被重载）
export { default as DiagnosisPickerDialog } from '#/components/doctor-station/diagnosis/components/diagnosis-picker-dialog.vue';
export { default as DiagnosisList } from '#/components/doctor-station/diagnosis/index.vue';
export type {
  DiagnosisConfirmPayload,
  DiagnosisItem,
  DiagnosisMedicineType,
  DiagnosisScopeType,
  EncounterDiagnosisTabContext,
  EncounterDiagnosisTabData,
} from './diagnosis/services/diagnosis.types';
export {
  fetchEncounterDiagnosisTabData,
  isEncounterDiagnosisTabContextReady,
  type DoctorStationDiagnosisScope,
} from './diagnosis/services/encounter-diagnosis.service';

export { default as TcmAdvice } from '#/components/doctor-station/tcm-advice/tcm-advice-entry';
export { default as ElepAdvice } from './elep-advice/elep-advice.vue';
export { default as ReportQuery } from './report-query/index.vue';
