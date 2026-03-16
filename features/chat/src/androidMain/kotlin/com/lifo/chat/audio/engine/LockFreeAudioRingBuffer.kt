package com.lifo.chat.audio.engine


import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong

/**
 * Lock-Free Single Producer Single Consumer (SPSC) Ring Buffer
 *
 * Architettura AAA per audio real-time:
 * - Zero allocazioni durante operazioni read/write
 * - Thread-safe senza lock (memoria atomica)
 * - Ottimizzato per single producer (WebSocket) / single consumer (AudioTrack)
 * - Memory barriers impliciti tramite AtomicInteger
 *
 * Design basato su:
 * - boost::lockfree::spsc_queue
 * - Cinder audio::dsp::RingBuffer
 * - GStreamer AudioRingBuffer
 *
 * @param capacityBytes Capacità totale in bytes (default: 2 secondi @ 24kHz 16-bit mono)
 *
 * @author Jarvis AI Assistant - AAA Audio Engine
 */
class LockFreeAudioRingBuffer(
    private val capacityBytes: Int = DEFAULT_CAPACITY_BYTES
) {
    companion object {
        // Default: 2 secondi di audio @ 24kHz, 16-bit mono
        // 24000 samples/sec * 2 bytes/sample * 2 seconds = 96000 bytes
        const val DEFAULT_CAPACITY_BYTES = 96000

        // Soglie per monitoring
        const val LOW_WATER_MARK_PERCENT = 25
        const val HIGH_WATER_MARK_PERCENT = 75
        const val CRITICAL_LOW_PERCENT = 10
    }

    // Buffer principale - allocato una sola volta
    private val buffer = ByteArray(capacityBytes)

    // Indici atomici per lock-free operation
    // writeIndex: posizione dove il producer scriverà il prossimo byte
    // readIndex: posizione dove il consumer leggerà il prossimo byte
    private val writeIndex = AtomicInteger(0)
    private val readIndex = AtomicInteger(0)

    // Statistiche per monitoring (atomiche per thread-safety)
    private val totalBytesWritten = AtomicLong(0)
    private val totalBytesRead = AtomicLong(0)
    private val overrunCount = AtomicInteger(0)
    private val underrunCount = AtomicInteger(0)

    // Timestamp ultimo accesso per rilevamento starvation
    @Volatile
    private var lastWriteTimestamp = 0L
    @Volatile
    private var lastReadTimestamp = 0L

    /**
     * Scrive dati nel buffer (chiamato dal Producer thread)
     *
     * @param data Array sorgente
     * @param offset Offset nell'array sorgente
     * @param length Numero di bytes da scrivere
     * @return Numero di bytes effettivamente scritti (può essere < length se buffer pieno)
     */
    fun write(data: ByteArray, offset: Int = 0, length: Int = data.size): Int {
        if (length <= 0) return 0

        val currentWrite = writeIndex.get()
        val currentRead = readIndex.get()

        // Calcola spazio disponibile per scrittura
        val available = availableToWriteInternal(currentWrite, currentRead)

        if (available == 0) {
            overrunCount.incrementAndGet()
            println("[LockFreeRingBuffer] WARNING: Buffer OVERRUN - buffer pieno, dropping data")
            return 0
        }

        // Scrivi solo quello che c'è spazio
        val toWrite = minOf(length, available)

        // Gestione wrap-around del buffer circolare
        val writePos = currentWrite % capacityBytes
        val contiguousSpace = capacityBytes - writePos

        if (toWrite <= contiguousSpace) {
            // Scrittura contigua (caso comune)
            System.arraycopy(data, offset, buffer, writePos, toWrite)
        } else {
            // Scrittura con wrap-around
            System.arraycopy(data, offset, buffer, writePos, contiguousSpace)
            System.arraycopy(data, offset + contiguousSpace, buffer, 0, toWrite - contiguousSpace)
        }

        // Aggiorna write index atomicamente (memory barrier implicito)
        writeIndex.set(currentWrite + toWrite)

        // Statistiche
        totalBytesWritten.addAndGet(toWrite.toLong())
        lastWriteTimestamp = System.nanoTime()

        return toWrite
    }

    /**
     * Legge dati dal buffer (chiamato dal Consumer thread)
     *
     * @param output Array destinazione
     * @param offset Offset nell'array destinazione
     * @param length Numero di bytes da leggere
     * @return Numero di bytes effettivamente letti (può essere < length se buffer vuoto)
     */
    fun read(output: ByteArray, offset: Int = 0, length: Int = output.size): Int {
        if (length <= 0) return 0

        val currentWrite = writeIndex.get()
        val currentRead = readIndex.get()

        // Calcola dati disponibili per lettura
        val available = availableToReadInternal(currentWrite, currentRead)

        if (available == 0) {
            underrunCount.incrementAndGet()
            return 0 // Non loggare qui - troppo frequente durante buffering iniziale
        }

        // Leggi solo quello che c'è disponibile
        val toRead = minOf(length, available)

        // Gestione wrap-around del buffer circolare
        val readPos = currentRead % capacityBytes
        val contiguousData = capacityBytes - readPos

        if (toRead <= contiguousData) {
            // Lettura contigua (caso comune)
            System.arraycopy(buffer, readPos, output, offset, toRead)
        } else {
            // Lettura con wrap-around
            System.arraycopy(buffer, readPos, output, offset, contiguousData)
            System.arraycopy(buffer, 0, output, offset + contiguousData, toRead - contiguousData)
        }

        // Aggiorna read index atomicamente (memory barrier implicito)
        readIndex.set(currentRead + toRead)

        // Statistiche
        totalBytesRead.addAndGet(toRead.toLong())
        lastReadTimestamp = System.nanoTime()

        return toRead
    }

    /**
     * Bytes disponibili per lettura
     */
    fun availableToRead(): Int {
        return availableToReadInternal(writeIndex.get(), readIndex.get())
    }

    /**
     * Bytes disponibili per scrittura
     */
    fun availableToWrite(): Int {
        return availableToWriteInternal(writeIndex.get(), readIndex.get())
    }

    /**
     * Livello buffer in percentuale (0-100)
     */
    fun bufferLevelPercent(): Int {
        val available = availableToRead()
        return (available * 100) / capacityBytes
    }

    /**
     * Livello buffer in millisecondi (assumendo 24kHz 16-bit mono)
     */
    fun bufferLevelMs(sampleRate: Int = 24000, bytesPerSample: Int = 2): Float {
        val available = availableToRead()
        val samples = available / bytesPerSample
        return (samples * 1000f) / sampleRate
    }

    /**
     * Verifica se il buffer è in stato critico (quasi vuoto)
     */
    fun isCriticallyLow(): Boolean {
        return bufferLevelPercent() < CRITICAL_LOW_PERCENT
    }

    /**
     * Verifica se il buffer è sotto il livello minimo
     */
    fun isLow(): Boolean {
        return bufferLevelPercent() < LOW_WATER_MARK_PERCENT
    }

    /**
     * Verifica se il buffer è sopra il livello alto
     */
    fun isHigh(): Boolean {
        return bufferLevelPercent() > HIGH_WATER_MARK_PERCENT
    }

    /**
     * Verifica se il buffer è vuoto
     */
    fun isEmpty(): Boolean {
        return availableToRead() == 0
    }

    /**
     * Verifica se il buffer è pieno
     */
    fun isFull(): Boolean {
        return availableToWrite() == 0
    }

    /**
     * Reset completo del buffer
     * ATTENZIONE: Chiamare solo quando producer e consumer sono fermi!
     */
    fun reset() {
        writeIndex.set(0)
        readIndex.set(0)
        // Non azzeriamo il buffer per performance - sarà sovrascritto
        println("[LockFreeRingBuffer] Buffer reset")
    }

    /**
     * Svuota il buffer scartando tutti i dati
     * Thread-safe: può essere chiamato mentre producer scrive
     */
    fun flush() {
        val currentWrite = writeIndex.get()
        readIndex.set(currentWrite)
        println("[LockFreeRingBuffer] Buffer flushed")
    }

    /**
     * Salta N bytes di dati (per sincronizzazione)
     */
    fun skip(bytes: Int): Int {
        val available = availableToRead()
        val toSkip = minOf(bytes, available)

        if (toSkip > 0) {
            val currentRead = readIndex.get()
            readIndex.set(currentRead + toSkip)
            totalBytesRead.addAndGet(toSkip.toLong())
        }

        return toSkip
    }

    /**
     * Ottiene statistiche del buffer
     */
    fun getStatistics(): BufferStatistics {
        return BufferStatistics(
            capacityBytes = capacityBytes,
            availableToRead = availableToRead(),
            availableToWrite = availableToWrite(),
            bufferLevelPercent = bufferLevelPercent(),
            totalBytesWritten = totalBytesWritten.get(),
            totalBytesRead = totalBytesRead.get(),
            overrunCount = overrunCount.get(),
            underrunCount = underrunCount.get(),
            lastWriteTimestamp = lastWriteTimestamp,
            lastReadTimestamp = lastReadTimestamp
        )
    }

    /**
     * Reset contatori statistiche
     */
    fun resetStatistics() {
        totalBytesWritten.set(0)
        totalBytesRead.set(0)
        overrunCount.set(0)
        underrunCount.set(0)
    }

    // ==================== Internal Methods ====================

    private fun availableToReadInternal(write: Int, read: Int): Int {
        return write - read
    }

    private fun availableToWriteInternal(write: Int, read: Int): Int {
        // Lasciamo sempre 1 byte libero per distinguere full da empty
        return capacityBytes - (write - read) - 1
    }

    /**
     * Data class per statistiche buffer
     */
    data class BufferStatistics(
        val capacityBytes: Int,
        val availableToRead: Int,
        val availableToWrite: Int,
        val bufferLevelPercent: Int,
        val totalBytesWritten: Long,
        val totalBytesRead: Long,
        val overrunCount: Int,
        val underrunCount: Int,
        val lastWriteTimestamp: Long,
        val lastReadTimestamp: Long
    ) {
        val utilizationPercent: Int
            get() = (availableToRead * 100) / capacityBytes

        val throughputBytesPerSecond: Long
            get() {
                val elapsed = lastReadTimestamp - lastWriteTimestamp
                return if (elapsed > 0) {
                    (totalBytesRead * 1_000_000_000L) / elapsed
                } else 0
            }
    }
}
