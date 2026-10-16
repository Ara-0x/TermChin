package ir.courseplanner.app.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow

import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ir.courseplanner.app.data.model.Conflict
import ir.courseplanner.app.ui.theme.ConflictRedBgDark
import ir.courseplanner.app.ui.theme.ConflictRedBgLight
import ir.courseplanner.app.ui.theme.ConflictRedDark
import ir.courseplanner.app.ui.theme.ConflictRedLight
import ir.courseplanner.app.ui.theme.SuccessGreenBgDark
import ir.courseplanner.app.ui.theme.SuccessGreenBgLight
import ir.courseplanner.app.ui.theme.SuccessGreenDark
import ir.courseplanner.app.ui.theme.SuccessGreenLight
import ir.courseplanner.app.ui.theme.TermChinDesign
import ir.courseplanner.app.ui.theme.isDarkTheme

@Composable
fun ConflictBanner(
    conflicts: List<Conflict>,
    hasEnrolledCourses: Boolean,
    modifier: Modifier = Modifier
) {
    // Respect the app's explicit light/dark preference, not only the device setting.
    val isDark = isDarkTheme()
    var isExpanded by remember { mutableStateOf(false) }

    if (!hasEnrolledCourses) {
        Surface(
            modifier = modifier
                .fillMaxWidth()
                .testTag("empty_schedule_banner"),
            shape = TermChinDesign.CardShape,
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
            border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    Icons.Default.Info,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    text = "برنامه درسی شما در حال حاضر خالی است. از تب «دروس» درس‌های مورد نظرتان را انتخاب کنید.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f)
                )
            }
        }
        return
    }

    val hasConflict = conflicts.isNotEmpty()
    val bgColor = if (hasConflict) {
        if (isDark) ConflictRedBgDark else ConflictRedBgLight
    } else {
        if (isDark) SuccessGreenBgDark else SuccessGreenBgLight
    }
    val contentColor = if (hasConflict) {
        if (isDark) ConflictRedDark else ConflictRedLight
    } else {
        if (isDark) SuccessGreenDark else SuccessGreenLight
    }
    val borderColor = if (hasConflict) {
        (if (isDark) ConflictRedDark else ConflictRedLight).copy(alpha = 0.4f)
    } else {
        (if (isDark) SuccessGreenDark else SuccessGreenLight).copy(alpha = 0.4f)
    }

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .shadow(TermChinDesign.CardElevation, TermChinDesign.CardShape)
            .clickable(enabled = hasConflict) { isExpanded = !isExpanded }
            .testTag("conflict_banner"),
        shape = TermChinDesign.CardShape,
        color = bgColor,
        border = androidx.compose.foundation.BorderStroke(1.dp, borderColor)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    Box(
                        modifier = Modifier
                            .size(32.dp)
                            .clip(CircleShape)
                            .background(contentColor.copy(alpha = 0.15f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = if (hasConflict) Icons.Default.Warning else Icons.Default.CheckCircle,
                            contentDescription = if (hasConflict) "هشدار تداخل برنامه" else "برنامه بدون تداخل است",
                            tint = contentColor,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = if (hasConflict) {
                                "${conflicts.size} تداخل در برنامه انتخابی یافت شد"
                            } else {
                                "برنامه هفتگی کاملاً هماهنگ و بدون تداخل است"
                            },
                            style = MaterialTheme.typography.bodyMedium.copy(
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.5.sp
                            ),
                            color = contentColor
                        )
                        if (hasConflict) {
                            Text(
                                text = "برای بررسی جزئیات تداخل‌ها کلیک کنید",
                                style = MaterialTheme.typography.labelSmall,
                                color = contentColor.copy(alpha = 0.8f)
                            )
                        }
                    }
                }

                if (hasConflict) {
                    Spacer(modifier = Modifier.width(8.dp))
                    Icon(
                        imageVector = if (isExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                        contentDescription = "نمایش جزئیات تداخل‌ها",
                        tint = contentColor,
                        modifier = Modifier.size(22.dp)
                    )
                }
            }

            AnimatedVisibility(
                visible = isExpanded && hasConflict,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    conflicts.forEachIndexed { index, conflict ->
                        Surface(
                            shape = TermChinDesign.ControlShape,
                            color = MaterialTheme.colorScheme.surface,
                            border = androidx.compose.foundation.BorderStroke(
                                1.dp,
                                contentColor.copy(alpha = 0.3f)
                            )
                        ) {
                            Column(modifier = Modifier.padding(10.dp)) {
                                Text(
                                    text = "${index + 1}. ${conflict.descriptionFa}",
                                    style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Medium),
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                if (conflict.timeRange.isNotBlank()) {
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = "بازه: ${conflict.timeRange}",
                                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                                        color = contentColor
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
