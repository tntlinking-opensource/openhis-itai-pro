<script setup lang="ts">
import {
  ElForm,
  ElFormItem,
  ElInput,
  ElInputNumber,
  ElOption,
  ElRadio,
  ElRadioGroup,
  ElSelect,
  ElSwitch,
} from 'element-plus';

defineOptions({ name: 'TcmPrescriptionHeader' });

withDefaults(defineProps<Props>(), {
  disabled: false,
  methodCodeOptions: () => [],
  rateCodeOptions: () => [],
  syndromeGroupOptions: () => [],
  showTherapyType: false,
});

export interface TcmPrescriptionHeaderForm {
  chineseHerbsDoseQuantity: number;
  days: number;
  methodCode: string;
  rateCode: string;
  remark: string;
  sufferingFlag: boolean;
  tcmSyndromeGroup: string;
  therapyEnum?: string;
}

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

const model = defineModel<TcmPrescriptionHeaderForm>({ required: true });
</script>

<template>
  <ElForm
    :model="model"
    label-width="72px"
    :class="['tcm-prescription-header', { 'tcm-prescription-header--inpatient': showTherapyType }]"
  >
    <div class="tcm-prescription-header__row tcm-prescription-header__row--primary">
      <div v-if="showTherapyType" class="tcm-prescription-header__therapy">
        <ElRadioGroup v-model="model.therapyEnum" :disabled="disabled">
          <ElRadio label="LONG_TERM">长期</ElRadio>
          <ElRadio label="TEMPORARY">临时</ElRadio>
        </ElRadioGroup>
      </div>
      <ElFormItem label="用法" class="tcm-prescription-header__select">
        <ElSelect
          v-model="model.methodCode"
          filterable
          clearable
          placeholder="请选择用法"
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
      <ElFormItem label="频次" class="tcm-prescription-header__select">
        <ElSelect
          v-model="model.rateCode"
          filterable
          clearable
          placeholder="请选择频次"
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
      <ElFormItem label="天数" class="tcm-prescription-header__num">
        <ElInputNumber
          v-model="model.days"
          class="tcm-prescription-header__num-input"
          :min="1"
          :max="30"
          :controls="false"
          :disabled="disabled"
        />
      </ElFormItem>
      <ElFormItem label="付数" class="tcm-prescription-header__num">
        <ElInputNumber
          v-model="model.chineseHerbsDoseQuantity"
          class="tcm-prescription-header__num-input"
          :min="1"
          :max="30"
          :controls="false"
          :disabled="disabled"
        />
      </ElFormItem>
      <ElFormItem label="代煎" class="tcm-prescription-header__switch">
        <ElSwitch v-model="model.sufferingFlag" :disabled="disabled" />
      </ElFormItem>
    </div>

    <div class="tcm-prescription-header__row tcm-prescription-header__row--last">
      <ElFormItem label="诊断" class="tcm-prescription-header__select">
        <ElSelect
          v-model="model.tcmSyndromeGroup"
          filterable
          clearable
          placeholder="请选择诊断"
          :disabled="disabled"
        >
          <ElOption
            v-for="opt in syndromeGroupOptions"
            :key="opt.value"
            :label="opt.label"
            :value="opt.value"
          />
        </ElSelect>
      </ElFormItem>
      <ElFormItem label="备注" class="tcm-prescription-header__remark">
        <ElInput
          v-model="model.remark"
          class="w-full"
          placeholder="备注"
          :disabled="disabled"
          clearable
        />
      </ElFormItem>
    </div>
  </ElForm>
</template>

<style scoped>
.tcm-prescription-header {
  --tcm-header-select-width: 180px;
  --tcm-header-num-width: 148px;
  --tcm-header-tail-min-width: 100px;
  --tcm-header-field-width: calc(72px + 8px + var(--tcm-header-select-width));
}

.tcm-prescription-header__row {
  display: grid;
  gap: 12px;
  align-items: start;
  margin-bottom: 12px;
}

.tcm-prescription-header__row--primary {
  grid-template-columns:
    var(--tcm-header-field-width)
    var(--tcm-header-field-width)
    var(--tcm-header-num-width)
    var(--tcm-header-num-width)
    auto;
}

.tcm-prescription-header--inpatient .tcm-prescription-header__row--primary {
  grid-template-columns:
    var(--tcm-header-field-width)
    var(--tcm-header-field-width)
    var(--tcm-header-field-width)
    var(--tcm-header-num-width)
    var(--tcm-header-num-width)
    auto;
}

.tcm-prescription-header__row--last {
  grid-template-columns:
    var(--tcm-header-field-width)
    minmax(var(--tcm-header-tail-min-width), 1fr);
  margin-bottom: 0;
}

.tcm-prescription-header :deep(.el-form-item) {
  margin-bottom: 0;
  min-width: 0;
}

.tcm-prescription-header :deep(.el-form-item__label) {
  color: var(--el-text-color-regular);
  padding-right: 8px;
  justify-content: flex-end;
}

.tcm-prescription-header :deep(.el-form-item__content) {
  min-width: 0;
}

.tcm-prescription-header__therapy {
  display: flex;
  align-items: center;
  width: var(--tcm-header-field-width);
  height: 32px;
  padding-left: calc(72px + 8px);
  box-sizing: border-box;
}

.tcm-prescription-header__therapy :deep(.el-radio) {
  margin-right: 35px;
}

.tcm-prescription-header__therapy :deep(.el-radio:last-child) {
  margin-right: 0;
}

.tcm-prescription-header__select :deep(.el-select) {
  width: var(--tcm-header-select-width);
}

.tcm-prescription-header__num-input {
  width: 72px;
}

.tcm-prescription-header__switch :deep(.el-form-item__content) {
  line-height: 32px;
}

.tcm-prescription-header__remark {
  min-width: var(--tcm-header-tail-min-width);
}

@media (max-width: 768px) {
  .tcm-prescription-header__row,
  .tcm-prescription-header--inpatient .tcm-prescription-header__row {
    display: flex;
    flex-direction: column;
    align-items: stretch;
  }

  .tcm-prescription-header__therapy,
  .tcm-prescription-header__select,
  .tcm-prescription-header__num,
  .tcm-prescription-header__switch,
  .tcm-prescription-header__remark {
    width: 100%;
  }

  .tcm-prescription-header__select :deep(.el-select) {
    width: 100%;
  }

  .tcm-prescription-header__therapy {
    width: 100%;
    padding-left: 0;
  }
}
</style>
