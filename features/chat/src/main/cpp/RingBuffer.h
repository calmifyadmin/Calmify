#pragma once

#include <atomic>
#include <cstring>
#include <algorithm>

/**
 * Lock-free Single-Producer Single-Consumer (SPSC) ring buffer.
 *
 * Used for zero-allocation audio data transfer between:
 * - JNI thread (producer) → Oboe callback thread (consumer) for playback
 * - Oboe callback thread (producer) → JNI thread (consumer) for recording
 *
 * Thread safety: One thread writes, one thread reads. No locks needed.
 * Memory ordering: acquire/release semantics on atomic indices.
 */
template<typename T>
class RingBuffer {
public:
    explicit RingBuffer(int32_t capacity)
        : capacity_(capacity)
        , buffer_(new T[capacity])
        , writeIndex_(0)
        , readIndex_(0)
        , overrunCount_(0)
        , underrunCount_(0) {
        std::memset(buffer_, 0, capacity * sizeof(T));
    }

    ~RingBuffer() {
        delete[] buffer_;
    }

    // Non-copyable
    RingBuffer(const RingBuffer&) = delete;
    RingBuffer& operator=(const RingBuffer&) = delete;

    /**
     * Write frames into the ring buffer (producer side).
     * @return Number of frames actually written.
     */
    int32_t write(const T* data, int32_t numFrames) {
        const int32_t available = getAvailableWrite();
        const int32_t toWrite = std::min(numFrames, available);

        if (toWrite < numFrames) {
            overrunCount_.fetch_add(1, std::memory_order_relaxed);
        }

        if (toWrite == 0) return 0;

        const int32_t writePos = writeIndex_.load(std::memory_order_relaxed);
        const int32_t firstPart = std::min(toWrite, capacity_ - writePos);
        const int32_t secondPart = toWrite - firstPart;

        std::memcpy(buffer_ + writePos, data, firstPart * sizeof(T));
        if (secondPart > 0) {
            std::memcpy(buffer_, data + firstPart, secondPart * sizeof(T));
        }

        writeIndex_.store((writePos + toWrite) % capacity_, std::memory_order_release);
        return toWrite;
    }

    /**
     * Read frames from the ring buffer (consumer side).
     * @return Number of frames actually read.
     */
    int32_t read(T* data, int32_t numFrames) {
        const int32_t available = getAvailableRead();
        const int32_t toRead = std::min(numFrames, available);

        if (toRead < numFrames) {
            underrunCount_.fetch_add(1, std::memory_order_relaxed);
        }

        if (toRead == 0) return 0;

        const int32_t readPos = readIndex_.load(std::memory_order_relaxed);
        const int32_t firstPart = std::min(toRead, capacity_ - readPos);
        const int32_t secondPart = toRead - firstPart;

        std::memcpy(data, buffer_ + readPos, firstPart * sizeof(T));
        if (secondPart > 0) {
            std::memcpy(data, buffer_ + readPos + firstPart, secondPart * sizeof(T));
        }

        readIndex_.store((readPos + toRead) % capacity_, std::memory_order_release);
        return toRead;
    }

    /** Available frames to read (consumer perspective). */
    int32_t getAvailableRead() const {
        const int32_t w = writeIndex_.load(std::memory_order_acquire);
        const int32_t r = readIndex_.load(std::memory_order_relaxed);
        return (w - r + capacity_) % capacity_;
    }

    /** Available space to write (producer perspective). */
    int32_t getAvailableWrite() const {
        return capacity_ - 1 - getAvailableRead();  // -1 to distinguish full from empty
    }

    /** Buffer fill level in milliseconds (given sample rate). */
    int32_t getBufferLevelMs(int32_t sampleRate) const {
        if (sampleRate <= 0) return 0;
        return (int32_t)((int64_t)getAvailableRead() * 1000 / sampleRate);
    }

    /** Flush all data. Only safe when both threads are idle. */
    void flush() {
        readIndex_.store(0, std::memory_order_relaxed);
        writeIndex_.store(0, std::memory_order_relaxed);
    }

    int32_t capacity() const { return capacity_; }
    int32_t getOverrunCount() const { return overrunCount_.load(std::memory_order_relaxed); }
    int32_t getUnderrunCount() const { return underrunCount_.load(std::memory_order_relaxed); }

private:
    const int32_t capacity_;
    T* buffer_;
    std::atomic<int32_t> writeIndex_;
    std::atomic<int32_t> readIndex_;
    std::atomic<int32_t> overrunCount_;
    std::atomic<int32_t> underrunCount_;
};
