package com.boro.notes

import com.boro.notes.data.Design
import com.boro.notes.data.LinkKind
import com.boro.notes.data.Material
import com.boro.notes.data.MaterialKind
import com.boro.notes.data.turned
import com.boro.notes.data.PhotoPin
import com.boro.notes.data.ProjectJson
import com.boro.notes.data.ProjectLink
import com.boro.notes.data.ProjectStep
import com.boro.notes.ui.ProjectsViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ProjectsViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private lateinit var repo: FakeProjectRepository
    private lateinit var designs: FakeDesignRepository
    private lateinit var vm: ProjectsViewModel

    @Before fun setUp() {
        Dispatchers.setMain(dispatcher)
        repo = FakeProjectRepository()
        designs = FakeDesignRepository(listOf(Design(id = 7, name = "Double knee", shapes = "[]")))
        vm = ProjectsViewModel(repo, designs, FakeIdeaRepository())
    }

    @After fun tearDown() = Dispatchers.resetMain()

    @Test
    fun blankProjectIsNotKept() = runTest(dispatcher) {
        vm.create()
        advanceUntilIdle()
        vm.close()
        advanceUntilIdle()
        assertTrue(repo.projects.value.isEmpty())
    }

    @Test
    fun editsSave() = runTest(dispatcher) {
        vm.create()
        advanceUntilIdle()
        vm.rename("Crane jeans")
        vm.setStep(ProjectStep.SEWING)
        vm.addTodo("Buy copper rivets")
        vm.addTodo("  ")
        vm.toggleTodo(0)
        vm.saveMaterial(null, Material(name = "14 oz selvedge", weight = "14 oz", weave = "3x1 twill", link = "shop.example/denim"))
        vm.toggleLink(ProjectLink(LinkKind.DESIGN, 7))
        vm.close()
        advanceUntilIdle()
        val p = repo.projects.value.single()
        assertEquals("Crane jeans", p.name)
        assertEquals(ProjectStep.SEWING, p.step)
        assertEquals(listOf(true), ProjectJson.todos(p.todos).map { it.done })
        assertEquals("3x1 twill", ProjectJson.materials(p.materials).single().weave)
        assertEquals(listOf(ProjectLink(LinkKind.DESIGN, 7)), ProjectJson.links(p.links))
    }

    @Test
    fun photosKeepPinsAndMakeItWorthKeeping() = runTest(dispatcher) {
        vm.create()
        advanceUntilIdle()
        vm.addPhoto("knee.jpg")
        advanceUntilIdle()
        val photo = vm.photos.value.single()
        vm.savePhoto(photo.id, "Knee panel", listOf(PhotoPin(0.5f, 0.4f, "Redo this rivet")), emptyList())
        advanceUntilIdle()
        assertEquals("Redo this rivet", ProjectJson.pins(vm.photos.value.single().pins).single().text)
        vm.close()
        advanceUntilIdle()
        assertEquals(1, repo.projects.value.size)
        assertEquals("knee.jpg", vm.latestPhotos.value.values.single())
    }

    @Test
    fun turnedPhotoKeepsCoverAndPinSpots() = runTest(dispatcher) {
        vm.create()
        advanceUntilIdle()
        vm.addPhoto("side.jpg")
        advanceUntilIdle()
        vm.setCover("side.jpg")
        val photo = vm.photos.value.single()
        // A pin near the top-left lands near the top-right once the photo turns clockwise.
        val pin = PhotoPin(0.1f, 0.2f, "Pocket").turned(clockwise = true)
        assertEquals(0.8f, pin.x, 1e-5f)
        assertEquals(0.1f, pin.y, 1e-5f)
        val back = pin.turned(clockwise = false)
        assertEquals(0.1f, back.x, 1e-5f)
        assertEquals(0.2f, back.y, 1e-5f)
        vm.savePhoto(photo.id, "", listOf(pin), emptyList(), file = "upright.jpg", oldFile = "side.jpg")
        advanceUntilIdle()
        assertEquals("upright.jpg", vm.photos.value.single().file)
        assertEquals("upright.jpg", vm.open.value!!.project.cover)
    }

    @Test
    fun materialKindsHaveTheirOwnFields() {
        assertEquals(MaterialKind.FABRIC, MaterialKind.of("Fabric"))
        assertEquals(MaterialKind.HARDWARE, MaterialKind.of("Hardware"))
        // Older categories fold into notions.
        assertEquals(MaterialKind.NOTIONS, MaterialKind.of("Thread"))
        assertEquals(MaterialKind.NOTIONS, MaterialKind.of("Other"))
    }

    @Test
    fun spinOffCopiesDesignsAndNotesTweaks() = runTest(dispatcher) {
        vm.create()
        advanceUntilIdle()
        vm.rename("Crane jeans")
        vm.saveMaterial(null, Material(name = "Denim"))
        vm.toggleLink(ProjectLink(LinkKind.DESIGN, 7))
        vm.setStep(ProjectStep.FINISHED)
        advanceUntilIdle()
        val originalId = vm.open.value!!.project.id
        vm.spinOff("Crane jeans v2", "Wider leg")
        advanceUntilIdle()
        val spun = vm.open.value!!
        assertNotEquals(originalId, spun.project.id)
        assertEquals(originalId, spun.project.parentId)
        assertEquals(ProjectStep.DESIGN, spun.project.step)
        assertTrue(spun.project.notes.contains("Wider leg"))
        assertEquals("Denim", spun.materials.single().name)
        val copied = spun.links.single()
        assertNotEquals(7L, copied.id)
        assertTrue(designs.get(copied.id)!!.name.startsWith("Double knee"))
        // The original is untouched.
        assertEquals(ProjectStep.FINISHED, repo.get(originalId)!!.step)
    }

    @Test
    fun deleteMovesToTrash() = runTest(dispatcher) {
        vm.create()
        advanceUntilIdle()
        vm.rename("Vest")
        vm.deleteOpen()
        advanceUntilIdle()
        assertTrue(vm.projects.value.isEmpty())
        assertEquals("Vest", repo.projects.value.single().name)
    }
}
