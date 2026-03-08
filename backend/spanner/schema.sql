-- ============================================================
-- Calmify Nexus — Spanner Graph Schema
-- Social Graph + Thread Content + Interactions
-- ============================================================
-- Deploy: gcloud spanner databases ddl update calmify-nexus \
--         --instance=calmify-prod \
--         --ddl-file=schema.sql
-- ============================================================

-- ===================
-- NODE TABLES
-- ===================

CREATE TABLE Users (
    userId       STRING(36) NOT NULL,
    displayName  STRING(255),
    avatarUrl    STRING(1024),
    bio          STRING(500),
    createdAt    TIMESTAMP NOT NULL OPTIONS (allow_commit_timestamp=true),
    lastActive   TIMESTAMP,
    isVerified   BOOL NOT NULL DEFAULT (false),
    followerCount  INT64 NOT NULL DEFAULT (0),
    followingCount INT64 NOT NULL DEFAULT (0),
    threadCount    INT64 NOT NULL DEFAULT (0),
) PRIMARY KEY(userId);

CREATE TABLE Threads (
    threadId       STRING(36) NOT NULL,
    authorId       STRING(36) NOT NULL,
    parentThreadId STRING(36),
    text           STRING(10000) NOT NULL,
    embedding      ARRAY<FLOAT64>,
    likeCount      INT64 NOT NULL DEFAULT (0),
    replyCount     INT64 NOT NULL DEFAULT (0),
    visibility     STRING(20) NOT NULL DEFAULT ('public'),
    moodTag        STRING(50),
    isFromJournal  BOOL NOT NULL DEFAULT (false),
    createdAt      TIMESTAMP NOT NULL OPTIONS (allow_commit_timestamp=true),
    updatedAt      TIMESTAMP,
    CONSTRAINT FK_Thread_Author FOREIGN KEY (authorId) REFERENCES Users(userId),
) PRIMARY KEY(threadId);

-- Index for feed queries: threads by author, newest first
CREATE INDEX ThreadsByAuthor ON Threads(authorId, createdAt DESC);

-- Index for reply threads
CREATE INDEX ThreadsByParent ON Threads(parentThreadId, createdAt ASC)
    WHERE parentThreadId IS NOT NULL;

-- ===================
-- EDGE TABLES (Relationships)
-- ===================

CREATE TABLE Follows (
    followerId STRING(36) NOT NULL,
    followeeId STRING(36) NOT NULL,
    createdAt  TIMESTAMP NOT NULL OPTIONS (allow_commit_timestamp=true),
    CONSTRAINT FK_Follower FOREIGN KEY (followerId) REFERENCES Users(userId),
    CONSTRAINT FK_Followee FOREIGN KEY (followeeId) REFERENCES Users(userId),
) PRIMARY KEY(followerId, followeeId);

-- Index for "who follows me" queries
CREATE INDEX FollowsByFollowee ON Follows(followeeId, createdAt DESC);

CREATE TABLE Likes (
    userId    STRING(36) NOT NULL,
    threadId  STRING(36) NOT NULL,
    createdAt TIMESTAMP NOT NULL OPTIONS (allow_commit_timestamp=true),
    CONSTRAINT FK_Like_User FOREIGN KEY (userId) REFERENCES Users(userId),
    CONSTRAINT FK_Like_Thread FOREIGN KEY (threadId) REFERENCES Threads(threadId),
) PRIMARY KEY(userId, threadId);

-- Index for "who liked this thread" queries
CREATE INDEX LikesByThread ON Likes(threadId, createdAt DESC);

CREATE TABLE Blocks (
    blockerId STRING(36) NOT NULL,
    blockedId STRING(36) NOT NULL,
    createdAt TIMESTAMP NOT NULL OPTIONS (allow_commit_timestamp=true),
    CONSTRAINT FK_Blocker FOREIGN KEY (blockerId) REFERENCES Users(userId),
    CONSTRAINT FK_Blocked FOREIGN KEY (blockedId) REFERENCES Users(userId),
) PRIMARY KEY(blockerId, blockedId);

-- ===================
-- GRAPH DEFINITION (GQL)
-- ===================

CREATE PROPERTY GRAPH SocialGraph
    NODE TABLES (Users, Threads)
    EDGE TABLES (
        Follows
            SOURCE KEY (followerId) REFERENCES Users
            DESTINATION KEY (followeeId) REFERENCES Users,
        Likes
            SOURCE KEY (userId) REFERENCES Users
            DESTINATION KEY (threadId) REFERENCES Threads,
        Blocks
            SOURCE KEY (blockerId) REFERENCES Users
            DESTINATION KEY (blockedId) REFERENCES Users
    );

-- ===================
-- EXAMPLE GQL QUERIES
-- ===================

-- Feed: threads from people I follow (1st degree)
-- GRAPH SocialGraph
-- MATCH (me:Users {userId: @currentUserId})
--       -[:Follows]->(following:Users)
-- LET threads = (
--     SELECT t.threadId, t.text, t.likeCount, t.createdAt
--     FROM Threads t
--     WHERE t.authorId = following.userId
--       AND t.createdAt > TIMESTAMP_SUB(CURRENT_TIMESTAMP(), INTERVAL 7 DAY)
--       AND t.visibility = 'public'
-- )
-- RETURN threads
-- ORDER BY threads.createdAt DESC
-- LIMIT 50

-- Suggestions: friends of friends I don't follow yet
-- GRAPH SocialGraph
-- MATCH (me:Users {userId: @currentUserId})
--       -[:Follows]->(:Users)
--       -[:Follows]->(suggestion:Users)
-- WHERE NOT EXISTS {
--     MATCH (me)-[:Follows]->(suggestion)
-- }
-- AND suggestion.userId != @currentUserId
-- AND NOT EXISTS {
--     MATCH (me)-[:Blocks]->(suggestion)
-- }
-- RETURN DISTINCT suggestion.userId, suggestion.displayName, suggestion.avatarUrl
-- LIMIT 20
