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
