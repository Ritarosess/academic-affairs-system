<template>
  <div class="page-container">
    <el-alert
      v-if="currentSemester"
      :type="windowOpen ? 'success' : 'info'"
      :closable="false"
      class="page-card"
      show-icon
    >
      <template #title>
        当前学期：<strong>{{ currentSemester.name }}</strong>
        <el-tag :type="windowOpen ? 'success' : 'info'" size="small" class="ml-8">
          选课窗口{{ windowOpen ? '开放中' : '未开放' }}
        </el-tag>
      </template>
      <div class="text-muted mt-8">
        选课时段：{{ currentSemester.selectStartTime || '-' }} ~
        {{ currentSemester.selectEndTime || '-' }}
      </div>
      <div class="text-muted mt-8">
        退课时段：{{ currentSemester.selectStartTime || '-' }} ~
        {{ currentSemester.dropEndTime || currentSemester.selectEndTime || '-' }}
        <span class="ml-8">（必修课不可退课）</span>
      </div>
    </el-alert>

    <el-card shadow="never">
      <div class="toolbar">
        <span class="text-muted">共 {{ semesters.length }} 个学期</span>
        <div class="toolbar-spacer" />
        <el-button type="primary" @click="openDialog()">
          <el-icon><Plus /></el-icon>新增学期
        </el-button>
      </div>

      <el-table v-loading="loading" :data="semesters" border stripe empty-text="暂无数据">
        <el-table-column prop="id" label="ID" width="80" align="center" />
        <el-table-column prop="name" label="学期名称" min-width="190" />
        <el-table-column prop="startDate" label="开始日期" width="130" align="center" />
        <el-table-column prop="endDate" label="结束日期" width="130" align="center" />
        <el-table-column prop="selectStartTime" label="选课开始" min-width="170" />
        <el-table-column prop="selectEndTime" label="选课结束" min-width="170" />
        <el-table-column label="退课截止" min-width="170">
          <template #default="{ row }">{{ row.dropEndTime || row.selectEndTime || '-' }}</template>
        </el-table-column>
        <el-table-column label="当前学期" width="100" align="center">
          <template #default="{ row }">
            <el-tag v-if="Number(row.isCurrent) === 1" type="success" size="small">是</el-tag>
            <span v-else class="text-muted">否</span>
          </template>
        </el-table-column>
        <el-table-column label="选课窗口" width="110" align="center">
          <template #default="{ row }">
            <el-tag :type="isWindowOpen(row) ? 'success' : 'info'" size="small">
              {{ isWindowOpen(row) ? '开放中' : '未开放' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="220" fixed="right" align="center">
          <template #default="{ row }">
            <el-button type="primary" link @click="openDialog(row)">编辑</el-button>
            <el-button
              type="success"
              link
              :disabled="Number(row.isCurrent) === 1"
              @click="handleSetCurrent(row)"
            >
              设为当前
            </el-button>
            <el-button type="danger" link @click="handleDelete(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <el-dialog
      v-model="dialogVisible"
      :title="form.id ? '编辑学期' : '新增学期'"
      width="620px"
      :close-on-click-modal="false"
      @closed="formRef?.clearValidate()"
    >
      <el-form ref="formRef" :model="form" :rules="rules" label-width="110px">
        <el-form-item label="学期名称" prop="name">
          <el-input v-model="form.name" placeholder="如：2025-2026学年第一学期" />
        </el-form-item>
        <el-row :gutter="16">
          <el-col :span="12">
            <el-form-item label="开始日期" prop="startDate">
              <el-date-picker
                v-model="form.startDate"
                type="date"
                value-format="YYYY-MM-DD"
                placeholder="选择开始日期"
                style="width: 100%"
              />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="结束日期" prop="endDate">
              <el-date-picker
                v-model="form.endDate"
                type="date"
                value-format="YYYY-MM-DD"
                placeholder="选择结束日期"
                style="width: 100%"
              />
            </el-form-item>
          </el-col>
        </el-row>
        <el-form-item label="选课开始时间" prop="selectStartTime">
          <el-date-picker
            v-model="form.selectStartTime"
            type="datetime"
            value-format="YYYY-MM-DD HH:mm:ss"
            placeholder="选择选课开始时间"
            style="width: 100%"
          />
        </el-form-item>
        <el-form-item label="选课结束时间" prop="selectEndTime">
          <el-date-picker
            v-model="form.selectEndTime"
            type="datetime"
            value-format="YYYY-MM-DD HH:mm:ss"
            placeholder="选择选课结束时间"
            style="width: 100%"
          />
        </el-form-item>
        <el-form-item label="退课截止时间" prop="dropEndTime">
          <el-date-picker
            v-model="form.dropEndTime"
            type="datetime"
            value-format="YYYY-MM-DD HH:mm:ss"
            placeholder="选填，不填则与选课结束时间一致"
            clearable
            style="width: 100%"
          />
        </el-form-item>
        <el-form-item label="设为当前学期" prop="isCurrent">
          <el-switch
            v-model="form.isCurrent"
            :active-value="1"
            :inactive-value="0"
            active-text="是"
            inactive-text="否"
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitting" @click="handleSubmit">确定</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  createSemester,
  deleteSemester,
  fetchSemesters,
  setCurrentSemester,
  updateSemester
} from '@/api/adminSemester'

const semesters = ref([])
const loading = ref(false)

const dialogVisible = ref(false)
const submitting = ref(false)
const formRef = ref(null)

const form = reactive({
  id: null,
  name: '',
  startDate: '',
  endDate: '',
  selectStartTime: '',
  selectEndTime: '',
  dropEndTime: '',
  isCurrent: 0
})

const currentSemester = computed(() =>
  semesters.value.find((item) => Number(item.isCurrent) === 1)
)

/** 解析 'yyyy-MM-dd HH:mm:ss'，兼容后端可能返回 ISO 格式 */
function parseTime(value) {
  if (!value) return null
  const normalized = String(value).replace(/-/g, '/').replace('T', ' ').replace(/\.\d+.*$/, '')
  const time = new Date(normalized).getTime()
  return Number.isNaN(time) ? null : time
}

function isWindowOpen(row) {
  const start = parseTime(row?.selectStartTime)
  const end = parseTime(row?.selectEndTime)
  if (start == null || end == null) return false
  const now = Date.now()
  return now >= start && now <= end
}

const windowOpen = computed(() => isWindowOpen(currentSemester.value))

const validateDates = (rule, value, callback) => {
  if (form.startDate && form.endDate && form.endDate < form.startDate) {
    return callback(new Error('结束日期不能早于开始日期'))
  }
  return callback()
}

const validateSelectTime = (rule, value, callback) => {
  if (
    form.selectStartTime &&
    form.selectEndTime &&
    form.selectEndTime <= form.selectStartTime
  ) {
    return callback(new Error('选课结束时间必须晚于开始时间'))
  }
  if (
    form.selectStartTime &&
    form.dropEndTime &&
    form.dropEndTime <= form.selectStartTime
  ) {
    return callback(new Error('退课截止时间必须晚于选课开始时间'))
  }
  return callback()
}

const rules = {
  name: [{ required: true, message: '请输入学期名称', trigger: 'blur' }],
  startDate: [
    { required: true, message: '请选择开始日期', trigger: 'change' },
    { validator: validateDates, trigger: 'change' }
  ],
  endDate: [
    { required: true, message: '请选择结束日期', trigger: 'change' },
    { validator: validateDates, trigger: 'change' }
  ],
  selectStartTime: [
    { required: true, message: '请选择选课开始时间', trigger: 'change' },
    { validator: validateSelectTime, trigger: 'change' }
  ],
  selectEndTime: [
    { required: true, message: '请选择选课结束时间', trigger: 'change' },
    { validator: validateSelectTime, trigger: 'change' }
  ],
  dropEndTime: [
    { validator: validateSelectTime, trigger: 'change' }
  ]
}

async function loadList() {
  loading.value = true
  try {
    const res = await fetchSemesters()
    semesters.value = res?.data || []
  } catch (e) {
    semesters.value = []
  } finally {
    loading.value = false
  }
}

function openDialog(row) {
  form.id = row?.id ?? null
  form.name = row?.name || ''
  form.startDate = row?.startDate || ''
  form.endDate = row?.endDate || ''
  form.selectStartTime = row?.selectStartTime || ''
  form.selectEndTime = row?.selectEndTime || ''
  form.dropEndTime = row?.dropEndTime || ''
  form.isCurrent = row?.isCurrent ?? 0
  dialogVisible.value = true
}

async function handleSubmit() {
  if (!formRef.value) return
  try {
    await formRef.value.validate()
  } catch (e) {
    return
  }

  submitting.value = true
  try {
    const payload = {
      name: form.name.trim(),
      startDate: form.startDate,
      endDate: form.endDate,
      selectStartTime: form.selectStartTime,
      selectEndTime: form.selectEndTime,
      dropEndTime: form.dropEndTime || null,
      isCurrent: form.isCurrent
    }
    if (form.id) {
      await updateSemester(form.id, payload)
      ElMessage.success('修改成功')
    } else {
      await createSemester(payload)
      ElMessage.success('新增成功')
    }
    dialogVisible.value = false
    loadList()
  } catch (e) {
    /* 拦截器已提示 */
  } finally {
    submitting.value = false
  }
}

async function handleSetCurrent(row) {
  try {
    await ElMessageBox.confirm(
      `确定将「${row.name}」设为当前学期吗？`,
      '设为当前学期',
      { confirmButtonText: '确定', cancelButtonText: '取消', type: 'warning' }
    )
  } catch (e) {
    return
  }
  try {
    await setCurrentSemester(row.id)
    ElMessage.success('已设为当前学期')
    loadList()
  } catch (e) {
    /* 拦截器已提示 */
  }
}

async function handleDelete(row) {
  try {
    await ElMessageBox.confirm(
      `确定要删除学期「${row.name}」吗？此操作不可恢复。`,
      '删除确认',
      { confirmButtonText: '确定删除', cancelButtonText: '取消', type: 'warning' }
    )
  } catch (e) {
    return
  }
  try {
    await deleteSemester(row.id)
    ElMessage.success('删除成功')
    loadList()
  } catch (e) {
    /* 拦截器已提示 */
  }
}

onMounted(loadList)
</script>

<style scoped>
.ml-8 {
  margin-left: 8px;
}
</style>
