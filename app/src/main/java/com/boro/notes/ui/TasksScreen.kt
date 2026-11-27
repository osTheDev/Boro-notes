package com.boro.notes.ui

import androidx.compose.foundation.background
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.LocalIndication
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.Spring
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.foundation.layout.height
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
import com.boro.notes.data.Wish
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsTopHeight
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.text.input.KeyboardType
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
const val TAG_TASKS_STRIP = "tasksStrip"
const val TAG_WISH_SORT = "wishSort"
const val TAG_WISH_NAME = "wishName"
const val TAG_WISH_PRICE = "wishPrice"
const val TAG_TODO_INPUT = "todoInput"

/** Weekly punch cards and the to-do list: side by side on the inner screen, stacked on the cover. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TasksScreen(vm: TasksViewModel, wide: Boolean) {
    val habits by vm.habits.collectAsStateWithLifecycle()
    val todos by vm.todos.collectAsStateWithLifecycle()
    val weekStart by vm.weekStart.collectAsStateWithLifecycle()
    val wishes by vm.wishes.collectAsStateWithLifecycle()
    val bought by vm.bought.collectAsStateWithLifecycle()
    val wishSort by vm.wishSort.collectAsStateWithLifecycle()
    // Punch cards are added and edited in place (no pop-up), one at a time.
    var editingId by rememberSaveable { mutableStateOf<Long?>(null) }
    var adding by rememberSaveable { mutableStateOf(false) }

    Scaffold(
        modifier = Modifier.testTag(TAG_TASKS),
        // No title bar: the section headers say what's what, and on the inner screen the strip by
        // the camera (otherwise dead space) carries a one-line summary instead.
        topBar = { if (wide) TasksStrip(habits, todos, wishes) },
    ) { padding ->
        val cards: LazyListScope.() -> Unit = {
            punchCardSection(
                vm, habits, weekStart,
                compact = !wide,
                adding = adding,
                editingId = editingId,
                onAdd = { adding = true; editingId = null },
                onEdit = { editingId = it.id; adding = false },
                onDone = { adding = false; editingId = null },
            )
        }
        val list: LazyListScope.() -> Unit = { todoSection(vm, todos, compact = !wide) }
        val saving: LazyListScope.() -> Unit = { savingSection(vm, wishes, bought, wishSort) }
        val pad = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 24.dp)
        if (wide) {
            Row(Modifier.padding(padding).fillMaxSize()) {
                LazyColumn(Modifier.weight(1f), contentPadding = pad, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    cards()
                    item { Spacer(Modifier.height(8.dp)) }
                    saving()
                }
                VerticalDivider()
                LazyColumn(Modifier.weight(1f), contentPadding = pad, verticalArrangement = Arrangement.spacedBy(4.dp)) { list() }
            }
        } else {
            LazyColumn(Modifier.padding(padding).fillMaxSize(), contentPadding = pad, verticalArrangement = Arrangement.spacedBy(10.dp)) {
                cards()
                item { Spacer(Modifier.padding(4.dp)) }
                list()
                item { Spacer(Modifier.padding(4.dp)) }
                saving()
            }
        }
    }

}

private fun LazyListScope.punchCardSection(
    vm: TasksViewModel,
    habits: List<HabitWeek>,
    weekStart: LocalDate,
    compact: Boolean,
    adding: Boolean,
    editingId: Long?,
    onAdd: () -> Unit,
    onEdit: (Habit) -> Unit,
    onDone: () -> Unit,
) {
    // Compact: adding a punch card is a small + beside the week arrows, not its own row.
    item(key = "week-header") { WeekHeader(vm, weekStart, onAdd = if (compact && !adding) onAdd else null) }
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
        if (week.habit.id == editingId) {
            HabitEditor(week.habit, onCancel = onDone) { name, color, target ->
                vm.updateHabit(week.habit.copy(name = name, color = color, target = target)); onDone()
            }
        } else {
            PunchCard(week, weekStart, vm.todayDate(), vm, onEdit)
        }
    }
    if (adding) {
        item(key = "new-habit") {
            HabitEditor(null, onCancel = onDone) { name, color, target -> vm.addHabit(name, color, target); onDone() }
        }
    } else if (!compact) item(key = "add-habit") {
        FilledTonalButton(onClick = onAdd, modifier = Modifier.testTag(TAG_ADD_HABIT)) {
            Icon(Icons.Filled.Add, null)
            Spacer(Modifier.width(6.dp))
            Text("Punch card")
        }
    }
}

@Composable
private fun WeekHeader(vm: TasksViewModel, weekStart: LocalDate, onAdd: (() -> Unit)? = null) {
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
        if (onAdd != null) {
            FilledTonalIconButton(onClick = onAdd, modifier = Modifier.size(36.dp).testTag(TAG_ADD_HABIT)) {
                Icon(Icons.Filled.Add, "Add punch card", Modifier.size(20.dp))
            }
        }
    }
}

@Composable
private fun PunchCard(week: HabitWeek, weekStart: LocalDate, today: LocalDate, vm: TasksViewModel, onEdit: (Habit) -> Unit) {
    val h = week.habit
    val color = PUNCH_COLORS[h.color.mod(PUNCH_COLORS.size)]
    var menu by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    if (confirmDelete) ConfirmDeleteDialog("this punch card", toTrash = false, onConfirm = { vm.deleteHabit(h.id) }, onDismiss = { confirmDelete = false })
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
                            onClick = { menu = false; confirmDelete = true },
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

/** A day on a punch card: an empty ring that fills with a bouncy pop when punched. Today has a thicker ring. */
@Composable
private fun PunchDot(punched: Boolean, today: Boolean, color: Color, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val fill by animateFloatAsState(
        if (punched) 1f else 0f,
        spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMediumLow),
        label = "punch",
    )
    val source = remember { MutableInteractionSource() }
    Box(
        modifier
            .padding(vertical = 4.dp)
            .size(38.dp)
            .bounce(source)
            .clip(CircleShape)
            .border(if (today) 3.dp else 1.5.dp, color.copy(alpha = if (punched || today) 1f else 0.45f), CircleShape)
            .clickable(interactionSource = source, indication = LocalIndication.current, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier
                .size(38.dp)
                .graphicsLayer { scaleX = fill; scaleY = fill; rotationZ = (1f - fill) * -45f }
                .background(color, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            if (punched) Icon(Icons.Filled.Check, contentDescription = "Punched", tint = Color.White, modifier = Modifier.size(20.dp))
        }
    }
}

@Composable
private fun HabitEditor(initial: Habit?, onCancel: () -> Unit, onSave: (String, Int, Int) -> Unit) {
    var name by rememberSaveable { mutableStateOf(initial?.name ?: "") }
    var color by rememberSaveable { mutableIntStateOf(initial?.color ?: 0) }
    var target by rememberSaveable { mutableIntStateOf(initial?.target ?: 7) }
    val accent = PUNCH_COLORS[color.mod(PUNCH_COLORS.size)]
    Card(
        colors = CardDefaults.cardColors(containerColor = accent.copy(alpha = 0.10f)),
        shape = RoundedCornerShape(18.dp),
        modifier = Modifier.fillMaxWidth().testTag("habit-editor"),
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(if (initial == null) "New punch card" else "Edit punch card", style = MaterialTheme.typography.titleMedium)
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                placeholder = { Text("What to do, e.g. Sketch a pocket") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().testTag("habit-name"),
            )
            Text("Colour", style = MaterialTheme.typography.labelLarge)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                PUNCH_COLORS.forEachIndexed { i, c ->
                    Box(
                        Modifier
                            .size(30.dp)
                            .clip(CircleShape)
                            .background(c)
                            .border(if (color == i) 3.dp else 0.dp, MaterialTheme.colorScheme.onSurface, CircleShape)
                            .clickable { color = i },
                        contentAlignment = Alignment.Center,
                    ) { if (color == i) Icon(Icons.Filled.Check, null, tint = Color.White, modifier = Modifier.size(16.dp)) }
                }
            }
            Text("Days per week", style = MaterialTheme.typography.labelLarge)
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                for (n in 1..7) {
                    val on = target == n
                    Box(
                        Modifier
                            .size(32.dp)
                            .clip(CircleShape)
                            .background(if (on) accent else MaterialTheme.colorScheme.surfaceVariant)
                            .clickable { target = n }
                            .testTag("habit-target-$n"),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text("$n", style = MaterialTheme.typography.labelLarge, color = if (on) Color.White else MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = onCancel) { Text("Cancel") }
                FilledTonalButton(onClick = { onSave(name, color, target) }, enabled = name.isNotBlank(), modifier = Modifier.testTag("habit-save")) {
                    Text("Save")
                }
            }
        }
    }
}

private fun LazyListScope.todoSection(vm: TasksViewModel, todos: List<Todo>, compact: Boolean) {
    val open = todos.filterNot { it.done }
    val done = todos.filter { it.done }.sortedByDescending { it.doneAt ?: 0 }
    item(key = "todo-header") {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("To do", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
            if (open.isNotEmpty()) Text("${open.size} left", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
    item(key = "todo-input") { if (compact) CompactTodoInput(vm) else TodoInput(vm) }
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

/** A slim one-line add bar for the cover screen. */
@Composable
private fun CompactTodoInput(vm: TasksViewModel) {
    var text by rememberSaveable { mutableStateOf("") }
    val add = { vm.addTodo(text); text = "" }
    val cs = MaterialTheme.colorScheme
    Row(
        Modifier
            .fillMaxWidth()
            .height(40.dp)
            .clip(RoundedCornerShape(50))
            .background(cs.surfaceContainerHigh),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        BasicTextField(
            value = text,
            onValueChange = { text = it },
            singleLine = true,
            textStyle = MaterialTheme.typography.bodyMedium.copy(color = cs.onSurface),
            cursorBrush = SolidColor(cs.primary),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { add() }),
            decorationBox = { inner ->
                Box(contentAlignment = Alignment.CenterStart) {
                    if (text.isEmpty()) Text("Add a to-do", style = MaterialTheme.typography.bodyMedium, color = cs.onSurfaceVariant)
                    inner()
                }
            },
            modifier = Modifier.weight(1f).padding(start = 16.dp).testTag(TAG_TODO_INPUT),
        )
        IconButton(onClick = add, enabled = text.isNotBlank(), modifier = Modifier.size(40.dp).testTag("todo-add")) {
            Icon(Icons.Filled.Add, "Add to-do", Modifier.size(20.dp))
        }
    }
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
    var confirmDelete by remember { mutableStateOf(false) }
    if (confirmDelete) ConfirmDeleteDialog("this to-do", toTrash = false, onConfirm = { vm.deleteTodo(todo.id) }, onDismiss = { confirmDelete = false })
    var editing by remember { mutableStateOf(false) }
    if (editing) {
        // Edit in place: Done saves.
        var text by rememberSaveable { mutableStateOf(todo.text) }
        val save = { vm.editTodo(todo, text); editing = false }
        OutlinedTextField(
            value = text,
            onValueChange = { text = it },
            singleLine = true,
            shape = RoundedCornerShape(14.dp),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { save() }),
            trailingIcon = { IconButton(onClick = save) { Icon(Icons.Filled.Check, "Save") } },
            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        )
        return
    }
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
                    onClick = { menu = false; confirmDelete = true },
                )
            }
        }
    }

}

/** The strip beside the camera on the inner screen: where things stand, in one quiet line. */
@Composable
private fun TasksStrip(habits: List<HabitWeek>, todos: List<Todo>, wishes: List<Wish>) {
    val open = todos.count { !it.done }
    Box(
        Modifier.fillMaxWidth().windowInsetsTopHeight(WindowInsets.statusBars),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            listOfNotNull(
                "Tasks",
                if (habits.isNotEmpty()) "${habits.count { it.complete }}/${habits.size} cards full this week" else null,
                if (open == 1) "1 to-do" else "$open to-dos",
                if (wishes.isNotEmpty()) "saving for ${formatPrice(wishes.sumOf { it.price })}" else null,
            ).joinToString("  ·  "),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.testTag(TAG_TASKS_STRIP),
        )
    }
}

/** "$40" or "$1,299.50", in the phone's currency. */
internal fun formatPrice(cents: Long): String {
    val f = java.text.NumberFormat.getCurrencyInstance()
    if (cents % 100 == 0L) { f.minimumFractionDigits = 0; f.maximumFractionDigits = 0 }
    return f.format(java.math.BigDecimal.valueOf(cents, 2))
}

/**
 * Saving up: things you actually need or want, with what they cost, so the goal stays in view.
 * In your own order (move them up and down) or sorted by price; bought ones fold away below.
 */
private fun LazyListScope.savingSection(vm: TasksViewModel, wishes: List<Wish>, bought: List<Wish>, sort: WishSort) {
    val most = wishes.maxOfOrNull { it.price }?.takeIf { it > 0 } ?: 1L
    item(key = "wish-header") {
        var menu by remember { mutableStateOf(false) }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Saving up", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                if (wishes.isNotEmpty()) {
                    Text(
                        "${formatPrice(wishes.sumOf { it.price })} for ${if (wishes.size == 1) "1 thing" else "${wishes.size} things"} · ${sort.label}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Box {
                IconButton(onClick = { menu = true }, modifier = Modifier.testTag(TAG_WISH_SORT)) {
                    Icon(Icons.AutoMirrored.Filled.Sort, "Sort")
                }
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                    WishSort.entries.forEach { s ->
                        DropdownMenuItem(
                            text = { Text(s.label) },
                            trailingIcon = { if (s == sort) Icon(Icons.Filled.Check, null) },
                            onClick = { vm.setWishSort(s); menu = false },
                            modifier = Modifier.testTag("wish-sort-${s.name.lowercase()}"),
                        )
                    }
                }
            }
        }
    }
    item(key = "wish-input") { WishInput(vm) }
    if (wishes.isEmpty() && bought.isEmpty()) {
        item(key = "no-wishes") {
            Text(
                "List the things worth saving for, with their price, to keep them in mind when little buys tempt you.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(vertical = 8.dp),
            )
        }
    }
    items(wishes, key = { "wish-${it.id}" }) { WishRow(it, vm, share = it.price.toFloat() / most, canMove = true) }
    if (bought.isNotEmpty()) {
        item(key = "bought-header") {
            Column {
                HorizontalDivider(Modifier.padding(top = 8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Bought (${bought.size})", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
                    TextButton(onClick = { vm.clearBought() }, modifier = Modifier.testTag("wish-clear")) { Text("Clear bought") }
                }
            }
        }
        items(bought, key = { "wish-${it.id}" }) { WishRow(it, vm, share = 0f, canMove = false) }
    }
}

/** Name and price on one slim pill, then +. */
@Composable
private fun WishInput(vm: TasksViewModel) {
    var name by rememberSaveable { mutableStateOf("") }
    var price by rememberSaveable { mutableStateOf("") }
    val ok = name.isNotBlank() && TasksViewModel.parsePrice(price) != null
    val add = { if (ok) { vm.addWish(name, price); name = ""; price = "" } }
    val cs = MaterialTheme.colorScheme
    val style = MaterialTheme.typography.bodyMedium.copy(color = cs.onSurface)
    Row(
        Modifier.fillMaxWidth().height(40.dp).clip(RoundedCornerShape(50)).background(cs.surfaceContainerHigh),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        BasicTextField(
            value = name,
            onValueChange = { name = it },
            singleLine = true,
            textStyle = style,
            cursorBrush = SolidColor(cs.primary),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
            decorationBox = { inner ->
                Box(contentAlignment = Alignment.CenterStart) {
                    if (name.isEmpty()) Text("Something to save for", style = MaterialTheme.typography.bodyMedium, color = cs.onSurfaceVariant)
                    inner()
                }
            },
            modifier = Modifier.weight(1f).padding(start = 16.dp).testTag(TAG_WISH_NAME),
        )
        Box(Modifier.width(1.dp).height(22.dp).background(cs.outlineVariant))
        BasicTextField(
            value = price,
            onValueChange = { price = it },
            singleLine = true,
            textStyle = style,
            cursorBrush = SolidColor(cs.primary),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { add() }),
            decorationBox = { inner ->
                Box(contentAlignment = Alignment.CenterStart) {
                    if (price.isEmpty()) Text("Price", style = MaterialTheme.typography.bodyMedium, color = cs.onSurfaceVariant)
                    inner()
                }
            },
            modifier = Modifier.width(84.dp).padding(start = 12.dp).testTag(TAG_WISH_PRICE),
        )
        IconButton(onClick = add, enabled = ok, modifier = Modifier.size(40.dp).testTag("wish-add")) {
            Icon(Icons.Filled.Add, "Add", Modifier.size(20.dp))
        }
    }
}

/** One thing to save for: a faint bar behind it shows its price against the dearest one. */
@Composable
private fun WishRow(wish: Wish, vm: TasksViewModel, share: Float, canMove: Boolean) {
    var menu by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf(false) }
    val cs = MaterialTheme.colorScheme
    if (confirmDelete) ConfirmDeleteDialog("“${wish.name}”", toTrash = false, onConfirm = { vm.deleteWish(wish.id) }, onDismiss = { confirmDelete = false })
    if (editing) {
        var name by rememberSaveable { mutableStateOf(wish.name) }
        var price by rememberSaveable { mutableStateOf(java.math.BigDecimal.valueOf(wish.price, 2).stripTrailingZeros().toPlainString()) }
        val save = { if (name.isNotBlank() && TasksViewModel.parsePrice(price) != null) { vm.editWish(wish, name, price); editing = false } }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(vertical = 4.dp)) {
            OutlinedTextField(value = name, onValueChange = { name = it }, singleLine = true, shape = RoundedCornerShape(14.dp), modifier = Modifier.weight(1f))
            OutlinedTextField(
                value = price, onValueChange = { price = it }, singleLine = true, shape = RoundedCornerShape(14.dp),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { save() }),
                modifier = Modifier.width(110.dp),
            )
            IconButton(onClick = save) { Icon(Icons.Filled.Check, "Save") }
        }
        return
    }
    val done = wish.boughtAt != null
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .drawBehind {
                if (share > 0f) drawRect(cs.primary.copy(alpha = 0.10f), size = androidx.compose.ui.geometry.Size(size.width * share.coerceIn(0f, 1f), size.height))
            }
            .padding(start = 14.dp)
            .testTag("wish-${wish.id}"),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            wish.name,
            style = MaterialTheme.typography.bodyLarge,
            textDecoration = if (done) TextDecoration.LineThrough else null,
            color = if (done) cs.onSurfaceVariant else cs.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f).padding(vertical = 12.dp),
        )
        Text(
            formatPrice(wish.price),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            color = if (done) cs.onSurfaceVariant else cs.primary,
        )
        Box {
            IconButton(onClick = { menu = true }) { Icon(Icons.Filled.MoreVert, "Options") }
            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                DropdownMenuItem(text = { Text(if (done) "Not bought yet" else "Bought it") }, onClick = { menu = false; vm.toggleBought(wish) }, modifier = Modifier.testTag("wish-bought"))
                if (!done) {
                    DropdownMenuItem(text = { Text("Edit") }, onClick = { menu = false; editing = true })
                    if (canMove) {
                        DropdownMenuItem(text = { Text("Move up") }, onClick = { menu = false; vm.moveWish(wish.id, -1) })
                        DropdownMenuItem(text = { Text("Move down") }, onClick = { menu = false; vm.moveWish(wish.id, 1) })
                    }
                }
                DropdownMenuItem(text = { Text("Delete", color = cs.error) }, onClick = { menu = false; confirmDelete = true })
            }
        }
    }
}

