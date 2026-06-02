<script setup lang="ts">
import type { FormInstance, FormRules } from 'element-plus';

import { computed, reactive, ref } from 'vue';

import { Page } from '@vben/common-ui';

import {
  ElButton,
  ElDatePicker,
  ElForm,
  ElFormItem,
  ElInput,
  ElOption,
  ElRadio,
  ElRadioGroup,
  ElSelect,
  ElSwitch,
} from 'element-plus';

import { useFormKeyboardNavigation } from '#/hooks/use-form-keyboard-navigation';

defineOptions({
  name: 'NurseStationTestFormKeyboard',
});

interface TestFormModel {
  areaCode: string;
  criticalMode: 'radio' | 'switch';
  dateRange: [] | [string, string];
  doctorName: string;
  inpatientNo: string;
  isCritical: boolean;
  nurseLevel: string;
  patientName: string;
  remark: string;
}

const areaOptions = [
  { label: '一病区', value: 'ward-1' },
  { label: '二病区', value: 'ward-2' },
  { label: '三病区', value: 'ward-3' },
];
const nurseLevelOptions = [
  { label: '特级护理', value: 'special' },
  { label: '一级护理', value: 'level-1' },
  { label: '二级护理', value: 'level-2' },
];
const criticalRadioOptions = [
  { label: '是', value: true },
  { label: '否', value: false },
];

// 业务状态：护理记录筛选条件，直接影响后续筛选提交参数。
const formModel = reactive<TestFormModel>({
  areaCode: '',
  criticalMode: 'switch',
  dateRange: [],
  doctorName: '',
  inpatientNo: '',
  isCritical: false,
  nurseLevel: '',
  patientName: '',
  remark: '',
});
// 视图状态：用于展示行为切换，不参与业务参数提交。
const submitting = ref(false);
// 临时交互状态：仅用于页面提示，不影响业务决策。
const lastActionText = ref('尚未触发提交');

const formRef = ref<FormInstance>();
const formAreaRef = ref<HTMLElement | null>(null);

const formRules: FormRules<TestFormModel> = {
  patientName: [{ required: true, message: '请输入姓名', trigger: 'blur' }],
  inpatientNo: [{ required: true, message: '请输入住院号', trigger: 'blur' }],
  areaCode: [{ required: true, message: '请选择病区', trigger: 'change' }],
  nurseLevel: [{ required: true, message: '请选择护理等级', trigger: 'change' }],
  dateRange: [{ required: true, message: '请选择日期范围', trigger: 'change' }],
};

const criticalLabel = computed(() => (formModel.isCritical ? '危重患者' : '非危重患者'));

const { handleFormKeydown } = useFormKeyboardNavigation(formAreaRef, {
  // 多行备注区需要保留 Enter 原生换行，所以在 textarea 内不触发焦点跳转。
  shouldIgnoreEvent: (event, target) => target.tagName === 'TEXTAREA' && event.key === 'Enter',
});

// 提交测试表单：验证校验与键盘录入后的数据完整性。
async function handleSubmit() {
  if (!formRef.value) return;
  const valid = await formRef.value.validate().catch(() => false);
  if (!valid) {
    lastActionText.value = '校验未通过，请检查必填项';
    return;
  }
  submitting.value = true;
  try {
    lastActionText.value = `提交成功：${formModel.patientName} / ${criticalLabel.value}`;
  } finally {
    submitting.value = false;
  }
}

// 重置测试表单：清空筛选条件，回到初始可录入态。
function handleReset() {
  formRef.value?.resetFields();
  formModel.criticalMode = 'switch';
  formModel.isCritical = false;
  lastActionText.value = '已重置表单';
}
</script>

<template>
  <Page
    auto-content-height
    class="test-form-keyboard-page"
    content-class="h-full"
    description="用于验证护理记录筛选表单的键盘导航规则（左右方向键与 Enter）。"
    title="住院护士站-护理记录筛选表单测试页"
  >
    <section ref="formAreaRef" class="form-card" @keydown="handleFormKeydown">
      <ElForm
        ref="formRef"
        :model="formModel"
        :rules="formRules"
        label-position="right"
        label-width="88px"
        require-asterisk-position="left"
        status-icon
      >
        <div class="form-grid">
          <ElFormItem label="姓名" prop="patientName">
            <ElInput v-model="formModel.patientName" clearable placeholder="请输入姓名" />
          </ElFormItem>
          <ElFormItem label="住院号" prop="inpatientNo">
            <ElInput v-model="formModel.inpatientNo" clearable placeholder="请输入住院号" />
          </ElFormItem>
          <ElFormItem label="病区" prop="areaCode">
            <ElSelect v-model="formModel.areaCode" clearable placeholder="请选择病区">
              <ElOption
                v-for="item in areaOptions"
                :key="item.value"
                :label="item.label"
                :value="item.value"
              />
            </ElSelect>
          </ElFormItem>
          <ElFormItem label="护理等级" prop="nurseLevel">
            <ElSelect v-model="formModel.nurseLevel" clearable placeholder="请选择护理等级">
              <ElOption
                v-for="item in nurseLevelOptions"
                :key="item.value"
                :label="item.label"
                :value="item.value"
              />
            </ElSelect>
          </ElFormItem>
          <ElFormItem class="col-span-2" label="日期范围" prop="dateRange">
            <ElDatePicker
              v-model="formModel.dateRange"
              clearable
              end-placeholder="结束日期"
              start-placeholder="开始日期"
              type="daterange"
              unlink-panels
              value-format="YYYY-MM-DD"
            />
          </ElFormItem>
          <ElFormItem label="医生" prop="doctorName">
            <ElInput v-model="formModel.doctorName" clearable placeholder="请输入医生姓名" />
          </ElFormItem>
          <ElFormItem label="危重模式">
            <ElSelect v-model="formModel.criticalMode" placeholder="请选择模式">
              <ElOption label="Switch" value="switch" />
              <ElOption label="Radio" value="radio" />
            </ElSelect>
          </ElFormItem>
          <ElFormItem v-if="formModel.criticalMode === 'switch'" label="是否危重">
            <ElSwitch
              v-model="formModel.isCritical"
              inline-prompt
              active-text="是"
              inactive-text="否"
            />
          </ElFormItem>
          <ElFormItem v-else class="col-span-2" label="是否危重">
            <ElRadioGroup v-model="formModel.isCritical">
              <ElRadio
                v-for="item in criticalRadioOptions"
                :key="String(item.value)"
                :label="item.value"
              >
                {{ item.label }}
              </ElRadio>
            </ElRadioGroup>
          </ElFormItem>
          <ElFormItem class="col-span-3" label="备注" prop="remark">
            <ElInput
              v-model="formModel.remark"
              :rows="4"
              maxlength="200"
              placeholder="支持多行输入，Enter 保持原生换行"
              show-word-limit
              type="textarea"
            />
          </ElFormItem>
        </div>
      </ElForm>
      <div class="action-row">
        <span class="action-tip">当前状态：{{ criticalLabel }}</span>
        <div class="flex items-center gap-2">
          <ElButton plain @click="handleReset">重置</ElButton>
          <ElButton :loading="submitting" type="primary" @click="handleSubmit">提交测试</ElButton>
        </div>
      </div>
      <p class="result-text">{{ lastActionText }}</p>
    </section>
  </Page>
</template>

<style scoped>
.test-form-keyboard-page {
  min-height: 100%;
  background-color: var(--el-fill-color-lighter);
}

.form-card {
  border: 1px solid var(--el-border-color-lighter);
  border-radius: 6px;
  background: var(--el-bg-color);
  padding: 16px 16px 12px;
}

.form-grid {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 10px 12px;
}

.col-span-2 {
  grid-column: span 2;
}

.col-span-3 {
  grid-column: span 3;
}

:deep(.el-form-item) {
  margin-bottom: 2px;
}

:deep(.el-select),
:deep(.el-date-editor),
:deep(.el-input-number) {
  width: 100%;
}

.action-row {
  margin-top: 12px;
  border-top: 1px solid var(--el-border-color-lighter);
  padding-top: 10px;
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.action-tip {
  color: var(--el-text-color-regular);
  font-size: 13px;
}

.result-text {
  margin-top: 8px;
  color: var(--el-text-color-secondary);
  font-size: 13px;
}
</style>
