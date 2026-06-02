/**
 * 发布状态编码，与后端 {@code PublicationStatus#getCode()}（med_medication_definition.status_enum / MedicationDto.statusEnum）一致。
 */
export const PublicationStatusCode = {
  DRAFT: 'DRAFT',
  ACTIVE: 'ACTIVE',
  INACTIVE: 'INACTIVE',
  RETIRED: 'RETIRED',
  DISCARDED: 'DISCARDED',
} as const;

export type PublicationStatusCodeValue =
  (typeof PublicationStatusCode)[keyof typeof PublicationStatusCode];
