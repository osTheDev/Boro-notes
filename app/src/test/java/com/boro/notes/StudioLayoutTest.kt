package com.boro.notes

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextReplacement
import androidx.lifecycle.SavedStateHandle
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.boro.notes.data.Idea
import com.boro.notes.data.IdeaKind
import com.boro.notes.ui.DesignsViewModel
import com.boro.notes.ui.IdeasViewModel
import com.boro.notes.ui.NotesViewModel
import com.boro.notes.ui.ProjectsViewModel
import com.boro.notes.ui.TAG_BACK
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/** Designs tab on the cover screen: tabs, a sketch pad and a project, each full screen. */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34], qualifiers = "w360dp-h780dp")
class CoverStudioLayoutTest {
    @get:Rule val rule = createComposeRule()

    @Test
    fun brushPopupHoldsAllItsSettings() {
        val notes = FakeNoteRepository()
        val notesVm = NotesViewModel(notes, SavedStateHandle())
        val designs = FakeDesignRepository()
        val designsVm = DesignsViewModel(designs, notes, SavedStateHandle())
        val ideas = FakeIdeaRepository()
        val ideasVm = IdeasViewModel(ideas)
        val projectsVm = ProjectsViewModel(FakeProjectRepository(), designs, ideas)
        rule.setContent { testRoot(notesVm, designsVm, projectsVm = projectsVm, ideasVm = ideasVm) }

        rule.onNodeWithTag("navDesigns").performClick()
        rule.waitForIdle()
        rule.onNodeWithTag("studio-tab-SKETCH").performClick()
        rule.waitForIdle()
        rule.onNodeWithTag(com.boro.notes.ui.TAG_NEW_IDEA).performClick()
        rule.waitForIdle()
        rule.onNodeWithTag("new-sketch").performClick()
        rule.waitForIdle()
        rule.onNodeWithTag("pad-draw").performClick()
        rule.waitForIdle()
        rule.onNodeWithTag("pad-draw-popup").assertIsDisplayed()
        rule.onNodeWithTag("pad-mirror").assertIsDisplayed()
        rule.onNodeWithTag("pad-straighten").assertIsDisplayed()
        rule.onNodeWithTag("pad-level-1").performClick()
        rule.waitForIdle()
    }

    @Test
    fun sketchAndProjectOpenFullScreen() {
        val notes = FakeNoteRepository()
        val notesVm = NotesViewModel(notes, SavedStateHandle())
        val designs = FakeDesignRepository()
        val designsVm = DesignsViewModel(designs, notes, SavedStateHandle())
        val ideas = FakeIdeaRepository()
        val ideasVm = IdeasViewModel(ideas)
        val projects = FakeProjectRepository()
        val projectsVm = ProjectsViewModel(projects, designs, ideas)
        rule.setContent { testRoot(notesVm, designsVm, projectsVm = projectsVm, ideasVm = ideasVm) }

        rule.onNodeWithTag("navDesigns").performClick()
        rule.waitForIdle()
        rule.onNodeWithTag(com.boro.notes.ui.TAG_DESIGN_LIST).assertIsDisplayed()
        rule.onNodeWithTag("studio-tab-PATTERN").performClick()
        rule.waitForIdle()
        rule.onNodeWithTag("patternSoon").assertIsDisplayed()

        // Sketch: a new sketch opens the pad over everything; Back returns to the board.
        rule.onNodeWithTag("studio-tab-SKETCH").performClick()
        rule.waitForIdle()
        rule.onNodeWithTag(com.boro.notes.ui.TAG_SKETCH_BOARD).assertIsDisplayed()
        rule.onNodeWithTag(com.boro.notes.ui.TAG_NEW_IDEA).performClick()
        rule.waitForIdle()
        rule.onNodeWithTag("new-sketch").performClick()
        rule.waitForIdle()
        rule.onNodeWithTag(com.boro.notes.ui.TAG_SKETCH_PAD).assertIsDisplayed()
        rule.onNodeWithTag(com.boro.notes.ui.TAG_PAD_CANVAS).assertIsDisplayed()
        rule.onNodeWithTag("navDesigns").assertDoesNotExist()
        rule.onNodeWithTag("pad-fill").performClick()
        rule.waitForIdle()
        rule.onNodeWithTag("fabric-0").assertIsDisplayed()
        rule.onNodeWithTag("fabric-0").performClick()
        rule.waitForIdle()
        rule.onNodeWithTag("idea-title").performTextReplacement("Scoop pocket")
        rule.onNodeWithTag(TAG_BACK).performClick()
        rule.waitForIdle()
        rule.onNodeWithTag(com.boro.notes.ui.TAG_SKETCH_BOARD).assertIsDisplayed()
        assertEquals("Scoop pocket", ideas.ideas.value.single().title)

        // Projects: a new project opens its page; its name shows on the card after Back.
        rule.onNodeWithTag("studio-tab-PROJECTS").performClick()
        rule.waitForIdle()
        rule.onNodeWithTag(com.boro.notes.ui.TAG_PROJECTS).assertIsDisplayed()
        rule.onNodeWithTag(com.boro.notes.ui.TAG_NEW_PROJECT).performClick()
        rule.waitForIdle()
        rule.onNodeWithTag(com.boro.notes.ui.TAG_PROJECT_EDITOR).assertIsDisplayed()
        rule.onNodeWithTag("project-name").performTextReplacement("Crane jeans")
        rule.onNodeWithTag("step-SEWING").performScrollTo().performClick()
        rule.onNodeWithTag("todo-field").performScrollTo().performTextReplacement("Buy rivets")
        rule.onNodeWithTag("todo-add").performClick()
        rule.waitForIdle()
        rule.onNodeWithTag("todo-check-0").assertExists()
        rule.onNodeWithTag(TAG_BACK).performClick()
        rule.waitForIdle()
        val saved = projects.projects.value.single()
        assertEquals("Crane jeans", saved.name)
        rule.onNodeWithTag("${com.boro.notes.ui.TAG_PROJECT_PREFIX}${saved.id}").assertIsDisplayed()
    }
}

/** Inner screen: a collage with a saved sketch pinned to it. */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34], qualifiers = "w840dp-h780dp")
class InnerStudioLayoutTest {
    @get:Rule val rule = createComposeRule()

    @Test
    fun collagePinsASavedIdea() {
        val notes = FakeNoteRepository()
        val notesVm = NotesViewModel(notes, SavedStateHandle())
        val designs = FakeDesignRepository()
        val designsVm = DesignsViewModel(designs, notes, SavedStateHandle())
        val ideas = FakeIdeaRepository(listOf(Idea(id = 1, kind = IdeaKind.SKETCH, title = "Hem idea", content = "{}")))
        val ideasVm = IdeasViewModel(ideas)
        val projectsVm = ProjectsViewModel(FakeProjectRepository(), designs, ideas)
        rule.setContent { testRoot(notesVm, designsVm, projectsVm = projectsVm, ideasVm = ideasVm) }

        rule.onNodeWithTag("navDesigns").performClick()
        rule.waitForIdle()
        rule.onNodeWithTag("studio-tab-SKETCH").performClick()
        rule.waitForIdle()
        rule.onNodeWithTag("${com.boro.notes.ui.TAG_IDEA_PREFIX}1").assertIsDisplayed()
        rule.onNodeWithTag(com.boro.notes.ui.TAG_NEW_IDEA).performClick()
        rule.waitForIdle()
        rule.onNodeWithTag("new-collage").performClick()
        rule.waitForIdle()
        rule.onNodeWithTag(com.boro.notes.ui.TAG_COLLAGE).assertIsDisplayed()
        rule.onNodeWithTag("collage-add").performClick()
        rule.waitForIdle()
        rule.onNodeWithTag("collage-idea").performClick()
        rule.waitForIdle()
        rule.onNodeWithTag("pick-1").performClick()
        rule.waitForIdle()
        val items = ideasVm.open.value!!.collage.items
        assertEquals(1, items.size)
        rule.onNodeWithTag("collage-item-${items.single().id}").assertExists()
        rule.onNodeWithTag("collage-selected").assertIsDisplayed()
    }
}
