<template>
  <div class="page-container">
    <el-card shadow="never">
      <el-tabs v-model="activeTab" @tab-change="onTabChange">
        <el-tab-pane
          v-for="tab in tabs"
          :key="tab.role"
          :label="tab.label"
          :name="tab.role"
        />
      </el-tabs>

      <div class="toolbar">
        <el-input
          v-model="keyword"
          placeholder="请输入用户名 / 姓名 / 学号 / 工号"
          clearable
          style="width: 280px"
          @keyup.enter="handleSearch"
          @clear="handleSearch"
        />
        <el-button type="primary" @click="handleSearch">
          <el-icon><Search /></el-icon>查询
        </el-button>
        <el-button @click="handleReset">
          <el-icon><Refresh /></el-icon>重置
        </el-button>
        <div class="toolbar-spacer" />
        <el-button type="primary" @click="openCreate">
          <el-icon><Plus /></el-icon>新增{{ currentTabLabel }}
        </el-button>
      </div>

      <PageTable
        v-model:page="page"
        v-model:size="size"
        :data="listData"
        :loading="loading"
        :total="total"
        mode="server"
        row-key="id"
        @change="loadList"
      >
        <el-table-column type="index" label="#" width="56" align="center" />
        <el-table-column prop="username" label="用户名" min-width="120" />
        <el-table-column prop="realName" label="姓名" min-width="100" />

        <template v-if="activeTab === 'STUDENT'">
          <el-table-column prop="studentNo" label="学号" min-width="120" />
          <el-table-column prop="gender" label="性别" width="80" />
          <el-table-column prop="clazzName" label="班级" min-width="120" />
          <el-table-column prop="majorName" label="专业" min-width="150" />
          <el-table-column prop="gradeName" label="年级" width="100" />
        </template>

        <template v-else-if="activeTab === 'TEACHER'">
          <el-table-column prop="teacherNo" label="工号" min-width="120" />
          <el-table-column prop="title" label="职称" min-width="110" />
          <el-table-column prop="department" label="所属院系" min-width="150" />
        </template>

        <el-table-column prop="phone" label="手机号" min-width="130" />
        <el-table-column prop="email" label="邮箱" min-width="170" show-overflow-tooltip />
        <el-table-column prop="lastLoginTime" label="最后登录" min-width="165" />

        <el-table-column label="状态" width="100" align="center">
          <template #default="{ row }">
            <el-switch
              :model-value="row.status"
              :active-value="1"
              :inactive-value="0"
              :loading="statusLoadingId === row.id"
              @change="(val) => handleStatusChange(row, val)"
            />
          </template>
        </el-table-column>

        <el-table-column label="操作" width="250" fixed="right" align="center">
          <template #default="{ row }">
            <el-button type="primary" link @click="openEdit(row)">编辑</el-button>
            <el-button type="warning" link @click="handleResetPassword(row)">
              重置密码
            </el-button>
            <el-button type="danger" link @click="handleDelete(row)">删除</el-button>
          </template>
        </el-table-column>
      </PageTable>
    </el-card>

    <!-- 新增 / 编辑 -->
    <el-dialog
      v-model="dialogVisible"
      :title="dialogTitle"
      width="640px"
      :close-on-click-modal="false"
      @closed="onDialogClosed"
    >
      <el-form
        ref="formRef"
        :model="form"
        :rules="formRules"
        label-width="100px"
      >
        <el-row :gutter="16">
          <el-col :span="12">
            <el-form-item label="用户名" prop="username">
              <el-input
                v-model="form.username"
                placeholder="请输入用户名"
                :disabled="isEdit"
              />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="姓名" prop="realName">
              <el-input v-model="form.realName" placeholder="请输入姓名" />
            </el-form-item>
          </el-col>
        </el-row>

        <el-row v-if="activeTab === 'STUDENT'" :gutter="16">
          <el-col :span="12">
            <el-form-item label="学号" prop="studentNo">
              <el-input v-model="form.studentNo" placeholder="请输入学号" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="性别" prop="gender">
              <el-select
                v-model="form.gender"
                placeholder="请选择性别"
                style="width: 100%"
              >
                <el-option
                  v-for="item in GENDER_OPTIONS"
                  :key="item.value"
                  :label="item.label"
                  :value="item.value"
                />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="班级" prop="clazzId">
              <el-select
                v-model="form.clazzId"
                placeholder="请选择班级"
                filterable
                style="width: 100%"
              >
                <el-option
                  v-for="item in clazzOptions"
                  :key="item.id"
                  :label="clazzLabel(item)"
                  :value="item.id"
                />
              </el-select>
            </el-form-item>
          </el-col>
        </el-row>

        <el-row v-if="activeTab === 'TEACHER'" :gutter="16">
          <el-col :span="12">
            <el-form-item label="工号" prop="teacherNo">
              <el-input v-model="form.teacherNo" placeholder="请输入工号" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="职称" prop="title">
              <el-input v-model="form.title" placeholder="如：副教授" />
            </el-form-item>
          </el-col>
          <el-col :span="24">
            <el-form-item label="所属院系" prop="department">
              <el-input v-model="form.department" placeholder="如：计算机学院" />
            </el-form-item>
          </el-col>
        </el-row>

        <el-row :gutter="16">
          <el-col :span="12">
            <el-form-item label="手机号" prop="phone">
              <el-input v-model="form.phone" placeholder="请输入手机号" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="邮箱" prop="email">
              <el-input v-model="form.email" placeholder="请输入邮箱" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="状态" prop="status">
              <el-radio-group v-model="form.status">
                <el-radio :value="1">启用</el-radio>
                <el-radio :value="0">停用</el-radio>
              </el-radio-group>
            </el-form-item>
          </el-col>
        </el-row>

        <el-alert
          v-if="!isEdit"
          type="info"
          :closable="false"
          title="新增用户无需设置密码，系统将生成初始密码。"
        />
      </el-form>

      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitLoading" @click="handleSubmit">
          确定
        </el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import PageTable from '@/components/PageTable.vue'
import {
  createUser,
  deleteUser,
  fetchUsers,
  resetUserPassword,
  updateUser,
  updateUserStatus
} from '@/api/adminUser'
import { fetchClazzOptions } from '@/api/meta'
import { GENDER_OPTIONS } from '@/utils/dict'

const tabs = [
  { label: '学生', role: 'STUDENT' },
  { label: '教师', role: 'TEACHER' },
  { label: '管理员', role: 'ADMIN' }
]

const activeTab = ref('STUDENT')
const currentTabLabel = computed(
  () => tabs.find((t) => t.role === activeTab.value)?.label || '用户'
)

const keyword = ref('')
const page = ref(1)
const size = ref(10)
const total = ref(0)
const listData = ref({ total: 0, page: 1, size: 10, records: [] })
const loading = ref(false)
const statusLoadingId = ref(null)

const clazzOptions = ref([])

const dialogVisible = ref(false)
const dialogTitle = ref('新增用户')
const isEdit = ref(false)
const submitLoading = ref(false)
const formRef = ref(null)
const editingId = ref(null)

const form = reactive({
  username: '',
  realName: '',
  role: 'STUDENT',
  phone: '',
  email: '',
  status: 1,
  studentNo: '',
  gender: '',
  clazzId: null,
  teacherNo: '',
  title: '',
  department: ''
})

const phoneValidator = (rule, value, callback) => {
  if (!value) return callback()
  if (!/^1[3-9]\d{9}$/.test(value)) {
    return callback(new Error('请输入正确的手机号'))
  }
  return callback()
}

const emailValidator = (rule, value, callback) => {
  if (!value) return callback()
  if (!/^[\w.-]+@[\w-]+(\.[\w-]+)+$/.test(value)) {
    return callback(new Error('请输入正确的邮箱'))
  }
  return callback()
}

const formRules = computed(() => {
  const base = {
    username: [
      { required: true, message: '请输入用户名', trigger: 'blur' },
      { min: 2, max: 32, message: '用户名长度为 2~32 个字符', trigger: 'blur' }
    ],
    realName: [{ required: true, message: '请输入姓名', trigger: 'blur' }],
    phone: [{ validator: phoneValidator, trigger: 'blur' }],
    email: [{ validator: emailValidator, trigger: 'blur' }]
  }
  if (activeTab.value === 'STUDENT') {
    base.studentNo = [{ required: true, message: '请输入学号', trigger: 'blur' }]
    base.gender = [{ required: true, message: '请选择性别', trigger: 'change' }]
    base.clazzId = [{ required: true, message: '请选择班级', trigger: 'change' }]
  } else if (activeTab.value === 'TEACHER') {
    base.teacherNo = [{ required: true, message: '请输入工号', trigger: 'blur' }]
  }
  return base
})

function clazzLabel(item) {
  const suffix = [item.majorName, item.gradeName].filter(Boolean).join(' / ')
  return suffix ? `${item.name}（${suffix}）` : item.name
}

async function loadList() {
  loading.value = true
  try {
    const res = await fetchUsers({
      role: activeTab.value,
      keyword: keyword.value.trim() || undefined,
      page: page.value,
      size: size.value
    })
    const data = res?.data || {}
    listData.value = data
    total.value = data.total ?? 0
  } catch (e) {
    listData.value = { total: 0, page: 1, size: size.value, records: [] }
    total.value = 0
  } finally {
    loading.value = false
  }
}

async function loadClazzs() {
  if (clazzOptions.value.length) return
  try {
    const res = await fetchClazzOptions()
    clazzOptions.value = res?.data || []
  } catch (e) {
    clazzOptions.value = []
  }
}

function handleSearch() {
  page.value = 1
  loadList()
}

function handleReset() {
  keyword.value = ''
  page.value = 1
  loadList()
}

function onTabChange() {
  keyword.value = ''
  page.value = 1
  loadList()
}

function resetForm() {
  form.username = ''
  form.realName = ''
  form.role = activeTab.value
  form.phone = ''
  form.email = ''
  form.status = 1
  form.studentNo = ''
  form.gender = ''
  form.clazzId = null
  form.teacherNo = ''
  form.title = ''
  form.department = ''
}

function openCreate() {
  resetForm()
  isEdit.value = false
  editingId.value = null
  dialogTitle.value = `新增${currentTabLabel.value}`
  dialogVisible.value = true
  if (activeTab.value === 'STUDENT') loadClazzs()
}

function openEdit(row) {
  resetForm()
  isEdit.value = true
  editingId.value = row.id
  dialogTitle.value = `编辑${currentTabLabel.value}`
  form.username = row.username || ''
  form.realName = row.realName || ''
  form.role = row.role || activeTab.value
  form.phone = row.phone || ''
  form.email = row.email || ''
  form.status = row.status ?? 1
  form.studentNo = row.studentNo || ''
  form.gender = row.gender || ''
  form.clazzId = row.clazzId ?? null
  form.teacherNo = row.teacherNo || ''
  form.title = row.title || ''
  form.department = row.department || ''
  dialogVisible.value = true
  if (activeTab.value === 'STUDENT') loadClazzs()
}

function onDialogClosed() {
  formRef.value?.clearValidate()
}

function buildPayload() {
  const payload = {
    username: form.username.trim(),
    realName: form.realName.trim(),
    role: activeTab.value,
    phone: form.phone.trim() || null,
    email: form.email.trim() || null,
    status: form.status,
    studentNo: null,
    gender: null,
    clazzId: null,
    teacherNo: null,
    title: null,
    department: null
  }
  if (activeTab.value === 'STUDENT') {
    payload.studentNo = form.studentNo.trim() || null
    payload.gender = form.gender || null
    payload.clazzId = form.clazzId ?? null
  } else if (activeTab.value === 'TEACHER') {
    payload.teacherNo = form.teacherNo.trim() || null
    payload.title = form.title.trim() || null
    payload.department = form.department.trim() || null
  }
  return payload
}

async function handleSubmit() {
  if (!formRef.value) return
  try {
    await formRef.value.validate()
  } catch (e) {
    return
  }

  submitLoading.value = true
  try {
    const payload = buildPayload()
    if (isEdit.value) {
      await updateUser(editingId.value, payload)
      ElMessage.success('修改成功')
    } else {
      await createUser(payload)
      ElMessage.success('新增成功')
    }
    dialogVisible.value = false
    loadList()
  } catch (e) {
    // 拦截器已提示
  } finally {
    submitLoading.value = false
  }
}

async function handleStatusChange(row, val) {
  statusLoadingId.value = row.id
  try {
    await updateUserStatus(row.id, val)
    row.status = val
    ElMessage.success(val === 1 ? '已启用' : '已停用')
  } catch (e) {
    // 失败时保持原值（row.status 未被修改）
  } finally {
    statusLoadingId.value = null
  }
}

async function handleResetPassword(row) {
  try {
    await ElMessageBox.confirm(
      `确定要重置「${row.realName || row.username}」的密码吗？`,
      '重置密码',
      { confirmButtonText: '确定', cancelButtonText: '取消', type: 'warning' }
    )
  } catch (e) {
    return
  }

  try {
    const res = await resetUserPassword(row.id)
    const password = res?.data?.password ?? '123456'
    ElMessageBox.alert(`默认密码为：${password}`, '重置成功', {
      confirmButtonText: '我知道了',
      type: 'success'
    })
  } catch (e) {
    // 拦截器已提示
  }
}

async function handleDelete(row) {
  try {
    await ElMessageBox.confirm(
      `确定要删除「${row.realName || row.username}」吗？此操作不可恢复。`,
      '删除确认',
      { confirmButtonText: '确定删除', cancelButtonText: '取消', type: 'warning' }
    )
  } catch (e) {
    return
  }

  try {
    await deleteUser(row.id)
    ElMessage.success('删除成功')
    const maxPage = Math.max(1, Math.ceil((total.value - 1) / size.value))
    if (page.value > maxPage) page.value = maxPage
    loadList()
  } catch (e) {
    // 拦截器已提示
  }
}

onMounted(loadList)
</script>
