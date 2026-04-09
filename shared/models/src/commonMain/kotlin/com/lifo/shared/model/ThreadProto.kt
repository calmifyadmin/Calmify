package com.lifo.shared.model

import kotlinx.serialization.Serializable
import kotlinx.serialization.protobuf.ProtoNumber

@Serializable
data class ThreadProto(
    @ProtoNumber(1) val threadId: String = "",
    @ProtoNumber(2) val authorId: String = "",
    @ProtoNumber(3) val parentThreadId: String = "",
    @ProtoNumber(4) val text: String = "",
    @ProtoNumber(5) val likeCount: Long = 0,
    @ProtoNumber(6) val replyCount: Long = 0,
    @ProtoNumber(7) val repostCount: Long = 0,
    @ProtoNumber(8) val visibility: String = "public",
    @ProtoNumber(9) val moodTag: String = "",
    @ProtoNumber(10) val isFromJournal: Boolean = false,
    @ProtoNumber(11) val createdAt: Long = 0,
    @ProtoNumber(12) val updatedAt: Long = 0,
    @ProtoNumber(13) val authorDisplayName: String = "",
    @ProtoNumber(14) val authorUsername: String = "",
    @ProtoNumber(15) val authorAvatarUrl: String = "",
    @ProtoNumber(16) val authorIsVerified: Boolean = false,
    @ProtoNumber(17) val mediaUrls: List<String> = emptyList(),
    @ProtoNumber(18) val isLikedByCurrentUser: Boolean = false,
    @ProtoNumber(19) val isRepostedByCurrentUser: Boolean = false,
    @ProtoNumber(20) val replyPreviewAvatars: List<String> = emptyList(),
    @ProtoNumber(21) val viewCount: Long = 0,
    @ProtoNumber(22) val shareCount: Long = 0,
    @ProtoNumber(23) val postCategory: String = "",
)
