package ir.courseplanner.app.ui.screens

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BrightnessAuto
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CleaningServices
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.DataObject
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.FileUpload
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.SwitchAccount
import androidx.compose.material.icons.filled.TableView
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.ViewWeek
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ir.courseplanner.app.BuildConfig
import ir.courseplanner.app.data.importer.CourseImporter
import ir.courseplanner.app.data.importer.ImportItem
import ir.courseplanner.app.data.importer.ImportSectionItem
import ir.courseplanner.app.data.model.ClassSession
import ir.courseplanner.app.data.model.CourseWithSections
import ir.courseplanner.app.data.preferences.AppColorTheme
import ir.courseplanner.app.data.preferences.ThemeMode
import ir.courseplanner.app.data.preferences.TimetableDensity
import ir.courseplanner.app.engine.ScheduleEngine
import ir.courseplanner.app.ui.CoursePlannerViewModel
import ir.courseplanner.app.ui.theme.paletteOf
import ir.courseplanner.app.util.JalaliDate
import ir.courseplanner.app.util.JalaliYmd
import ir.courseplanner.app.util.TimetableExporter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private const val MAX_IMPORT_CHARS = 15_000_000

/** Byte cap for the portal picker: single-file (.mht) archives embed images. */
private const val MAX_IMPORT_BYTES = 24_000_000

/**
 * Reads user-selected text without letting a huge file block the app.
 *
 * Throws when the content could not be opened at all: an unreadable file must
 * NOT come back as an empty string, otherwise the import would claim "the file
 * is empty" instead of "the file could not be read".
 */
private fun readImportText(context: Context, uri: Uri): String {
    val stream = context.contentResolver.openInputStream(uri)
        ?: throw java.io.IOException("input stream unavailable for $uri")
    return stream.bufferedReader(Charsets.UTF_8).use { reader ->
        val builder = StringBuilder()
        val buffer = CharArray(8192)
        var total = 0
        while (true) {
            val read = reader.read(buffer)
            if (read < 0) break
            total += read
            require(total <= MAX_IMPORT_CHARS) { "فایل انتخاب‌شده بزرگ‌تر از حد مجاز است." }
            builder.append(buffer, 0, read)
        }
        builder.toString()
    }
}

/**
 * Byte twin of [readImportText] for the portal picker: MHTML (.mht) archives
 * are multipart MIME, so decoding the file as UTF-8 text first would corrupt
 * binary parts and the transfer-decoding step ([MhtHtmlExtractor]). Returns
 * raw bytes; the ViewModel sniffs the format and picks the decoder.
 *
 * Throws exactly like [readImportText] when the content cannot be opened.
 */
private fun readImportBytes(context: Context, uri: Uri): ByteArray {
    val stream = context.contentResolver.openInputStream(uri)
        ?: throw java.io.IOException("input stream unavailable for $uri")
    return stream.use { input ->
        val out = java.io.ByteArrayOutputStream(65536)
        val buffer = ByteArray(8192)
        var total = 0
        while (true) {
            val read = input.read(buffer)
            if (read < 0) break
            total += read
            require(total <= MAX_IMPORT_BYTES) { "فایل انتخاب‌شده بزرگ‌تر از حد مجاز است." }
            out.write(buffer, 0, read)
        }
        out.toByteArray()
    }
}

/** Display name of a picked document (best effort; never throws). */
private fun queryDisplayName(context: Context, uri: Uri): String? {
    return try {
        context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            val idx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (idx >= 0 && cursor.moveToFirst()) cursor.getString(idx) else null
        }
    } catch (_: Exception) {
        null
    }
}

private fun String.normalizeDigitsForNumber(): String = map { char ->
    when (char) {
        in '۰'..'۹' -> ('0'.code + (char.code - '۰'.code)).toChar()
        in '٠'..'٩' -> ('0'.code + (char.code - '٠'.code)).toChar()
        else -> char
    }
}.joinToString("")

@Composable
fun SettingsScreen(
    viewModel: CoursePlannerViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val allCoursesWithSections by viewModel.coursesWithSections.collectAsStateWithLifecycle()
    val enrolledSections by viewModel.enrolledSections.collectAsStateWithLifecycle()
    val preferences by viewModel.userPreferences.collectAsStateWithLifecycle()
    val userMessage by viewModel.userMessage.collectAsStateWithLifecycle()
    val isErrorMessage by viewModel.isErrorMessage.collectAsStateWithLifecycle()

    var showDeleteAllDialog by remember { mutableStateOf(false) }
    var showImportJsonDialog by remember { mutableStateOf(false) }
    var showImportCsvDialog by remember { mutableStateOf(false) }
    var showExportDialog by remember { mutableStateOf(false) }
    var showEditProfileDialog by remember { mutableStateOf(false) }
    var portalFileName by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    // Portal HTML file picker: the saved "presented courses" page is parsed
    // off-main-thread into the hidden catalog (see PooyaHtmlParser).
    // The four import stages stay distinguishable:
    //   (1) file could not be read → error naming the file,
    //   (2) file read but empty → "save the page again" error,
    //   (3) file read, nothing extracted → parser failure message,
    //   (4) courses imported → success message.
    val portalFilePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch(Dispatchers.IO) {
            val displayName = queryDisplayName(context, uri)
            val bytes = try {
                readImportBytes(context, uri)
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    portalFileName = displayName
                    viewModel.reportImportFileReadFailed(e.toString(), displayName)
                }
                return@launch
            }
            withContext(Dispatchers.Main) {
                portalFileName = displayName
                if (bytes.isEmpty()) {
                    viewModel.reportImportFileEmpty(displayName)
                } else {
                    viewModel.importPortalBytes(bytes, displayName, clearExisting = false)
                }
            }
        }
    }

    val jsonFilePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch(Dispatchers.IO) {
            val displayName = queryDisplayName(context, uri)
            val content = try {
                readImportText(context, uri)
            } catch (e: Exception) {
                withContext(Dispatchers.Main) { viewModel.reportImportFileReadFailed(e.toString(), displayName) }
                return@launch
            }
            withContext(Dispatchers.Main) {
                if (content.isBlank()) viewModel.reportImportFileEmpty(displayName)
                else viewModel.importData(content, isJson = true, clearExisting = false)
            }
        }
    }

    val csvFilePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch(Dispatchers.IO) {
            val displayName = queryDisplayName(context, uri)
            val content = try {
                readImportText(context, uri)
            } catch (e: Exception) {
                withContext(Dispatchers.Main) { viewModel.reportImportFileReadFailed(e.toString(), displayName) }
                return@launch
            }
            withContext(Dispatchers.Main) {
                if (content.isBlank()) viewModel.reportImportFileEmpty(displayName)
                else viewModel.importData(content, isJson = false, clearExisting = false)
            }
        }
    }

    val scrollState = rememberScrollState()

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Header
        Column {
            Text(
                text = "تنظیمات و پایگاه داده آفلاین",
                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = "دریافت اطلاعات درس‌ها، پشتیبان‌گیری و تنظیمات داخلی",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        // Status / Message Alert Banner
        userMessage?.let { msg ->
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(
                        if (isErrorMessage) MaterialTheme.colorScheme.errorContainer
                        else MaterialTheme.colorScheme.primaryContainer
                    )
                    .padding(12.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = if (isErrorMessage) Icons.Default.Warning else Icons.Default.Info,
                        contentDescription = null,
                        tint = if (isErrorMessage) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = msg,
                        style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Medium),
                        color = if (isErrorMessage) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.weight(1f)
                    )
                    TextButton(onClick = { viewModel.dismissUserMessage() }) {
                        Text("بستن", fontSize = 11.sp)
                    }
                }
            }
        }

        // Section: Personalization & Color Palettes
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surface,
            shadowElevation = 1.dp,
            border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Default.Palette,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "شخصی‌سازی تم و رنگ‌بندی",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }

                Text(
                    text = "حالت نمایش و پالت رنگی مورد نظر خود را برای محیط برنامه انتخاب کنید:",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                // Dark/Light Mode Selector
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    ThemeMode.values().forEach { mode ->
                        val isSelected = preferences.themeMode == mode
                        val icon = when (mode) {
                            ThemeMode.SYSTEM -> Icons.Default.BrightnessAuto
                            ThemeMode.LIGHT -> Icons.Default.LightMode
                            ThemeMode.DARK -> Icons.Default.DarkMode
                        }
                        FilterChip(
                            selected = isSelected,
                            onClick = { viewModel.setThemeMode(mode) },
                            label = { Text(mode.titleFa, fontSize = 11.5.sp, fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal) },
                            leadingIcon = {
                                Icon(icon, contentDescription = null, modifier = Modifier.size(15.dp))
                            },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                                selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
                            ),
                            modifier = Modifier.weight(1f)
                        )
                    }
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))

                Text(
                    text = "پالت‌های رنگی جذاب (۷ رنگ اختصاصی):",
                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onSurface
                )

                // 7 palette cards: gradient accent + light/dark surface preview
                val paletteThemes = AppColorTheme.values()
                for (index in paletteThemes.indices step 2) {
                    val second = paletteThemes.getOrNull(index + 1)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        PaletteCard(
                            theme = paletteThemes[index],
                            selected = preferences.theme == paletteThemes[index],
                            onClick = { viewModel.setColorTheme(paletteThemes[index]) },
                            modifier = Modifier.weight(1f)
                        )
                        if (second != null) {
                            PaletteCard(
                                theme = second,
                                selected = preferences.theme == second,
                                onClick = { viewModel.setColorTheme(second) },
                                modifier = Modifier.weight(1f)
                            )
                        } else {
                            Spacer(modifier = Modifier.weight(1f))
                        }
                    }
                }
            }
        }

        // Section: Student Profile & Credit Target
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surface,
            shadowElevation = 1.dp,
            border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Default.Person,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "مشخصات دانشجو و سقف واحد",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }

                    OutlinedButton(
                        onClick = { showEditProfileDialog = true },
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.height(34.dp)
                    ) {
                        Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("ویرایش", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                }

                // Profile Summary Card
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "نام دانشجو:",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = if (preferences.studentName.isNotBlank()) preferences.studentName else "تنظیم نشده (اختیاری)",
                                style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold),
                                color = if (preferences.studentName.isNotBlank()) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline
                            )
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "رشته تحصیلی:",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = if (preferences.major.isNotBlank()) preferences.major else "تنظیم نشده",
                                style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Medium),
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "نیم‌سال جاری:",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = preferences.semesterName,
                                style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Medium),
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "سقف واحد هدف:",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = "${preferences.creditTarget} واحد",
                                style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.primary
                            )
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "شروع کلاس‌ها:",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            val savedStart = preferences.semesterStartEpochDay
                                ?.let { JalaliDate.fromEpochDay(it) }
                            Text(
                                text = if (savedStart != null) {
                                    val weekday = ClassSession.getDayName(
                                        JalaliDate.appDayIndexOfEpochDay(
                                            preferences.semesterStartEpochDay!!
                                        )
                                    )
                                    "$savedStart ($weekday)"
                                } else "تنظیم نشده",
                                style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Medium),
                                color = if (savedStart != null) MaterialTheme.colorScheme.onSurface
                                else MaterialTheme.colorScheme.outline
                            )
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "هفته جاری آموزشی:",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            val todayEpoch = remember { JalaliDate.todayEpochDay() }
                            val currentWeek = remember(
                                preferences.semesterStartEpochDay,
                                preferences.firstWeekIsOdd
                            ) {
                                ScheduleEngine.academicWeek(
                                    todayEpoch,
                                    preferences.semesterStartEpochDay,
                                    preferences.firstWeekIsOdd
                                )
                            }
                            Text(
                                text = when {
                                    preferences.semesterStartEpochDay == null -> "تنظیم نشده"
                                    currentWeek == null -> "ترم هنوز شروع نشده"
                                    else -> "هفته ${currentWeek.number} (${currentWeek.parity.titleFa})"
                                },
                                style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold),
                                color = if (currentWeek != null) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.outline
                            )
                        }
                    }
                }
            }
        }

        // Section: Timetable Layout Preferences
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surface,
            shadowElevation = 1.dp,
            border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Default.ViewWeek,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "تنظیمات تقویم و جدول هفتگی",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }

                // Show Thursday Toggle
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .padding(end = 12.dp)
                    ) {
                        Text(
                            text = "نمایش ستون روز پنج‌شنبه",
                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "با خاموش کردن، پنج‌شنبه پنهان شده و ستون روزهای شنبه تا چهارشنبه فضای بزرگ‌تری دارند.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    Switch(
                        checked = preferences.showThursday,
                        onCheckedChange = { viewModel.setShowThursday(it) },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = MaterialTheme.colorScheme.primary,
                            checkedTrackColor = MaterialTheme.colorScheme.primaryContainer
                        )
                    )
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))

                // Density Selector
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "ارتفاع و تراکم خانه‌های جدول:",
                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        TimetableDensity.values().forEach { density ->
                            val isSelected = preferences.timetableDensity == density
                            FilterChip(
                                selected = isSelected,
                                onClick = { viewModel.setTimetableDensity(density) },
                                label = {
                                    Text(
                                        density.titleFa,
                                        fontSize = 11.5.sp,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                                    )
                                },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                                    selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
                                ),
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }
            }
        }

        // Section 1: Data Import
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surface,
            shadowElevation = 1.dp,
            border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Default.CloudDownload,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "دریافت اطلاعات درس‌ها (Import)",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
                Text(
                    text = "می‌توانید کاتالوگ دروس را کاملاً آفلاین وارد کنید. فایل را انتخاب کنید یا متن را بچسبانید:",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = { jsonFilePicker.launch("application/json") },
                        modifier = Modifier
                            .weight(1f)
                            .testTag("import_json_button"),
                        shape = RoundedCornerShape(12.dp),
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 8.dp, vertical = 10.dp)
                    ) {
                        Icon(Icons.Default.DataObject, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            "فایل JSON",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            softWrap = false
                        )
                    }

                    OutlinedButton(
                        onClick = { csvFilePicker.launch("text/*") },
                        modifier = Modifier
                            .weight(1f)
                            .testTag("import_csv_button"),
                        shape = RoundedCornerShape(12.dp),
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 8.dp, vertical = 10.dp)
                    ) {
                        Icon(Icons.Default.TableView, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            "فایل CSV",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            softWrap = false
                        )
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = { showImportJsonDialog = true },
                        modifier = Modifier
                            .weight(1f)
                            .testTag("paste_json_button"),
                        shape = RoundedCornerShape(12.dp),
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 8.dp, vertical = 8.dp)
                    ) {
                        Icon(Icons.Default.DataObject, contentDescription = null, modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("چسباندن JSON", fontSize = 11.sp, fontWeight = FontWeight.Medium, maxLines = 1, softWrap = false)
                    }
                    OutlinedButton(
                        onClick = { showImportCsvDialog = true },
                        modifier = Modifier
                            .weight(1f)
                            .testTag("paste_csv_button"),
                        shape = RoundedCornerShape(12.dp),
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 8.dp, vertical = 8.dp)
                    ) {
                        Icon(Icons.Default.TableView, contentDescription = null, modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("چسباندن CSV", fontSize = 11.sp, fontWeight = FontWeight.Medium, maxLines = 1, softWrap = false)
                    }
                }

                // Portal HTML file import (Pooya / Golestan / Sama …)
                // The saved "presented courses" page becomes a hidden catalog;
                // courses are added to "my courses" later by entering their code.
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        listOf(
                            "۱. صفحه «دروس ارائه‌شده» دانشگاه را باز کنید.",
                            "۲. صفحه را به‌صورت HTML ذخیره کنید (Save as HTML) یا تک‌فایل (.mht).",
                            "۳. همین‌جا فایل را انتخاب کنید."
                        ).forEach { step ->
                            Text(
                                text = step,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
                OutlinedButton(
                    onClick = { portalFilePicker.launch("*/*") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(44.dp)
                        .testTag("import_portal_html_button"),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(Icons.Default.FileUpload, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        if (portalFileName != null) "فایل پرتال: $portalFileName" else "ورود فایل HTML/MHT پرتال (پویا)",
                        fontSize = 12.5.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                    )
                }
                Text(
                    text = "صفحه «لیست دروس ارائه‌شده» پرتال را ذخیره (Save as HTML یا تک‌فایل .mht) و همین‌جا انتخاب کنید؛ " +
                        "همه دروس با ساعت کلاس، استاد و ظرفیت وارد کاتالوگ می‌شوند و جلوی چشم نیستند.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        // Section 2: Data Export
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surface,
            shadowElevation = 1.dp,
            border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Default.FileDownload,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                    text = "خروجی دروس و گروه‌ها (Export)",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
                Text(
                    text = "از دروس و گروه‌ها خروجی JSON بگیرید. جزوات و فایل‌های پیوست در این خروجی قرار نمی‌گیرند.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = { showExportDialog = true },
                        modifier = Modifier
                            .weight(1f)
                            .height(42.dp),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(Icons.Default.FileDownload, contentDescription = null, modifier = Modifier.size(17.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("خروجی JSON", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                    }

                    Button(
                        onClick = {
                            TimetableExporter.shareSchedule(
                                context = context,
                                sections = enrolledSections,
                                studentName = preferences.studentName,
                                major = preferences.major,
                                semester = preferences.semesterName
                            )
                        },
                        modifier = Modifier
                            .weight(1f)
                            .height(42.dp),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary
                        )
                    ) {
                        Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(17.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("اشتراک برنامه", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }

        // Section 3: Data Management & Reset
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surface,
            shadowElevation = 1.dp,
            border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Default.CleaningServices,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "مدیریت و پاک‌سازی داده‌ها",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }

                // Clear Schedule Only
                OutlinedButton(
                    onClick = { viewModel.clearScheduleOnly() },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(42.dp),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(Icons.Default.CleaningServices, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("خالی کردن برنامه هفتگی (حفظ لیست دروس)", fontWeight = FontWeight.SemiBold)
                }
                Text(
                    text = "جزوات حذف نمی‌شوند، اما تا زمان نهایی‌کردن دوبارهٔ درس‌ها در بخش جزوات نمایش داده نخواهند شد.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

                // Delete All Data (With strict confirmation)
                Button(
                    onClick = { showDeleteAllDialog = true },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(44.dp)
                        .testTag("delete_all_data_button"),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(Icons.Default.DeleteForever, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("حذف تمام اطلاعات (Delete All)", fontWeight = FontWeight.Bold)
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // App version footer: proves which exact build is installed.
        // If latest changes are missing, compare GIT_SHA with GitHub commit.
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
        ) {
            Column(
                modifier = Modifier.padding(12.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Text(
                    text = "TermChin | ترم‌چین  •  v${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})",
                    style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "بیلد ${BuildConfig.GIT_SHA} • ${BuildConfig.BUILD_TIME}",
                    style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))
    }

    // Confirmation Dialog for Delete All
    if (showDeleteAllDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteAllDialog = false },
            icon = { Icon(Icons.Default.Warning, contentDescription = null, tint = MaterialTheme.colorScheme.error) },
            title = {
                Text(
                    text = "تأیید حذف تمام اطلاعات",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                )
            },
            text = {
                Text(
                    text = "آیا مطمئن هستید که می‌خواهید تمام دروس، گروه‌ها، برنامه‌های هفتگی و جزوات را حذف کنید؟ این عمل غیرقابل بازگشت است.",
                    style = MaterialTheme.typography.bodyMedium
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.clearAllData()
                        showDeleteAllDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("بله، تمام داده‌ها حذف شوند")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteAllDialog = false }) {
                    Text("انصراف")
                }
            }
        )
    }

    // Import JSON Dialog
    if (showImportJsonDialog) {
        ImportTextDialog(
            title = "ورود داده با فرمت JSON",
            placeholder = """[
  {
    "code": "CE101",
    "name": "مبانی کامپیوتر",
    "department": "کامپیوتر",
    "credits": 3,
    "sections": [
      {
        "sectionCode": "01",
        "instructor": "دکتر رضایی",
        "examDate": "1403/10/25",
        "examStartTime": "09:00",
        "examEndTime": "12:00",
        "sessions": [
          { "dayOfWeek": 0, "startTime": "08:00", "endTime": "10:00", "location": "کلاس ۱۰۱" }
        ]
      }
    ]
  }
]""",
            onDismiss = { showImportJsonDialog = false },
            onImport = { content, clearExisting ->
                viewModel.importData(content, isJson = true, clearExisting = clearExisting)
                showImportJsonDialog = false
            }
        )
    }

    // Import CSV Dialog
    if (showImportCsvDialog) {
        ImportTextDialog(
            title = "ورود داده با فرمت CSV",
            placeholder = """course_code,course_name,department,credits,section_code,instructor,capacity,exam_date,exam_start,exam_end,day_of_week,start_time,end_time,location
MATH101,ریاضی ۱,علوم پایه,3,01,دکتر حسنی,40,1403/10/20,08:30,11:30,شنبه,08:00,10:00,کلاس ۱""",
            onDismiss = { showImportCsvDialog = false },
            onImport = { content, clearExisting ->
                viewModel.importData(content, isJson = false, clearExisting = clearExisting)
                showImportCsvDialog = false
            }
        )
    }

    // Export Dialog
    if (showExportDialog) {
        val importItems = allCoursesWithSections.map { cws ->
            ImportItem(
                course = cws.course,
                sections = cws.sections.map { sec ->
                    ImportSectionItem(section = sec.section, sessions = sec.sessions)
                }
            )
        }
        val exportedJson = CourseImporter.exportToJson(importItems)

        AlertDialog(
            onDismissRequest = { showExportDialog = false },
            title = {
                Text(
                    text = "خروجی JSON (${importItems.size} درس)",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                )
            },
            text = {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(300.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                        .verticalScroll(rememberScrollState())
                        .padding(8.dp)
                ) {
                    Text(
                        text = exportedJson,
                        style = MaterialTheme.typography.bodySmall.copy(
                            fontFamily = FontFamily.Monospace,
                            fontSize = 11.sp
                        )
                    )
                }
            },
            confirmButton = {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Copies the full JSON (not just the visible preview) so
                    // the user can back it up outside the phone — the only
                    // backup path while OS backup stays disabled.
                    OutlinedButton(
                        onClick = {
                            TimetableExporter.copyTextToClipboard(
                                context = context,
                                text = exportedJson,
                                label = "TermChin JSON Export",
                                toastMessage = "متن JSON در کلیپ‌بورد کپی شد"
                            )
                        },
                        modifier = Modifier.testTag("export_json_copy_button")
                    ) {
                        Text("کپی")
                    }
                    TextButton(onClick = { showExportDialog = false }) {
                        Text("بستن")
                    }
                }
            }
        )
    }

    // Edit Student Profile Dialog
    if (showEditProfileDialog) {
        val savedStart = preferences.semesterStartEpochDay?.let { JalaliDate.fromEpochDay(it) }
        EditProfileDialog(
            initialName = preferences.studentName,
            initialMajor = preferences.major,
            initialSemester = preferences.semesterName,
            initialCreditTarget = preferences.creditTarget,
            initialSemesterStart = savedStart?.toString() ?: "",
            initialFirstWeekIsOdd = preferences.firstWeekIsOdd,
            onDismiss = { showEditProfileDialog = false },
            onSave = { name, major, semester, target, semesterStart, firstWeekIsOdd ->
                viewModel.setStudentProfile(name, major, semester)
                viewModel.setCreditTarget(target)
                viewModel.setSemesterStartEpochDay(
                    semesterStart?.let { JalaliDate.toEpochDay(it.year, it.month, it.day) }
                )
                viewModel.setFirstWeekIsOdd(firstWeekIsOdd)
                showEditProfileDialog = false
            }
        )
    }
}

@Composable
private fun ImportTextDialog(
    title: String,
    placeholder: String,
    onDismiss: () -> Unit,
    onImport: (content: String, clearExisting: Boolean) -> Unit
) {
    var text by remember { mutableStateOf("") }
    var clearExisting by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(text = title, style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold))
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    placeholder = {
                        Text(
                            placeholder,
                            fontSize = 11.sp,
                            fontFamily = FontFamily.Monospace
                        )
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(220.dp),
                    textStyle = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace)
                )

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Checkbox(
                        checked = clearExisting,
                        onCheckedChange = { clearExisting = it }
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "پاک کردن دروس قبلی پیش از بارگذاری",
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onImport(text, clearExisting) },
                enabled = text.isNotBlank()
            ) {
                Text("تأیید و بارگذاری")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("انصراف")
            }
        }
    )
}

@Composable
private fun EditProfileDialog(
    initialName: String,
    initialMajor: String,
    initialSemester: String,
    initialCreditTarget: Int,
    initialSemesterStart: String,
    initialFirstWeekIsOdd: Boolean,
    onDismiss: () -> Unit,
    onSave: (
        name: String,
        major: String,
        semester: String,
        target: Int,
        semesterStart: JalaliYmd?,
        firstWeekIsOdd: Boolean
    ) -> Unit
) {
    var name by remember { mutableStateOf(initialName) }
    var major by remember { mutableStateOf(initialMajor) }
    var semester by remember { mutableStateOf(initialSemester) }
    var creditTarget by remember { mutableStateOf(initialCreditTarget.toString()) }
    var creditTargetError by remember { mutableStateOf<String?>(null) }
    var semesterStartInput by remember { mutableStateOf(initialSemesterStart) }
    var semesterStartError by remember { mutableStateOf<String?>(null) }
    var firstWeekIsOdd by remember { mutableStateOf(initialFirstWeekIsOdd) }
    val parsedStart = remember(semesterStartInput) {
        semesterStartInput.takeIf { it.isNotBlank() }?.let { JalaliDate.parse(it) }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "ویرایش اطلاعات دانشجو",
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("نام و نام خانوادگی") },
                    placeholder = { Text("مثلاً: امیررضا آصفی") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = major,
                    onValueChange = { major = it },
                    label = { Text("رشته تحصیلی") },
                    placeholder = { Text("مثلاً: مهندسی کامپیوتر") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = semester,
                    onValueChange = { semester = it },
                    label = { Text("نیم‌سال تحصیلی") },
                    placeholder = { Text("مثلاً: نیم‌سال اول ۱۴۰۳-۱۴۰۴") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = creditTarget,
                    onValueChange = { input ->
                        if (input.all { it.isDigit() } && input.length <= 2) {
                            creditTarget = input
                            creditTargetError = null
                        }
                    },
                    label = { Text("سقف واحد هدف (ترم)") },
                    placeholder = { Text("۲۰") },
                    singleLine = true,
                    isError = creditTargetError != null,
                    supportingText = { creditTargetError?.let { Text(it) } },
                    modifier = Modifier.fillMaxWidth()
                )

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))

                // Semester start: academic even/odd weeks are counted from here.
                Text(
                    text = "شروع کلاس‌های دانشگاه (برای هفته زوج/فرد)",
                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onSurface
                )
                OutlinedTextField(
                    value = semesterStartInput,
                    onValueChange = {
                        semesterStartInput = it
                        semesterStartError = null
                    },
                    label = { Text("تاریخ شروع کلاس‌ها (شمسی)") },
                    placeholder = { Text("مثلاً: ۱۴۰۴/۰۷/۰۵") },
                    singleLine = true,
                    isError = semesterStartError != null,
                    supportingText = {
                        when {
                            semesterStartError != null -> Text(semesterStartError!!)
                            parsedStart != null -> {
                                val epoch = JalaliDate.toEpochDay(
                                    parsedStart.year, parsedStart.month, parsedStart.day
                                )!!
                                val weekday = ClassSession.getDayName(
                                    JalaliDate.appDayIndexOfEpochDay(epoch)
                                )
                                Text("✓ $weekday، ${parsedStart}")
                            }
                            semesterStartInput.isNotBlank() -> Text("قالب درست: سال/ماه/روز، مثل ۱۴۰۴/۰۷/۰۵")
                            else -> Text("خالی بماند یعنی هفته زوج/فرد نامشخص می‌ماند.")
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                )

                Text(
                    text = "هفته اول ترم:",
                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onSurface
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FilterChip(
                        selected = firstWeekIsOdd,
                        onClick = { firstWeekIsOdd = true },
                        label = { Text("هفته فرد", fontSize = 11.5.sp) },
                        modifier = Modifier.weight(1f)
                    )
                    FilterChip(
                        selected = !firstWeekIsOdd,
                        onClick = { firstWeekIsOdd = false },
                        label = { Text("هفته زوج", fontSize = 11.5.sp) },
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val targetInt = creditTarget.normalizeDigitsForNumber().toIntOrNull()
                    if (targetInt == null || targetInt !in 1..24) {
                        creditTargetError = "سقف واحد باید عددی بین ۱ تا ۲۴ باشد."
                        return@Button
                    }
                    if (semesterStartInput.isNotBlank() && parsedStart == null) {
                        semesterStartError = "تاریخ معتبر نیست (مثلاً ۱۴۰۴/۰۷/۰۵)."
                        return@Button
                    }
                    onSave(name, major, semester, targetInt, parsedStart, firstWeekIsOdd)
                }
            ) {
                Text("ذخیره")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("انصراف")
            }
        }
    )
}

/**
 * One selectable palette card: theme name, gradient accent dot (ticked when
 * selected) and a miniature light/dark surface preview, so the chosen theme
 * reads clearly without switching theme mode first.
 */
@Composable
private fun PaletteCard(
    theme: AppColorTheme,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val palette = paletteOf(theme)
    Surface(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
            .testTag("theme_palette_${theme.id}"),
        shape = RoundedCornerShape(16.dp),
        color = if (selected) palette.light.primaryContainer.copy(alpha = 0.6f)
        else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.25f),
        border = androidx.compose.foundation.BorderStroke(
            if (selected) 2.dp else 1.dp,
            if (selected) palette.light.primary
            else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)
        ),
        shadowElevation = if (selected) 2.dp else 0.dp
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 9.dp),
            verticalArrangement = Arrangement.spacedBy(7.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(7.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(26.dp)
                        .clip(CircleShape)
                        .background(Brush.horizontalGradient(palette.swatch)),
                    contentAlignment = Alignment.Center
                ) {
                    if (selected) {
                        Icon(
                            Icons.Default.Check,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(15.dp)
                        )
                    }
                }
                Text(
                    text = theme.titleFa,
                    style = MaterialTheme.typography.labelMedium.copy(
                        fontWeight = if (selected) FontWeight.Bold else FontWeight.SemiBold
                    ),
                    color = MaterialTheme.colorScheme.onSurface,
                    fontSize = 12.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Box(modifier = Modifier.weight(1f)) {
                    PaletteModePreview(palette.light)
                }
                Box(modifier = Modifier.weight(1f)) {
                    PaletteModePreview(palette.dark)
                }
            }
        }
    }
}

/** Tiny strip: a mode's surface carrying its primary/secondary/tertiary dots. */
@Composable
private fun PaletteModePreview(scheme: ColorScheme) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(6.dp),
        color = scheme.surface,
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            scheme.outlineVariant.copy(alpha = 0.6f)
        )
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 5.dp, vertical = 3.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Box(modifier = Modifier.size(7.dp).clip(CircleShape).background(scheme.primary))
            Box(modifier = Modifier.size(7.dp).clip(CircleShape).background(scheme.secondary))
            Box(modifier = Modifier.size(7.dp).clip(CircleShape).background(scheme.tertiary))
        }
    }
}
