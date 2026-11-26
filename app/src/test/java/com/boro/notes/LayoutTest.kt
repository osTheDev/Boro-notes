package com.boro.notes

import androidx.compose.ui.test.assertIsDisplayed
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
import org.junit.Assert.assertEquals
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

        rule.onNodeWithTag(TAG_LIST_PANE).assertIsDisplayed()
        rule.onNodeWithTag(TAG_EMPTY_DETAIL).assertIsDisplayed()

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
        rule.setContent { com.boro.notes.ui.BoroRoot(notesVm, designsVm) }

        rule.onNodeWithTag("navDesigns").performClick()
        rule.waitForIdle()
        rule.onNodeWithTag(com.boro.notes.ui.TAG_DESIGN_LIST).assertIsDisplayed()

        rule.onNodeWithTag(com.boro.notes.ui.TAG_NEW_DESIGN).performClick()
        rule.waitForIdle()
        rule.onNodeWithTag(com.boro.notes.ui.TAG_SKETCH_EDITOR).assertIsDisplayed()
        rule.onNodeWithTag(com.boro.notes.ui.TAG_CANVAS).assertIsDisplayed()
        rule.onNodeWithTag("navDesigns").assertDoesNotExist()
    }
}

/** Designs on the inner screen: tracker board, then canvas with the linked note beside it. */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34], qualifiers = "w840dp-h780dp")
class InnerDesignsLayoutTest {
    @get:Rule val rule = createComposeRule()

    @Test
    fun boardThenCanvasBesideNote() {
        val notes = FakeNoteRepository()
        val notesVm = NotesViewModel(notes, SavedStateHandle())
        val designsVm = com.boro.notes.ui.DesignsViewModel(
            FakeDesignRepository(listOf(com.boro.notes.data.Design(id = 1, name = "Smock"))),
            notes,
            SavedStateHandle(),
        )
        rule.setContent { com.boro.notes.ui.BoroRoot(notesVm, designsVm) }

        rule.onNodeWithTag("navDesigns").performClick()
        rule.waitForIdle()
        rule.onNodeWithTag(com.boro.notes.ui.TAG_DESIGN_BOARD).assertIsDisplayed()

        rule.onNodeWithTag("${com.boro.notes.ui.TAG_DESIGN_PREFIX}1").performClick()
        rule.waitForIdle()
        rule.onNodeWithTag(com.boro.notes.ui.TAG_CANVAS).assertIsDisplayed()
        rule.onNodeWithTag(com.boro.notes.ui.TAG_LINKED_NOTE).assertIsDisplayed()

        rule.onNodeWithTag(com.boro.notes.ui.TAG_LINK_NOTE_BUTTON).performClick()
        rule.waitForIdle()
        rule.onNodeWithTag(TAG_EDITOR_PANE).assertIsDisplayed()
        assertEquals("Smock", notesVm.editor.value!!.title)
    }
}
