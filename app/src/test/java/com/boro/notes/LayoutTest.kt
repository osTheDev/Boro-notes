package com.boro.notes

import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.isFocused
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextReplacement
import androidx.lifecycle.SavedStateHandle
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.boro.notes.data.Note
import com.boro.notes.ui.NotesScreen
import com.boro.notes.ui.NotesViewModel
import com.boro.notes.ui.TAG_BACK
import com.boro.notes.ui.TAG_EDITOR_PANE
import com.boro.notes.ui.TAG_EMPTY_DETAIL
import com.boro.notes.ui.TAG_LIST_PANE
import com.boro.notes.ui.TAG_NOTE_PREFIX
import com.boro.notes.ui.TAG_TITLE
import com.boro.notes.ui.TAG_FORMAT_BAR
import com.boro.notes.ui.TAG_NOTE_BLANK
import com.boro.notes.ui.TAG_NOTE_CHECK
import com.boro.notes.ui.TAG_NOTE_COLUMNS
import com.boro.notes.ui.TAG_NOTE_TABLE
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/** Cover screen: roughly 360dp wide, one pane at a time. */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34], qualifiers = "w360dp-h780dp")
class CoverScreenLayoutTest {
    @get:Rule val rule = createComposeRule()

    @Test
    fun opensNoteFullScreenAndGoesBack() {
        val vm = NotesViewModel(FakeNoteRepository(listOf(Note(id = 1, title = "Tee"))), SavedStateHandle())
        rule.setContent { NotesScreen(vm) }

        rule.onNodeWithTag(TAG_LIST_PANE).assertIsDisplayed()
        rule.onNodeWithTag("${TAG_NOTE_PREFIX}1").performClick()
        rule.waitForIdle()

        rule.onNodeWithTag(TAG_EDITOR_PANE).assertIsDisplayed()
        rule.onNodeWithTag(TAG_LIST_PANE).assertDoesNotExist()

        rule.onNodeWithTag(TAG_BACK).performClick()
        rule.waitForIdle()
        rule.onNodeWithTag(TAG_LIST_PANE).assertIsDisplayed()
    }
}

/** Inner screen: roughly 700dp wide, list and note side by side. */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34], qualifiers = "w700dp-h780dp")
class InnerScreenLayoutTest {
    @get:Rule val rule = createComposeRule()

    @Test
    fun showsListAndNoteTogether() {
        val repo = FakeNoteRepository(listOf(Note(id = 1, title = "Tee")))
        val vm = NotesViewModel(repo, SavedStateHandle())
        rule.setContent { NotesScreen(vm) }

        // Nothing open: notes fill the inner screen as cards, no empty half.
        rule.onNodeWithTag(TAG_LIST_PANE).assertIsDisplayed()
        rule.onNodeWithTag("${TAG_NOTE_PREFIX}1").assertIsDisplayed()
        rule.onNodeWithTag(TAG_EMPTY_DETAIL).assertDoesNotExist()

        rule.onNodeWithTag("${TAG_NOTE_PREFIX}1").performClick()
        rule.waitForIdle()

        rule.onNodeWithTag(TAG_LIST_PANE).assertIsDisplayed()
        rule.onNodeWithTag(TAG_EDITOR_PANE).assertIsDisplayed()
        rule.onNodeWithTag(TAG_BACK).assertDoesNotExist()

        rule.onNodeWithTag(TAG_TITLE).performTextReplacement("Tee shirt")
        rule.waitForIdle()
        assertEquals("Tee shirt", vm.editor.value!!.title)
    }
}

/** Rich notes: the formatting bar inserts tables and columns, which are saved with the note. */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34], qualifiers = "w700dp-h780dp")
class RichNoteLayoutTest {
    @get:Rule val rule = createComposeRule()

    @Test
    fun insertsTableAndColumns() {
        val repo = FakeNoteRepository(listOf(Note(id = 1, title = "Pockets", body = "scoop")))
        val vm = NotesViewModel(repo, SavedStateHandle())
        vm.open(1)
        rule.setContent { NotesScreen(vm) }
        rule.waitForIdle()

        rule.onNodeWithTag(TAG_FORMAT_BAR).assertIsDisplayed()
        rule.onNodeWithTag("insert-table").performScrollTo().performClick()
        rule.waitForIdle()
        rule.onNodeWithTag(TAG_NOTE_TABLE).assertExists()

        rule.onNodeWithTag("insert-columns").performScrollTo().performClick()
        rule.waitForIdle()
        rule.onNodeWithTag(TAG_NOTE_COLUMNS).assertExists()

        // Edits are debounced before reaching the view model.
        rule.mainClock.advanceTimeBy(1_000)
        rule.waitForIdle()
        val content = vm.editor.value!!.content
        assertTrue("content was $content", content != null && content.contains("\"table\"") && content.contains("\"columns\""))
        assertEquals("scoop", vm.editor.value!!.body.lineSequence().first())
    }
}

/** Note paper: colour, lines and margin are chosen from the Page menu and saved with the note. */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34], qualifiers = "w700dp-h780dp")
class NotePaperLayoutTest {
    @get:Rule val rule = createComposeRule()

    @Test
    fun pageMenuSetsPaper() {
        val vm = NotesViewModel(FakeNoteRepository(listOf(Note(id = 1, title = "Journal", body = "hello"))), SavedStateHandle())
        vm.open(1)
        rule.setContent { NotesScreen(vm) }
        rule.waitForIdle()

        for (item in listOf("paper-kraft", "pattern-ruled", "paper-margin")) {
            rule.onNodeWithTag("format-page").performScrollTo().performClick()
            rule.onNodeWithTag(item).performScrollTo().performClick()
            rule.waitForIdle()
        }
        rule.mainClock.advanceTimeBy(1_000)
        rule.waitForIdle()

        val content = vm.editor.value!!.content.orEmpty()
        assertTrue(content, content.contains("KRAFT") && content.contains("RULED") && content.contains("\"margin\":true"))
        rule.onNodeWithTag(TAG_TITLE).assertIsDisplayed()
    }

    @Test
    fun columnsTakeAFill() {
        val vm = NotesViewModel(FakeNoteRepository(listOf(Note(id = 1, title = "Plan", body = "x"))), SavedStateHandle())
        vm.open(1)
        rule.setContent { NotesScreen(vm) }
        rule.waitForIdle()

        rule.onNodeWithTag("insert-columns").performScrollTo().performClick()
        rule.waitForIdle()
        rule.onNodeWithTag("format-fill").performScrollTo().performClick()
        rule.onNodeWithTag("fill-sage").performScrollTo().performClick()
        rule.waitForIdle()
        rule.mainClock.advanceTimeBy(1_000)
        rule.waitForIdle()

        val content = vm.editor.value!!.content.orEmpty()
        assertTrue(content, content.contains("\"fill\":\"SAGE\""))
    }
}

/** Cover screen: columns stay side by side, and inserting doesn't open the keyboard. */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34], qualifiers = "w360dp-h780dp")
class CoverRichNoteLayoutTest {
    @get:Rule val rule = createComposeRule()

    @Test
    fun columnsStaySideBySide() {
        val vm = NotesViewModel(FakeNoteRepository(listOf(Note(id = 1, title = "Pockets"))), SavedStateHandle())
        vm.open(1)
        rule.setContent { NotesScreen(vm) }
        rule.waitForIdle()

        rule.onNodeWithTag("insert-columns").performScrollTo().performClick()
        rule.waitForIdle()

        val first = rule.onNodeWithText("Column 1", useUnmergedTree = true).getUnclippedBoundsInRoot()
        val second = rule.onNodeWithText("Column 2", useUnmergedTree = true).getUnclippedBoundsInRoot()
        assertEquals(first.top, second.top)
        assertTrue(second.left > first.left)
        rule.onNode(isFocused()).assertDoesNotExist()
    }

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun backspaceClearsEmptyChecklistItemAndBlankSpaceFocusesNote() {
        val vm = NotesViewModel(FakeNoteRepository(listOf(Note(id = 1, title = "Pockets", body = "scoop"))), SavedStateHandle())
        vm.open(1)
        rule.setContent { NotesScreen(vm) }
        rule.waitForIdle()

        rule.onNodeWithTag("insert-check").performScrollTo().performClick()
        rule.waitForIdle()
        rule.onNodeWithTag(TAG_NOTE_CHECK).assertExists()

        val checkField = rule.onNode(hasSetTextAction() and hasAnyAncestor(hasTestTag(TAG_NOTE_CHECK)))
        checkField.performClick()
        rule.waitForIdle()
        checkField.performKeyInput { pressKey(Key.Backspace) }
        rule.waitForIdle()
        rule.onNodeWithTag(TAG_NOTE_CHECK).assertDoesNotExist()

        rule.onNodeWithTag(TAG_NOTE_BLANK).performClick()
        rule.waitForIdle()
        rule.onNode(isFocused()).assertExists()
    }
}

/** Designs on the cover screen: list, then a full-screen canvas. */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34], qualifiers = "w360dp-h780dp")
class CoverDesignsLayoutTest {
    @get:Rule val rule = createComposeRule()

    @Test
    fun newDesignOpensCanvasWithoutBottomBar() {
        val notes = FakeNoteRepository()
        val notesVm = NotesViewModel(notes, SavedStateHandle())
        val designsVm = com.boro.notes.ui.DesignsViewModel(FakeDesignRepository(), notes, SavedStateHandle())
        rule.setContent { testRoot(notesVm, designsVm) }

        rule.onNodeWithTag("navDesigns").performClick()
        rule.waitForIdle()
        rule.onNodeWithTag(com.boro.notes.ui.TAG_DESIGN_LIST).assertIsDisplayed()

        rule.onNodeWithTag(com.boro.notes.ui.TAG_NEW_DESIGN).performClick()
        rule.waitForIdle()
        rule.onNodeWithTag("${com.boro.notes.ui.TAG_PRESET_PREFIX}JEANS_FRONT").performClick()
        rule.waitForIdle()
        rule.onNodeWithTag(com.boro.notes.ui.TAG_SKETCH_EDITOR).assertIsDisplayed()
        assertEquals("Jeans (front)", designsVm.sketch.value!!.name)
        rule.onNodeWithTag(com.boro.notes.ui.TAG_CANVAS).assertIsDisplayed()
        rule.onNodeWithTag("navDesigns").assertDoesNotExist()
    }
}

/** Designs on the inner screen: tracker board, then canvas; the design's note opens full screen. */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34], qualifiers = "w840dp-h780dp")
class InnerDesignsLayoutTest {
    @get:Rule val rule = createComposeRule()

    @Test
    fun boardThenCanvasThenFullScreenNote() {
        val notes = FakeNoteRepository()
        val notesVm = NotesViewModel(notes, SavedStateHandle())
        val designsVm = com.boro.notes.ui.DesignsViewModel(
            FakeDesignRepository(listOf(com.boro.notes.data.Design(id = 1, name = "Smock"))),
            notes,
            SavedStateHandle(),
        )
        rule.setContent { testRoot(notesVm, designsVm) }

        rule.onNodeWithTag("navDesigns").performClick()
        rule.waitForIdle()
        rule.onNodeWithTag(com.boro.notes.ui.TAG_DESIGN_BOARD).assertIsDisplayed()

        rule.onNodeWithTag("${com.boro.notes.ui.TAG_DESIGN_PREFIX}1").performClick()
        rule.waitForIdle()
        rule.onNodeWithTag(com.boro.notes.ui.TAG_CANVAS).assertIsDisplayed()

        // Notes creates the design's note and opens it over everything.
        rule.onNodeWithTag(com.boro.notes.ui.TAG_NOTES_TOGGLE).performClick()
        rule.waitForIdle()
        rule.onNodeWithTag(TAG_EDITOR_PANE).assertIsDisplayed()
        rule.onNodeWithTag(com.boro.notes.ui.TAG_CANVAS).assertDoesNotExist()
        rule.onNodeWithTag("navDesigns").assertDoesNotExist()
        assertEquals("Smock", notesVm.editor.value!!.title)

        // Back returns to the sketch.
        rule.onNodeWithTag(TAG_BACK).performClick()
        rule.waitForIdle()
        rule.onNodeWithTag(com.boro.notes.ui.TAG_CANVAS).assertIsDisplayed()
    }
}

/** Notes on the inner screen open full screen with a list that slides in. */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34], qualifiers = "w840dp-h780dp")
class InnerNotesFullScreenTest {
    @get:Rule val rule = createComposeRule()

    @Test
    fun noteOpensFullScreenWithCollapsibleList() {
        val notes = FakeNoteRepository(listOf(Note(id = 1, title = "Tee"), Note(id = 2, title = "Coat")))
        val notesVm = NotesViewModel(notes, SavedStateHandle())
        val designsVm = com.boro.notes.ui.DesignsViewModel(FakeDesignRepository(), notes, SavedStateHandle())
        rule.setContent { testRoot(notesVm, designsVm) }

        // The app opens on Home; the Notes tile goes to the notes list.
        rule.onNodeWithTag(com.boro.notes.ui.TAG_HOME).assertIsDisplayed()
        rule.onNodeWithTag("tile-notes").performClick()
        rule.waitForIdle()
        rule.onNodeWithTag("${TAG_NOTE_PREFIX}1").performClick()
        rule.waitForIdle()
        rule.onNodeWithTag(TAG_EDITOR_PANE).assertIsDisplayed()
        rule.onNodeWithTag(TAG_LIST_PANE).assertDoesNotExist()
        rule.onNodeWithTag("navNotes").assertDoesNotExist()

        rule.onNodeWithTag(com.boro.notes.ui.TAG_LIST_TOGGLE).performClick()
        rule.waitForIdle()
        rule.onNodeWithTag("${TAG_NOTE_PREFIX}2").performClick()
        rule.waitForIdle()
        assertEquals("Coat", notesVm.editor.value!!.title)

        // Back closes the note and returns to the list with the side menu.
        rule.onNodeWithTag(TAG_BACK).performClick()
        rule.waitForIdle()
        rule.onNodeWithTag("navNotes").assertIsDisplayed()
        rule.onNodeWithTag(TAG_LIST_PANE).assertIsDisplayed()
    }
}

/** Tasks on the cover screen: add a punch card, punch today, add a to-do. */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34], qualifiers = "w360dp-h780dp")
class TasksLayoutTest {
    @get:Rule val rule = createComposeRule()

    @Test
    fun punchCardAndTodo() {
        val repo = FakeTaskRepository()
        val vm = com.boro.notes.ui.TasksViewModel(repo) { java.time.LocalDate.of(2026, 11, 26) }
        rule.setContent { com.boro.notes.ui.TasksScreen(vm, wide = false) }

        rule.onNodeWithTag(com.boro.notes.ui.TAG_ADD_HABIT).performScrollTo().performClick()
        rule.onNodeWithTag("habit-name").performTextReplacement("Stretch")
        rule.onNodeWithTag("habit-save").performClick()
        rule.waitForIdle()
        val id = vm.habits.value.single().habit.id
        rule.onNodeWithTag("punch-$id-3").performScrollTo().performClick()
        rule.waitForIdle()
        assertEquals(1, vm.habits.value.single().count)

        rule.onNodeWithTag(com.boro.notes.ui.TAG_TODO_INPUT).performScrollTo().performTextReplacement("Wash indigo")
        rule.onNodeWithTag("todo-add").performClick()
        rule.waitForIdle()
        assertEquals(listOf("Wash indigo"), vm.todos.value.map { it.text })
    }
}

@androidx.compose.runtime.Composable
private fun testRoot(
    notesVm: NotesViewModel,
    designsVm: com.boro.notes.ui.DesignsViewModel,
    journals: FakeJournalRepository = FakeJournalRepository(),
) = com.boro.notes.ui.BoroRoot(
    notesVm,
    designsVm,
    com.boro.notes.ui.TasksViewModel(FakeTaskRepository()),
    com.boro.notes.ui.JournalViewModel(journals),
    com.boro.notes.ui.TrashViewModel(FakeNoteRepository(), FakeDesignRepository(), journals),
)

/** Journals on the cover screen: the picked book stays closed until opened. */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34], qualifiers = "w360dp-h780dp")
class CoverJournalLayoutTest {
    @get:Rule val rule = createComposeRule()

    @Test
    fun newBookShowsClosedCoverThenOpensHereWithDrawing() {
        val notes = FakeNoteRepository()
        val notesVm = NotesViewModel(notes, SavedStateHandle())
        val designsVm = com.boro.notes.ui.DesignsViewModel(FakeDesignRepository(), notes, SavedStateHandle())
        val journals = FakeJournalRepository()
        rule.setContent { testRoot(notesVm, designsVm, journals) }

        // Trash is reachable from Home.
        rule.onNodeWithTag(com.boro.notes.ui.TAG_HOME_TRASH).performClick()
        rule.waitForIdle()
        rule.onNodeWithTag(com.boro.notes.ui.TAG_TRASH).assertIsDisplayed()
        rule.onNodeWithTag("trash-back").performClick()
        rule.waitForIdle()

        rule.onNodeWithTag("tile-journal").performScrollTo().performClick()
        rule.waitForIdle()
        rule.onNodeWithTag(com.boro.notes.ui.TAG_NEW_JOURNAL).performScrollTo().performClick()
        rule.waitForIdle()
        rule.onNodeWithTag(com.boro.notes.ui.TAG_COVER_TITLE).performTextReplacement("Field guide")
        rule.onNodeWithTag("cover-colour-2").performScrollTo().performClick()
        rule.onNodeWithTag(com.boro.notes.ui.TAG_COVER_SAVE).performScrollTo().performClick()
        rule.waitForIdle()

        assertEquals("Field guide", journals.journals.value.single().title)
        rule.onNodeWithTag(com.boro.notes.ui.TAG_CLOSED_BOOK).assertIsDisplayed()
        rule.onNodeWithTag("navJournal").assertDoesNotExist()

        rule.onNodeWithTag(com.boro.notes.ui.TAG_OPEN_HERE).performScrollTo().performClick()
        rule.waitForIdle()
        rule.onNodeWithTag(com.boro.notes.ui.TAG_OPEN_BOOK).assertExists()
        rule.onNodeWithTag(com.boro.notes.ui.TAG_PAGE_TITLE).assertExists()

        rule.onNodeWithTag("insert-drawing").performScrollTo().performClick()
        rule.waitForIdle()
        rule.onNodeWithTag(com.boro.notes.ui.TAG_NOTE_DRAWING).assertExists()
        rule.onNodeWithTag("drawing-done").performScrollTo().performClick()
        rule.waitForIdle()
        rule.onNodeWithTag("drawing-done").assertDoesNotExist()
    }
}

/** Journals on the inner screen: pages beside the open page; deleting asks first. */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34], qualifiers = "w840dp-h780dp")
class InnerJournalLayoutTest {
    @get:Rule val rule = createComposeRule()

    @Test
    fun opensBookAndTrashesPageAfterConfirm() {
        val journals = FakeJournalRepository()
        kotlinx.coroutines.runBlocking { journals.createJournal("Days", 1, 2) }
        val vm = com.boro.notes.ui.JournalViewModel(journals)
        rule.setContent { com.boro.notes.ui.JournalScreen(vm, wide = true) }

        rule.onNodeWithTag("book-1").performClick()
        rule.waitForIdle()
        // Unfolded, a book opens straight into book view: two facing pages across the screen.
        rule.onNodeWithTag(com.boro.notes.ui.TAG_BOOK_SPREAD).assertIsDisplayed()
        assertTrue(vm.bookView.value)

        // Even an empty book shows two pages ready to write on; both are written in place.
        rule.onNodeWithTag("spread-title-left").performTextReplacement("Moss walk")
        rule.onNodeWithTag("spread-title-right").performTextReplacement("Lichen")
        rule.waitForIdle()
        rule.onNodeWithTag("spread-date-left").assertIsDisplayed()
        rule.onNodeWithTag("spread-date-right").assertIsDisplayed()

        // The contents list page titles, and a chapter can start at a page.
        rule.onNodeWithTag("spread-contents").performClick()
        rule.waitForIdle()
        rule.onNodeWithTag(com.boro.notes.ui.TAG_CONTENTS).assertIsDisplayed()
        val second = journals.pages.value.maxBy { it.id }
        rule.onNodeWithTag("start-chapter-${second.id}").performClick()
        rule.onNodeWithTag(com.boro.notes.ui.TAG_CHAPTER_NAME).performTextReplacement("Autumn")
        rule.onNodeWithTag(com.boro.notes.ui.TAG_CHAPTER_SAVE).performClick()
        rule.waitForIdle()
        rule.onNodeWithTag("chapter-${second.id}").assertIsDisplayed()
        rule.onNodeWithTag("contents-close").performClick()
        rule.waitForIdle()

        // Leaving book view shows the pages beside the open page.
        rule.onNodeWithTag(com.boro.notes.ui.TAG_BOOK_VIEW).performClick()
        rule.waitForIdle()
        rule.onNodeWithTag(com.boro.notes.ui.TAG_BOOK_SPREAD).assertDoesNotExist()
        rule.onNodeWithTag(com.boro.notes.ui.TAG_PAGE_LIST).assertIsDisplayed()
        rule.onNodeWithTag(com.boro.notes.ui.TAG_BOOK_VIEW).performClick()
        rule.waitForIdle()
        rule.onNodeWithTag(com.boro.notes.ui.TAG_BOOK_SPREAD).assertIsDisplayed()

        // Read only hides the delete controls; tapping again goes back to editing.
        rule.onNodeWithTag(com.boro.notes.ui.TAG_READ_MODE).performClick()
        rule.waitForIdle()
        assertTrue(vm.reading.value)
        rule.onNodeWithTag("spread-delete-right").assertDoesNotExist()
        rule.onNodeWithTag(com.boro.notes.ui.TAG_READ_MODE).performClick()
        rule.waitForIdle()

        // One formatting bar for both pages: it drops down when asked for and folds away again.
        rule.onNodeWithTag(com.boro.notes.ui.TAG_SPREAD_TOOLBAR).assertDoesNotExist()
        rule.onNodeWithTag("spread-format").performClick()
        rule.waitForIdle()
        rule.onAllNodesWithTag(com.boro.notes.ui.TAG_SPREAD_TOOLBAR).assertCountEquals(1)
        rule.onNodeWithTag("spread-format").performClick()
        rule.waitForIdle()
        rule.onNodeWithTag(com.boro.notes.ui.TAG_SPREAD_TOOLBAR).assertDoesNotExist()

        rule.onNodeWithTag("spread-delete-right").performClick()
        rule.waitForIdle()
        rule.onNodeWithTag(com.boro.notes.ui.TAG_CONFIRM_DELETE).performClick()
        rule.waitForIdle()

        val trashed = journals.pages.value.single { it.deletedAt != null }
        assertEquals("Lichen", trashed.title)
        val page = journals.pages.value.single { it.deletedAt == null && it.title.isNotBlank() }
        assertEquals("Moss walk", page.title)
    }
}
