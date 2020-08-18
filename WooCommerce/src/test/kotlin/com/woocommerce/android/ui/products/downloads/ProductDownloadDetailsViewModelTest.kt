package com.woocommerce.android.ui.products.downloads

import androidx.lifecycle.SavedStateHandle
import com.woocommerce.android.model.ProductFile
import com.woocommerce.android.ui.products.downloads.ProductDownloadDetailsViewModel.ProductDownloadDetailsEvent.UpdateFileAndExitEvent
import com.woocommerce.android.ui.products.downloads.ProductDownloadDetailsViewModel.ProductDownloadDetailsViewState
import com.woocommerce.android.util.CoroutineTestRule
import com.woocommerce.android.viewmodel.BaseUnitTest
import com.woocommerce.android.viewmodel.MultiLiveEvent.Event
import com.woocommerce.android.viewmodel.SavedStateWithArgs
import kotlinx.coroutines.ExperimentalCoroutinesApi
import org.assertj.core.api.Assertions.assertThat
import org.junit.Rule
import org.junit.Test
import kotlin.test.assertEquals

@ExperimentalCoroutinesApi
class ProductDownloadDetailsViewModelTest : BaseUnitTest() {
    private lateinit var viewModel: ProductDownloadDetailsViewModel
    private val file = ProductFile(id = "id", name = "file", url = "url")
    private val savedStateForEditing = SavedStateWithArgs(
        SavedStateHandle(),
        null,
        ProductDownloadDetailsFragmentArgs(file)
    )

    @get:Rule
    var coroutinesTestRule = CoroutineTestRule()

    @Test
    fun `test has the correct init state`() {
        viewModel = ProductDownloadDetailsViewModel(
            savedStateForEditing,
            coroutinesTestRule.testDispatchers
        )

        var state: ProductDownloadDetailsViewState? = null
        viewModel.productDownloadDetailsViewStateData.observeForever { _, new -> state = new }

        assertThat(state!!.fileDraft).isEqualTo(file)
        assertThat(state!!.hasChanges).isEqualTo(false)
    }

    @Test
    fun `test file name edit`() {
        viewModel = ProductDownloadDetailsViewModel(
            savedStateForEditing,
            coroutinesTestRule.testDispatchers
        )

        val newName = "new name"
        viewModel.onFileNameChanged(newName)

        var state: ProductDownloadDetailsViewState? = null
        viewModel.productDownloadDetailsViewStateData.observeForever { _, new -> state = new }

        assertThat(state!!.fileDraft.name).isEqualTo(newName)
        assertThat(state!!.hasChanges).isEqualTo(true)
    }

    @Test
    fun `test file url edit`() {
        viewModel = ProductDownloadDetailsViewModel(
            savedStateForEditing,
            coroutinesTestRule.testDispatchers
        )

        val newUrl = "new url"
        viewModel.onFileUrlChanged(newUrl)

        var state: ProductDownloadDetailsViewState? = null
        viewModel.productDownloadDetailsViewStateData.observeForever { _, new -> state = new }

        assertThat(state!!.fileDraft.url).isEqualTo(newUrl)
        assertThat(state!!.hasChanges).isEqualTo(true)
    }

    @Test
    fun `test dispatch update event`() {
        viewModel = ProductDownloadDetailsViewModel(
            savedStateForEditing,
            coroutinesTestRule.testDispatchers
        )

        val newUrl = "new url"
        val newName = "new name"
        viewModel.onFileNameChanged(newName)
        viewModel.onFileUrlChanged(newUrl)

        var event: Event? = null
        viewModel.event.observeForever { new -> event = new }
        viewModel.onDoneOrUpdateClicked()

        assertThat(event).isInstanceOf(UpdateFileAndExitEvent::class.java)
        assertEquals(newName, (event as UpdateFileAndExitEvent).updatedFile.name)
        assertEquals(newUrl, (event as UpdateFileAndExitEvent).updatedFile.url)
    }
}