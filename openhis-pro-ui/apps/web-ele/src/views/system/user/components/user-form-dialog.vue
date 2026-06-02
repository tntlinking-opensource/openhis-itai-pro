<script lang="ts" setup>
import type { VbenFormSchema } from '#/adapter/form';
import type { RoleInfo } from '#/api/user/role';
import type { CreateUserParams, UpdateUserParams, UserInfo } from '#/api/user/user';

import { computed, nextTick, onMounted, reactive, ref, watch } from 'vue';

import { useVbenModal } from '@vben/common-ui';

import { ElMessage } from 'element-plus';
import { pinyin } from 'pinyin-pro';

import { useVbenForm, z } from '#/adapter/form';
import { getRoleListApi } from '#/api/user/role';
import { readUKeyInfo } from '#/api/vendor/ca-vendor';
import { assignRolesApi, createUserApi, getUserRolesApi, updateUserApi } from '#/api/user/user';

interface Props {
  modelValue: boolean;
  user?: null | UserInfo;
}

const props = withDefaults(defineProps<Props>(), {
  user: null,
});

const emit = defineEmits<{
  (e: 'update:modelValue', value: boolean): void;
  (e: 'success'): void;
}>();

const isEdit = computed(() => !!props.user);

const originalData = ref<Record<string, any>>({});

// 角色列表
const allRoles = ref<RoleInfo[]>([]);
// 选中的角色ID
const selectedRoleIds = ref<number[]>([]);
// UKey读取loading
const ukeyLoading = ref(false);

// 加载角色列表
const loadRoles = async () => {
  try {
    const response = await getRoleListApi({
      pageNo: 1,
      pageSize: 1000, // 获取所有角色
    });
    allRoles.value = response.content || [];
  } catch (error) {
    console.error('加载角色列表失败:', error);
    allRoles.value = [];
  }
};

// 角色选项
const roleOptions = computed(() => {
  return allRoles.value.map((role) => ({
    label: role.displayName || role.name,
    value: role.id,
  }));
});

/**
 * 读取UKey设备信息，自动填充表单
 */
async function handleReadUKey() {
  ukeyLoading.value = true;
  try {
    const info = await readUKeyInfo();
    const values: Record<string, any> = {
      name: info.certName,
      ukeyId: info.deviceSN.replace(/;$/, ''),
      ukeySrcId: info.certSerialNumber,
    };
    // 新增模式：同时填充用户名(拼音)和默认密码
    if (!isEdit.value) {
      values.userName = pinyin(info.certName, { pattern: 'pinyin', toneType: 'none' })
        .replace(/\s+/g, '')
        .toLowerCase();
      values.password = 'Abc@12345';
    }
    await formApi.setValues(values);
    ElMessage.success(`UKey读取成功: ${info.certName}`);
  } catch (e: any) {
    ElMessage.error(e?.message || '读取UKey失败，请确认设备已插入');
  } finally {
    ukeyLoading.value = false;
  }
}

async function handleSubmit() {
  const { valid } = await formApi.validate();
  if (!valid) {
    return;
  }

  modalApi.lock(true);
  try {
    const formValues = await formApi.getValues();
    const mergedData = {
      ...originalData.value,
      ...formValues,
    };

    // 从表单中获取角色ID，确保类型为数字数组
    const roleIds = mergedData.roleIds || [];
    selectedRoleIds.value = Array.isArray(roleIds) ? roleIds.map((id) => id) : [];

    let userId: number;

    if (isEdit.value && props.user) {
      // 编辑用户
      userId = props.user.id;
      const updateParams: UpdateUserParams = {
        id: userId,
        request: {
          name: mergedData.name || '',
          phoneNumber: mergedData.phoneNumber || '',
          email: mergedData.email || '',
          isActive: mergedData.isActive || 'true',
          extraProperties: mergedData.extraProperties || undefined,
          ukeyId: mergedData.ukeyId || undefined,
          ukeySrcId: mergedData.ukeySrcId || undefined,
        },
      };
      await updateUserApi(updateParams);

      await assignRolesApi({
        id: userId,
        request: {
          roleIds: selectedRoleIds.value,
        },
      });

      ElMessage.success('编辑用户成功');
    } else {
      // 新增用户
      const createData: CreateUserParams = {
        userName: mergedData.userName || '',
        password: mergedData.password || '',
        name: mergedData.name || '',
        phoneNumber: mergedData.phoneNumber || '',
        email: mergedData.email || '',
        isActive: mergedData.isActive || 'true',
        extraProperties: mergedData.extraProperties || undefined,
        ukeyId: mergedData.ukeyId || undefined,
        ukeySrcId: mergedData.ukeySrcId || undefined,
      };
      const createResponse = await createUserApi(createData);

      // 从响应中获取用户ID
      const response = createResponse as unknown as { data?: { id: number }; id?: number };
      userId = response.data?.id || response.id || 0;

      // 绑定角色
      if (selectedRoleIds.value.length > 0 && userId) {
        await assignRolesApi({
          id: userId,
          request: {
            roleIds: selectedRoleIds.value,
          },
        });
      }

      ElMessage.success('新增用户成功');
    }
    emit('success');
    modalApi.close();
  } catch (error: any) {
    const errorMessage =
      error?.response?.data?.msg ||
      error?.response?.data?.message ||
      error?.message ||
      error?.msg ||
      (isEdit.value ? '编辑用户失败，请稍后重试' : '新增用户失败，请稍后重试');
    ElMessage.error(errorMessage);
  } finally {
    modalApi.unlock();
  }
}

async function loadFormData() {
  // 确保角色列表已加载
  if (allRoles.value.length === 0) {
    await loadRoles();
  }

  if (props.user && props.user.id) {
    originalData.value = { ...props.user };

    // 获取用户的角色
    try {
      const rolesResponse = await getUserRolesApi({ value: props.user.id });
      const rolesData =
        (rolesResponse as unknown as { data?: RoleInfo[] }).data || (rolesResponse as RoleInfo[]);
      const roleIds = Array.isArray(rolesData) ? rolesData.map((role) => role.id) : [];
      selectedRoleIds.value = roleIds;

      const formData = {
        userName: props.user.userName || '',
        name: props.user.name || '',
        phoneNumber: props.user.phoneNumber || '',
        email: props.user.email || '',
        isActive: String(props.user.isActive),
        extraProperties: props.user.extraProperties
          ? JSON.stringify(props.user.extraProperties)
          : '',
        ukeyId: props.user.ukeyId || '',
        ukeySrcId: props.user.ukeySrcId || '',
        roleIds,
      };

      // 先设置其他字段
      await formApi.setValues(formData);

      // 等待选项渲染完成后再设置角色字段，确保能正确显示 label
      await nextTick();
      await formApi.setFieldValue('roleIds', roleIds);
    } catch (error) {
      console.error('获取用户角色失败:', error);
      selectedRoleIds.value = [];
      const formData = {
        userName: props.user.userName || '',
        name: props.user.name || '',
        phoneNumber: props.user.phoneNumber || '',
        email: props.user.email || '',
        isActive: String(props.user.isActive),
        extraProperties: props.user.extraProperties
          ? JSON.stringify(props.user.extraProperties)
          : '',
        ukeyId: props.user.ukeyId || '',
        ukeySrcId: props.user.ukeySrcId || '',
        roleIds: [],
      };
      await formApi.setValues(formData);
    }
  } else {
    originalData.value = {};
    selectedRoleIds.value = [];
    await formApi.resetForm();
    await formApi.setValues({ isActive: 'true', roleIds: [] });
  }
}

const [Modal, modalApi] = useVbenModal({
  onOpenChange: (isOpen: boolean) => {
    emit('update:modelValue', isOpen);
  },
  onOpened: async () => {
    await nextTick();
    await loadFormData();
  },
  onConfirm: handleSubmit,
  showCancelButton: true,
  showConfirmButton: true,
  confirmText: '确定',
  cancelText: '取消',
});

const formConfig = reactive({
  commonConfig: {
    componentProps: {
      style: { width: '220px' },
    },
  },
  layout: 'horizontal' as const,
  schema: computed((): VbenFormSchema[] => {
    const schema: VbenFormSchema[] = [];

    schema.push({
      component: 'Input',
      fieldName: 'userName',
      label: '用户名',
      disabled: isEdit.value,
      componentProps: {
        placeholder: '请输入用户名',
      },
      rules: z.string().min(1, { message: '请输入用户名' }),
    });

    if (!isEdit.value) {
      schema.push({
        component: 'Input',
        fieldName: 'password',
        label: '密码',
        componentProps: {
          type: 'password',
          showPassword: true,
          placeholder: '请输入密码（至少8位）',
        },
        rules: z.string().min(6, { message: '密码长度不能少于6位' }),
      });
    }

    schema.push(
      {
        component: 'Input',
        fieldName: 'name',
        label: '姓名',
        componentProps: {
          placeholder: '请输入姓名',
        },
      },
      {
        component: 'Input',
        fieldName: 'phoneNumber',
        label: '手机号',
        componentProps: {
          placeholder: '请输入手机号',
        },
        rules: z
          .string()
          .optional()
          .refine((val) => !val || /^1[3-9]\d{9}$/.test(val), { message: '请输入正确的手机号' }),
      },
      {
        component: 'Input',
        fieldName: 'email',
        label: '邮箱',
        componentProps: {
          placeholder: '请输入邮箱',
        },
        rules: z
          .string()
          .optional()
          .refine((val) => !val || z.string().email().safeParse(val).success, {
            message: '请输入正确的邮箱地址',
          }),
      },
      {
        component: 'Select',
        fieldName: 'isActive',
        label: '状态',
        componentProps: {
          placeholder: '请选择状态',
          options: [
            { label: '启用', value: 'true' },
            { label: '禁用', value: 'false' },
          ],
          disabled: props.user?.userName === 'admin', // admin 用户不能编辑状态
        },
        rules: z.string().min(1, { message: '请选择状态' }),
      },
      {
        component: 'Select',
        fieldName: 'roleIds',
        label: '角色',
        componentProps: {
          placeholder: '请选择角色',
          options: roleOptions.value,
          multiple: true,
          disabled: props.user?.userName === 'admin', // admin 用户不能编辑角色
        },
      },
    );

    // UKey字段（只读展示，新增/编辑模式均显示）
    schema.push(
      {
        component: 'Input',
        fieldName: 'ukeyId',
        label: '设备序列号',
        componentProps: {
          placeholder: '读取UKey后自动填充',
          disabled: true,
        },
      },
      {
        component: 'Input',
        fieldName: 'ukeySrcId',
        label: '证书序列号',
        componentProps: {
          placeholder: '读取UKey后自动填充',
          disabled: true,
        },
      },
    );

    return schema;
  }),
  showDefaultActions: false,
});

const [Form, formApi] = useVbenForm(formConfig);

watch(
  () => props.modelValue,
  (visible) => {
    if (visible) {
      modalApi.open();
    } else {
      modalApi.close();
    }
  },
  { immediate: true },
);

onMounted(() => {
  loadRoles();
});
</script>

<template>
  <Modal
    class="w-[600px]"
    :title="isEdit ? '编辑用户' : '新增用户'"
    :close-on-click-modal="false"
    :close-on-press-escape="false"
  >
    <!-- UKey读取按钮（新增/编辑模式均可用） -->
    <div class="mb-4 flex items-center gap-2">
      <el-button type="primary" :loading="ukeyLoading" @click="handleReadUKey">
        读取UKey
      </el-button>
      <span class="text-muted-foreground text-xs">插入USB Key后点击读取，自动填充用户信息</span>
    </div>
    <Form />
  </Modal>
</template>
