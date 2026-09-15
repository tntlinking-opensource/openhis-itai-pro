export {
  clearDataElementRegistry,
  getDataElementDef,
  registerDataElement,
  unregisterDataElement,
} from './components/simple-editor/data-element-registry';
export {
  collectRequiredViolations,
  validateRequiredFillables,
} from './components/simple-editor/emr-required-validate';
export type { EmrRequiredViolation } from './components/simple-editor/emr-required-validate';
export type {
  DataElementDef,
  EmrDataElementWidget,
  EmrWidgetOptionItem,
} from './components/simple-editor/data-element-types';
export {
  EMR_WIDGET_OPTION_PRESETS,
  parseEmrWidgetOptions,
  serializeEmrWidgetOptions,
} from './components/simple-editor/emr-widget-options';
export { EmrDataElement } from './components/simple-editor/extensions/emr-data-element';
export type { InsertEmrDataElementAttrs } from './components/simple-editor/extensions/emr-data-element';
export {
  extractDataElementsFromDoc,
  extractDataElementsFromEditor,
} from './components/simple-editor/extract-data-elements';
export type { ExtractedDataElementRow } from './components/simple-editor/extract-data-elements';
export { getSimpleEditorExtensions } from './components/simple-editor/get-simple-editor-extensions';
export { default as SimpleEditor } from './components/simple-editor/SimpleEditor.vue';
export type { DraftRecord, EmrSaveStatus } from './components/simple-editor/use-emr-auto-save';
export { useEmrAutoSave } from './components/simple-editor/use-emr-auto-save';
export {
  beginForceLogoutSession,
  confirmEmrDiscardUnsaved,
  isForceLogoutSession,
  registerEmrUnsavedGuard,
} from './use-emr-unsaved-guard';
export type { UseEmrMetadataOptions } from './composables/use-emr-metadata';
export { useEmrMetadata } from './composables/use-emr-metadata';
export {
  normalizeEmrRootDoc,
  parseEmrContentToDoc,
  scrubEmptyTextNodes,
  stripEmrPageBreaks,
  stripLeadingEmrPageBreaks,
} from './document-content';
export { findTableByName, getTableCellTextByName, listNamedTableCells } from './table-by-name';
export type { ApplyEmrTemplatePayload, PatientInfoForEmrMetadata } from './emr-patient-metadata';
export {
  applyDiagnosisMetadataToContentJson,
  applyMetadataValuesToContentJson,
  buildMetadataValuesFromPatientInfo,
  CONTEXT_REFRESH_FIELD_KEYS,
  EMR_DIAGNOSIS_METADATA_FIELD_KEYS,
  mergeEmrMetadataValues,
  PATIENT_BOUND_FIELD_KEYS,
  resolveFillableMetadataKey,
  stripDocumentStampMetadataFromContentJson,
  stripPatientBoundMetadataFromContentJson,
} from './emr-patient-metadata';
export type {
  ApplyMetadataValuesOptions,
  EmrDiagnosisMetadataFieldKey,
} from './emr-patient-metadata';
export type { EmrMetadataField } from './types';
