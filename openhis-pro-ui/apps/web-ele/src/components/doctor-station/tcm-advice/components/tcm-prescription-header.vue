<script setup lang="ts">
import {
  ElButton,
  ElCheckbox,
  ElForm,
  ElFormItem,
  ElInputNumber,
  ElOption,
  ElRadio,
  ElRadioGroup,
  ElSelect,
} from 'element-plus';

import './tcm-rx-header.css';

defineOptions({ name: 'TcmPrescriptionHeader' });

interface SelectOption {
  label: string;
  value: string;
}

interface Props {
  disabled?: boolean;
  methodCodeOptions?: SelectOption[];
  rateCodeOptions?: SelectOption[];
  syndromeGroupOptions?: SelectOption[];
  /** 住院：展示长临选择 */
  showTherapyType?: boolean;
}

export interface TcmPrescriptionHeaderForm {
  chineseHerbsDoseQuantity?: number;
  days?: number;
  methodCode: string;
  rateCode: string;
  sufferingFlag: boolean;
  tcmSyndromeGroup: string;
  therapyEnum?: string;
}

withDefaults(defineProps<Props>(), {
  disabled: false,
  methodCodeOptions: () => [],
  rateCodeOptions: () => [],
  syndromeGroupOptions: () => [],
  showTherapyType: false,
});

const emit = defineEmits<{
  addDiagnosis: [];
}>();

const model = defineModel<TcmPrescriptionHeaderForm>({ required: true });

/** 打开添加诊断弹窗 */
function handleAddDiagnosis() {
  emit('addDiagnosis');
}
</script>

<template>
  <!-- 朝阳：紧凑 meta 条；布局走外挂 css + 原子类（院点 scoped 会丢） -->
  <ElForm
    :model="model"
    label-width="48px"
    class="tcm-rx-header"
    :class="{ 'tcm-rx-header--inpatient': showTherapyType }"
    :disabled="disabled"
  >
    <div class="tcm-rx-header__row flex flex-wrap items-center gap-x-2.5 gap-y-1">
      <ElFormItem label="诊断" class="tcm-rx-header__item tcm-rx-header__item--diagnosis shrink-0">
        <div class="tcm-rx-header__diagnosis inline-flex items-center gap-1">
          <ElSelect
            v-model="model.tcmSyndromeGroup"
            class="!w-[180px]"
            filterable
            clearable
            placeholder="请选择诊断"
          >
            <ElOption
              v-for="opt in syndromeGroupOptions"
              :key="opt.value"
              :label="opt.label"
              :value="opt.value"
            />
          </ElSelect>
          <ElButton link type="primary" @click="handleAddDiagnosis">添加</ElButton>
        </div>
      </ElFormItem>

      <div v-if="showTherapyType" class="tcm-rx-header__therapy shrink-0">
        <ElRadioGroup v-model="model.therapyEnum">
          <ElRadio label="LONG_TERM">长期</ElRadio>
          <ElRadio label="TEMPORARY">临时</ElRadio>
        </ElRadioGroup>
      </div>

      <ElFormItem label="用法" class="tcm-rx-header__item tcm-rx-header__item--select shrink-0">
        <ElSelect
          v-model="model.methodCode"
          class="!w-[100px]"
          filterable
          clearable
          placeholder="用法"
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
        />
      </ElFormItem>

      <ElFormItem label-width="0" class="tcm-rx-header__item tcm-rx-header__item--check shrink-0">
        <ElCheckbox v-model="model.sufferingFlag" class="tcm-rx-header__suffering">
          代煎
        </ElCheckbox>
      </ElFormItem>
    </div>
  </ElForm>
</template>
