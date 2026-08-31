<!--
  门诊处方批量点评弹窗：共用是否合理 / 原因 / 备注。
  不合理时原因必填；合理时可空。
-->
<script setup lang="ts">
import { computed, reactive, ref } from 'vue';

import {
  ElButton,
  ElDialog,
  ElForm,
  ElFormItem,
  ElInput,
  ElMessage,
  ElRadio,
  ElRadioGroup,
} from 'element-plus';

import {
  batchCreatePrescriptionReviewsApi,
  type BatchCreateReviewItem,
} from '#/api/pharmacy/prescription-review';
import { useRequest } from '#/hooks/use-request';

defineOptions({ name: 'PrescriptionBatchReviewDialog' });

interface Props {
  /** 待点评处方（已由调用方过滤为待点评且已发药） */
  items?: BatchCreateReviewItem[];
}

const props = withDefaults(defineProps<Props>(), {
  items: () => [],
});

const emit = defineEmits<{
  success: [];
}>();

const visible = ref(false);

interface FormState {
  reasonableFlag: boolean;
  reason: string;
  remark: string;
}

const form = reactive<FormState>({
  reasonableFlag: true,
  reason: '',
  remark: '',
});

const itemCount = computed(() => props.items.length);

const { run: runBatchCreate, loading: submitLoading } = useRequest(
  batchCreatePrescriptionReviewsApi,
  {
    manual: true,
    onSuccess: () => {
      ElMessage.success(`已点评 ${itemCount.value} 张处方`);
      visible.value = false;
      emit('success');
    },
  },
);

/** 打开弹窗并重置为合理默认值。 */
function open() {
  form.reasonableFlag = true;
  form.reason = '';
  form.remark = '';
  visible.value = true;
}

/** 校验并提交批量点评。 */
async function handleSubmit() {
  if (props.items.length === 0) {
    ElMessage.warning('请先选择待点评处方');
    return;
  }
  // 不合理时原因去空白后须非空
  if (!form.reasonableFlag) {
    const reason = form.reason.trim();
    if (!reason) {
      ElMessage.warning('判定不合理时请填写原因');
      return;
    }
  }
  await runBatchCreate({
    items: props.items.map((item) => ({
      prescriptionNo: item.prescriptionNo,
    })),
    reasonableFlag: form.reasonableFlag,
    reason: form.reason.trim() || undefined,
    remark: form.remark.trim() || undefined,
  });
}

defineExpose({ open });
</script>

<template>
  <ElDialog
    v-model="visible"
    :title="itemCount <= 1 ? '处方点评' : '批量处方点评'"
    width="480px"
    destroy-on-close
    :close-on-click-modal="false"
  >
    <p class="mb-3 text-sm text-[var(--el-text-color-regular)]">
      <template v-if="itemCount <= 1">填写本方点评结果。</template>
      <template v-else>已选 {{ itemCount }} 张处方，将共用以下点评结果。</template>
    </p>
    <ElForm label-width="88px">
      <ElFormItem label="是否合理" required>
        <ElRadioGroup v-model="form.reasonableFlag">
          <ElRadio :value="true">合理</ElRadio>
          <ElRadio :value="false">不合理</ElRadio>
        </ElRadioGroup>
      </ElFormItem>
      <ElFormItem label="原因" :required="!form.reasonableFlag">
        <ElInput
          v-model="form.reason"
          type="textarea"
          :rows="3"
          maxlength="1000"
          show-word-limit
          :placeholder="form.reasonableFlag ? '可选' : '不合理时必填'"
        />
      </ElFormItem>
      <ElFormItem label="备注">
        <ElInput
          v-model="form.remark"
          type="textarea"
          :rows="2"
          maxlength="1000"
          show-word-limit
          placeholder="可选"
        />
      </ElFormItem>
    </ElForm>
    <template #footer>
      <ElButton @click="visible = false">取消</ElButton>
      <ElButton type="primary" :loading="submitLoading" @click="handleSubmit"> 提交点评 </ElButton>
    </template>
  </ElDialog>
</template>
