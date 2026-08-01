export {
  clearDataElementRegistry,
  getDataElementDef,
  registerDataElement,
  unregisterDataElement,
} from './components/simple-editor/data-element-registry';
export type {
  DataElementDef,
  EmrDataElementWidget,
} from './components/simple-editor/data-element-types';
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
export { normalizeEmrRootDoc, parseEmrContentToDoc } from './document-content';
export { findTableByName, getTableCellTextByName, listNamedTableCells } from './table-by-name';
export type { ApplyEmrTemplatePayload, PatientInfoForEmrMetadata } from './emr-patient-metadata';
export {
  applyMetadataValuesToContentJson,
  buildMetadataValuesFromPatientInfo,
  mergeEmrMetadataValues,
} from './emr-patient-metadata';
export type { EmrMetadataField } from './types';
