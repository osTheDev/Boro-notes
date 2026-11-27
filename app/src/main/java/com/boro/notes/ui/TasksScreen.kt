package com.boro.notes.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.boro.notes.data.Habit
import com.boro.notes.data.Todo
import java.time.LocalDate
import java.time.format.DateTimeFormatter

/** Punch card colours: indigo, rust, sage, ochre, plum, teal, brick, slate. */
val PUNCH_COLORS = listOf(
    Color(0xFF2E4A7D), Color(0xFFB5562B), Color(0xFF5E8259), Color(0xFFC08A1E),
    Color(0xFF7A3E6E), Color(0xFF2D7C7A), Color(0xFF9C3D35), Color(0xFF4F5B66),
)

private val DAY_LETTERS = listOf("M", "T", "W", "T", "F", "S", "S")

const val TAG_TASKS = "tasksScreen"
const val TAG_ADD_HABIT = "addHabit"
const val TAG_TODO_INPUT = "todoInput"

/** Weekly punch cards and the to-do list: side by side on the inner screen, stacked on the cover. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TasksScreen(vm: TasksViewModel, wide: Boolean) {
    val habits by vm.habits.collectAsStateWithLifecycle()
    val todos by vm.todos.collectAsStateWithLifecycle()
    val weekStart by vm.weekStart.collectAsStateWithLifecycle()
    var editing by remember { mutableStateOf<Habit?>(null) }
    var adding by remember { mutableStateOf(false) }

    Scaffold(
        modifier = Modifier.testTag(TAG_TASKS),
        topBar = { TopAppBar(title = { Text("Tasks") }) },
    ) { padding ->
        val cards: LazyListScope.() -> Unit = {
            punchCardSection(vm, habits, weekStart, onAdd = { adding = true }, onEdit = { editing = it })
        }
        val list: LazyListScope.() -> Unit = { todoSection(vm, todos) }
        val pad = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 24.dp)
        if (wide) {
            Row(Modifier.padding(padding).fillMaxSize()) {
                LazyColumn(Modifier.weight(1f), contentPadding = pad, verticalArrangement = Arrangement.spacedBy(12.dp)) { cards() }
                VerticalDivider()
                LazyColumn(Modifier.weight(1f), contentPadding = pad, verticalArrangement = Arrangement.spacedBy(4.dp)) { list() }
            }
        } else {
            LazyColumn(Modifier.padding(padding).fillMaxSize(), contentPadding = pad, verticalArrangement = Arrangement.spacedBy(10.dp)) {
                cards()
                item { Spacer(Modifier.padding(4.dp)) }
                list()
            }
        }
    }

    if (adding) {
        HabitDialog(null, onDismiss = { adding = false }) { name, color, target ->
            vm.addHabit(name, color, target); adding = false
        }
    }
    editing?.let { h ->
        HabitDialog(h, onDismiss = { editing = null }) { name, color, target ->
            vm.updateHabit(h.copy(name = name, color = color, target = target)); editing = null
        }
    }
}

private fun LazyListScope.punchCardSection(
    vm: TasksViewModel,
    habits: List<HabitWeek>,
    weekStart: LocalDate,
    onAdd: () -> Unit,
    onEdit: (Habit) -> Unit,
) {
    item(key = "week-header") { WeekHeader(vm, weekStart) }
    if (habits.isEmpty()) {
        item(key = "no-habits") {
            Text(
                "Punch cards track things you do each week, like stretching or sketching daily. Add one to start.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(vertical = 8.dp),
            )
        }
    }
    items(habits, key = { "habit-${it.habit.id}" }) { week ->
        PunchCard(week, weekStart, vm.todayDate(), vm, onEdit)
    }
    item(key = "add-habit") {
        FilledTonalButton(onClick = onAdd, modifier = Modifier.testTag(TAG_ADD_HABIT)) {
            Icon(Icons.Filled.Add, null)
            Spacer(Modifier.width(6.dp))
            Text("Punch card")
        }
    }
}

@Composable
private fun WeekHeader(vm: TasksViewModel, weekStart: LocalDate) {
    val fmt = DateTimeFormatter.ofPattern("MMM d")
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text("Punch cards", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
            Text(
                if (vm.isThisWeek) "This week · ${weekStart.format(fmt)} – ${weekStart.plusDays(6).format(fmt)}"
                else "${weekStart.format(fmt)} – ${weekStart.plusDays(6).format(fmt)}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (!vm.isThisWeek) TextButton(onClick = vm::thisWeek) { Text("Today") }
        IconButton(onClick = vm::previousWeek, modifier = Modifier.testTag("week-prev")) {
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, "Previous week")
        }
        IconButton(onClick = vm::nextWeek, modifier = Modifier.testTag("week-next")) {
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, "Next week")
        }
    }
}

@Composable
private fun PunchCard(week: HabitWeek, weekStart: LocalDate, today: LocalDate, vm: TasksViewModel, onEdit: (Habit) -> Unit) {
    val h = week.habit
    val color = PUNCH_COLORS[h.color.mod(PUNCH_COLORS.size)]
    var menu by remember { mutableStateOf(false) }
    Card(
        colors = CardDefaults.cardColors(containerColor = color.copy(alpha = 0.10f)),
        shape = RoundedCornerShape(18.dp),
        modifier = Modifier.fillMaxWidth().testTag("habit-${h.id}"),
    ) {
        Column(Modifier.padding(start = 14.dp, end = 6.dp, top = 8.dp, bottom = 12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(h.name, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                // Progress toward this week's target; fills in when the week is complete.
                Row(
                    Modifier
                        .clip(RoundedCornerShape(50))
                        .background(if (week.complete) color else color.copy(alpha = 0.18f))
                        .padding(horizontal = 10.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (week.complete) {
                        Icon(Icons.Filled.Check, null, tint = Color.White, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                    }
                    Text(
                        "${week.count}/${h.target}",
                        style = MaterialTheme.typography.labelLarge,
                        color = if (week.complete) Color.White else MaterialTheme.colorScheme.onSurface,
                    )
                }
                Box {
                    IconButton(onClick = { menu = true }) { Icon(Icons.Filled.MoreVert, "Punch card options") }
                    DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                        DropdownMenuItem(text = { Text("Edit") }, onClick = { menu = false; onEdit(h) })
                        DropdownMenuItem(text = { Text("Move up") }, onClick = { menu = false; vm.moveHabit(h.id, -1) })
                        DropdownMenuItem(text = { Text("Move down") }, onClick = { menu = false; vm.moveHabit(h.id, 1) })
                        DropdownMenuItem(
                            text = { Text("Delete", color = MaterialTheme.colorScheme.error) },
                            onClick = { menu = false; vm.deleteHabit(h.id) },
                        )
                    }
                }
            }
            Spacer(Modifier.padding(top = 6.dp))
            Row(Modifier.fillMaxWidth().padding(end = 8.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                for (i in 0..6) {
                    val date = weekStart.plusDays(i.toLong())
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(DAY_LETTERS[i], style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        PunchDot(
                            punched = week.punched[i],
                            today = date == today,
                            color = color,
                            onClick = { vm.togglePunch(h.id, i) },
                            modifier = Modifier.testTag("punch-${h.id}-$i"),
                        )
                        Text(
                            "${date.dayOfMonth}",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = if (date == today) FontWeight.Bold else FontWeight.Normal,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}

/** A day on a punch card: an empty ring, or filled with a check once punched. Today has a thicker ring. */
@Composable
private fun PunchDot(punched: Boolean, today: Boolean, color: Color, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Box(
        modifier
            .padding(vertical = 4.dp)
            .size(38.dp)
            .clip(CircleShape)
            .background(if (punched) color else Color.Transparent)
            .border(if (today) 3.dp else 1.5.dp, color.copy(alpha = if (punched || today) 1f else 0.45f), CircleShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        if (punched) Icon(Icons.Filled.Check, contentDescription = "Punched", tint = Color.White, modifier = Modifier.size(20.dp))
    }
}

@Composable
private fun HabitDialog(initial: Habit?, onDismiss: () -> Unit, onSave: (String, Int, Int) -> Unit) {
    var name by rememberSaveable { mutableStateOf(initial?.name ?: "") }
    var color by rememberSaveable { mutableIntStateOf(initial?.color ?: 0) }
    var target by rememberSaveable { mutableIntStateOf(initial?.target ?: 7) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initial == null) "New punch card" else "Edit punch card") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("What to do") },
                    placeholder = { Text("e.g. Sketch a pocket") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("habit-name"),
                )
                Text("Colour", style = MaterialTheme.typography.labelLarge)
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    PUNCH_COLORS.forEachIndexed { i, c ->
                        Box(
                            Modifier
                                .size(28.dp)
                                .clip(CircleShape)
                                .background(c)
                                .border(if (color == i) 3.dp else 0.dp, MaterialTheme.colorScheme.onSurface, CircleShape)
                                .clickable { color = i },
                            contentAlignment = Alignment.Center,
                        ) { if (color == i) Icon(Icons.Filled.Check, null, tint = Color.White, modifier = Modifier.size(16.dp)) }
                    }
                }
                Text("Days per week", style = MaterialTheme.typography.labelLarge)
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    for (n in 1..7) {
                        FilterChip(selected = target == n, onClick = { target = n }, label = { Text("$n") })
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onSave(name, color, target) }, enabled = name.isNotBlank(), modifier = Modifier.testTag("habit-save")) {
                Text("Save")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

private fun LazyListScope.todoSection(vm: TasksViewModel, todos: List<Todo>) {
    val open = todos.filterNot { it.done }
    val done = todos.filter { it.done }.sortedByDescending { it.doneAt ?: 0 }
    item(key = "todo-header") {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("To do", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
            if (open.isNotEmpty()) Text("${open.size} left", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
    item(key = "todo-input") { TodoInput(vm) }
    items(open, key = { "todo-${it.id}" }) { TodoRow(it, vm) }
    if (done.isNotEmpty()) {
        item(key = "done-header") { DoneHeader(done.size, vm) }
        items(done, key = { "todo-${it.id}" }) { TodoRow(it, vm) }
    }
}

@Composable
private fun TodoInput(vm: TasksViewModel) {
    var text by rememberSaveable { mutableStateOf("") }
    val add = { vm.addTodo(text); text = "" }
    OutlinedTextField(
        value = text,
        onValueChange = { text = it },
        placeholder = { Text("Add a to-do") },
        singleLine = true,
        shape = RoundedCornerShape(14.dp),
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
        // Done adds the item and keeps the keyboard up for the next one.
        keyboardActions = KeyboardActions(onDone = { add() }),
        trailingIcon = {
            IconButton(onClick = add, enabled = text.isNotBlank(), modifier = Modifier.testTag("todo-add")) {
                Icon(Icons.Filled.Add, "Add to-do")
            }
        },
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp).testTag(TAG_TODO_INPUT),
    )
}

@Composable
private fun DoneHeader(count: Int, vm: TasksViewModel) {
    Column {
        HorizontalDivider(Modifier.padding(top = 8.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Done ($count)", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
            TextButton(onClick = { vm.clearDone() }, modifier = Modifier.testTag("todo-clear")) { Text("Clear done") }
        }
    }
}

@Composable
private fun TodoRow(todo: Todo, vm: TasksViewModel) {
    var menu by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf(false) }
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable { vm.toggleTodo(todo) }
            .testTag("todo-${todo.id}"),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Checkbox(checked = todo.done, onCheckedChange = { vm.toggleTodo(todo) })
        Text(
            todo.text,
            style = MaterialTheme.typography.bodyLarge,
            textDecoration = if (todo.done) TextDecoration.LineThrough else null,
            color = if (todo.done) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f).padding(vertical = 10.dp),
        )
        Box {
            IconButton(onClick = { menu = true }) { Icon(Icons.Filled.MoreVert, "To-do options") }
            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                DropdownMenuItem(text = { Text("Edit") }, onClick = { menu = false; editing = true })
                if (!todo.done) {
                    DropdownMenuItem(text = { Text("Move up") }, onClick = { menu = false; vm.moveTodo(todo.id, -1) })
                    DropdownMenuItem(text = { Text("Move down") }, onClick = { menu = false; vm.moveTodo(todo.id, 1) })
                }
                DropdownMenuItem(
                    text = { Text("Delete", color = MaterialTheme.colorScheme.error) },
                    onClick = { menu = false; vm.deleteTodo(todo.id) },
                )
            }
        }
    }
    if (editing) {
        var text by rememberSaveable { mutableStateOf(todo.text) }
        AlertDialog(
            onDismissRequest = { editing = false },
            title = { Text("Edit to-do") },
            text = { OutlinedTextField(value = text, onValueChange = { text = it }, modifier = Modifier.fillMaxWidth()) },
            confirmButton = {
                TextButton(onClick = { vm.editTodo(todo, text); editing = false }, enabled = text.isNotBlank()) { Text("Save") }
            },
            dismissButton = { TextButton(onClick = { editing = false }) { Text("Cancel") } },
        )
    }
}
