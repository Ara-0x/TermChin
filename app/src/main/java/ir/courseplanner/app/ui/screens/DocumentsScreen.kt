package ir.courseplanner.app.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.OpenableColumns
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ir.courseplanner.app.ui.theme.BookmarkAmberDark
import ir.courseplanner.app.ui.theme.BookmarkAmberLight
import ir.courseplanner.app.ui.theme.CategoryGreenDark
import ir.courseplanner.app.ui.theme.CategoryGreenLight
import ir.courseplanner.app.ui.theme.CategoryOrangeDark
import ir.courseplanner.app.ui.theme.CategoryOrangeLight
import ir.courseplanner.app.ui.theme.CategoryPinkDark
import ir.courseplanner.app.ui.theme.CategoryPinkLight
import ir.courseplanner.app.ui.theme.CategoryPurpleDark
import ir.courseplanner.app.ui.theme.CategoryPurpleLight
import ir.courseplanner.app.ui.theme.CategoryTealDark
import ir.courseplanner.app.ui.theme.CategoryTealLight
import ir.courseplanner.app.ui.theme.isDarkTheme
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ir.courseplanner.app.data.model.Course
import ir.courseplanner.app.data.model.CourseDocument
import ir.courseplanner.app.data.model.DocumentCategory
import ir.courseplanner.app.data.model.DocumentWithCourse
import ir.courseplanner.app.ui.AppDestination
import ir.courseplanner.app.ui.CoursePlannerViewModel
import ir.courseplanner.app.ui.theme.TermChinDesign
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DocumentsScreen(
    viewModel: CoursePlannerViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val allCourses by viewModel.allCourses.collectAsStateWithLifecycle()
    val enrolledSections by viewModel.enrolledSections.collectAsStateWithLifecycle()
    // Tabs and pickers only for courses that are on the applied weekly plan
    // (finalized enrollments), not the hidden portal catalog.
    val courses = remember(allCourses, enrolledSections) {
        val enrolledCourseIds = enrolledSections.map { it.course.id }.toSet()
        allCourses.filter { it.id in enrolledCourseIds }
    }
    val allDocsRaw by viewModel.documentsWithCourse.collectAsStateWithLifecycle()
    val allDocs = remember(allDocsRaw, courses) {
        val allowedIds = courses.map { it.id }.toSet()
        allDocsRaw.filter { it.course.id in allowedIds }
    }
    val filteredDocsRaw by viewModel.filteredDocuments.collectAsStateWithLifecycle()
    val filteredDocs = remember(filteredDocsRaw, courses) {
        val allowedIds = courses.map { it.id }.toSet()
        filteredDocsRaw.filter { it.course.id in allowedIds }
    }
    val selectedCourseId by viewModel.selectedDocCourseId.collectAsStateWithLifecycle()
    val selectedCategory by viewModel.selectedDocCategory.collectAsStateWithLifecycle()
    val searchQuery by viewModel.docSearchQuery.collectAsStateWithLifecycle()
    val onlyBookmarked by viewModel.onlyBookmarkedDocs.collectAsStateWithLifecycle()

    LaunchedEffect(courses, selectedCourseId) {
        if (selectedCourseId != null && courses.none { it.id == selectedCourseId }) {
            viewModel.onDocCourseFilterSelected(null)
        }
    }

    var showAddDialog by remember { mutableStateOf(false) }
    var editingDoc by remember { mutableStateOf<CourseDocument?>(null) }
    var docToDelete by remember { mutableStateOf<CourseDocument?>(null) }

    Scaffold(
        modifier = modifier.testTag("documents_screen"),
        floatingActionButton = {
            if (courses.isNotEmpty()) {
                ExtendedFloatingActionButton(
                    onClick = { showAddDialog = true },
                    icon = { Icon(Icons.Filled.Add, contentDescription = "افزودن سند") },
                    text = { Text("افزودن جزوه / سند", fontWeight = FontWeight.Bold) },
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.testTag("fab_add_document")
                )
            }
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            // Screen Header
            Surface(
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 1.dp,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = "جزوات و اسناد دروس",
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "مدیریت فایل‌ها، خلاصه دروس و نمونه‌سوالات",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        // Bookmark Filter Toggle
                        FilterChip(
                            selected = onlyBookmarked,
                            onClick = { viewModel.toggleOnlyBookmarkedDocs() },
                            label = { Text("نشان‌شده‌ها", fontSize = 11.5.sp) },
                            leadingIcon = {
                                Icon(
                                    imageVector = if (onlyBookmarked) Icons.Filled.Star else Icons.Outlined.StarBorder,
                                    contentDescription = null,
                                    tint = if (onlyBookmarked) (if (isDarkTheme()) BookmarkAmberDark else BookmarkAmberLight) else MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(16.dp)
                                )
                            },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                                selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        )
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    // Search Field
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { viewModel.onDocSearchQueryChange(it) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("doc_search_field"),
                        placeholder = { Text("جستجو در عنوان، یادداشت، نام فایل یا درس...", fontSize = 12.5.sp) },
                        leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant) },
                        trailingIcon = {
                            if (searchQuery.isNotEmpty()) {
                                IconButton(onClick = { viewModel.onDocSearchQueryChange("") }) {
                                    Icon(Icons.Filled.Clear, contentDescription = "پاک کردن", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        },
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp)
                    )
                }
            }

            // Filters Section: Courses Chips
            if (courses.isNotEmpty()) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
                        .padding(vertical = 8.dp)
                ) {
                    // Course Chips Row
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState())
                            .padding(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        FilterChip(
                            selected = selectedCourseId == null,
                            onClick = { viewModel.onDocCourseFilterSelected(null) },
                            shape = RoundedCornerShape(10.dp),
                            label = { Text("همه دروس (${allDocs.size})", fontSize = 12.sp, maxLines = 1, softWrap = false) }
                        )

                        courses.forEach { course ->
                            val docCountForCourse = allDocs.count { it.course.id == course.id }
                            FilterChip(
                                selected = selectedCourseId == course.id,
                                onClick = { viewModel.onDocCourseFilterSelected(course.id) },
                                shape = RoundedCornerShape(10.dp),
                                label = {
                                    Text("${course.name} ($docCountForCourse)", fontSize = 12.sp, maxLines = 1, softWrap = false)
                                }
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(6.dp))

                    // Category Chips Row
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState())
                            .padding(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        FilterChip(
                            selected = selectedCategory == null,
                            onClick = { viewModel.onDocCategoryFilterSelected(null) },
                            shape = RoundedCornerShape(10.dp),
                            label = { Text("همه دسته‌ها", fontSize = 11.5.sp, maxLines = 1, softWrap = false) }
                        )

                        DocumentCategory.values().forEach { cat ->
                            FilterChip(
                                selected = selectedCategory == cat,
                                onClick = { viewModel.onDocCategoryFilterSelected(cat) },
                                shape = RoundedCornerShape(10.dp),
                                label = { Text(cat.titleFa, fontSize = 11.5.sp, maxLines = 1, softWrap = false) }
                            )
                        }
                    }
                }
            }

            // Main Content Area
            if (courses.isEmpty()) {
                // Empty state: No courses yet
                NoCoursesEmptyState(onGoToCourses = { viewModel.navigateTo(AppDestination.COURSES) })
            } else if (allDocs.isEmpty()) {
                // Empty state: No documents added yet
                NoDocumentsEmptyState(onAddFirst = { showAddDialog = true })
            } else if (filteredDocs.isEmpty()) {
                // Empty state: Search/filter returned 0
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            imageVector = Icons.Filled.FilterList,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                            modifier = Modifier.size(56.dp)
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = "هیچ سندی با این فیلترها یافت نشد",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "فیلترها یا عبارت جستجو را تغییر دهید.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        OutlinedButton(
                            onClick = {
                                viewModel.onDocSearchQueryChange("")
                                viewModel.onDocCourseFilterSelected(null)
                                viewModel.onDocCategoryFilterSelected(null)
                            }
                        ) {
                            Text("پاکسازی فیلترها")
                        }
                    }
                }
            } else {
                // Document List
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(
                        items = filteredDocs,
                        key = { it.document.id }
                    ) { docWithCourse ->
                        DocumentItemCard(
                            item = docWithCourse,
                            onToggleBookmark = {
                                viewModel.toggleDocumentBookmark(
                                    docWithCourse.document.id,
                                    docWithCourse.document.isBookmarked
                                )
                            },
                            onEdit = { editingDoc = docWithCourse.document },
                            onDelete = { docToDelete = docWithCourse.document },
                            onOpenUri = { uriString ->
                                openDocumentUri(context, uriString)
                            },
                            onShare = {
                                shareDocumentDetails(context, docWithCourse)
                            },
                            onCopyNotes = { notes ->
                                copyToClipboard(context, notes)
                            }
                        )
                    }

                    item {
                        Spacer(modifier = Modifier.height(72.dp))
                    }
                }
            }
        }
    }

    // Add Document Dialog
    if (showAddDialog) {
        AddEditDocumentDialog(
            courses = courses,
            initialCourseId = selectedCourseId ?: courses.firstOrNull()?.id,
            onDismiss = { showAddDialog = false },
            onConfirm = { courseId, title, cat, notes, uri, fileName, size, isBookmarked ->
                viewModel.addDocument(
                    courseId = courseId,
                    title = title,
                    category = cat,
                    notes = notes,
                    fileUri = uri,
                    fileName = fileName,
                    fileSizeBytes = size,
                    isBookmarked = isBookmarked
                )
                showAddDialog = false
            }
        )
    }

    // Edit Document Dialog
    editingDoc?.let { doc ->
        AddEditDocumentDialog(
            courses = courses,
            existingDocument = doc,
            initialCourseId = doc.courseId,
            onDismiss = { editingDoc = null },
            onConfirm = { courseId, title, cat, notes, uri, fileName, size, isBookmarked ->
                viewModel.updateDocument(
                    doc.copy(
                        courseId = courseId,
                        title = title,
                        category = cat,
                        contentNotes = notes,
                        fileUri = uri,
                        fileName = fileName,
                        fileSizeBytes = size,
                        isBookmarked = isBookmarked
                    )
                )
                editingDoc = null
            }
        )
    }

    // Delete Confirmation Dialog
    docToDelete?.let { doc ->
        AlertDialog(
            onDismissRequest = { docToDelete = null },
            title = { Text("حذف جزوه یا سند", fontWeight = FontWeight.Bold) },
            text = { Text("آیا از حذف «${doc.title}» اطمینان دارید؟ این عملیات غیرقابل بازگشت است.") },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.deleteDocument(doc.id)
                        docToDelete = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("حذف شود")
                }
            },
            dismissButton = {
                TextButton(onClick = { docToDelete = null }) {
                    Text("انصراف")
                }
            }
        )
    }
}

@Composable
private fun DocumentItemCard(
    item: DocumentWithCourse,
    onToggleBookmark: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onOpenUri: (String) -> Unit,
    onShare: () -> Unit,
    onCopyNotes: (String) -> Unit
) {
    val doc = item.document
    val course = item.course
    var expandedNotes by remember { mutableStateOf(false) }

    // Same card language as the Courses screen (Surface + soft shadow +
    // hairline border + shared card shape) so all tabs look like one app.
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .shadow(1.5.dp, TermChinDesign.CardShape)
            .testTag("doc_card_${doc.id}"),
        shape = TermChinDesign.CardShape,
        color = MaterialTheme.colorScheme.surface,
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp)
        ) {
            // Top Row: Course badge, Category badge & Bookmark toggle
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    // Course Badge
                    Surface(
                        color = MaterialTheme.colorScheme.primaryContainer,
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.weight(1f, fill = false)
                    ) {
                        Text(
                            text = "${course.name} (${course.code})",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            softWrap = false
                        )
                    }

                    // Category Badge
                    val (catBg, catFg) = getCategoryColors(doc.category)
                    Surface(
                        color = catBg,
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text(
                            text = doc.category.titleFa,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Medium,
                            color = catFg,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            maxLines = 1,
                            softWrap = false
                        )
                    }
                }

                // Bookmark Star Button
                IconButton(
                    onClick = onToggleBookmark,
                    modifier = Modifier.size(40.dp)
                ) {
                    Icon(
                        imageVector = if (doc.isBookmarked) Icons.Filled.Star else Icons.Outlined.StarBorder,
                        contentDescription = "نشان کردن",
                        tint = if (doc.isBookmarked) (if (isDarkTheme()) BookmarkAmberDark else BookmarkAmberLight) else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Title
            Text(
                text = doc.title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )

            // Date
            Text(
                text = formatTimestamp(doc.createdAt),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                modifier = Modifier.padding(top = 2.dp)
            )

            // Notes / Text Content if available
            if (doc.contentNotes.isNotBlank()) {
                Spacer(modifier = Modifier.height(10.dp))
                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(10.dp)) {
                        Text(
                            text = doc.contentNotes,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = if (expandedNotes) Int.MAX_VALUE else 3,
                            overflow = TextOverflow.Ellipsis
                        )

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 4.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            if (doc.contentNotes.length > 100) {
                                Text(
                                    text = if (expandedNotes) "نمایش کمتر" else "نمایش کامل...",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.primary,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.clickable { expandedNotes = !expandedNotes }
                                )
                            } else {
                                Spacer(modifier = Modifier.width(1.dp))
                            }

                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.clickable { onCopyNotes(doc.contentNotes) }
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.ContentCopy,
                                    contentDescription = "کپی",
                                    modifier = Modifier.size(13.dp),
                                    tint = MaterialTheme.colorScheme.primary
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = "کپی متن",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    }
                }
            }

            // Attached File Box
            if (!doc.fileUri.isNullOrBlank() || !doc.fileName.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(10.dp))
                Surface(
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.08f),
                    shape = RoundedCornerShape(12.dp),
                    border = CardDefaults.outlinedCardBorder(),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.weight(1f)
                        ) {
                            Surface(
                                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
                                shape = CircleShape,
                                modifier = Modifier.size(36.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        imageVector = Icons.Filled.AttachFile,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.width(10.dp))

                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = doc.fileName ?: "فایل پیوست",
                                    style = MaterialTheme.typography.bodySmall,
                                    fontWeight = FontWeight.SemiBold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    softWrap = false
                                )
                                if (doc.fileSizeBytes > 0) {
                                    Text(
                                        text = formatFileSize(doc.fileSizeBytes),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1,
                                        softWrap = false
                                    )
                                }
                            }
                        }

                        if (!doc.fileUri.isNullOrBlank()) {
                            IconButton(
                                onClick = { onOpenUri(doc.fileUri) },
                                modifier = Modifier.size(40.dp)
                            ) {
                                Icon(
                                     imageVector = Icons.AutoMirrored.Filled.OpenInNew,
                                    contentDescription = "مشاهده فایل",
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Bottom Actions: Share, Edit, Delete
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = onShare,
                    modifier = Modifier.size(40.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.Share,
                        contentDescription = "اشتراک‌گذاری",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(18.dp)
                    )
                }

                IconButton(
                    onClick = onEdit,
                    modifier = Modifier.size(40.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.Edit,
                        contentDescription = "ویرایش",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(18.dp)
                    )
                }

                IconButton(
                    onClick = onDelete,
                    modifier = Modifier.size(40.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.Delete,
                        contentDescription = "حذف",
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun AddEditDocumentDialog(
    courses: List<Course>,
    existingDocument: CourseDocument? = null,
    initialCourseId: Long? = null,
    onDismiss: () -> Unit,
    onConfirm: (
        courseId: Long,
        title: String,
        category: DocumentCategory,
        notes: String,
        fileUri: String?,
        fileName: String?,
        fileSizeBytes: Long,
        isBookmarked: Boolean
    ) -> Unit
) {
    val context = LocalContext.current
    var selectedCourseId by remember {
        mutableStateOf(existingDocument?.courseId ?: initialCourseId ?: courses.firstOrNull()?.id ?: 0L)
    }
    var title by remember { mutableStateOf(existingDocument?.title ?: "") }
    var selectedCategory by remember { mutableStateOf(existingDocument?.category ?: DocumentCategory.PAMPHLET) }
    var notes by remember { mutableStateOf(existingDocument?.contentNotes ?: "") }
    var isBookmarked by remember { mutableStateOf(existingDocument?.isBookmarked ?: false) }

    var attachedUri by remember { mutableStateOf(existingDocument?.fileUri) }
    var attachedFileName by remember { mutableStateOf(existingDocument?.fileName) }
    var attachedFileSize by remember { mutableStateOf(existingDocument?.fileSizeBytes ?: 0L) }

    var courseDropdownExpanded by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    // SAF file picker launcher
    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) {
            try {
                // Request persistable read permission
                val takeFlags: Int = Intent.FLAG_GRANT_READ_URI_PERMISSION
                context.contentResolver.takePersistableUriPermission(uri, takeFlags)
            } catch (e: Exception) {
                // Persistable permission might fail on some providers; continue with uri
            }

            attachedUri = uri.toString()

            // Resolve file name and size from content provider
            var resolvedName: String? = null
            var resolvedSize: Long = 0L
            try {
                context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                    if (cursor.moveToFirst()) {
                        val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                        val sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE)
                        if (nameIndex != -1) {
                            resolvedName = cursor.getString(nameIndex)
                        }
                        if (sizeIndex != -1) {
                            resolvedSize = cursor.getLong(sizeIndex)
                        }
                    }
                }
            } catch (e: Exception) {
                // Fallback to last path segment
                resolvedName = uri.lastPathSegment
            }

            attachedFileName = resolvedName ?: "سند پیوست"
            attachedFileSize = resolvedSize
        }
    }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            shape = TermChinDesign.CardShape,
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp)
            ) {
                Text(
                    text = if (existingDocument == null) "افزودن جزوه / سند جدید" else "ویرایش جزوه / سند",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )

                Spacer(modifier = Modifier.height(14.dp))

                // Course selector
                ExposedDropdownMenuBox(
                    expanded = courseDropdownExpanded,
                    onExpandedChange = { courseDropdownExpanded = !courseDropdownExpanded }
                ) {
                    val currentCourse = courses.find { it.id == selectedCourseId }
                    OutlinedTextField(
                        value = currentCourse?.let { "${it.name} (${it.code})" } ?: "انتخاب درس",
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("درس مربوطه") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = courseDropdownExpanded) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .menuAnchor(MenuAnchorType.PrimaryNotEditable),
                        shape = RoundedCornerShape(12.dp)
                    )

                    ExposedDropdownMenu(
                        expanded = courseDropdownExpanded,
                        onDismissRequest = { courseDropdownExpanded = false }
                    ) {
                        courses.forEach { course ->
                            DropdownMenuItem(
                                text = { Text("${course.name} - ${course.department} (${course.code})") },
                                onClick = {
                                    selectedCourseId = course.id
                                    courseDropdownExpanded = false
                                }
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Title field
                OutlinedTextField(
                    value = title,
                    onValueChange = {
                        title = it
                        if (it.isNotBlank()) errorMessage = null
                    },
                    label = { Text("عنوان سند / جزوه *") },
                    placeholder = { Text("مثلاً: جزوه فصول ۱ تا ۳ یا نمونه سوال پایان‌ترم") },
                    isError = errorMessage != null,
                    supportingText = errorMessage?.let { { Text(it, color = MaterialTheme.colorScheme.error) } },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                )

                Spacer(modifier = Modifier.height(10.dp))

                // Category chips
                Text(
                    text = "دسته‌بندی:",
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(6.dp))
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    DocumentCategory.values().forEach { cat ->
                        FilterChip(
                            selected = selectedCategory == cat,
                            onClick = { selectedCategory = cat },
                            label = { Text(cat.titleFa, fontSize = 11.sp) }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Notes / Summary Multiline text
                OutlinedTextField(
                    value = notes,
                    onValueChange = { notes = it },
                    label = { Text("خلاصه، یادداشت یا نکات کلیدی") },
                    placeholder = { Text("فرمول‌های مهم، سرفصل‌های امتحانی یا توضیحات...") },
                    minLines = 3,
                    maxLines = 5,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                )

                Spacer(modifier = Modifier.height(12.dp))

                // Attached File Section
                if (!attachedUri.isNullOrBlank()) {
                    Surface(
                        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(
                                    Icons.Filled.AttachFile,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = attachedFileName ?: "فایل پیوست",
                                    style = MaterialTheme.typography.labelMedium,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            IconButton(
                                onClick = {
                                    attachedUri = null
                                    attachedFileName = null
                                    attachedFileSize = 0L
                                },
                                modifier = Modifier.size(40.dp)
                            ) {
                                Icon(Icons.Filled.Close, contentDescription = "حذف فایل", modifier = Modifier.size(16.dp))
                            }
                        }
                    }
                } else {
                    OutlinedButton(
                        onClick = {
                            filePickerLauncher.launch(
                                arrayOf(
                                    "application/pdf",
                                    "image/*",
                                    "application/msword",
                                    "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                                    "text/plain",
                                    "*/*"
                                )
                            )
                        },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(Icons.Filled.AttachFile, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("پیوست فایل (PDF، تصویر یا سند)", fontSize = 12.5.sp)
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Bookmark toggle
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "نشان کردن به عنوان سند مهم",
                        style = MaterialTheme.typography.bodySmall
                    )
                    Switch(
                        checked = isBookmarked,
                        onCheckedChange = { isBookmarked = it }
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Actions
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(onClick = onDismiss) {
                        Text("انصراف")
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = {
                            if (title.isBlank()) {
                                errorMessage = "لطفاً عنوان را وارد نمایید."
                                return@Button
                            }
                            if (selectedCourseId == 0L) {
                                errorMessage = "لطفاً درس را انتخاب کنید."
                                return@Button
                            }
                            onConfirm(
                                selectedCourseId,
                                title,
                                selectedCategory,
                                notes,
                                attachedUri,
                                attachedFileName,
                                attachedFileSize,
                                isBookmarked
                            )
                        },
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text(if (existingDocument == null) "ذخیره سند" else "به‌روزرسانی")
                    }
                }
            }
        }
    }
}

@Composable
private fun NoCoursesEmptyState(onGoToCourses: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(
                 imageVector = Icons.AutoMirrored.Filled.MenuBook,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f),
                modifier = Modifier.size(64.dp)
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "هنوز درس نهایی‌شده‌ای ندارید",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "تب جزوات فقط برای دروسی است که در برنامه هفتگی اعمال و نهایی شده‌اند. ابتدا دروس را انتخاب کنید و برنامه را اعمال نمایید.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(20.dp))
            Button(
                onClick = onGoToCourses,
                shape = RoundedCornerShape(12.dp)
            ) {
                Text("ورود به بخش دروس و افزودن درس")
            }
        }
    }
}

@Composable
private fun NoDocumentsEmptyState(onAddFirst: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Surface(
                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f),
                shape = CircleShape,
                modifier = Modifier.size(80.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Filled.FolderOpen,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(44.dp)
                    )
                }
            }
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "دفترچه جزوات و اسناد شما خالی است",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "شما می‌توانید جزوه‌های کلاسی، خلاصه‌درس‌ها، نمونه‌سوالات امتحانی، اسلایدها و فایل‌های PDF را به تفکیک درس‌هایتان در اینجا ذخیره کنید.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(20.dp))
            Button(
                onClick = onAddFirst,
                shape = RoundedCornerShape(12.dp)
            ) {
                Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text("افزودن اولین جزوه یا سند")
            }
        }
    }
}

@Composable
private fun getCategoryColors(category: DocumentCategory): Pair<Color, Color> {
    // Category accents follow the active palette: the light variants stay
    // readable on a white card, the dark variants on a dark surface.
    val accent = if (isDarkTheme()) {
        when (category) {
            DocumentCategory.PAMPHLET -> MaterialTheme.colorScheme.primary
            DocumentCategory.SUMMARY -> CategoryGreenDark
            DocumentCategory.EXAM_SAMPLE -> CategoryPurpleDark
            DocumentCategory.ASSIGNMENT -> CategoryOrangeDark
            DocumentCategory.SLIDES -> CategoryTealDark
            DocumentCategory.NOTE -> CategoryPinkDark
        }
    } else {
        when (category) {
            DocumentCategory.PAMPHLET -> MaterialTheme.colorScheme.primary
            DocumentCategory.SUMMARY -> CategoryGreenLight
            DocumentCategory.EXAM_SAMPLE -> CategoryPurpleLight
            DocumentCategory.ASSIGNMENT -> CategoryOrangeLight
            DocumentCategory.SLIDES -> CategoryTealLight
            DocumentCategory.NOTE -> CategoryPinkLight
        }
    }
    return accent.copy(alpha = 0.15f) to accent
}

private fun formatTimestamp(timestamp: Long): String {
    val sdf = SimpleDateFormat("yyyy/MM/dd - HH:mm", Locale.getDefault())
    return sdf.format(Date(timestamp))
}

private fun formatFileSize(bytes: Long): String {
    if (bytes <= 0) return ""
    val kb = bytes / 1024.0
    val mb = kb / 1024.0
    return if (mb >= 1.0) {
        String.format(Locale.US, "%.1f MB", mb)
    } else {
        String.format(Locale.US, "%.0f KB", kb)
    }
}

private fun openDocumentUri(context: Context, uriString: String) {
    try {
        val uri = Uri.parse(uriString)
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, context.contentResolver.getType(uri) ?: "*/*")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(intent, "مشاهده سند"))
    } catch (e: Exception) {
        Toast.makeText(context, "امکان باز کردن مستقیم فایل وجود ندارد یا برنامه مناسب نصب نیست.", Toast.LENGTH_LONG).show()
    }
}

private fun shareDocumentDetails(context: Context, item: DocumentWithCourse) {
    val doc = item.document
    val text = buildString {
        appendLine("📚 ${doc.title}")
        appendLine("درس: ${item.course.name} (${item.course.code})")
        appendLine("دسته‌بندی: ${doc.category.titleFa}")
        if (doc.contentNotes.isNotBlank()) {
            appendLine("\nیادداشت‌ها و خلاصه:")
            appendLine(doc.contentNotes)
        }
        if (!doc.fileName.isNullOrBlank()) {
            appendLine("\nنام فایل پیوست: ${doc.fileName}")
        }
    }

    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_SUBJECT, doc.title)
        putExtra(Intent.EXTRA_TEXT, text)
    }
    context.startActivity(Intent.createChooser(intent, "اشتراک‌گذاری اطلاعات جزوه"))
}

private fun copyToClipboard(context: Context, text: String) {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    val clip = ClipData.newPlainText("Notes", text)
    clipboard.setPrimaryClip(clip)
    Toast.makeText(context, "متن یادداشت در کلیپ‌بورد کپی شد.", Toast.LENGTH_SHORT).show()
}
