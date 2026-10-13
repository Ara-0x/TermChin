package ir.courseplanner.app.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Event
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.GroupAdd
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ir.courseplanner.app.data.model.ClassSession
import ir.courseplanner.app.data.model.Course
import ir.courseplanner.app.data.model.CourseWithSections
import ir.courseplanner.app.data.model.SectionWithDetails
import ir.courseplanner.app.data.model.WeekType
import ir.courseplanner.app.ui.AppDestination
import ir.courseplanner.app.ui.ManualSessionInput
import ir.courseplanner.app.ui.CourseDegreeFilter
import ir.courseplanner.app.ui.CoursePlannerViewModel
import ir.courseplanner.app.ui.CourseSortOrder
import ir.courseplanner.app.ui.CourseUnitsFilter
import ir.courseplanner.app.ui.FILTERABLE_DAYS
import ir.courseplanner.app.ui.components.AddCourseDialog
import ir.courseplanner.app.ui.components.AddSectionDialog
import ir.courseplanner.app.ui.components.EditCourseDialog
import ir.courseplanner.app.ui.screens.courses.CatalogQuickAddResults
import ir.courseplanner.app.ui.screens.courses.CourseCard
import ir.courseplanner.app.ui.screens.courses.FilterLabelRow
import ir.courseplanner.app.ui.theme.TermChinDesign

@Composable
fun CoursesScreen(
    viewModel: CoursePlannerViewModel,
    modifier: Modifier = Modifier
) {
    val searchQuery by viewModel.searchQuery.collectAsStateWithLifecycle()
    val selectedDept by viewModel.selectedDepartment.collectAsStateWithLifecycle()
    val sortOrder by viewModel.sortOrder.collectAsStateWithLifecycle()
    val filteredCourses by viewModel.filteredCourses.collectAsStateWithLifecycle()
    val allCoursesWithSections by viewModel.coursesWithSections.collectAsStateWithLifecycle()
    val sectionsByCourse by viewModel.sectionsByCourse.collectAsStateWithLifecycle()
    val documentCountByCourse by viewModel.documentCountByCourse.collectAsStateWithLifecycle()
    val departmentNames by viewModel.departmentNames.collectAsStateWithLifecycle()
    val catalogOnlyCourseCount by viewModel.catalogOnlyCourseCount.collectAsStateWithLifecycle()
    val selectedCount = remember(allCoursesWithSections) {
        allCoursesWithSections.count { it.course.isSelectedForGeneration }
    }
    val enrolledSections by viewModel.enrolledSections.collectAsStateWithLifecycle()

    var showAddCourseDialog by remember { mutableStateOf(false) }
    var courseForAddSection by remember { mutableStateOf<Course?>(null) }
    var courseToDelete by remember { mutableStateOf<Course?>(null) }
    var courseToEdit by remember { mutableStateOf<Course?>(null) }
    var sectionToEdit by remember { mutableStateOf<SectionWithDetails?>(null) }

    val departments = departmentNames
    // Stable signature of the enrollment set: conflict checks are recomputed
    // only when enrollments actually change, not on every recomposition.
    val enrolledKey = remember(enrolledSections) {
        enrolledSections.map { it.section.id }.sorted().joinToString(",")
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { viewModel.openAddCourse() },
                icon = { Icon(Icons.Default.Add, contentDescription = "افزودن درس") },
                text = { Text("افزودن درس", fontWeight = FontWeight.Bold, fontSize = 13.sp) },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                shape = RoundedCornerShape(12.dp),
                elevation = androidx.compose.material3.FloatingActionButtonDefaults.elevation(defaultElevation = 1.dp),
                modifier = Modifier.testTag("fab_add_course")
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp, vertical = 8.dp)
        ) {
            // Orientation is carried by the bottom bar + the info bar below the
            // filters, so no separate hero header is needed here.

            // Search bar (Offline search)
            Surface(
                shape = TermChinDesign.CardShape,
                color = MaterialTheme.colorScheme.surface,
                border = androidx.compose.foundation.BorderStroke(
                    1.dp,
                    MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f)
                ),
                modifier = Modifier.fillMaxWidth()
            ) {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { viewModel.onSearchQueryChange(it) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("course_search_input"),
                    placeholder = {
                        Text(
                            "جست‌وجوی درس، کد، دانشکده یا استاد...",
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                        )
                    },
                    leadingIcon = {
                        Icon(
                            Icons.Default.Search,
                            contentDescription = "جست‌وجوی درس",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    },
                    trailingIcon = {
                        if (searchQuery.isNotBlank()) {
                            IconButton(onClick = { viewModel.onSearchQueryChange("") }) {
                                Icon(Icons.Default.Clear, contentDescription = "پاک کردن جست‌وجو")
                            }
                        }
                    },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                        unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                    ),
                    shape = TermChinDesign.CardShape
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Quick-add by course code: matches inside the hidden portal catalog
            // (courses not yet in "my courses"). The cards are rendered inside the
            // LazyColumn below so any number of matches stays scrollable.
            val myCourseIds = remember(allCoursesWithSections) {
                allCoursesWithSections.filter { cws ->
                    cws.course.isSelectedForGeneration ||
                        cws.sections.any { it.section.isEnrolled }
                }.map { it.course.id }.toSet()
            }
            val catalogMatches = remember(searchQuery, allCoursesWithSections) {
                val q = searchQuery.trim()
                if (q.isEmpty()) emptyList()
                else allCoursesWithSections.filter { cws ->
                    cws.course.id !in myCourseIds &&
                        (cws.course.code.contains(q, ignoreCase = true) ||
                            cws.course.name.contains(q, ignoreCase = true))
                }.take(6)
            }

            // Advanced filter panel (department / degree / units / day):
            // collapsible so the list stays compact. Status tabs + sort live
            // pinned below, always visible. Badge shows the active-filter count.
            val unitsFilter by viewModel.unitsFilter.collectAsStateWithLifecycle()
            val degreeFilter by viewModel.degreeFilter.collectAsStateWithLifecycle()
            val dayFilter by viewModel.dayFilter.collectAsStateWithLifecycle()
            val onlyWithSessions by viewModel.onlyWithSessions.collectAsStateWithLifecycle()
            var filtersExpanded by remember { mutableStateOf(false) }
            val activeFilterCount =
                (if (selectedDept != null) 1 else 0) +
                    (if (unitsFilter != CourseUnitsFilter.ALL) 1 else 0) +
                    (if (degreeFilter != CourseDegreeFilter.ALL) 1 else 0) +
                    (if (dayFilter != null) 1 else 0) +
                    (if (onlyWithSessions) 1 else 0)
            // One-line summary shown on the collapsed header, so applied
            // filters are visible without expanding the panel.
            val activeFilterSummary = listOfNotNull(
                selectedDept,
                degreeFilter.takeIf { it != CourseDegreeFilter.ALL }?.titleFa,
                unitsFilter.takeIf { it != CourseUnitsFilter.ALL }?.titleFa,
                dayFilter?.let { ClassSession.getDayName(it) },
                "دارای ساعت".takeIf { onlyWithSessions }
            ).joinToString(" • ")

            Surface(
                shape = TermChinDesign.CardShape,
                color = MaterialTheme.colorScheme.surface,
                border = androidx.compose.foundation.BorderStroke(
                    1.dp,
                    MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f)
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { filtersExpanded = !filtersExpanded }
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            Icons.Default.FilterList,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(18.dp)
                        )
                        Text(
                            text = "فیلترهای پیشرفته",
                            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        if (activeFilterCount > 0) {
                            Box(
                                modifier = Modifier
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.primary)
                                    .padding(horizontal = 7.dp, vertical = 1.dp)
                            ) {
                                Text(
                                    text = "$activeFilterCount",
                                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                    color = MaterialTheme.colorScheme.onPrimary
                                )
                            }
                        }
                        Spacer(modifier = Modifier.weight(1f))
                        if (activeFilterCount > 0) {
                            TextButton(onClick = { viewModel.clearCourseFilters() }) {
                                Text("حذف همه", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                        Icon(
                            if (filtersExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    if (!filtersExpanded && activeFilterSummary.isNotEmpty()) {
                        Text(
                            text = activeFilterSummary,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.padding(start = 26.dp)
                        )
                    }
                }
            }

            AnimatedVisibility(
                visible = filtersExpanded,
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Department filter chips
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        departments.forEach { dept ->
                            val isSelected = if (dept == "همه") selectedDept == null else selectedDept == dept
                            FilterChip(
                                selected = isSelected,
                                onClick = {
                                    viewModel.onDepartmentSelected(if (dept == "همه") null else dept)
                                },
                                label = {
                                    Text(
                                        text = dept,
                                        fontSize = 12.sp,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                                    )
                                },
                                shape = RoundedCornerShape(10.dp),
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                                    selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                            )
                        }
                    }

                    // Degree level chips (کاردانی / کارشناسی / ارشد)
                    FilterLabelRow(label = "مقطع:")
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        CourseDegreeFilter.values().forEach { filter ->
                            val isSelected = degreeFilter == filter
                            FilterChip(
                                selected = isSelected,
                                onClick = { viewModel.setDegreeFilter(filter) },
                                label = {
                                    Text(
                                        text = filter.titleFa,
                                        fontSize = 11.5.sp,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                                    )
                                },
                                shape = RoundedCornerShape(10.dp),
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = MaterialTheme.colorScheme.secondaryContainer,
                                    selectedLabelColor = MaterialTheme.colorScheme.onSecondaryContainer
                                )
                            )
                        }
                    }

                    // Units chips
                    FilterLabelRow(label = "واحد:")
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        CourseUnitsFilter.values().forEach { filter ->
                            val isSelected = unitsFilter == filter
                            FilterChip(
                                selected = isSelected,
                                onClick = { viewModel.setUnitsFilter(filter) },
                                label = {
                                    Text(
                                        text = filter.titleFa,
                                        fontSize = 11.5.sp,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                                    )
                                },
                                shape = RoundedCornerShape(10.dp),
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                                    selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                            )
                        }
                    }

                    // Class-time presence is unrelated to credit units, so it
                    // gets its own row instead of hiding inside the units row.
                    FilterLabelRow(label = "ساعت کلاسی:")
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        FilterChip(
                            selected = onlyWithSessions,
                            onClick = { viewModel.setOnlyWithSessions(!onlyWithSessions) },
                            label = {
                                Text(
                                    "فقط دارای ساعت کلاسی",
                                    fontSize = 11.5.sp,
                                    fontWeight = if (onlyWithSessions) FontWeight.Bold else FontWeight.Normal
                                )
                            },
                            shape = RoundedCornerShape(10.dp),
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                                selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        )
                    }

                    // Class-day chips
                    FilterLabelRow(label = "روز کلاس:")
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        FilterChip(
                            selected = dayFilter == null,
                            onClick = { viewModel.setDayFilter(null) },
                            label = { Text("همه روزها", fontSize = 11.5.sp) },
                            shape = RoundedCornerShape(10.dp)
                        )
                        FILTERABLE_DAYS.forEach { day ->
                            val isSelected = dayFilter == day
                            FilterChip(
                                selected = isSelected,
                                onClick = { viewModel.setDayFilter(if (isSelected) null else day) },
                                label = {
                                    Text(
                                        text = ClassSession.getDayName(day),
                                        fontSize = 11.5.sp,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                                    )
                                },
                                shape = RoundedCornerShape(10.dp),
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                                    selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                            )
                        }
                    }

                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Sort control only: the course list is always «دروس من», so the
            // former status tab row (which toggled between the user's courses
            // and the whole catalog) was removed — it was pure chrome.
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                    border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
                    modifier = Modifier.clickable {
                        val next = when (sortOrder) {
                            CourseSortOrder.NAME -> CourseSortOrder.CREDITS_DESC
                            CourseSortOrder.CREDITS_DESC -> CourseSortOrder.CODE
                            CourseSortOrder.CODE -> CourseSortOrder.NAME
                        }
                        viewModel.setSortOrder(next)
                    }
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                         Icon(
                             Icons.AutoMirrored.Filled.Sort,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(15.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = sortOrder.titleFa,
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Info Bar
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "${filteredCourses.size} درس ثبت شده",
                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.7f)
                ) {
                    Text(
                        text = "$selectedCount درس در برنامه‌ساز",
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Whenever sorting or any filter/search changes, the result set is
            // rebuilt — jump back to the top so the user never has to scroll
            // up manually to see the new order.
            val listState = rememberLazyListState()
            LaunchedEffect(
                sortOrder, unitsFilter, degreeFilter,
                dayFilter, onlyWithSessions, selectedDept, searchQuery
            ) {
                listState.scrollToItem(0)
            }

            if (filteredCourses.isEmpty()) {
                // When the search matches catalog courses, show those scrollable
                // cards first — the "nothing found" box alone would hide them.
                if (catalogMatches.isNotEmpty()) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        CatalogQuickAddResults(
                            catalogMatches = catalogMatches,
                            sectionsByCourse = sectionsByCourse,
                            onAdd = { viewModel.addCatalogCourseToMine(it) }
                        )
                        Text(
                            text = "در «دروس من» چیزی با این مشخصات یافت نشد.",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(vertical = 4.dp)
                        )
                    }
                } else {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    Surface(
                        shape = TermChinDesign.CardShape,
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)),
                        modifier = Modifier.padding(24.dp)
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.padding(24.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(54.dp)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    Icons.Default.School,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(28.dp)
                                )
                            }
                            Spacer(modifier = Modifier.height(12.dp))
                            // Guide the user when the portal catalog exists but
                            // "my courses" is still empty: search a code to add.
                            if (allCoursesWithSections.isEmpty()) {
                                Text(
                                    text = "کاتالوگ دروس هنوز خالی است.",
                                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                    color = MaterialTheme.colorScheme.onSurface,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = "می‌توانید اولین درس خود را دستی ثبت نمایید.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Spacer(modifier = Modifier.height(16.dp))
                                Button(
                                    onClick = { showAddCourseDialog = true },
                                    shape = RoundedCornerShape(12.dp)
                                ) {
                                    Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("افزودن درس دستی")
                                }
                            } else if (searchQuery.isBlank() && catalogOnlyCourseCount > 0
                            ) {
                                Text(
                                    text = "کاتالوگ پرتال با $catalogOnlyCourseCount درس آماده است.",
                                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                    color = MaterialTheme.colorScheme.onSurface,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = "از صفحه «افزودن درس» می‌توانید پیشنهادهای سازگار با برنامه فعلی را ببینید و بدون وارد کردن کد، درس اضافه کنید.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Spacer(modifier = Modifier.height(14.dp))
                                Button(
                                    onClick = { viewModel.openAddCourse() },
                                    shape = RoundedCornerShape(12.dp)
                                ) {
                                    Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("مشاهده پیشنهادها و افزودن درس")
                                }
                            } else {
                                Text(
                                    text = "هیچ درسی با این مشخصات یافت نشد.",
                                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = "فیلترها یا عبارت جست‌وجو را بررسی کنید، یا درس جدید اضافه نمایید.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Spacer(modifier = Modifier.height(14.dp))
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Button(
                                        onClick = { viewModel.openAddCourse() },
                                        shape = RoundedCornerShape(12.dp)
                                    ) {
                                        Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text("افزودن درس")
                                    }
                                    TextButton(onClick = { viewModel.clearCourseFilters() }) {
                                        Text("حذف همه فیلترها", fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                        }
                    }
                }
                }
            } else {
                LazyColumn(
                    state = listState,
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    item(key = "catalog_quick_add") {
                        CatalogQuickAddResults(
                            catalogMatches = catalogMatches,
                            sectionsByCourse = sectionsByCourse,
                            onAdd = { viewModel.addCatalogCourseToMine(it) }
                        )
                    }
                    items(filteredCourses, key = { it.course.id }) { cws ->
                        CourseCard(
                            courseWithSections = cws,
                            sections = sectionsByCourse[cws.course.id].orEmpty(),
                            enrolledKey = enrolledKey,
                            docCount = documentCountByCourse[cws.course.id] ?: 0,
                            onOpenDocuments = {
                                viewModel.navigateToCourseDocuments(cws.course.id)
                            },
                            onToggleSelectedForGeneration = { isSelected ->
                                viewModel.toggleCourseSelectedForGeneration(cws.course.id, isSelected)
                            },
                            onSelectSection = { sec, isEnrolled ->
                                viewModel.toggleSectionEnrolled(sec, isEnrolled)
                            },
                            onAddSectionClick = {
                                courseForAddSection = cws.course
                            },
                            onEditCourseClick = {
                                courseToEdit = cws.course
                            },
                            onDeleteCourseClick = {
                                courseToDelete = cws.course
                            },
                            onDeleteSectionClick = { sec ->
                                viewModel.deleteSection(sec.section.id, sec.sectionCode)
                            },
                            onEditSectionClick = { sec ->
                                sectionToEdit = sec
                            },
                            checkConflict = { sec ->
                                viewModel.checkConflictForCandidate(sec)
                            }
                        )
                    }
                }
            }
        }
    }

    // Add Course Dialog
    if (showAddCourseDialog) {
        AddCourseDialog(
            onDismiss = { showAddCourseDialog = false },
            onConfirm = { name, code, dept, credits, secCode, instructor, examDate, examStart, examEnd, sessions ->
                viewModel.addManualCourse(
                    name = name,
                    code = code,
                    department = dept,
                    credits = credits,
                    sectionCode = secCode,
                    instructor = instructor,
                    examDate = examDate,
                    examStartTime = examStart,
                    examEndTime = examEnd,
                    sessions = sessions
                )
                showAddCourseDialog = false
            }
        )
    }

    // Add Section Dialog
    courseForAddSection?.let { targetCourse ->
        AddSectionDialog(
            course = targetCourse,
            onDismiss = { courseForAddSection = null },
            onConfirm = { secCode, instructor, examDate, examStart, examEnd, sessions ->
                viewModel.addSectionToCourse(
                    courseId = targetCourse.id,
                    sectionCode = secCode,
                    instructor = instructor,
                    examDate = examDate,
                    examStartTime = examStart,
                    examEndTime = examEnd,
                    sessions = sessions
                )
                courseForAddSection = null
            }
        )
    }

    // Edit Section Dialog (same form as add, prefilled; enrollment is kept)
    sectionToEdit?.let { targetSection ->
        AddSectionDialog(
            course = targetSection.course,
            onDismiss = { sectionToEdit = null },
            onConfirm = { secCode, instructor, examDate, examStart, examEnd, sessions ->
                viewModel.updateSection(
                    sectionId = targetSection.section.id,
                    sectionCode = secCode,
                    instructor = instructor,
                    examDate = examDate,
                    examStartTime = examStart,
                    examEndTime = examEnd,
                    sessions = sessions
                )
                sectionToEdit = null
            },
            initialSectionCode = targetSection.section.sectionCode,
            initialInstructor = targetSection.section.instructor,
            initialExamDate = targetSection.section.examDate,
            initialExamTimeRange = targetSection.examTimeRange,
            initialSessions = targetSection.sessions.map { sess ->
                ManualSessionInput(
                    dayOfWeek = sess.dayOfWeek,
                    startTime = sess.startTime,
                    endTime = sess.endTime,
                    location = sess.location,
                    weekType = sess.weekType
                )
            },
            dialogTitle = "ویرایش گروه ${targetSection.section.sectionCode} • ${targetSection.course.name}",
            confirmLabel = "ذخیره تغییرات"
        )
    }

    // Edit Course Dialog (in-place: sections, enrollments and docs are kept)
    courseToEdit?.let { targetCourse ->
        EditCourseDialog(
            course = targetCourse,
            onDismiss = { courseToEdit = null },
            onConfirm = { name, code, department, credits ->
                viewModel.updateCourse(
                    courseId = targetCourse.id,
                    name = name,
                    code = code,
                    department = department,
                    credits = credits
                )
                courseToEdit = null
            }
        )
    }

    // Delete Course Confirmation
    courseToDelete?.let { targetCourse ->
        AlertDialog(
            onDismissRequest = { courseToDelete = null },
            icon = { Icon(Icons.Default.DeleteOutline, contentDescription = null, tint = MaterialTheme.colorScheme.error) },
            title = { Text("حذف درس «${targetCourse.name}»", fontWeight = FontWeight.Bold) },
            text = { Text("آیا مطمئن هستید که می‌خواهید این درس و تمام گروه‌های آن را حذف کنید؟") },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.deleteCourse(targetCourse.id, targetCourse.name)
                        courseToDelete = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Text("بله، حذف کن")
                }
            },
            dismissButton = {
                TextButton(onClick = { courseToDelete = null }) {
                    Text("انصراف")
                }
            }
        )
    }
}
