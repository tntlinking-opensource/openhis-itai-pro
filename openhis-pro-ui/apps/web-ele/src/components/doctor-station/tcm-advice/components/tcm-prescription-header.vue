<script setup lang="ts">
import {
  ElCol,
  ElForm,
  ElFormItem,
  ElInput,
  ElInputNumber,
  ElOption,
  ElRow,
  ElSelect,
  ElSwitch,
} from 'element-plus';

defineOptions({ name: 'TcmPrescriptionHeader' });

withDefaults(defineProps<Props>(), {
  disabled: false,
  methodCodeOptions: () => [],
  rateCodeOptions: () => [],
  syndromeGroupOptions: () => [],
});

export interface TcmPrescriptionHeaderForm {
  chineseHerbsDoseQuantity: number;
  days: number;
  methodCode: string;
  rateCode: string;
  remark: string;
  sufferingFlag: boolean;
  tcmSyndromeGroup: string;
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
}

const model = defineModel<TcmPrescriptionHeaderForm>({ required: true });
</script>

<template>
  <ElForm :model="model" label-width="72px" class="tcm-prescription-header">
    <ElRow :gutter="12">
      <ElCol :xs="24" :sm="12" :md="8">
        <ElFormItem label="病证组">
          <ElSelect
            v-model="model.tcmSyndromeGroup"
            class="w-full"
            filterable
            clearable
            placeholder="请选择病证组"
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
      </ElCol>
      <ElCol :xs="12" :sm="8" :md="4">
        <ElFormItem label="付数">
          <ElInputNumber
            v-model="model.chineseHerbsDoseQuantity"
            class="w-full"
            :min="1"
            :max="30"
            :controls="false"
            :disabled="disabled"
          />
        </ElFormItem>
      </ElCol>
      <ElCol :xs="12" :sm="8" :md="4">
        <ElFormItem label="代煎">
          <ElSwitch v-model="model.sufferingFlag" :disabled="disabled" />
        </ElFormItem>
      </ElCol>
      <ElCol :xs="12" :sm="8" :md="4">
        <ElFormItem label="用法">
          <ElSelect
            v-model="model.methodCode"
            class="w-full"
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
      </ElCol>
      <ElCol :xs="12" :sm="8" :md="4">
        <ElFormItem label="频次">
          <ElSelect
            v-model="model.rateCode"
            class="w-full"
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
      </ElCol>
    </ElRow>
    <ElRow :gutter="12">
      <ElCol :xs="12" :sm="8" :md="4">
        <ElFormItem label="天数">
          <ElInputNumber
            v-model="model.days"
            class="w-full"
            :min="1"
            :max="30"
            :controls="false"
            :disabled="disabled"
          />
        </ElFormItem>
      </ElCol>
      <ElCol :xs="24" :sm="16" :md="20">
        <ElFormItem label="备注">
          <ElInput v-model="model.remark" class="w-full" placeholder="备注" :disabled="disabled" />
        </ElFormItem>
      </ElCol>
    </ElRow>
  </ElForm>
</template>

<style scoped>
.tcm-prescription-header :deep(.el-form-item) {
  margin-bottom: 10px;
}

.tcm-prescription-header :deep(.el-row:last-child .el-form-item) {
  margin-bottom: 0;
}

.tcm-prescription-header :deep(.el-form-item__label) {
  color: var(--el-text-color-regular);
}
</style>
