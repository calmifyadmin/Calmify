package com.lifo.search

import app.cash.turbine.test
import com.lifo.util.model.RequestState
import com.lifo.util.repository.SearchRepository
import com.lifo.util.repository.SocialGraphRepository
import com.lifo.util.repository.ThreadRepository
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
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
class SearchViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private lateinit var viewModel: SearchViewModel
    private val searchRepository = mockk<SearchRepository>(relaxed = true)

    @Before
    fun setUp() {
        viewModel = SearchViewModel(searchRepository)
    }

    @Test
    fun `initial state is correct`() {
        val state = viewModel.state.value
        assertEquals("", state.query)
        assertEquals(emptyList<ThreadRepository.Thread>(), state.threadResults)
        assertEquals(emptyList<SocialGraphRepository.SocialUser>(), state.userResults)
        assertFalse(state.isSearching)
        assertEquals(SearchContract.SearchFilter.ALL, state.selectedFilter)
        assertFalse(state.hasSearched)
        assertNull(state.error)
    }

    @Test
    fun `UpdateQuery updates query in state`() {
        viewModel.onIntent(SearchContract.Intent.UpdateQuery("hello"))

        assertEquals("hello", viewModel.state.value.query)
        assertNull(viewModel.state.value.error)
    }

    @Test
    fun `Search with valid query calls repository and updates results`() = runTest {
        val threads = listOf(
            ThreadRepository.Thread(threadId = "1", text = "Hello world"),
        )
        val users = listOf(
            SocialGraphRepository.SocialUser(userId = "u1", displayName = "Alice"),
        )
        every { searchRepository.searchThreads("hello") } returns flowOf(
            RequestState.Success(threads),
        )
        every { searchRepository.searchUsers("hello") } returns flowOf(
            RequestState.Success(users),
        )

        viewModel.onIntent(SearchContract.Intent.UpdateQuery("hello"))
        viewModel.onIntent(SearchContract.Intent.Search)
        advanceUntilIdle()

        val state = viewModel.state.value
        assertEquals(threads, state.threadResults)
        assertEquals(users, state.userResults)
        assertFalse(state.isSearching)
        assertTrue(state.hasSearched)
        assertNull(state.error)
    }

    @Test
    fun `Search with blank query emits ShowError effect`() = runTest {
        viewModel.effects.test {
            viewModel.onIntent(SearchContract.Intent.UpdateQuery("   "))
            viewModel.onIntent(SearchContract.Intent.Search)
            advanceUntilIdle()

            val effect = awaitItem()
            assertTrue(effect is SearchContract.Effect.ShowError)
            assertEquals(
                "Please enter a search query",
                (effect as SearchContract.Effect.ShowError).message,
            )
        }
    }

    @Test
    fun `ClearSearch resets state to initial`() {
        // First update some state
        viewModel.onIntent(SearchContract.Intent.UpdateQuery("test"))
        viewModel.onIntent(SearchContract.Intent.SelectFilter(SearchContract.SearchFilter.THREADS))

        assertEquals("test", viewModel.state.value.query)
        assertEquals(SearchContract.SearchFilter.THREADS, viewModel.state.value.selectedFilter)

        // Clear
        viewModel.onIntent(SearchContract.Intent.ClearSearch)

        val state = viewModel.state.value
        assertEquals("", state.query)
        assertEquals(SearchContract.SearchFilter.ALL, state.selectedFilter)
        assertFalse(state.hasSearched)
    }

    @Test
    fun `SelectFilter changes filter in state`() {
        viewModel.onIntent(SearchContract.Intent.SelectFilter(SearchContract.SearchFilter.USERS))
        assertEquals(SearchContract.SearchFilter.USERS, viewModel.state.value.selectedFilter)
    }

    @Test
    fun `SelectFilter re-searches when query exists and hasSearched`() = runTest {
        val threads = listOf(
            ThreadRepository.Thread(threadId = "1", text = "test"),
        )
        every { searchRepository.searchThreads("test") } returns flowOf(
            RequestState.Success(threads),
        )
        every { searchRepository.searchUsers("test") } returns flowOf(
            RequestState.Success(emptyList()),
        )

        // First, do a search
        viewModel.onIntent(SearchContract.Intent.UpdateQuery("test"))
        viewModel.onIntent(SearchContract.Intent.Search)
        advanceUntilIdle()
        assertTrue(viewModel.state.value.hasSearched)

        // Now change filter — should trigger a new search
        viewModel.onIntent(SearchContract.Intent.SelectFilter(SearchContract.SearchFilter.THREADS))
        advanceUntilIdle()

        // Verify searchThreads was called at least twice (initial + re-search)
        verify(atLeast = 2) { searchRepository.searchThreads("test") }
    }

    @Test
    fun `Search error updates state with error message`() = runTest {
        every { searchRepository.searchThreads("fail") } returns flowOf(
            RequestState.Error(Exception("Network error")),
        )
        every { searchRepository.searchUsers("fail") } returns flowOf(
            RequestState.Success(emptyList()),
        )

        viewModel.effects.test {
            viewModel.onIntent(SearchContract.Intent.UpdateQuery("fail"))
            viewModel.onIntent(SearchContract.Intent.Search)
            advanceUntilIdle()

            val effect = awaitItem()
            assertTrue(effect is SearchContract.Effect.ShowError)
            assertEquals("Network error", (effect as SearchContract.Effect.ShowError).message)
        }

        assertTrue(viewModel.state.value.hasSearched)
        assertFalse(viewModel.state.value.isSearching)
    }
}
