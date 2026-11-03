package ir.courseplanner.app.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ir.courseplanner.app.data.model.PassedCourse
import ir.courseplanner.app.ui.CoursePlannerViewModel
import ir.courseplanner.app.ui.theme.TermChinDesign
import ir.courseplanner.app.util.JalaliDate
import ir.courseplanner.app.util.normalizeDigits

/**
 * The student's academic record: courses already passed, with optional grade
 * and term. Deliberately separate from «دروس من» — a passed course must never
 * re-enter the generator or the conflict checks.
 */
@Composable
fun PassedCoursesScreen(
    viewModel: CoursePlannerViewModel,
    modifier: Modifier = Modifier
) {
    val passedCourses by viewModel.passedCourses.collectAsStateWithLifecycle()
    val totalCredits by viewModel.passedCourseCredits.collectAsStateWithLifecycle()

    var query by remember { mutableStateOf("") }
    var editorFor by remember { mutableStateOf<PassedCourse?>(null) }
    var showAddDialog by remember { mutableStateOf(false) }
    var deleteTarget by remember { mutableStateOf<PassedCourse?>(null) }

    val visible = remember(passedCourses, query) {
        val q = normalizeDigits(query.trim())
        if (q.isEmpty()) passedCourses
        else passedCourses.filter {
            normalizeDigits(it.name).contains(q, ignoreCase = true) ||
                normalizeDigits(it.code).contains(q, ignoreCase = true) ||
                (it.termLabel?.let { term -> normalizeDigits(term).contains(q, ignoreCase = true) } == true)
        }
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { showAddDialog = true },
                icon = { Icon(Icons.Default.Add, contentDescription = null) },
                text = { Text("افزودن درس پاس‌شده", fontWeight = FontWeight.Bold, fontSize = 13.sp) },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                shape = TermChinDesign.RowShape,
                elevation = androidx.compose.material3.FloatingActionButtonDefaults.elevation(1.dp),
                modifier = Modifier.testTag("fab_add_passed_course")
            )
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp, vertical = 4.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            item {
                PassedCoursesHeader(
                    count = passedCourses.size,
                    totalCredits = totalCredits,
                    onBack = { viewModel.navigateTo(ir.courseplanner.app.ui.AppDestination.COURSES) }
                )
            }
            item {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text("جست‌وجوی درس پاس‌شده...", fontSize = 13.sp) },
                    leadingIcon = {
                        Icon(Icons.Default.Search, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    },
                    singleLine = true,
                    shape = TermChinDesign.CardShape
                )
            }

            if (visible.isEmpty()) {
                item {
                    EmptyPassedCourses(hasAny = passedCourses.isNotEmpty())
                }
            } else {
                items(visible, key = { it.id }) { course ->
                    PassedCourseCard(
                        course = course,
                        onEdit = { editorFor = course },
                        onDelete = { deleteTarget = course }
                    )
                }
            }

            item { Spacer(modifier = Modifier.height(80.dp)) }
        }
    }

    if (showAddDialog || editorFor != null) {
        val editing = editorFor
        PassedCourseEditorDialog(
            initial = editing,
            onDismiss = { showAddDialog = false; editorFor = null },
            onConfirm = { code, name, credits, grade, term, note ->
                if (editing == null) {
                    viewModel.addPassedCourse(code, name, credits, grade, term, note)
                } else {
                    viewModel.updatePassedCourse(editing.id, code, name, credits, grade, term, note)
                }
                showAddDialog = false
                editorFor = null
            }
        )
    }

    deleteTarget?.let { target ->
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            title = { Text("حذف درس پاس‌شده", fontWeight = FontWeight.Bold) },
            text = {
                Text("«${target.name}» از سابقهٔ دروس پاس‌شده حذف شود؟ این کار روی برنامه هفتگی و دروس فعلی اثری ندارد.")
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.deletePassedCourse(target.id, target.name)
                        deleteTarget = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) { Text("حذف") }
            },
            dismissButton = {
                TextButton(onClick = { deleteTarget = null }) { Text("انصراف") }
            }
        )
    }
}

@Composable
private fun PassedCoursesHeader(
    count: Int,
    totalCredits: Int,
    onBack: () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = onBack,
                modifier = Modifier.size(TermChinDesign.MinTouchHeight)
            ) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "بازگشت")
            }
            Spacer(modifier = Modifier.width(4.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "دروس پاس‌شده",
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.ExtraBold),
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "سابقهٔ دروس گذرانده‌شده؛ از «دروس من» جدا است.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = TermChinDesign.CardShape,
            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.18f))
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(18.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    Icons.Default.School,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(TermChinDesign.IconMd)
                )
                StatText(label = "درس", value = count)
                Box(
                    modifier = Modifier
                        .width(1.dp)
                        .height(24.dp)
                        .background(MaterialTheme.colorScheme.outlineVariant)
                )
                StatText(label = "واحد", value = totalCredits)
            }
        }
    }
}

@Composable
private fun StatText(label: String, value: Int) {
    Row(verticalAlignment = Alignment.Bottom) {
        Text(
            text = JalaliDate.toFaDigits(value),
            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.ExtraBold),
            color = MaterialTheme.colorScheme.primary
        )
        Spacer(modifier = Modifier.width(3.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = 2.dp)
        )
    }
}

@Composable
private fun PassedCourseCard(
    course: PassedCourse,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("passed_course_${course.code}"),
        shape = TermChinDesign.CardShape,
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(TermChinDesign.CourseAvatarSize)
                    .clip(TermChinDesign.ControlShape)
                    .background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Default.Check,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.size(TermChinDesign.IconMd)
                )
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = course.name,
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = buildString {
                        append("کد: ")
                        append(JalaliDate.toFaDigits(course.code))
                        append("  •  ")
                        append(JalaliDate.toFaDigits(course.credits))
                        append(" واحد")
                        course.grade?.takeIf { it.isNotBlank() }?.let {
                            append("  •  نمره: ")
                            append(JalaliDate.toFaDigits(it))
                        }
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                course.termLabel?.takeIf { it.isNotBlank() }?.let {
                    Text(
                        text = JalaliDate.toFaDigits(it),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            IconButton(onClick = onEdit, modifier = Modifier.size(TermChinDesign.MinTouchHeight)) {
                Icon(
                    Icons.Default.Edit,
                    contentDescription = "ویرایش درس پاس‌شده",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(TermChinDesign.IconSm)
                )
            }
            IconButton(onClick = onDelete, modifier = Modifier.size(TermChinDesign.MinTouchHeight)) {
                Icon(
                    Icons.Default.DeleteOutline,
                    contentDescription = "حذف درس پاس‌شده",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(TermChinDesign.IconSm)
                )
            }
        }
    }
}

@Composable
private fun EmptyPassedCourses(hasAny: Boolean) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 24.dp),
        shape = TermChinDesign.CardShape,
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.25f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(
                Icons.Default.School,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(36.dp)
            )
            Text(
                text = if (hasAny) "درسی با این جست‌وجو پیدا نشد." else "هنوز درسی به سابقهٔ پاس‌شده‌ها اضافه نکرده‌اید.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = "دروس پاس‌شده فقط برای مرجع‌اند و در برنامه‌ساز یا هشدار تداخل استفاده نمی‌شوند.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun PassedCourseEditorDialog(
    initial: PassedCourse?,
    onDismiss: () -> Unit,
    onConfirm: (code: String, name: String, credits: Int, grade: String?, term: String?, note: String) -> Unit
) {
    var code by remember(initial?.id) { mutableStateOf(initial?.code ?: "") }
    var name by remember(initial?.id) { mutableStateOf(initial?.name ?: "") }
    var credits by remember(initial?.id) { mutableStateOf(initial?.credits ?: 3) }
    var grade by remember(initial?.id) { mutableStateOf(initial?.grade ?: "") }
    var term by remember(initial?.id) { mutableStateOf(initial?.termLabel ?: "") }
    var note by remember(initial?.id) { mutableStateOf(initial?.note ?: "") }
    var error by remember(initial?.id) { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = if (initial == null) "افزودن درس پاس‌شده" else "ویرایش درس پاس‌شده",
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 460.dp)
                    .imePadding()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = code,
                    onValueChange = { code = it; error = null },
                    label = { Text("کد درس *") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it; error = null },
                    label = { Text("نام درس *") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text("واحد:", style = MaterialTheme.typography.bodyMedium)
                    OutlinedButton(
                        onClick = { credits = (credits - 1).coerceAtLeast(0) },
                        shape = CircleShape,
                        modifier = Modifier.size(44.dp),
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp)
                    ) { Text("−", fontSize = 18.sp) }
                    Text(
                        text = JalaliDate.toFaDigits(credits),
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                    )
                    OutlinedButton(
                        onClick = { credits = (credits + 1).coerceAtMost(30) },
                        shape = CircleShape,
                        modifier = Modifier.size(44.dp),
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp)
                    ) { Text("+", fontSize = 18.sp) }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = grade,
                        onValueChange = { grade = it },
                        label = { Text("نمره (اختیاری)") },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = term,
                        onValueChange = { term = it },
                        label = { Text("ترم (اختیاری)") },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                }
                OutlinedTextField(
                    value = note,
                    onValueChange = { note = it },
                    label = { Text("یادداشت (اختیاری)") },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 2
                )
                error?.let {
                    Text(
                        text = it,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    when {
                        code.isBlank() -> error = "کد درس الزامی است."
                        name.isBlank() -> error = "نام درس الزامی است."
                        else -> onConfirm(
                            code,
                            name,
                            credits,
                            grade.ifBlank { null },
                            term.ifBlank { null },
                            note
                        )
                    }
                }
            ) { Text(if (initial == null) "افزودن" else "ذخیره") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("انصراف") }
        }
    )
}
