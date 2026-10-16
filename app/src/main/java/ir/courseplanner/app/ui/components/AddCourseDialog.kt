package ir.courseplanner.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.School
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.DialogProperties
import ir.courseplanner.app.data.model.ClassSession
import ir.courseplanner.app.data.model.WeekType
import ir.courseplanner.app.engine.SectionSessionValidator
import ir.courseplanner.app.ui.ManualSessionInput
import ir.courseplanner.app.ui.toClassSession
import ir.courseplanner.app.ui.theme.TermChinDesign

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddCourseDialog(
    onDismiss: () -> Unit,
    onConfirm: (
        name: String,
        code: String,
        department: String,
        credits: Int,
        sectionCode: String,
        instructor: String,
        examDate: String,
        examStartTime: String,
        examEndTime: String,
        sessions: List<ManualSessionInput>
    ) -> Unit
) {
    var name by remember { mutableStateOf("") }
    var code by remember { mutableStateOf("") }
    var department by remember { mutableStateOf("") }
    var credits by remember { mutableIntStateOf(3) }
    var sectionCode by remember { mutableStateOf("01") }
    var instructor by remember { mutableStateOf("") }
    var examDate by remember { mutableStateOf("") }
    var examTimeRange by remember { mutableStateOf("") }

    var errorMessage by remember { mutableStateOf<String?>(null) }

    // List of sessions (supports multiple sessions, e.g. 2 times a week, or alternating weeks)
    val sessions = remember {
        mutableStateListOf(
            ManualSessionInput(dayOfWeek = 0, startTime = "08:00", endTime = "10:00", location = "", weekType = WeekType.EVERY_WEEK)
        )
    }

    val daysList = listOf("شنبه", "یکشنبه", "دوشنبه", "سه‌شنبه", "چهارشنبه", "پنج‌شنبه")

    AlertDialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
        modifier = Modifier
            .fillMaxWidth(0.95f)
            .padding(vertical = 16.dp),
        title = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Default.School,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "افزودن درس جدید به صورت دستی",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                    )
                }
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Default.Close, contentDescription = "بستن")
                }
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                errorMessage?.let { error ->
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(TermChinDesign.ChipShape)
                            .background(MaterialTheme.colorScheme.errorContainer)
                            .padding(8.dp)
                    ) {
                        Text(
                            text = error,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onErrorContainer
                        )
                    }
                }

                // Course Name & Code
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it; errorMessage = null },
                        label = { Text("نام درس *", fontSize = 11.5.sp) },
                        placeholder = { Text("مثال: معماری کامپیوتر") },
                        modifier = Modifier
                            .weight(1.5f)
                            .testTag("input_course_name"),
                        singleLine = true,
                        shape = TermChinDesign.ControlShape
                    )

                    OutlinedTextField(
                        value = code,
                        onValueChange = { code = it },
                        label = { Text("کد درس", fontSize = 11.5.sp) },
                        placeholder = { Text("CE205") },
                        modifier = Modifier
                            .weight(1f)
                            .testTag("input_course_code"),
                        singleLine = true,
                        shape = TermChinDesign.ControlShape
                    )
                }

                // Department & Credits
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = department,
                        onValueChange = { department = it },
                        label = { Text("دانشکده", fontSize = 11.5.sp) },
                        placeholder = { Text("مهندسی کامپیوتر") },
                        modifier = Modifier.weight(1.4f),
                        singleLine = true,
                        shape = TermChinDesign.ControlShape
                    )

                    // Credit Counter (1 to 6)
                    Column(modifier = Modifier.weight(1f)) {
                        Text("تعداد واحد: $credits", style = MaterialTheme.typography.labelSmall)
                        Spacer(modifier = Modifier.height(2.dp))
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            OutlinedButton(
                                onClick = { if (credits > 1) credits-- },
                                shape = TermChinDesign.MicroShape,
                                contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 10.dp, vertical = 2.dp),
                                modifier = Modifier.height(TermChinDesign.MinTouchHeight)
                            ) {
                                Text("-", fontWeight = FontWeight.Bold)
                            }
                            Text(
                                text = "$credits",
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                modifier = Modifier.padding(horizontal = 4.dp)
                            )
                            OutlinedButton(
                                onClick = { if (credits < 10) credits++ },
                                shape = TermChinDesign.MicroShape,
                                contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 10.dp, vertical = 2.dp),
                                modifier = Modifier.height(TermChinDesign.MinTouchHeight)
                            ) {
                                Text("+", fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }

                // Section Code & Instructor
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = sectionCode,
                        onValueChange = { sectionCode = it },
                        label = { Text("شماره گروه", fontSize = 11.5.sp) },
                        placeholder = { Text("01") },
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                        shape = TermChinDesign.ControlShape
                    )

                    OutlinedTextField(
                        value = instructor,
                        onValueChange = { instructor = it },
                        label = { Text("نام استاد", fontSize = 11.5.sp) },
                        placeholder = { Text("دکتر صبوری") },
                        modifier = Modifier.weight(1.5f),
                        singleLine = true,
                        shape = TermChinDesign.ControlShape
                    )
                }

                // Exam Info
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = examDate,
                        onValueChange = { examDate = it },
                        label = { Text("تاریخ امتحان", fontSize = 11.5.sp) },
                        placeholder = { Text("1403/10/28") },
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                        shape = TermChinDesign.ControlShape
                    )

                    OutlinedTextField(
                        value = examTimeRange,
                        onValueChange = { examTimeRange = it },
                        label = { Text("ساعت امتحان", fontSize = 11.5.sp) },
                        placeholder = { Text("09:00 - 12:00") },
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                        shape = TermChinDesign.ControlShape
                    )
                }

                HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

                // Sessions Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "ساعات و جلسات هفتگی کلاس",
                            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = "امکان افزودن چندین جلسه در هفته (ثابت یا هفته‌های زوج و فرد)",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                // List of Class Sessions
                sessions.forEachIndexed { index, sessionInput ->
                    SessionCardInput(
                        sessionIndex = index,
                        sessionInput = sessionInput,
                        daysList = daysList,
                        canDelete = sessions.size > 1,
                        onUpdate = { updated ->
                            sessions[index] = updated
                        },
                        onDelete = {
                            if (sessions.size > 1) {
                                sessions.removeAt(index)
                            }
                        }
                    )
                }

                // Button to add another session
                OutlinedButton(
                    onClick = {
                        sessions.add(
                            ManualSessionInput(
                                dayOfWeek = (sessions.lastOrNull()?.dayOfWeek?.plus(2))?.rem(6) ?: 0,
                                startTime = sessions.lastOrNull()?.startTime ?: "08:00",
                                endTime = sessions.lastOrNull()?.endTime ?: "10:00",
                                location = sessions.lastOrNull()?.location ?: "",
                                weekType = WeekType.EVERY_WEEK
                            )
                        )
                    },
                    modifier = Modifier.fillMaxWidth(),
                    shape = TermChinDesign.ControlShape
                ) {
                    Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("افزودن جلسه دوم یا تایم دیگر به این درس")
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (name.isBlank()) {
                        errorMessage = "نام درس نمی‌تواند خالی باشد."
                        return@Button
                    }
                    if (code.isBlank()) {
                        // A course code is real domain data (needed to match the
                        // portal / re-import); it is never auto-generated.
                        errorMessage = "کد درس الزامی است؛ لطفاً کد واقعی درس را وارد کنید."
                        return@Button
                    }
                    if (sectionCode.isBlank()) {
                        errorMessage = "کد گروه نمی‌تواند خالی باشد."
                        return@Button
                    }
                    for ((idx, sess) in sessions.withIndex()) {
                        if (sess.startTime.isBlank() || sess.endTime.isBlank()) {
                            errorMessage = "ساعت شروع و پایان جلسه ${idx + 1} الزامی است."
                            return@Button
                        }
                        val startMin = ClassSession.parseTimeMinutesOrNull(sess.startTime)
                        val endMin = ClassSession.parseTimeMinutesOrNull(sess.endTime)
                        if (startMin == null || endMin == null) {
                            errorMessage = "در جلسه ${idx + 1}، ساعت معتبر نیست (قالب درست: 08:00)."
                            return@Button
                        }
                        if (startMin >= endMin) {
                            errorMessage = "در جلسه ${idx + 1}، ساعت شروع باید قبل از ساعت پایان باشد."
                            return@Button
                        }
                    }
                    // Sessions of ONE group must not clash with each other
                    // (same day + overlapping week parity + overlapping clock).
                    // Shared rule with the repository: SectionSessionValidator.
                    when (val clash = SectionSessionValidator.validate(sessions.map { it.toClassSession() })) {
                        is SectionSessionValidator.Result.Overlap -> {
                            errorMessage = "جلسه ${clash.firstIndex + 1} با جلسه ${clash.secondIndex + 1} " +
                                "در ${ClassSession.getDayName(clash.dayOfWeek)} (${clash.overlapRange}) تداخل دارد."
                            return@Button
                        }
                        is SectionSessionValidator.Result.InvalidTime -> {
                            errorMessage = "در جلسه ${clash.index + 1}، ساعت شروع باید قبل از پایان باشد."
                            return@Button
                        }
                        SectionSessionValidator.Result.Valid -> Unit
                    }

                    // Parse exam times
                    var examStart = ""
                    var examEnd = ""
                    if (examTimeRange.contains("-")) {
                        val parts = examTimeRange.split("-")
                        examStart = parts[0].trim()
                        examEnd = parts.getOrNull(1)?.trim() ?: ""
                    }

                    onConfirm(
                        name,
                        code,
                        department,
                        credits,
                        sectionCode,
                        instructor,
                        examDate,
                        examStart,
                        examEnd,
                        sessions.toList()
                    )
                },
                modifier = Modifier.testTag("submit_add_course_button")
            ) {
                Text("ذخیره و افزودن درس")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("انصراف")
            }
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SessionCardInput(
    sessionIndex: Int,
    sessionInput: ManualSessionInput,
    daysList: List<String>,
    canDelete: Boolean,
    onUpdate: (ManualSessionInput) -> Unit,
    onDelete: () -> Unit
) {
    Surface(
        shape = TermChinDesign.ControlShape,
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(10.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "جلسه ${sessionIndex + 1}:",
                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.primary
                )

                if (canDelete) {
                    IconButton(
                        onClick = onDelete,
                        modifier = Modifier.size(TermChinDesign.MinTouchHeight)
                    ) {
                        Icon(
                            Icons.Default.Delete,
                            contentDescription = "حذف جلسه",
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }

            // Day of Week selector
            var dayExpanded by remember { mutableStateOf(false) }
            ExposedDropdownMenuBox(
                expanded = dayExpanded,
                onExpandedChange = { dayExpanded = !dayExpanded },
                modifier = Modifier.fillMaxWidth()
            ) {
                OutlinedTextField(
                    value = daysList.getOrElse(sessionInput.dayOfWeek) { "شنبه" },
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("روز هفته", fontSize = 11.5.sp) },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = dayExpanded) },
                    modifier = Modifier
                        .menuAnchor(MenuAnchorType.PrimaryNotEditable, enabled = true)
                        .fillMaxWidth(),
                    shape = TermChinDesign.ChipShape
                )

                ExposedDropdownMenu(
                    expanded = dayExpanded,
                    onDismissRequest = { dayExpanded = false }
                ) {
                    daysList.forEachIndexed { dIndex, dName ->
                        DropdownMenuItem(
                            text = { Text(dName) },
                            onClick = {
                                onUpdate(sessionInput.copy(dayOfWeek = dIndex))
                                dayExpanded = false
                            }
                        )
                    }
                }
            }

            // Start & End Time
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = sessionInput.startTime,
                    onValueChange = { onUpdate(sessionInput.copy(startTime = it)) },
                    label = { Text("شروع (HH:mm)", fontSize = 11.5.sp) },
                    placeholder = { Text("08:00") },
                    modifier = Modifier.weight(1f),
                    singleLine = true,
                    shape = TermChinDesign.ChipShape
                )

                OutlinedTextField(
                    value = sessionInput.endTime,
                    onValueChange = { onUpdate(sessionInput.copy(endTime = it)) },
                    label = { Text("پایان (HH:mm)", fontSize = 11.5.sp) },
                    placeholder = { Text("10:00") },
                    modifier = Modifier.weight(1f),
                    singleLine = true,
                    shape = TermChinDesign.ChipShape
                )
            }

            // Location
            OutlinedTextField(
                value = sessionInput.location,
                onValueChange = { onUpdate(sessionInput.copy(location = it)) },
                label = { Text("محل یا شماره کلاس (اختیاری)", fontSize = 11.5.sp) },
                placeholder = { Text("مثال: تالار خوارزمی یا آزمایشگاه") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                shape = TermChinDesign.ChipShape
            )

            // Week Recurrence / Type (هر هفته / زوج / فرد)
            Column(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = "دوره برگزاری درس در ترم:",
                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(4.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    val weekOptions = listOf(
                        WeekType.EVERY_WEEK to "هر هفته",
                        WeekType.EVEN_WEEKS to "هفته‌های زوج",
                        WeekType.ODD_WEEKS to "هفته‌های فرد"
                    )

                    weekOptions.forEach { (type, label) ->
                        val isSelected = sessionInput.weekType == type
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(TermChinDesign.ChipShape)
                                .background(
                                    if (isSelected) MaterialTheme.colorScheme.primaryContainer
                                    else MaterialTheme.colorScheme.surface
                                )
                                .border(
                                    1.dp,
                                    if (isSelected) MaterialTheme.colorScheme.primary
                                    else MaterialTheme.colorScheme.outline.copy(alpha = 0.2f),
                                    TermChinDesign.ChipShape
                                )
                                .clickable { onUpdate(sessionInput.copy(weekType = type)) }
                                .heightIn(min = TermChinDesign.MinTouchHeight)
                                .padding(vertical = 6.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = label,
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                    fontSize = 11.sp
                                ),
                                color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer
                                else MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }
            }
        }
    }
}
