package com.boro.notes.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AutoStories
import androidx.compose.material.icons.filled.Checkroom
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.EditNote
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material.icons.filled.Description
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.boro.notes.data.DesignRepository
import com.boro.notes.data.JournalRepository
import com.boro.notes.data.NoteRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.concurrent.TimeUnit

const val TRASH_DAYS = 7
const val TAG_TRASH = "trashScreen"
const val TAG_CONFIRM_DELETE = "confirmDelete"
const val TAG_CONFIRM_CANCEL = "confirmCancel"

enum class TrashKind(val label: String, val icon: ImageVector) {
    NOTE("Note", Icons.Filled.EditNote),
    DESIGN("Design", Icons.Filled.Checkroom),
    JOURNAL("Journal", Icons.Filled.AutoStories),
    PAGE("Journal page", Icons.Filled.Description),
}

data class TrashItem(val kind: TrashKind, val id: Long, val title: String, val deletedAt: Long)

/** Everything in the Trash, newest first. Items are emptied after [TRASH_DAYS] days. */
class TrashViewModel(
    private val notes: NoteRepository,
    private val designs: DesignRepository,
    private val journals: JournalRepository,
) : ViewModel() {
    val items: StateFlow<List<TrashItem>> = combine(
        notes.observeTrash(),
        designs.observeTrash(),
        journals.observeTrashedJournals(),
        journals.observeTrashedPages(),
    ) { n, d, j, p ->
        (n.map { TrashItem(TrashKind.NOTE, it.id, it.title.ifBlank { it.body.lineSequence().firstOrNull().orEmpty() }.ifBlank { "Untitled note" }, it.deletedAt ?: 0) } +
            d.map { TrashItem(TrashKind.DESIGN, it.id, it.name.ifBlank { "Untitled design" }, it.deletedAt ?: 0) } +
            j.map { TrashItem(TrashKind.JOURNAL, it.id, it.title.ifBlank { "Journal" }, it.deletedAt ?: 0) } +
            p.map { TrashItem(TrashKind.PAGE, it.id, it.title.ifBlank { java.time.LocalDate.ofEpochDay(it.day).toString() }, it.deletedAt ?: 0) })
            .sortedByDescending { it.deletedAt }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    fun restore(item: TrashItem) = viewModelScope.launch {
        when (item.kind) {
            TrashKind.NOTE -> notes.restore(item.id)
            TrashKind.DESIGN -> designs.restore(item.id)
            TrashKind.JOURNAL -> journals.restoreJournal(item.id)
            TrashKind.PAGE -> journals.restorePage(item.id)
        }
    }

    fun deleteForever(item: TrashItem) = viewModelScope.launch { deleteNow(item) }

    fun emptyTrash() = viewModelScope.launch { items.value.forEach { deleteNow(it) } }

    private suspend fun deleteNow(item: TrashItem) {
        when (item.kind) {
            TrashKind.NOTE -> notes.delete(item.id)
            TrashKind.DESIGN -> designs.delete(item.id)
            TrashKind.JOURNAL -> journals.deleteJournalForever(item.id)
            TrashKind.PAGE -> journals.deletePage(item.id)
        }
    }

    companion object {
        fun factory(notes: NoteRepository, designs: DesignRepository, journals: JournalRepository): ViewModelProvider.Factory =
            viewModelFactory { initializer { TrashViewModel(notes, designs, journals) } }

        /** Whole days left before [deletedAt] is emptied out of the Trash. */
        fun daysLeft(deletedAt: Long, now: Long = System.currentTimeMillis()): Int {
            val left = deletedAt + TimeUnit.DAYS.toMillis(TRASH_DAYS.toLong()) - now
            return ((left + TimeUnit.DAYS.toMillis(1) - 1) / TimeUnit.DAYS.toMillis(1)).toInt().coerceIn(0, TRASH_DAYS)
        }
    }
}

/**
 * "Are you sure?" before anything is deleted. [toTrash] says it can be restored for a week;
 * otherwise it warns that it's gone for good.
 */
@Composable
fun ConfirmDeleteDialog(
    what: String,
    toTrash: Boolean = true,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(if (toTrash) Icons.Filled.DeleteOutline else Icons.Filled.DeleteForever, null) },
        title = { Text(if (toTrash) "Move $what to Trash?" else "Delete $what?") },
        text = {
            Text(
                if (toTrash) "You can restore it from the Trash on Home for $TRASH_DAYS days."
                else "This can't be undone."
            )
        },
        confirmButton = {
            TextButton(onClick = { onDismiss(); onConfirm() }, modifier = Modifier.testTag(TAG_CONFIRM_DELETE)) {
                Text(if (toTrash) "Move to Trash" else "Delete", color = MaterialTheme.colorScheme.error)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, modifier = Modifier.testTag(TAG_CONFIRM_CANCEL)) { Text("Cancel") }
        },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TrashScreen(vm: TrashViewModel, onBack: () -> Unit) {
    val items by vm.items.collectAsStateWithLifecycle()
    var confirm by remember { mutableStateOf<TrashItem?>(null) }
    var confirmEmpty by remember { mutableStateOf(false) }
    val cs = MaterialTheme.colorScheme
    Scaffold(
        modifier = Modifier.testTag(TAG_TRASH),
        topBar = {
            TopAppBar(
                title = { Text("Trash") },
                navigationIcon = {
                    IconButton(onClick = onBack, modifier = Modifier.testTag("trash-back")) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                    }
                },
                actions = {
                    if (items.isNotEmpty()) TextButton(onClick = { confirmEmpty = true }, modifier = Modifier.testTag("trash-empty")) {
                        Text("Empty", color = cs.error)
                    }
                },
            )
        },
    ) { padding ->
        if (items.isEmpty()) {
            Box(Modifier.padding(padding).fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Filled.DeleteOutline, null, tint = cs.onSurfaceVariant, modifier = Modifier.size(48.dp))
                    Text("Trash is empty", color = cs.onSurfaceVariant)
                    Text("Deleted things wait here for $TRASH_DAYS days", style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant)
                }
            }
        } else {
            LazyColumn(
                Modifier.padding(padding).fillMaxSize(),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(items, key = { "${it.kind}-${it.id}" }) { item ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .background(cs.surfaceContainer, MaterialTheme.shapes.large)
                            .padding(start = 14.dp, end = 4.dp, top = 8.dp, bottom = 8.dp)
                            .testTag("trash-${item.kind.name.lowercase()}-${item.id}"),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(Modifier.size(36.dp).background(cs.secondaryContainer, CircleShape), contentAlignment = Alignment.Center) {
                            Icon(item.kind.icon, null, tint = cs.onSecondaryContainer, modifier = Modifier.size(20.dp))
                        }
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(item.title, style = MaterialTheme.typography.titleSmall, maxLines = 1)
                            val days = TrashViewModel.daysLeft(item.deletedAt)
                            Text(
                                "${item.kind.label} · ${if (days <= 1) "goes today or tomorrow" else "$days days left"}",
                                style = MaterialTheme.typography.bodySmall,
                                color = cs.onSurfaceVariant,
                            )
                        }
                        IconButton(onClick = { vm.restore(item) }, modifier = Modifier.testTag("restore-${item.id}")) {
                            Icon(Icons.Filled.Restore, "Restore", tint = cs.primary)
                        }
                        IconButton(onClick = { confirm = item }, modifier = Modifier.testTag("forever-${item.id}")) {
                            Icon(Icons.Filled.DeleteForever, "Delete forever", tint = cs.error)
                        }
                    }
                }
            }
        }
    }
    confirm?.let { item ->
        ConfirmDeleteDialog(item.title, toTrash = false, onConfirm = { vm.deleteForever(item) }, onDismiss = { confirm = null })
    }
    if (confirmEmpty) {
        ConfirmDeleteDialog("everything in the Trash", toTrash = false, onConfirm = { vm.emptyTrash() }, onDismiss = { confirmEmpty = false })
    }
}
