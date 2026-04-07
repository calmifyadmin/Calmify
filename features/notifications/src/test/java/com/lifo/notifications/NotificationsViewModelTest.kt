package com.lifo.notifications

import app.cash.turbine.test
import com.lifo.util.auth.AuthProvider
import com.lifo.util.model.RequestState
import com.lifo.util.repository.NotificationRepository
import io.mockk.coEvery
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
class NotificationsViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private lateinit var viewModel: NotificationsViewModel
    private val notificationRepository = mockk<NotificationRepository>(relaxed = true)
    private val authProvider = mockk<AuthProvider>(relaxed = true)

    @Before
    fun setUp() {
        every { authProvider.isAuthenticated } returns true
        every { authProvider.currentUserId } returns "test-user-id"
        viewModel = NotificationsViewModel(
            notificationRepository = notificationRepository,
            authProvider = authProvider
        )
    }

    @Test
    fun `initial state is correct`() {
        val state = viewModel.state.value
        assertEquals(emptyList<NotificationRepository.Notification>(), state.notifications)
        assertEquals(0, state.unreadCount)
        assertFalse(state.isLoading)
        assertNull(state.error)
    }

    @Test
    fun `LoadNotifications with authenticated user populates notifications`() = runTest {
        val notifications = listOf(
            NotificationRepository.Notification(
                id = "n1",
                userId = "test-user-id",
                type = NotificationRepository.NotificationType.LIKE,
                actorId = "actor1",
                actorName = "Alice",
                message = "Alice liked your post",
                isRead = false,
            ),
            NotificationRepository.Notification(
                id = "n2",
                userId = "test-user-id",
                type = NotificationRepository.NotificationType.NEW_FOLLOWER,
                actorId = "actor2",
                actorName = "Bob",
                message = "Bob started following you",
                isRead = true,
            ),
        )

        every { notificationRepository.getNotifications("test-user-id") } returns flowOf(
            RequestState.Success(notifications),
        )
        every { notificationRepository.getUnreadCount("test-user-id") } returns flowOf(1)

        viewModel.onIntent(NotificationsContract.Intent.LoadNotifications)
        advanceUntilIdle()

        val state = viewModel.state.value
        assertEquals(2, state.notifications.size)
        assertEquals("n1", state.notifications[0].id)
        assertEquals("n2", state.notifications[1].id)
        assertEquals(1, state.unreadCount)
        assertFalse(state.isLoading)
    }

    @Test
    fun `LoadNotifications without authenticated user emits ShowError`() = runTest {
        every { authProvider.isAuthenticated } returns false
        every { authProvider.currentUserId } returns null

        viewModel.effects.test {
            viewModel.onIntent(NotificationsContract.Intent.LoadNotifications)
            advanceUntilIdle()

            val effect = awaitItem()
            assertTrue(effect is NotificationsContract.Effect.ShowError)
            assertEquals(
                "User not authenticated",
                (effect as NotificationsContract.Effect.ShowError).message,
            )
        }
    }

    @Test
    fun `MarkAsRead optimistically updates notification to read`() = runTest {
        val notifications = listOf(
            NotificationRepository.Notification(id = "n1", isRead = false),
            NotificationRepository.Notification(id = "n2", isRead = false),
        )
        every { notificationRepository.getNotifications("test-user-id") } returns flowOf(
            RequestState.Success(notifications),
        )
        every { notificationRepository.getUnreadCount("test-user-id") } returns flowOf(2)

        viewModel.onIntent(NotificationsContract.Intent.LoadNotifications)
        advanceUntilIdle()
        assertEquals(2, viewModel.state.value.unreadCount)

        coEvery { notificationRepository.markAsRead("n1") } returns RequestState.Success(true)

        viewModel.onIntent(NotificationsContract.Intent.MarkAsRead("n1"))
        // Optimistic update is synchronous
        val state = viewModel.state.value
        assertTrue(state.notifications.first { it.id == "n1" }.isRead)
        assertFalse(state.notifications.first { it.id == "n2" }.isRead)
        assertEquals(1, state.unreadCount)
    }

    @Test
    fun `MarkAsRead reverts on error`() = runTest {
        val notifications = listOf(
            NotificationRepository.Notification(id = "n1", isRead = false),
        )
        every { notificationRepository.getNotifications("test-user-id") } returns flowOf(
            RequestState.Success(notifications),
        )
        every { notificationRepository.getUnreadCount("test-user-id") } returns flowOf(1)

        viewModel.onIntent(NotificationsContract.Intent.LoadNotifications)
        advanceUntilIdle()

        coEvery { notificationRepository.markAsRead("n1") } returns
            RequestState.Error(Exception("Server error"))

        viewModel.effects.test {
            viewModel.onIntent(NotificationsContract.Intent.MarkAsRead("n1"))
            // Optimistic: isRead = true, unreadCount = 0
            assertTrue(viewModel.state.value.notifications.first().isRead)
            assertEquals(0, viewModel.state.value.unreadCount)

            advanceUntilIdle()

            // Reverted after error
            assertFalse(viewModel.state.value.notifications.first().isRead)
            assertEquals(1, viewModel.state.value.unreadCount)

            val effect = awaitItem()
            assertTrue(effect is NotificationsContract.Effect.ShowError)
            assertEquals(
                "Server error",
                (effect as NotificationsContract.Effect.ShowError).message,
            )
        }
    }

    @Test
    fun `MarkAllRead optimistically marks all notifications as read`() = runTest {
        val notifications = listOf(
            NotificationRepository.Notification(id = "n1", isRead = false),
            NotificationRepository.Notification(id = "n2", isRead = false),
            NotificationRepository.Notification(id = "n3", isRead = true),
        )
        every { notificationRepository.getNotifications("test-user-id") } returns flowOf(
            RequestState.Success(notifications),
        )
        every { notificationRepository.getUnreadCount("test-user-id") } returns flowOf(2)

        viewModel.onIntent(NotificationsContract.Intent.LoadNotifications)
        advanceUntilIdle()

        coEvery { notificationRepository.markAllAsRead("test-user-id") } returns
            RequestState.Success(true)

        viewModel.onIntent(NotificationsContract.Intent.MarkAllRead)

        val state = viewModel.state.value
        assertTrue(state.notifications.all { it.isRead })
        assertEquals(0, state.unreadCount)
    }

    @Test
    fun `MarkAllRead reverts on error`() = runTest {
        val notifications = listOf(
            NotificationRepository.Notification(id = "n1", isRead = false),
            NotificationRepository.Notification(id = "n2", isRead = false),
        )
        every { notificationRepository.getNotifications("test-user-id") } returns flowOf(
            RequestState.Success(notifications),
        )
        every { notificationRepository.getUnreadCount("test-user-id") } returns flowOf(2)

        viewModel.onIntent(NotificationsContract.Intent.LoadNotifications)
        advanceUntilIdle()

        coEvery { notificationRepository.markAllAsRead("test-user-id") } returns
            RequestState.Error(Exception("Network error"))

        viewModel.effects.test {
            viewModel.onIntent(NotificationsContract.Intent.MarkAllRead)
            // Optimistic
            assertTrue(viewModel.state.value.notifications.all { it.isRead })
            assertEquals(0, viewModel.state.value.unreadCount)

            advanceUntilIdle()

            // Reverted
            assertEquals(2, viewModel.state.value.notifications.count { !it.isRead })
            assertEquals(2, viewModel.state.value.unreadCount)

            val effect = awaitItem()
            assertTrue(effect is NotificationsContract.Effect.ShowError)
        }
    }
}
