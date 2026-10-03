package com.example.ui.main

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import com.example.ui.util.withAlpha
import androidx.compose.material.icons.filled.DriveFileRenameOutline
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.model.Project
import com.example.model.ProjectAspectRatio
import com.example.ui.theme.BackgroundDark
import com.example.ui.theme.NeonAmber
import com.example.ui.theme.NeonCyan
import com.example.ui.theme.NeonEmerald
import com.example.ui.theme.NeonMagenta
import com.example.ui.theme.SurfaceBorderDark
import com.example.ui.theme.SurfaceContainerDark
import com.example.ui.theme.SurfaceDark
import com.example.ui.theme.SurfaceVariantDark
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import com.example.ui.util.toComposeColor
import com.example.ui.viewmodel.ProjectsViewModel
import com.example.util.AppVersion
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

import com.example.di.KmpViewModelFactory
import com.example.data.repository.createProjectRepository

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun MainMenuScreen(
    module: com.example.di.AppModule,
    onOpenProject: (Long) -> Unit,
    viewModel: ProjectsViewModel = viewModel(
        factory = KmpViewModelFactory(module.repository)
    )
) {
    val projects by viewModel.allProjects.collectAsStateWithLifecycle()
    val selectedFilter by viewModel.selectedFilter.collectAsStateWithLifecycle()

    var showCreateSheet by remember { mutableStateOf(false) }
    var projectToRename by remember { mutableStateOf<Project?>(null) }
    var projectToDelete by remember { mutableStateOf<Project?>(null) }

    val filteredProjects = projects.filter { project ->
        when (selectedFilter) {
            "9:16" -> project.width < project.height
            "16:9" -> project.width > project.height
            "1:1" -> project.width == project.height
            else -> true
        }
    }

    Scaffold(
        modifier = Modifier
            .fillMaxSize()
            .background(BackgroundDark),
        containerColor = BackgroundDark,
        contentWindowInsets = androidx.compose.foundation.layout.WindowInsets(0, 0, 0, 0),
        floatingActionButton = {
            FloatingActionButton(
                onClick = { showCreateSheet = true },
                containerColor = NeonCyan,
                contentColor = Color.Black,
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier
                    // .navigationBarsPadding()
                    .testTag("create_project_fab")
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.Add, contentDescription = "New Project", tint = Color.Black)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("New Project", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = Color.Black)
                }
            }
        }
    ) { _ ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                // .statusBarsPadding()
        ) {
            // Header Bar
            MainMenuHeader()

            // Filter Chips Row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                listOf("ALL" to "All Projects", "9:16" to "9:16 Shorts", "16:9" to "16:9 Landscape", "1:1" to "1:1 Square").forEach { (filterKey, label) ->
                    val isSel = selectedFilter == filterKey
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(if (isSel) NeonCyan else SurfaceVariantDark)
                            .clickable { viewModel.setFilter(filterKey) }
                            .padding(horizontal = 12.dp, vertical = 6.dp)
                    ) {
                        Text(
                            text = label,
                            style = MaterialTheme.typography.labelSmall.copy(
                                color = if (isSel) Color(0xFF090B10.toInt()) else TextSecondary,
                                fontWeight = FontWeight.Bold,
                                fontSize = 11.sp
                            )
                        )
                    }
                }
            }

            // Projects Grid / Empty State
            if (filteredProjects.isEmpty()) {
                EmptyProjectsState(onCreateClick = { showCreateSheet = true })
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(minSize = 160.dp),
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 96.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    items(
                        items = filteredProjects,
                        key = { it.id }
                    ) { project ->
                        ProjectGridCard(
                            project = project,
                            onClick = { onOpenProject(project.id) },
                            onDuplicate = { viewModel.duplicateProject(project.id) },
                            onRename = { projectToRename = project },
                            onDelete = { projectToDelete = project }
                        )
                    }
                }
            }
        }
    }

    // Create Project Bottom Sheet
    if (showCreateSheet) {
        CreateProjectSheet(
            onDismiss = { showCreateSheet = false },
            onCreate = { title, ratio, fps, bg, engine ->
                showCreateSheet = false
                viewModel.createProject(
                    title = title,
                    aspectRatio = ratio,
                    fps = fps,
                    durationSeconds = 5.0f,
                    backgroundColor = bg,
                    renderEngine = engine,
                    onCreated = { newId ->
                        onOpenProject(newId)
                    }
                )
            }
        )
    }

    // Rename Dialog
    projectToRename?.let { proj ->
        RenameProjectDialog(
            project = proj,
            onDismiss = { projectToRename = null },
            onRename = { newTitle ->
                viewModel.renameProject(proj, newTitle)
                projectToRename = null
            }
        )
    }

    // Delete Confirmation Dialog
    projectToDelete?.let { proj ->
        AlertDialog(
            onDismissRequest = { projectToDelete = null },
            containerColor = SurfaceDark,
            title = {
                Text("Delete Project?", style = MaterialTheme.typography.titleLarge.copy(color = TextPrimary, fontWeight = FontWeight.Bold))
            },
            text = {
                Text("Are you sure you want to delete '${proj.title}'? This action cannot be undone.", style = MaterialTheme.typography.bodyMedium.copy(color = TextSecondary))
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.deleteProject(proj.id)
                        projectToDelete = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = NeonMagenta, contentColor = Color.White),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.testTag("confirm_delete_project_btn")
                ) {
                    Text("Delete", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                OutlinedButton(
                    onClick = { projectToDelete = null },
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = TextSecondary),
                    border = androidx.compose.foundation.BorderStroke(1.dp, SurfaceBorderDark),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
private fun MainMenuHeader() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(SurfaceDark)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(38.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(NeonCyan.withAlpha(0.2f))
                    .border(width = 1.dp, color = NeonCyan, shape = RoundedCornerShape(10.dp)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Movie,
                    contentDescription = null,
                    tint = NeonCyan,
                    modifier = Modifier.size(22.dp)
                )
            }
            Spacer(modifier = Modifier.width(10.dp))
            Column {
                Text(
                    text = "Fusion Cut",
                    style = MaterialTheme.typography.titleLarge.copy(
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary,
                        letterSpacing = (-0.5).sp
                    )
                )
                Text(
                    text = "Motion Graphics & Timeline Studio",
                    style = MaterialTheme.typography.bodySmall.copy(
                        color = TextSecondary,
                        fontSize = 11.sp
                    )
                )
            }
        }

        // Version Chip
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(6.dp))
                .background(SurfaceVariantDark)
                .border(width = 1.dp, color = SurfaceBorderDark, shape = RoundedCornerShape(6.dp))
                .padding(horizontal = 8.dp, vertical = 4.dp)
        ) {
            Text(
                text = "v${AppVersion.name}",
                style = MaterialTheme.typography.labelSmall.copy(
                    color = NeonEmerald,
                    fontWeight = FontWeight.Bold,
                    fontSize = 11.sp
                )
            )
        }
    }
}

@Composable
private fun ProjectGridCard(
    project: Project,
    onClick: () -> Unit,
    onDuplicate: () -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit
) {
    var showMenu by remember { mutableStateOf(false) }
    val dateStr = SimpleDateFormat("MMM dd, yyyy", Locale.US).format(Date(project.updatedAt))

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(SurfaceDark)
            .border(width = 1.dp, color = SurfaceBorderDark, shape = RoundedCornerShape(12.dp))
            .clickable { onClick() }
            .testTag("project_card_${project.id}")
    ) {
        // Thumbnail Aspect Frame
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(110.dp)
                .background(project.backgroundColor.toComposeColor()),
            contentAlignment = Alignment.Center
        ) {
            // Stylized aspect ratio silhouette
            val isPortrait = project.height > project.width
            Box(
                modifier = Modifier
                    .size(width = if (isPortrait) 40.dp else 70.dp, height = if (isPortrait) 70.dp else 40.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(NeonCyan.withAlpha(0.15f))
                    .border(width = 1.dp, color = NeonCyan.withAlpha(0.6f), shape = RoundedCornerShape(4.dp)),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Default.Movie, contentDescription = null, tint = NeonCyan, modifier = Modifier.size(18.dp))
            }

            // Duration Pill Top Left
            Box(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(8.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(Color.Black.withAlpha(0.7f))
                    .padding(horizontal = 5.dp, vertical = 2.dp)
            ) {
                Text(
                    text = "${String.format(Locale.US, "%.1f", project.durationSeconds)}s",
                    style = MaterialTheme.typography.labelSmall.copy(color = Color.White, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                )
            }

            // FPS Badge Top Right
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(8.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(NeonEmerald.withAlpha(0.2f))
                    .border(width = 0.5.dp, color = NeonEmerald, shape = RoundedCornerShape(4.dp))
                    .padding(horizontal = 4.dp, vertical = 2.dp)
            ) {
                Text(
                    text = "${project.fps} FPS",
                    style = MaterialTheme.typography.labelSmall.copy(color = NeonEmerald, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                )
            }
        }

        // Project Info
        Column(modifier = Modifier.padding(10.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = project.title,
                    style = MaterialTheme.typography.titleMedium.copy(
                        color = TextPrimary,
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp
                    ),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )

                Box {
                    IconButton(
                        onClick = { showMenu = true },
                        modifier = Modifier.size(24.dp).testTag("project_menu_btn")
                    ) {
                        Icon(Icons.Default.MoreVert, contentDescription = "Menu", tint = TextSecondary, modifier = Modifier.size(16.dp))
                    }

                    DropdownMenu(
                        expanded = showMenu,
                        onDismissRequest = { showMenu = false },
                        modifier = Modifier.background(SurfaceVariantDark)
                    ) {
                        DropdownMenuItem(
                            text = { Text("Duplicate", color = TextPrimary) },
                            onClick = {
                                showMenu = false
                                onDuplicate()
                            },
                            leadingIcon = { Icon(Icons.Default.ContentCopy, contentDescription = null, tint = NeonCyan) }
                        )
                        DropdownMenuItem(
                            text = { Text("Rename", color = TextPrimary) },
                            onClick = {
                                showMenu = false
                                onRename()
                            },
                            leadingIcon = { Icon(Icons.Default.DriveFileRenameOutline, contentDescription = null, tint = TextPrimary) }
                        )
                        DropdownMenuItem(
                            text = { Text("Delete", color = NeonMagenta) },
                            onClick = {
                                showMenu = false
                                onDelete()
                            },
                            leadingIcon = { Icon(Icons.Default.Delete, contentDescription = null, tint = NeonMagenta) }
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(4.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "${project.width}x${project.height}",
                    style = MaterialTheme.typography.bodySmall.copy(color = TextSecondary, fontSize = 10.sp)
                )
                Text(
                    text = dateStr,
                    style = MaterialTheme.typography.bodySmall.copy(color = TextMuted, fontSize = 10.sp)
                )
            }
        }
    }
}

@Composable
private fun EmptyProjectsState(onCreateClick: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(SurfaceDark)
                .border(width = 1.dp, color = SurfaceBorderDark, shape = RoundedCornerShape(16.dp))
                .padding(32.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(64.dp)
                    .clip(CircleShape)
                    .background(NeonCyan.withAlpha(0.15f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Default.VideoLibrary, contentDescription = null, tint = NeonCyan, modifier = Modifier.size(32.dp))
            }
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "No Projects Yet",
                style = MaterialTheme.typography.titleLarge.copy(color = TextPrimary, fontWeight = FontWeight.Bold)
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = "Create your first motion graphics project with multi-layer timeline and transform tools.",
                style = MaterialTheme.typography.bodyMedium.copy(color = TextSecondary),
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )
            Spacer(modifier = Modifier.height(20.dp))
            Button(
                onClick = onCreateClick,
                colors = ButtonDefaults.buttonColors(containerColor = NeonCyan, contentColor = Color.Black),
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.testTag("empty_state_create_btn")
            ) {
                Icon(Icons.Default.Add, contentDescription = null, tint = Color.Black, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("Create New Project", fontWeight = FontWeight.Bold, color = Color.Black)
            }
        }
    }
}

@Composable
private fun RenameProjectDialog(
    project: Project,
    onDismiss: () -> Unit,
    onRename: (String) -> Unit
) {
    var newTitle by remember { mutableStateOf(project.title) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = SurfaceDark,
        title = {
            Text("Rename Project", style = MaterialTheme.typography.titleLarge.copy(color = TextPrimary, fontWeight = FontWeight.Bold))
        },
        text = {
            OutlinedTextField(
                value = newTitle,
                onValueChange = { newTitle = it },
                modifier = Modifier.fillMaxWidth().testTag("rename_input"),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = NeonCyan,
                    unfocusedBorderColor = SurfaceBorderDark,
                    focusedTextColor = TextPrimary,
                    unfocusedTextColor = TextPrimary
                ),
                shape = RoundedCornerShape(8.dp)
            )
        },
        confirmButton = {
            Button(
                onClick = { onRename(newTitle) },
                colors = ButtonDefaults.buttonColors(containerColor = NeonCyan, contentColor = Color(0xFF090B10.toInt())),
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.testTag("confirm_rename_btn")
            ) {
                Text("Rename", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            OutlinedButton(
                onClick = onDismiss,
                colors = ButtonDefaults.outlinedButtonColors(contentColor = TextSecondary),
                border = androidx.compose.foundation.BorderStroke(1.dp, SurfaceBorderDark),
                shape = RoundedCornerShape(8.dp)
            ) {
                Text("Cancel")
            }
        }
    )
}
