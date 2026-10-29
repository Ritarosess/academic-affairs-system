<template>
  <div class="page-container">
    <el-card shadow="never">
      <el-tabs v-model="activeTab">
        <!-- 年级 -->
        <el-tab-pane label="年级" name="GRADE">
          <div class="toolbar">
            <span class="text-muted">共 {{ grades.length }} 个年级</span>
            <div class="toolbar-spacer" />
            <el-button type="primary" @click="openGradeDialog()">
              <el-icon><Plus /></el-icon>新增年级
            </el-button>
          </div>

          <el-table v-loading="gradeLoading" :data="grades" border stripe empty-text="暂无数据">
            <el-table-column prop="id" label="ID" width="80" align="center" />
            <el-table-column prop="name" label="年级名称" min-width="160" />
            <el-table-column prop="enrollYear" label="入学年份" min-width="120" align="center" />
            <el-table-column label="操作" width="150" align="center">
              <template #default="{ row }">
                <el-button type="primary" link @click="openGradeDialog(row)">编辑</el-button>
                <el-button type="danger" link @click="handleDeleteGrade(row)">删除</el-button>
              </template>
            </el-table-column>
          </el-table>
        </el-tab-pane>

        <!-- 专业 -->
        <el-tab-pane label="专业" name="MAJOR">
          <div class="toolbar">
            <span class="text-muted">共 {{ majors.length }} 个专业</span>
            <div class="toolbar-spacer" />
            <el-button type="primary" @click="openMajorDialog()">
              <el-icon><Plus /></el-icon>新增专业
            </el-button>
          </div>

          <el-table v-loading="majorLoading" :data="majors" border stripe empty-text="暂无数据">
            <el-table-column prop="id" label="ID" width="80" align="center" />
            <el-table-column prop="name" label="专业名称" min-width="200" />
            <el-table-column prop="department" label="所属院系" min-width="180" />
            <el-table-column label="操作" width="150" align="center">
              <template #default="{ row }">
                <el-button type="primary" link @click="openMajorDialog(row)">编辑</el-button>
                <el-button type="danger" link @click="handleDeleteMajor(row)">删除</el-button>
              </template>
            </el-table-column>
          </el-table>
        </el-tab-pane>

        <!-- 班级 -->
        <el-tab-pane label="班级" name="CLAZZ">
          <div class="toolbar">
            <span class="text-muted">共 {{ clazzs.length }} 个班级</span>
            <div class="toolbar-spacer" />
            <el-button type="primary" @click="openClazzDialog()">
              <el-icon><Plus /></el-icon>新增班级
            </el-button>
          </div>

          <el-table v-loading="clazzLoading" :data="clazzs" border stripe empty-text="暂无数据">
            <el-table-column prop="id" label="ID" width="80" align="center" />
            <el-table-column prop="name" label="班级名称" min-width="140" />
            <el-table-column prop="majorName" label="专业" min-width="180" />
            <el-table-column prop="gradeName" label="年级" min-width="120" />
            <el-table-column prop="counselor" label="辅导员" min-width="120" />
            <el-table-column label="操作" width="150" align="center">
              <template #default="{ row }">
                <el-button type="primary" link @click="openClazzDialog(row)">编辑</el-button>
                <el-button type="danger" link @click="handleDeleteClazz(row)">删除</el-button>
              </template>
            </el-table-column>
          </el-table>
        </el-tab-pane>
      </el-tabs>
    </el-card>

    <!-- 年级弹窗 -->
    <el-dialog
      v-model="gradeDialogVisible"
      :title="gradeForm.id ? '编辑年级' : '新增年级'"
      width="460px"
      :close-on-click-modal="false"
      @closed="gradeFormRef?.clearValidate()"
    >
      <el-form ref="gradeFormRef" :model="gradeForm" :rules="gradeRules" label-width="90px">
        <el-form-item label="年级名称" prop="name">
          <el-input v-model="gradeForm.name" placeholder="如：2023级" />
        </el-form-item>
        <el-form-item label="入学年份" prop="enrollYear">
          <el-input-number
            v-model="gradeForm.enrollYear"
            :min="1990"
            :max="2100"
            :step="1"
            style="width: 100%"
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="gradeDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="gradeSubmitting" @click="submitGrade">确定</el-button>
      </template>
    </el-dialog>

    <!-- 专业弹窗 -->
    <el-dialog
      v-model="majorDialogVisible"
      :title="majorForm.id ? '编辑专业' : '新增专业'"
      width="460px"
      :close-on-click-modal="false"
      @closed="majorFormRef?.clearValidate()"
    >
      <el-form ref="majorFormRef" :model="majorForm" :rules="majorRules" label-width="90px">
        <el-form-item label="专业名称" prop="name">
          <el-input v-model="majorForm.name" placeholder="如：计算机科学与技术" />
        </el-form-item>
        <el-form-item label="所属院系" prop="department">
          <el-input v-model="majorForm.department" placeholder="如：计算机学院" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="majorDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="majorSubmitting" @click="submitMajor">确定</el-button>
      </template>
    </el-dialog>

    <!-- 班级弹窗 -->
    <el-dialog
      v-model="clazzDialogVisible"
      :title="clazzForm.id ? '编辑班级' : '新增班级'"
      width="520px"
      :close-on-click-modal="false"
      @closed="clazzFormRef?.clearValidate()"
    >
      <el-form ref="clazzFormRef" :model="clazzForm" :rules="clazzRules" label-width="90px">
        <el-form-item label="班级名称" prop="name">
          <el-input v-model="clazzForm.name" placeholder="如：计科2301" />
        </el-form-item>
        <el-form-item label="所属专业" prop="majorId">
          <el-select v-model="clazzForm.majorId" placeholder="请选择专业" filterable style="width: 100%">
            <el-option
              v-for="item in majorOptions"
              :key="item.id"
              :label="item.name"
              :value="item.id"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="所属年级" prop="gradeId">
          <el-select v-model="clazzForm.gradeId" placeholder="请选择年级" style="width: 100%">
            <el-option
              v-for="item in gradeOptions"
              :key="item.id"
              :label="item.name"
              :value="item.id"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="辅导员" prop="counselor">
          <el-input v-model="clazzForm.counselor" placeholder="如：王老师" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="clazzDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="clazzSubmitting" @click="submitClazz">确定</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  createClazz,
  createGrade,
  createMajor,
  deleteClazz,
  deleteGrade,
  deleteMajor,
  fetchClazzs,
  fetchGrades,
  fetchMajors,
  updateClazz,
  updateGrade,
  updateMajor
} from '@/api/adminOrg'
import { fetchGradeOptions, fetchMajorOptions } from '@/api/meta'

const activeTab = ref('GRADE')

/* ---------------- 年级 ---------------- */
const grades = ref([])
const gradeLoading = ref(false)
const gradeDialogVisible = ref(false)
const gradeSubmitting = ref(false)
const gradeFormRef = ref(null)
const gradeForm = reactive({ id: null, name: '', enrollYear: new Date().getFullYear() })
const gradeRules = {
  name: [{ required: true, message: '请输入年级名称', trigger: 'blur' }],
  enrollYear: [{ required: true, message: '请输入入学年份', trigger: 'blur' }]
}

async function loadGrades() {
  gradeLoading.value = true
  try {
    const res = await fetchGrades()
    grades.value = res?.data || []
  } catch (e) {
    grades.value = []
  } finally {
    gradeLoading.value = false
  }
}

function openGradeDialog(row) {
  gradeForm.id = row?.id ?? null
  gradeForm.name = row?.name || ''
  gradeForm.enrollYear = row?.enrollYear ?? new Date().getFullYear()
  gradeDialogVisible.value = true
}

async function submitGrade() {
  if (!gradeFormRef.value) return
  try {
    await gradeFormRef.value.validate()
  } catch (e) {
    return
  }
  gradeSubmitting.value = true
  try {
    const payload = { name: gradeForm.name.trim(), enrollYear: gradeForm.enrollYear }
    if (gradeForm.id) {
      await updateGrade(gradeForm.id, payload)
      ElMessage.success('修改成功')
    } else {
      await createGrade(payload)
      ElMessage.success('新增成功')
    }
    gradeDialogVisible.value = false
    loadGrades()
    loadGradeOptions()
  } catch (e) {
    /* 拦截器已提示 */
  } finally {
    gradeSubmitting.value = false
  }
}

async function handleDeleteGrade(row) {
  try {
    await ElMessageBox.confirm(`确定要删除年级「${row.name}」吗？`, '删除确认', {
      confirmButtonText: '确定删除',
      cancelButtonText: '取消',
      type: 'warning'
    })
  } catch (e) {
    return
  }
  try {
    await deleteGrade(row.id)
    ElMessage.success('删除成功')
    loadGrades()
    loadGradeOptions()
  } catch (e) {
    /* 拦截器已提示 */
  }
}

/* ---------------- 专业 ---------------- */
const majors = ref([])
const majorLoading = ref(false)
const majorDialogVisible = ref(false)
const majorSubmitting = ref(false)
const majorFormRef = ref(null)
const majorForm = reactive({ id: null, name: '', department: '' })
const majorRules = {
  name: [{ required: true, message: '请输入专业名称', trigger: 'blur' }],
  department: [{ required: true, message: '请输入所属院系', trigger: 'blur' }]
}

async function loadMajors() {
  majorLoading.value = true
  try {
    const res = await fetchMajors()
    majors.value = res?.data || []
  } catch (e) {
    majors.value = []
  } finally {
    majorLoading.value = false
  }
}

function openMajorDialog(row) {
  majorForm.id = row?.id ?? null
  majorForm.name = row?.name || ''
  majorForm.department = row?.department || ''
  majorDialogVisible.value = true
}

async function submitMajor() {
  if (!majorFormRef.value) return
  try {
    await majorFormRef.value.validate()
  } catch (e) {
    return
  }
  majorSubmitting.value = true
  try {
    const payload = { name: majorForm.name.trim(), department: majorForm.department.trim() }
    if (majorForm.id) {
      await updateMajor(majorForm.id, payload)
      ElMessage.success('修改成功')
    } else {
      await createMajor(payload)
      ElMessage.success('新增成功')
    }
    majorDialogVisible.value = false
    loadMajors()
    loadMajorOptions()
  } catch (e) {
    /* 拦截器已提示 */
  } finally {
    majorSubmitting.value = false
  }
}

async function handleDeleteMajor(row) {
  try {
    await ElMessageBox.confirm(`确定要删除专业「${row.name}」吗？`, '删除确认', {
      confirmButtonText: '确定删除',
      cancelButtonText: '取消',
      type: 'warning'
    })
  } catch (e) {
    return
  }
  try {
    await deleteMajor(row.id)
    ElMessage.success('删除成功')
    loadMajors()
    loadMajorOptions()
  } catch (e) {
    /* 拦截器已提示 */
  }
}

/* ---------------- 班级 ---------------- */
const clazzs = ref([])
const clazzLoading = ref(false)
const clazzDialogVisible = ref(false)
const clazzSubmitting = ref(false)
const clazzFormRef = ref(null)
const majorOptions = ref([])
const gradeOptions = ref([])
const clazzForm = reactive({ id: null, name: '', majorId: null, gradeId: null, counselor: '' })
const clazzRules = {
  name: [{ required: true, message: '请输入班级名称', trigger: 'blur' }],
  majorId: [{ required: true, message: '请选择所属专业', trigger: 'change' }],
  gradeId: [{ required: true, message: '请选择所属年级', trigger: 'change' }]
}

async function loadClazzs() {
  clazzLoading.value = true
  try {
    const res = await fetchClazzs()
    clazzs.value = res?.data || []
  } catch (e) {
    clazzs.value = []
  } finally {
    clazzLoading.value = false
  }
}

async function loadMajorOptions() {
  try {
    const res = await fetchMajorOptions()
    majorOptions.value = res?.data || []
  } catch (e) {
    majorOptions.value = []
  }
}

async function loadGradeOptions() {
  try {
    const res = await fetchGradeOptions()
    gradeOptions.value = res?.data || []
  } catch (e) {
    gradeOptions.value = []
  }
}

function openClazzDialog(row) {
  clazzForm.id = row?.id ?? null
  clazzForm.name = row?.name || ''
  clazzForm.majorId = row?.majorId ?? null
  clazzForm.gradeId = row?.gradeId ?? null
  clazzForm.counselor = row?.counselor || ''
  clazzDialogVisible.value = true
}

async function submitClazz() {
  if (!clazzFormRef.value) return
  try {
    await clazzFormRef.value.validate()
  } catch (e) {
    return
  }
  clazzSubmitting.value = true
  try {
    const payload = {
      name: clazzForm.name.trim(),
      majorId: clazzForm.majorId,
      gradeId: clazzForm.gradeId,
      counselor: clazzForm.counselor.trim() || null
    }
    if (clazzForm.id) {
      await updateClazz(clazzForm.id, payload)
      ElMessage.success('修改成功')
    } else {
      await createClazz(payload)
      ElMessage.success('新增成功')
    }
    clazzDialogVisible.value = false
    loadClazzs()
  } catch (e) {
    /* 拦截器已提示 */
  } finally {
    clazzSubmitting.value = false
  }
}

async function handleDeleteClazz(row) {
  try {
    await ElMessageBox.confirm(`确定要删除班级「${row.name}」吗？`, '删除确认', {
      confirmButtonText: '确定删除',
      cancelButtonText: '取消',
      type: 'warning'
    })
  } catch (e) {
    return
  }
  try {
    await deleteClazz(row.id)
    ElMessage.success('删除成功')
    loadClazzs()
  } catch (e) {
    /* 拦截器已提示 */
  }
}

onMounted(() => {
  loadGrades()
  loadMajors()
  loadClazzs()
  loadMajorOptions()
  loadGradeOptions()
})
</script>
