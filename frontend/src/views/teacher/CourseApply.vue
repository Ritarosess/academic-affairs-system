<template>
  <div class="page-container">
    <el-card shadow="never">
      <div class="toolbar">
        <span class="page-tip">课程由本人申报，提交后需经管理员审核；通过后才会对学生开放选课。</span>
        <div class="toolbar-spacer" />
        <el-button type="primary" @click="openCreate">
          <el-icon><Plus /></el-icon>申报课程
        </el-button>
      </div>

      <PageTable
        v-model:page="page"
        v-model:size="size"
        :data="list"
        :loading="loading"
        :total="list.length"
        mode="client"
        row-key="id"
      >
        <el-table-column type="index" label="#" width="56" align="center" />
        <el-table-column prop="courseNo" label="课程号" width="110" fixed="left" />
        <el-table-column prop="name" label="课程名称" min-width="150" show-overflow-tooltip />
        <el-table-column prop="credit" label="学分" width="70" align="center" />
        <el-table-column label="性质" width="80" align="center">
          <template #default="{ row }">
            <el-tag :type="courseTypeTag(row.courseType)" size="small">{{ row.courseType }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="semesterName" label="学期" min-width="170" show-overflow-tooltip />
        <el-table-column label="上课安排" min-width="160">
          <template #default="{ row }">{{ formatSchedule(row) }}</template>
        </el-table-column>
        <el-table-column label="审核状态" width="100" align="center">
          <template #default="{ row }">
            <el-tag :type="courseAuditTag(row.auditStatus)" size="small">
              {{ row.auditStatusLabel || courseAuditText(row.auditStatus) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="审核意见" min-width="140" show-overflow-tooltip>
          <template #default="{ row }">{{ row.reviewRemark || '—' }}</template>
        </el-table-column>
        <el-table-column label="操作" width="150" fixed="right" align="center">
          <template #default="{ row }">
            <template v-if="row.auditStatus === 'APPROVED'">
              <el-button type="info" link disabled title="已通过审核，不可修改">已生效</el-button>
            </template>
            <template v-else>
              <el-button type="primary" link @click="openEdit(row)">修改</el-button>
              <el-button type="danger" link @click="handleDelete(row)">删除</el-button>
            </template>
          </template>
        </el-table-column>
      </PageTable>
    </el-card>

    <el-dialog
      v-model="dialogVisible"
      :title="dialogTitle"
      width="720px"
      :close-on-click-modal="false"
      @closed="onDialogClosed"
    >
      <el-form ref="formRef" :model="form" :rules="formRules" label-width="100px">
        <el-row :gutter="16">
          <el-col :span="12">
            <el-form-item label="课程号" prop="courseNo">
              <el-input v-model="form.courseNo" placeholder="如：CS101" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="课程名称" prop="name">
              <el-input v-model="form.name" placeholder="如：数据结构" />
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="学分" prop="credit">
              <el-input-number v-model="form.credit" :min="0.5" :max="20" :step="0.5" :precision="1" style="width:100%" />
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="学时" prop="hours">
              <el-input-number v-model="form.hours" :min="1" :max="500" :step="1" style="width:100%" />
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="课程性质" prop="courseType">
              <el-select v-model="form.courseType" placeholder="请选择" style="width:100%">
                <el-option
                  v-for="item in COURSE_TYPE_OPTIONS"
                  :key="item.value"
                  :label="item.label"
                  :value="item.value"
                />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="开课学期" prop="semesterId">
              <el-select v-model="form.semesterId" placeholder="请选择学期" style="width:100%">
                <el-option
                  v-for="item in semesterOptions"
                  :key="item.id"
                  :label="item.name"
                  :value="item.id"
                />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="容量" prop="capacity">
              <el-input-number v-model="form.capacity" :min="1" :max="1000" :step="1" style="width:100%" />
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="上课星期" prop="weekday">
              <el-select v-model="form.weekday" placeholder="请选择" clearable style="width:100%">
                <el-option
                  v-for="item in WEEKDAY_OPTIONS"
                  :key="item.value"
                  :label="item.label"
                  :value="item.value"
                />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="开始节次" prop="startSection">
              <el-select v-model="form.startSection" placeholder="请选择" clearable style="width:100%">
                <el-option
                  v-for="item in SECTION_OPTIONS"
                  :key="item.value"
                  :label="item.label"
                  :value="item.value"
                />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="结束节次" prop="endSection">
              <el-select v-model="form.endSection" placeholder="请选择" clearable style="width:100%">
                <el-option
                  v-for="item in SECTION_OPTIONS"
                  :key="item.value"
                  :label="item.label"
                  :value="item.value"
                />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="上课地点" prop="location">
              <el-input v-model="form.location" placeholder="如：教一101" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="通过后选课" prop="status">
              <el-radio-group v-model="form.status">
                <el-radio :value="1">开放</el-radio>
                <el-radio :value="0">关闭</el-radio>
              </el-radio-group>
            </el-form-item>
          </el-col>
          <el-col :span="24">
            <el-form-item label="课程简介" prop="description">
              <el-input
                v-model="form.description"
                type="textarea"
                :rows="3"
                maxlength="500"
                show-word-limit
                placeholder="选填"
              />
            </el-form-item>
          </el-col>
        </el-row>
      </el-form>

      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitLoading" @click="handleSubmit">
          {{ isEdit ? '重新提交审核' : '提交申报' }}
        </el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import PageTable from '@/components/PageTable.vue'
import {
  createCourseApply,
  deleteCourseApply,
  fetchCourseApplies,
  updateCourseApply
} from '@/api/teacher'
import { fetchSemesterOptions } from '@/api/meta'
import {
  COURSE_TYPE_OPTIONS,
  SECTION_OPTIONS,
  WEEKDAY_OPTIONS,
  courseAuditTag,
  courseAuditText,
  courseTypeTag,
  formatSchedule
} from '@/utils/dict'

const page = ref(1)
const size = ref(10)
const list = ref([])
const loading = ref(false)
const semesterOptions = ref([])

const dialogVisible = ref(false)
const dialogTitle = ref('申报课程')
const isEdit = ref(false)
const editingId = ref(null)
const submitLoading = ref(false)
const formRef = ref(null)

const form = reactive({
  courseNo: '',
  name: '',
  credit: 1,
  hours: 16,
  courseType: '必修',
  semesterId: null,
  capacity: 60,
  weekday: null,
  startSection: null,
  endSection: null,
  location: '',
  description: '',
  status: 1
})

const validateSections = (rule, value, callback) => {
  if (form.startSection && form.endSection && form.endSection < form.startSection) {
    return callback(new Error('结束节次不能早于开始节次'))
  }
  return callback()
}

const formRules = {
  courseNo: [{ required: true, message: '请输入课程号', trigger: 'blur' }],
  name: [{ required: true, message: '请输入课程名称', trigger: 'blur' }],
  credit: [{ required: true, message: '请输入学分', trigger: 'blur' }],
  hours: [{ required: true, message: '请输入学时', trigger: 'blur' }],
  courseType: [{ required: true, message: '请选择课程性质', trigger: 'change' }],
  semesterId: [{ required: true, message: '请选择学期', trigger: 'change' }],
  capacity: [{ required: true, message: '请输入容量', trigger: 'blur' }],
  endSection: [{ validator: validateSections, trigger: 'change' }]
}

async function loadList() {
  loading.value = true
  try {
    const res = await fetchCourseApplies()
    list.value = res.data || []
  } catch (e) {
    list.value = []
  } finally {
    loading.value = false
  }
}

async function loadOptions() {
  try {
    const res = await fetchSemesterOptions()
    semesterOptions.value = res?.data || []
  } catch (e) {
    /* 拦截器已提示 */
  }
}

function currentSemesterId() {
  const cur = semesterOptions.value.find((s) => s.isCurrent === 1 || s.isCurrent === true)
  return cur ? cur.id : (semesterOptions.value[0]?.id ?? null)
}

function resetForm() {
  form.courseNo = ''
  form.name = ''
  form.credit = 1
  form.hours = 16
  form.courseType = '必修'
  form.semesterId = currentSemesterId()
  form.capacity = 60
  form.weekday = null
  form.startSection = null
  form.endSection = null
  form.location = ''
  form.description = ''
  form.status = 1
}

function openCreate() {
  resetForm()
  isEdit.value = false
  editingId.value = null
  dialogTitle.value = '申报课程'
  dialogVisible.value = true
}

function openEdit(row) {
  resetForm()
  isEdit.value = true
  editingId.value = row.id
  dialogTitle.value = `修改课程（${row.auditStatusLabel || courseAuditText(row.auditStatus)}）`
  form.courseNo = row.courseNo || ''
  form.name = row.name || ''
  form.credit = row.credit ?? 1
  form.hours = row.hours ?? 16
  form.courseType = row.courseType || '必修'
  form.semesterId = row.semesterId ?? currentSemesterId()
  form.capacity = row.capacity ?? 60
  form.weekday = row.weekday ?? null
  form.startSection = row.startSection ?? null
  form.endSection = row.endSection ?? null
  form.location = row.location || ''
  form.description = row.description || ''
  form.status = row.status ?? 1
  dialogVisible.value = true
}

function onDialogClosed() {
  formRef.value?.clearValidate()
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
    const payload = {
      courseNo: form.courseNo.trim(),
      name: form.name.trim(),
      credit: form.credit,
      hours: form.hours,
      courseType: form.courseType,
      semesterId: form.semesterId,
      capacity: form.capacity,
      weekday: form.weekday,
      startSection: form.startSection,
      endSection: form.endSection,
      location: form.location.trim(),
      description: form.description.trim() || null,
      status: form.status
    }
    if (isEdit.value) {
      await updateCourseApply(editingId.value, payload)
      ElMessage.success('已重新提交，请等待管理员审核')
    } else {
      await createCourseApply(payload)
      ElMessage.success('申报已提交，请等待管理员审核')
    }
    dialogVisible.value = false
    loadList()
  } catch (e) {
    /* 拦截器已提示 */
  } finally {
    submitLoading.value = false
  }
}

async function handleDelete(row) {
  try {
    await ElMessageBox.confirm(`确定删除课程「${row.name}」吗？此操作不可恢复。`, '删除确认', {
      confirmButtonText: '确定删除',
      cancelButtonText: '取消',
      type: 'warning'
    })
  } catch (e) {
    return
  }
  try {
    await deleteCourseApply(row.id)
    ElMessage.success('已删除')
    loadList()
  } catch (e) {
    /* 拦截器已提示 */
  }
}

onMounted(async () => {
  await loadOptions()
  loadList()
})
</script>

<style scoped>
.page-tip {
  font-size: 13px;
  color: #909399;
}
</style>
