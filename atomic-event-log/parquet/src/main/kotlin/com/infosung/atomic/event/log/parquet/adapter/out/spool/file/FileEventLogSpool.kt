package com.infosung.atomic.event.log.parquet.adapter.out.spool.file

import com.infosung.atomic.event.log.application.model.EventLogRecord
import com.infosung.atomic.event.log.parquet.application.model.EventLogSpoolSyncMode
import com.infosung.atomic.event.log.parquet.application.model.EventLogSpoolWritePolicy
import com.infosung.atomic.event.log.parquet.application.model.deduplicationKey
import com.infosung.atomic.event.log.parquet.application.port.out.EventLogSpool
import com.infosung.atomic.event.log.parquet.application.port.out.EventLogSpoolAppendReceipt
import com.infosung.atomic.event.log.parquet.application.port.out.EventLogSpoolCheckpoint
import com.infosung.atomic.event.log.parquet.application.port.out.EventLogSpoolEntry
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.EOFException
import java.io.FileOutputStream
import java.io.ObjectInputStream
import java.io.ObjectOutputStream
import java.nio.channels.FileChannel
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardOpenOption
import java.time.Instant

/** File-backed spool for crash-safe local persistence. */
class FileEventLogSpool(
    private val directory: Path,
    private val writePolicy: EventLogSpoolWritePolicy = EventLogSpoolWritePolicy(),
) : EventLogSpool {
  private val log = System.getLogger(FileEventLogSpool::class.java.name)
  private val entriesFile = directory.resolve("event-log-spool.bin")
  private val checkpointFile = directory.resolve("event-log-spool.checkpoint")
  private val entries = mutableListOf<EventLogSpoolEntry>()
  private val dedupeIndex = linkedMapOf<String, Long>()
  private var nextSequence = 1L
  private var committedSequence = 0L

  init {
    Files.createDirectories(directory)
    loadState()
  }

  override fun append(records: List<EventLogRecord>): EventLogSpoolAppendReceipt =
      synchronized(this) {
        if (records.isEmpty()) {
          return EventLogSpoolAppendReceipt(
              startSequence = committedSequence,
              endSequence = committedSequence,
              count = 0,
          )
        }
        val startSequence = nextSequence
        val appendedAt = Instant.now()
        val newEntries =
            records.map { record ->
              EventLogSpoolEntry(
                  sequence = nextSequence++, record = record, appendedAt = appendedAt)
            }
        persistEntries(newEntries)
        entries += newEntries
        newEntries.forEach { dedupeIndex[it.record.deduplicationKey()] = it.sequence }
        val receipt =
            EventLogSpoolAppendReceipt(
                startSequence = startSequence,
                endSequence = nextSequence - 1,
                count = records.size,
            )
        log.log(
            System.Logger.Level.DEBUG,
            "File spool append finished: count={0}, startSequence={1}, endSequence={2}, path={3}, syncMode={4}",
            receipt.count,
            receipt.startSequence,
            receipt.endSequence,
            entriesFile.toString(),
            writePolicy.syncMode.name,
        )
        receipt
      }

  override fun pending(limit: Int): List<EventLogSpoolEntry> =
      synchronized(this) {
        entries.asSequence().filter { it.sequence > committedSequence }.take(limit).toList()
      }

  override fun markCommittedThrough(sequenceInclusive: Long) {
    synchronized(this) {
      require(sequenceInclusive >= committedSequence) {
        "sequenceInclusive must not move backwards. committedSequence=$committedSequence, requested=$sequenceInclusive"
      }
      committedSequence = sequenceInclusive
      syncEntriesIfNeededForCheckpoint()
      persistCheckpoint()
      log.log(
          System.Logger.Level.DEBUG,
          "File spool checkpoint advanced: committedSequence={0}, path={1}, syncMode={2}",
          committedSequence,
          checkpointFile.toString(),
          writePolicy.syncMode.name,
      )
    }
  }

  override fun checkpoint(): EventLogSpoolCheckpoint =
      synchronized(this) { EventLogSpoolCheckpoint(committedSequence = committedSequence) }

  override fun knownDeduplicationIndex(): Map<String, Long> =
      synchronized(this) { dedupeIndex.toMap() }

  private fun loadState() {
    synchronized(this) {
      entries.clear()
      dedupeIndex.clear()
      committedSequence = loadCheckpoint()
      if (Files.exists(entriesFile)) {
        DataInputStream(BufferedInputStream(Files.newInputStream(entriesFile))).use { input ->
          while (true) {
            try {
              val length = input.readInt()
              if (length <= 0) {
                logCorruptedTail("non-positive entry length=$length")
                break
              }
              val bytes = input.readNBytes(length)
              if (bytes.size != length) {
                logCorruptedTail("truncated entry expected=$length actual=${bytes.size}")
                break
              }
              val entry = deserialize(bytes)
              entries += entry
              dedupeIndex[entry.record.deduplicationKey()] = entry.sequence
            } catch (_: EOFException) {
              break
            } catch (exception: Exception) {
              logCorruptedTail(exception.message ?: exception::class.java.simpleName)
              break
            }
          }
        }
      }
      nextSequence = (entries.maxOfOrNull(EventLogSpoolEntry::sequence) ?: 0L) + 1L
    }
  }

  private fun loadCheckpoint(): Long =
      if (Files.exists(checkpointFile)) {
        Files.readString(checkpointFile).trim().ifBlank { "0" }.toLong()
      } else {
        0L
      }

  private fun persistEntries(newEntries: List<EventLogSpoolEntry>) {
    FileOutputStream(entriesFile.toFile(), true).use { fileOutput ->
      DataOutputStream(BufferedOutputStream(fileOutput)).use { output ->
        newEntries.forEach { entry ->
          val bytes = serialize(entry)
          output.writeInt(bytes.size)
          output.write(bytes)
        }
        output.flush()
        if (writePolicy.syncMode == EventLogSpoolSyncMode.SYNC_ON_APPEND) {
          fileOutput.fd.sync()
        }
      }
    }
  }

  private fun persistCheckpoint() {
    FileOutputStream(checkpointFile.toFile(), false).use { output ->
      output.write(committedSequence.toString().toByteArray(StandardCharsets.UTF_8))
      output.flush()
      output.fd.sync()
    }
  }

  private fun syncEntriesIfNeededForCheckpoint() {
    if (writePolicy.syncMode != EventLogSpoolSyncMode.SYNC_ON_CHECKPOINT ||
        !Files.exists(entriesFile)) {
      return
    }
    FileChannel.open(entriesFile, StandardOpenOption.WRITE).use { channel -> channel.force(true) }
  }

  private fun logCorruptedTail(reason: String) {
    log.log(
        System.Logger.Level.WARNING,
        "File spool recovery stopped at corrupted tail: path={0}, reason={1}",
        entriesFile.toString(),
        reason,
    )
  }

  private fun serialize(entry: EventLogSpoolEntry): ByteArray =
      ByteArrayOutputStream().use { buffer ->
        ObjectOutputStream(buffer).use { it.writeObject(entry) }
        buffer.toByteArray()
      }

  private fun deserialize(bytes: ByteArray): EventLogSpoolEntry =
      ObjectInputStream(ByteArrayInputStream(bytes)).use { input ->
        input.readObject() as EventLogSpoolEntry
      }
}
