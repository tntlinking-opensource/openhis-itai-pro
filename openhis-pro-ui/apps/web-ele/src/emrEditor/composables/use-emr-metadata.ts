import type { MaybeRefOrGetter } from 'vue';

import type { PatientInfoForEmrMetadata } from '#/emrEditor/emr-patient-metadata';
import type { EmrMetadataField } from '#/emrEditor/types';

import { computed, ref, toValue, watch } from 'vue';

import { getEnabledMetadataFieldListApi, mapMetadataFieldDtoToEmr } from '#/api/emr/metadata-field';
import { resolveEncounterMetadataApi } from '#/api/emr/encounter-metadata';
import {
  buildMetadataValuesFromPatientInfo,
  mergeEmrMetadataValues,
} from '#/emrEditor/emr-patient-metadata';

export interface UseEmrMetadataOptions {
  patientId?: MaybeRefOrGetter<string | undefined>;
  encounterId?: MaybeRefOrGetter<string | undefined>;
  documentId?: MaybeRefOrGetter<string | undefined>;
  patientInfo?: MaybeRefOrGetter<PatientInfoForEmrMetadata | null | undefined>;
  /** 拉取启用中的字段目录（模板侧栏） */
  loadFieldDefinitions?: boolean;
  /** 按就诊解析 fieldKey → value（文书模式） */
  loadValues?: boolean;
}

function localFallbackValues(
  patientInfo: PatientInfoForEmrMetadata | null | undefined,
): Record<string, string> {
  return buildMetadataValuesFromPatientInfo(patientInfo ?? undefined);
}

/**
 * 元数据字段目录 + 就诊上下文取值（数据来自 metadataField / encounterMetadata 接口）。
 */
export function useEmrMetadata(options: UseEmrMetadataOptions = {}) {
  const loadFieldDefinitions = options.loadFieldDefinitions !== false;
  const loadValues = options.loadValues !== false;

  const metadataFields = ref<EmrMetadataField[]>([]);
  const metadataValues = ref<Record<string, string>>({});
  const fieldsLoading = ref(false);
  const valuesLoading = ref(false);
  const fieldsError = ref<string | null>(null);
  const valuesError = ref<string | null>(null);

  const resolvedPatientId = computed(() => {
    const id = toValue(options.patientId);
    return id === null || id === undefined ? '' : String(id).trim();
  });

  const resolvedEncounterId = computed(() => {
    const id = toValue(options.encounterId);
    return id === null || id === undefined ? '' : String(id).trim();
  });

  const resolvedDocumentId = computed(() => {
    const id = toValue(options.documentId);
    if (id === null || id === undefined || id === '') return undefined;
    const n = typeof id === 'number' ? id : Number(id);
    return Number.isFinite(n) ? n : undefined;
  });

  async function fetchFieldDefinitions() {
    if (!loadFieldDefinitions) return;
    fieldsLoading.value = true;
    fieldsError.value = null;
    try {
      const list = await getEnabledMetadataFieldListApi();
      metadataFields.value = Array.isArray(list) ? list.map(mapMetadataFieldDtoToEmr) : [];
    } catch (e) {
      fieldsError.value = e instanceof Error ? e.message : '加载元数据字段失败';
      metadataFields.value = [];
    } finally {
      fieldsLoading.value = false;
    }
  }

  async function fetchMetadataValues() {
    if (!loadValues) return;

    const patientId = resolvedPatientId.value;
    const patientInfo = toValue(options.patientInfo);

    if (!patientId) {
      metadataValues.value = localFallbackValues(patientInfo);
      return;
    }

    valuesLoading.value = true;
    valuesError.value = null;
    try {
      const dto = await resolveEncounterMetadataApi({
        patientId,
        encounterId: resolvedEncounterId.value || undefined,
        documentId: resolvedDocumentId.value,
      });
      const fromApi = dto?.values;
      if (fromApi && Object.keys(fromApi).length > 0) {
        // 服务端空串不覆盖患者条本地已有电话/医保等
        metadataValues.value = mergeEmrMetadataValues(localFallbackValues(patientInfo), fromApi);
      } else {
        metadataValues.value = localFallbackValues(patientInfo);
      }
    } catch (e) {
      valuesError.value = e instanceof Error ? e.message : '解析元数据失败';
      metadataValues.value = localFallbackValues(patientInfo);
    } finally {
      valuesLoading.value = false;
    }
  }

  async function refresh() {
    await Promise.all([fetchFieldDefinitions(), fetchMetadataValues()]);
  }

  watch(
    () => [resolvedPatientId.value, resolvedEncounterId.value, resolvedDocumentId.value] as const,
    () => {
      void fetchMetadataValues();
    },
    { immediate: loadValues },
  );

  watch(
    () => toValue(options.patientInfo),
    () => {
      if (!resolvedPatientId.value) {
        metadataValues.value = localFallbackValues(toValue(options.patientInfo));
      }
    },
    { deep: true },
  );

  if (loadFieldDefinitions) {
    void fetchFieldDefinitions();
  }

  return {
    metadataFields,
    metadataValues,
    fieldsLoading,
    valuesLoading,
    fieldsError,
    valuesError,
    refresh,
    fetchFieldDefinitions,
    fetchMetadataValues,
  };
}
