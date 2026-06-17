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

export type {
  ConfirmDetailSection,
  ConfirmDetailSectionRow,
  RegisterConfirmDetail,
  RegisterFormAreaExpose,
  RegisterHistoryExpose,
  RegisterPatientRecord,
} from './register.types';

export { ENCOUNTER_STATUS_CODE, ENCOUNTER_STATUS_EL_TAG_MAP } from './encounter-status';

export {
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
} from './register.service';
