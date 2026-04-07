package com.lifo.composer

import android.app.Application
import app.cash.turbine.test
import com.lifo.util.auth.AuthProvider
import com.lifo.util.model.RequestState
import com.lifo.util.repository.MediaUploadRepository
import com.lifo.util.repository.ThreadRepository
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ComposerViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private lateinit var viewModel: ComposerViewModel
    private val threadRepository = mockk<ThreadRepository>(relaxed = true)
    private val authProvider = mockk<AuthProvider>(relaxed = true)
    private val mediaUploadRepository = mockk<MediaUploadRepository>(relaxed = true)
    private val appContext = mockk<Application>(relaxed = true)

    @Before
    fun setUp() {
        every { authProvider.isAuthenticated } returns true
        every { authProvider.currentUserId } returns "test-user-id"
        viewModel = ComposerViewModel(
            threadRepository = threadRepository,
            authProvider = authProvider,
            mediaUploadRepository = mediaUploadRepository,
            appContext = appContext
        )
    }

    @Test
    fun `initial state is correct`() {
        val state = viewModel.state.value
        assertEquals("", state.content)
        assertEquals(ComposerContract.Visibility.PUBLIC, state.visibility)
        assertNull(state.moodTag)
        assertFalse(state.isFromJournal)
        assertFalse(state.isSubmitting)
        assertEquals(0, state.characterCount)
        assertEquals(500, state.maxCharacters)
    }

    @Test
    fun `UpdateContent updates content and characterCount`() {
        viewModel.onIntent(ComposerContract.Intent.UpdateContent("Hello World"))

        val state = viewModel.state.value
        assertEquals("Hello World", state.content)
        assertEquals(11, state.characterCount)
    }

    @Test
    fun `Submit with empty content emits ShowError effect`() = runTest {
        viewModel.effects.test {
            viewModel.onIntent(ComposerContract.Intent.UpdateContent("   "))
            viewModel.onIntent(ComposerContract.Intent.Submit)
            advanceUntilIdle()

            val effect = awaitItem()
            assertTrue(effect is ComposerContract.Effect.ShowError)
            assertEquals(
                "Post content cannot be empty.",
                (effect as ComposerContract.Effect.ShowError).message,
            )
        }
    }

    @Test
    fun `Submit with valid content calls createThread and emits PostCreated`() = runTest {
        coEvery { threadRepository.createThread(any()) } returns RequestState.Success("new-thread-id")

        viewModel.effects.test {
            viewModel.onIntent(ComposerContract.Intent.UpdateContent("My first post"))
            viewModel.onIntent(ComposerContract.Intent.Submit)
            advanceUntilIdle()

            val effect = awaitItem()
            assertEquals(ComposerContract.Effect.PostCreated, effect)
        }

        assertFalse(viewModel.state.value.isSubmitting)
    }

    @Test
    fun `Submit with content exceeding maxCharacters emits ShowError`() = runTest {
        val longContent = "a".repeat(501)

        viewModel.effects.test {
            viewModel.onIntent(ComposerContract.Intent.UpdateContent(longContent))
            viewModel.onIntent(ComposerContract.Intent.Submit)
            advanceUntilIdle()

            val effect = awaitItem()
            assertTrue(effect is ComposerContract.Effect.ShowError)
            assertTrue(
                (effect as ComposerContract.Effect.ShowError).message.contains("maximum of 500"),
            )
        }
    }

    @Test
    fun `SetMoodTag toggles mood tag on and off`() {
        // Set mood tag
        viewModel.onIntent(ComposerContract.Intent.SetMoodTag("Happy"))
        assertEquals("Happy", viewModel.state.value.moodTag)

        // Toggle same tag off
        viewModel.onIntent(ComposerContract.Intent.SetMoodTag("Happy"))
        assertNull(viewModel.state.value.moodTag)

        // Set different tag
        viewModel.onIntent(ComposerContract.Intent.SetMoodTag("Calm"))
        assertEquals("Calm", viewModel.state.value.moodTag)
    }

    @Test
    fun `SetVisibility updates visibility`() {
        viewModel.onIntent(ComposerContract.Intent.SetVisibility(ComposerContract.Visibility.FOLLOWERS_ONLY))
        assertEquals(ComposerContract.Visibility.FOLLOWERS_ONLY, viewModel.state.value.visibility)

        viewModel.onIntent(ComposerContract.Intent.SetVisibility(ComposerContract.Visibility.PRIVATE))
        assertEquals(ComposerContract.Visibility.PRIVATE, viewModel.state.value.visibility)
    }

    @Test
    fun `Discard emits Discarded effect`() = runTest {
        viewModel.effects.test {
            viewModel.onIntent(ComposerContract.Intent.Discard)
            advanceUntilIdle()

            assertEquals(ComposerContract.Effect.Discarded, awaitItem())
        }
    }

    @Test
    fun `Submit without authenticated user emits ShowError`() = runTest {
        every { authProvider.isAuthenticated } returns false
        every { authProvider.currentUserId } returns null

        viewModel.effects.test {
            viewModel.onIntent(ComposerContract.Intent.UpdateContent("Valid content"))
            viewModel.onIntent(ComposerContract.Intent.Submit)
            advanceUntilIdle()

            val effect = awaitItem()
            assertTrue(effect is ComposerContract.Effect.ShowError)
            assertEquals(
                "You must be signed in to post.",
                (effect as ComposerContract.Effect.ShowError).message,
            )
        }
    }
}
