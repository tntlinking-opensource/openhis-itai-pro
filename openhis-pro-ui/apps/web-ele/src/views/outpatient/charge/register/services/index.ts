export type {
  BookableSlotHintDto,
  CancelWithRefundResult,
  ChargeItemPreviewDto,
  ClinicRegisterItemDto,
  ListPractitionersParams,
  ListPractitionersResult,
  LoadRegisterFormInitParams,
  LoadRegisterFormInitResult,
  OptionDto,
  OutpatientBookingSnapshotDto,
  PatientItemDto,
  PayRegisterFeeParams,
  PayRegisterFeeResult,
  PreRegisterSettlementParams,
  PreRegisterSettlementResult,
  RegisterHistoryTableRow,
} from '#/api/outpatient-registration';

export {
  ENCOUNTER_CANCELABLE_STATUS_CODES,
  ENCOUNTER_STATUS_CODE,
  ENCOUNTER_STATUS_EL_TAG_MAP,
} from './encounter-status';

export { loadTodayRegisterSummary } from './register-stats.service';

export {
  cancelPlannedRegisterEncounter,
  changeRegistrationDepartment,
  cancelRegisterEncounter,
  DEFAULT_REGISTER_PAY_CHANNEL_CODE,
  listBookableSlotHints,
  listPractitioners,
  loadRegisterFormInit,
  pageRegisterHistoryForTable,
  pageRegisterPatientsForSelect,
  payRegisterFee,
  preRegisterFromAppointment,
  preRegisterSettlement,
  refreshRegisterFeeInsurancePreSettle,
} from './register.service';

export type {
  ConfirmDetailSection,
  ConfirmDetailSectionRow,
  RegisterBenefitDisplayPayload,
  RegisterBenefitPreviewContext,
  RegisterConfirmDetail,
  RegisterFormAreaExpose,
  RegisterHistoryExpose,
  RegisterPatientRecord,
  RegisterRightPanelExpose,
  RegisterSlotSelectionPayload,
} from './register.types';
