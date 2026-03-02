package com.lifo.feed

import app.cash.turbine.test
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.lifo.util.model.RequestState
import com.lifo.util.repository.FeedRepository
import com.lifo.util.repository.ThreadRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
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
class FeedViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private lateinit var viewModel: FeedViewModel
    private val feedRepository = mockk<FeedRepository>(relaxed = true)
    private val threadRepository = mockk<ThreadRepository>(relaxed = true)
    private val firebaseAuth = mockk<FirebaseAuth>()
    private val mockUser = mockk<FirebaseUser>()

    @Before
    fun setUp() {
        every { firebaseAuth.currentUser } returns mockUser
        every { mockUser.uid } returns "test-user-id"
        viewModel = FeedViewModel(feedRepository, threadRepository, firebaseAuth)
    }

    @Test
    fun `initial state has empty threads and is not loading`() {
        val state = viewModel.state.value
        assertEquals(emptyList<ThreadRepository.Thread>(), state.threads)
        assertFalse(state.isLoading)
        assertFalse(state.isRefreshing)
        assertFalse(state.isLoadingMore)
        assertEquals(FeedContract.FeedTab.FOR_YOU, state.selectedTab)
        assertFalse(state.hasMore)
        assertNull(state.nextCursor)
        assertNull(state.error)
    }

    @Test
    fun `LoadFeed with authenticated user calls getForYouFeed`() = runTest {
        val threads = listOf(
            ThreadRepository.Thread(threadId = "1", text = "Hello", authorId = "author1"),
            ThreadRepository.Thread(threadId = "2", text = "World", authorId = "author2"),
        )
        val page = FeedRepository.FeedPage(items = threads, hasMore = true, nextCursor = "cursor-1")
        every { feedRepository.getForYouFeed("test-user-id") } returns flowOf(
            RequestState.Loading,
            RequestState.Success(page),
        )

        viewModel.onIntent(FeedContract.Intent.LoadFeed)
        advanceUntilIdle()

        val state = viewModel.state.value
        assertEquals(threads, state.threads)
        assertFalse(state.isLoading)
        assertTrue(state.hasMore)
        assertEquals("cursor-1", state.nextCursor)
    }

    @Test
    fun `LoadFeed without authenticated user emits ShowError effect`() = runTest {
        every { firebaseAuth.currentUser } returns null

        viewModel.effects.test {
            viewModel.onIntent(FeedContract.Intent.LoadFeed)
            advanceUntilIdle()

            val effect = awaitItem()
            assertTrue(effect is FeedContract.Effect.ShowError)
            assertEquals("User not authenticated", (effect as FeedContract.Effect.ShowError).message)
        }
    }

    @Test
    fun `SelectTab changes tab and clears threads`() = runTest {
        // Pre-populate state by loading feed
        val page = FeedRepository.FeedPage(
            items = listOf(ThreadRepository.Thread(threadId = "1", text = "Test")),
            hasMore = true,
            nextCursor = "c1",
        )
        every { feedRepository.getForYouFeed("test-user-id") } returns flowOf(
            RequestState.Success(page),
        )
        viewModel.onIntent(FeedContract.Intent.LoadFeed)
        advanceUntilIdle()
        assertEquals(1, viewModel.state.value.threads.size)

        // Now switch tab
        every { feedRepository.getFollowingFeed("test-user-id") } returns flowOf(
            RequestState.Success(FeedRepository.FeedPage()),
        )
        viewModel.onIntent(FeedContract.Intent.SelectTab(FeedContract.FeedTab.FOLLOWING))
        advanceUntilIdle()

        assertEquals(FeedContract.FeedTab.FOLLOWING, viewModel.state.value.selectedTab)
    }

    @Test
    fun `LikeThread does optimistic update incrementing likeCount`() = runTest {
        val thread = ThreadRepository.Thread(threadId = "t1", text = "Hello", likeCount = 5)
        val page = FeedRepository.FeedPage(items = listOf(thread))
        every { feedRepository.getForYouFeed("test-user-id") } returns flowOf(
            RequestState.Success(page),
        )

        viewModel.onIntent(FeedContract.Intent.LoadFeed)
        advanceUntilIdle()
        assertEquals(5L, viewModel.state.value.threads.first().likeCount)

        coEvery { threadRepository.likeThread("test-user-id", "t1") } returns RequestState.Success(true)

        viewModel.onIntent(FeedContract.Intent.LikeThread("t1"))
        // Optimistic update is synchronous
        assertEquals(6L, viewModel.state.value.threads.first().likeCount)

        advanceUntilIdle()
        assertEquals(6L, viewModel.state.value.threads.first().likeCount)
    }

    @Test
    fun `UnlikeThread does optimistic update decrementing likeCount with min 0`() = runTest {
        val thread = ThreadRepository.Thread(threadId = "t1", text = "Hello", likeCount = 0)
        val page = FeedRepository.FeedPage(items = listOf(thread))
        every { feedRepository.getForYouFeed("test-user-id") } returns flowOf(
            RequestState.Success(page),
        )

        viewModel.onIntent(FeedContract.Intent.LoadFeed)
        advanceUntilIdle()
        assertEquals(0L, viewModel.state.value.threads.first().likeCount)

        coEvery { threadRepository.unlikeThread("test-user-id", "t1") } returns RequestState.Success(true)

        viewModel.onIntent(FeedContract.Intent.UnlikeThread("t1"))
        // Should be clamped to 0
        assertEquals(0L, viewModel.state.value.threads.first().likeCount)
    }

    @Test
    fun `LoadMore appends to existing threads`() = runTest {
        val initialThreads = listOf(ThreadRepository.Thread(threadId = "1", text = "First"))
        val initialPage = FeedRepository.FeedPage(
            items = initialThreads,
            hasMore = true,
            nextCursor = "cursor-1",
        )
        every { feedRepository.getForYouFeed("test-user-id") } returns flowOf(
            RequestState.Success(initialPage),
        )

        viewModel.onIntent(FeedContract.Intent.LoadFeed)
        advanceUntilIdle()
        assertEquals(1, viewModel.state.value.threads.size)

        val moreThreads = listOf(ThreadRepository.Thread(threadId = "2", text = "Second"))
        val morePage = FeedRepository.FeedPage(
            items = moreThreads,
            hasMore = false,
            nextCursor = null,
        )
        every { feedRepository.getForYouFeed("test-user-id", cursor = "cursor-1") } returns flowOf(
            RequestState.Success(morePage),
        )

        viewModel.onIntent(FeedContract.Intent.LoadMore)
        advanceUntilIdle()

        val state = viewModel.state.value
        assertEquals(2, state.threads.size)
        assertEquals("1", state.threads[0].threadId)
        assertEquals("2", state.threads[1].threadId)
        assertFalse(state.hasMore)
    }

    @Test
    fun `RefreshFeed sets isRefreshing to true`() = runTest {
        coEvery { feedRepository.refreshFeed("test-user-id") } returns RequestState.Loading
        every { feedRepository.getForYouFeed("test-user-id") } returns flowOf(RequestState.Idle)

        viewModel.onIntent(FeedContract.Intent.RefreshFeed)

        assertTrue(viewModel.state.value.isRefreshing)
    }
}
