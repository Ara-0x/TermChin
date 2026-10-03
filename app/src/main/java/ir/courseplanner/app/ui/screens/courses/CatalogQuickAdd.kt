package ir.courseplanner.app.ui.screens.courses

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
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
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
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
import ir.courseplanner.app.ui.CourseStatusFilter
import ir.courseplanner.app.ui.CourseUnitsFilter
import ir.courseplanner.app.ui.FILTERABLE_DAYS
import ir.courseplanner.app.ui.components.AddCourseDialog
import ir.courseplanner.app.ui.components.AddSectionDialog
import ir.courseplanner.app.ui.components.EditCourseDialog

/** Tiny section label used inside the collapsible filter panel. */
@Composable
internal fun FilterLabelRow(label: String) {
    Text(
        text = label,
        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
}

/**
 * Scrollable quick-add results block. Rendered as the first item of the course
 * list (and in the empty state) so every catalog match is reachable no matter
 * how many there are.
 */
@Composable
internal fun CatalogQuickAddResults(
    catalogMatches: List<CourseWithSections>,
    sectionsByCourse: Map<Long, List<SectionWithDetails>>,
    onAdd: (Long) -> Unit
) {
    AnimatedVisibility(
        visible = catalogMatches.isNotEmpty(),
        enter = fadeIn(),
        exit = fadeOut()
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = "یافته‌ها در کاتالوگ پرتال (${catalogMatches.size} مورد) — برای افزودن لمس کنید:",
                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.primary
            )
            catalogMatches.forEach { cws ->
                CatalogQuickAddCard(
                    courseWithSections = cws,
                    sections = sectionsByCourse[cws.course.id].orEmpty(),
                    onAdd = { onAdd(cws.course.id) }
                )
            }
            Spacer(modifier = Modifier.height(4.dp))
        }
    }
}

/**
 * Compact catalog result card for "add by course code".
 * Shows just enough detail (groups, instructor, first session, capacity)
 * for the user to pick the right course without opening the full catalog.
 */
@Composable
internal fun CatalogQuickAddCard(
    courseWithSections: CourseWithSections,
    sections: List<SectionWithDetails>,
    onAdd: () -> Unit
) {
    val course = courseWithSections.course
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f),
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            MaterialTheme.colorScheme.primary.copy(alpha = 0.45f)
        ),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("catalog_quick_add_${course.code}")
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            // Layout contract: the course name owns the full first line and may
            // wrap to a second one; the "add" Button keeps a fixed slot at the
            // line end. ... appears only past two full lines. Structure:
            //   Column
            //   ├─ nameRow   (name Text only)
            //   ├─ actionRow (meta Text + spacer + Button)
            Text(
                text = course.name,
                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("catalog_quick_add_name_${course.code}")
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "کد: ${course.code}  •  ${course.credits} واحد  •  ${sections.size} گروه",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Button(
                    onClick = onAdd,
                    shape = RoundedCornerShape(10.dp),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(
                        horizontal = 12.dp,
                        vertical = 4.dp
                    ),
                    modifier = Modifier.height(36.dp)
                ) {
                    Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("افزودن", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
            }

            sections.take(2).forEach { sec ->
                val first = sec.sessions.minByOrNull { it.dayOfWeek * 1440 + it.startMinutes }
                val preview = if (first != null) {
                    val extra = if (sec.sessions.size > 1) " +${sec.sessions.size - 1} جلسه دیگر" else ""
                    "${ClassSession.getDayName(first.dayOfWeek)} ${first.startTime} تا ${first.endTime}" +
                        (if (first.location.isNotBlank()) " (${first.location})" else "") + extra
                } else {
                    "بدون ساعت کلاسی ثبت‌شده"
                }
                Text(
                    text = "گروه ${sec.sectionCode} • ${sec.instructor.ifBlank { "استاد نامشخص" }} • $preview • ظرفیت ${sec.section.capacity}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
            if (sections.size > 2) {
                Text(
                    text = "و ${sections.size - 2} گروه دیگر…",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}
