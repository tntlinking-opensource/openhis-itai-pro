export {
  ENSURE_CLINICAL_RECEPTION_KEY,
  type EnsureClinicalReceptionFn,
} from './clinical-reception-context';
export { useEnsureClinicalReception } from './use-ensure-clinical-reception';

export { default as DiagnosisPickerDialog } from './diagnosis/components/diagnosis-picker-dialog.vue';
export { default as DiagnosisList } from './diagnosis/index.vue';
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

export { default as TcmAdvice } from './tcm-advice/tcm-advice.vue';
