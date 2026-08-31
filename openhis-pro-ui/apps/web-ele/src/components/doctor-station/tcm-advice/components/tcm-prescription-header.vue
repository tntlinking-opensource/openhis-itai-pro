<script setup lang="ts">
import type { DiagnosisItem } from '#/components/doctor-station/diagnosis';
import type { DoctorStationDiagnosisScope } from '#/components/doctor-station/diagnosis/services/encounter-diagnosis.service';
import type { AdviceDiagnosisOptionsRefreshedPayload } from '#/components/doctor-station/diagnosis';

import {
  ElCheckbox,
  ElForm,
  ElFormItem,
  ElInputNumber,
  ElOption,
  ElRadio,
  ElRadioGroup,
  ElSelect,
} from 'element-plus';

import { AdviceDiagnosisBindPanel } from '#/components/doctor-station/diagnosis';

import './tcm-rx-header.css';

defineOptions({ name: 'TcmPrescriptionHeader' });

interface SelectOption {
  label: string;
  value: string;
}

interface Props {
  /** 已签发未代煎：仅代煎勾选可点（补充代煎费） */
  allowSufferingEdit?: boolean;
  disabled?: boolean;
  /** 中医诊断候选项（多选绑定） */
  diagnosisOptions?: DiagnosisItem[];
  encounterId?: string;
  methodCodeOptions?: SelectOption[];
  patientId?: string;
  rateCodeOptions?: SelectOption[];
  /** 住院：展示长临选择 */
  showTherapyType?: boolean;
  stationScope?: DoctorStationDiagnosisScope;
}

export interface TcmPrescriptionHeaderForm {
  chineseHerbsDoseQuantity?: number;
  /** 费用性质/合同主键 */
  contractId: string;
  days?: number;
  encounterDiagnosisIds: string[];
  /** 费用性质展示名（与 contractId 同步） */
  feeNatureText: string;
  methodCode: string;
  rateCode: string;
  sufferingFlag: boolean;
  tcmSyndromeGroup: string;
  therapyEnum?: string;
}

withDefaults(defineProps<Props>(), {
  allowSufferingEdit: false,
  disabled: false,
  diagnosisOptions: () => [],
  encounterId: '',
  methodCodeOptions: () => [],
  patientId: '',
  rateCodeOptions: () => [],
  showTherapyType: false,
  stationScope: 'outpatient',
});

const emit = defineEmits<{
  addDiagnosis: [];
  diagnosisDeleted: [encounterDiagnosisId: string];
  diagnosisMainSwitched: [];
  diagnosisOptionsRefreshed: [payload: AdviceDiagnosisOptionsRefreshedPayload];
  'update:encounterDiagnosisIds': [value: string[]];
}>();

const model = defineModel<TcmPrescriptionHeaderForm>({ required: true });

/** 多选诊断写回：同步 ids，组号由父级按候选项推导 */
function handleDiagnosisIdsUpdate(value: string[]) {
  const ids = Array.isArray(value)
    ? value.map((id) => String(id ?? '').trim()).filter(Boolean)
    : [];
  model.value.encounterDiagnosisIds = ids;
  emit('update:encounterDiagnosisIds', ids);
}
</script>

<template>
  <!-- 朝阳：紧凑 meta 条；布局走外挂 css + 原子类（院点 scoped 会丢） -->
  <ElForm
    :model="model"
    label-width="48px"
    class="tcm-rx-header"
    :class="{ 'tcm-rx-header--inpatient': showTherapyType }"
    :disabled="disabled && !allowSufferingEdit"
  >
    <div class="tcm-rx-header__row flex items-center gap-x-2.5 gap-y-1">
      <ElFormItem label="诊断" class="tcm-rx-header__item tcm-rx-header__item--diagnosis min-w-0">
        <div class="tcm-rx-header__diagnosis">
          <AdviceDiagnosisBindPanel
            :selected-ids="model.encounterDiagnosisIds"
            :options="diagnosisOptions"
            :encounter-id="encounterId"
            :patient-id="patientId"
            :station-scope="stationScope"
            medicine-type-filter="tcm"
            :disabled="disabled"
            @update:selected-ids="handleDiagnosisIdsUpdate"
            @add-diagnosis="emit('addDiagnosis')"
            @deleted="(id) => emit('diagnosisDeleted', id)"
            @main-switched="() => emit('diagnosisMainSwitched')"
            @options-refreshed="(payload) => emit('diagnosisOptionsRefreshed', payload)"
          />
        </div>
      </ElFormItem>

      <div v-if="showTherapyType" class="tcm-rx-header__therapy shrink-0">
        <ElRadioGroup v-model="model.therapyEnum" :disabled="disabled">
          <ElRadio value="LONG_TERM">长期</ElRadio>
          <ElRadio value="TEMPORARY">临时</ElRadio>
        </ElRadioGroup>
      </div>

      <ElFormItem label="用法" class="tcm-rx-header__item tcm-rx-header__item--select shrink-0">
        <ElSelect
          v-model="model.methodCode"
          class="!w-[100px]"
          filterable
          clearable
          placeholder="用法"
          :disabled="disabled"
        >
          <ElOption
            v-for="opt in methodCodeOptions"
            :key="opt.value"
            :label="opt.label"
            :value="opt.value"
          />
        </ElSelect>
      </ElFormItem>

      <ElFormItem label="频次" class="tcm-rx-header__item tcm-rx-header__item--select shrink-0">
        <ElSelect
          v-model="model.rateCode"
          class="!w-[100px]"
          filterable
          clearable
          placeholder="频次"
          :disabled="disabled"
        >
          <ElOption
            v-for="opt in rateCodeOptions"
            :key="opt.value"
            :label="opt.label"
            :value="opt.value"
          />
        </ElSelect>
      </ElFormItem>

      <ElFormItem label="付数" class="tcm-rx-header__item tcm-rx-header__item--num shrink-0">
        <ElInputNumber
          v-model="model.chineseHerbsDoseQuantity"
          class="tcm-rx-header__num !w-[52px]"
          :min="1"
          :max="30"
          :controls="false"
          :disabled="disabled"
        />
      </ElFormItem>

      <ElFormItem label-width="0" class="tcm-rx-header__item tcm-rx-header__item--check shrink-0">
        <ElCheckbox
          v-model="model.sufferingFlag"
          class="tcm-rx-header__suffering"
          :disabled="disabled && !allowSufferingEdit"
        >
          代煎
        </ElCheckbox>
      </ElFormItem>
    </div>
  </ElForm>
</template>
