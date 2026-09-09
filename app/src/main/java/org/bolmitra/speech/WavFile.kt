package org.bolmitra.speech

import android.util.Log
import java.io.File
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.ByteOrder
import org.bolmitra.translate.AudioClip

/**
 * Reads and writes 16-bit mono PCM WAV files, for the Recordings list.
 *
 * ### Why WAV and not the raw PCM we already have
 *
 * `AudioClip` is a bare `ShortArray` with no sample rate attached, and the two clips a turn produces
 * do **not** share one: the microphone is fixed at 16 kHz by the recogniser's `FeatureConfig`, while
 * the VITS voice reports its own rate. Storing raw PCM would mean storing the rate somewhere else and
 * keeping the two in step. A 44-byte header that carries the rate next to the samples costs nothing
 * and makes each file independently playable — including by anything other than this app, which
 * matters the first time someone needs to check what a tablet actually said.
 *
 * WAV also matches what the rest of the project already assumes: V48 chose WAV over Opus because you
 * cannot `mmap` a compressed bitstream into `AudioTrack`, and §5.4's size budget was computed for it.
 *
 * ### Scope
 *
 * PCM16 mono only, which is the one audio shape in this domain. No compression, no resampling, no
 * multi-channel — every one of those would be a feature nothing has asked for, and the reader is
 * deliberately strict so a malformed file is a caught failure rather than noise played to a class.
 */
object WavFile {

    private const val TAG = "BolMitra/wav"
    private const val HEADER_BYTES = 44
    private const val PCM16 = 1

    /**
     * Writes [samples] to [file] as PCM16 mono WAV. Returns false on any failure.
     *
     * Never throws: a recording that cannot be saved must not take down the turn that produced it.
     * The translation has already been spoken to the class by this point, so failing to archive it
     * is a lost history row, not a lost lesson.
     */
    fun write(file: File, samples: ShortArray, sampleRate: Int): Boolean {
        if (samples.isEmpty()) {
            Log.w(TAG, "refusing to write an empty clip to ${file.name}")
            return false
        }
        return try {
            file.parentFile?.mkdirs()
            val dataBytes = samples.size * 2
            val buf = ByteBuffer.allocate(HEADER_BYTES + dataBytes).order(ByteOrder.LITTLE_ENDIAN)

            buf.put("RIFF".toByteArray(Charsets.US_ASCII))
            // Everything after this field, i.e. total length minus "RIFF" and minus the field itself.
            buf.putInt(36 + dataBytes)
            buf.put("WAVE".toByteArray(Charsets.US_ASCII))

            buf.put("fmt ".toByteArray(Charsets.US_ASCII))
            buf.putInt(16)                             // PCM fmt chunk length
            buf.putShort(PCM16.toShort())              // audio format: uncompressed PCM
            buf.putShort(1)                            // channels: mono
            buf.putInt(sampleRate)
            buf.putInt(sampleRate * 2)                 // byte rate = rate * channels * bytesPerSample
            buf.putShort(2)                            // block align = channels * bytesPerSample
            buf.putShort(16)                           // bits per sample

            buf.put("data".toByteArray(Charsets.US_ASCII))
            buf.putInt(dataBytes)
            for (s in samples) buf.putShort(s)

            file.writeBytes(buf.array())
            true
        } catch (e: IOException) {
            // IOException, not Throwable: a full disk or a revoked directory is expected on a school
            // tablet, and both are this. A programming error should still surface.
            Log.w(TAG, "could not write ${file.name}: ${e.message}")
            false
        }
    }

    /** Convenience for the synthesised side, which arrives as an [AudioClip]. */
    fun write(file: File, clip: AudioClip, sampleRate: Int): Boolean =
        write(file, clip.pcm16Mono16k, sampleRate)

    /** A file's samples and its declared rate, or null if it is not a PCM16 mono WAV. */
    data class Decoded(val samples: ShortArray, val sampleRate: Int) {
        val durationMs: Long get() = if (sampleRate > 0) samples.size * 1000L / sampleRate else 0L

        // ShortArray in a data class needs these; the generated ones compare references.
        override fun equals(other: Any?): Boolean =
            this === other || (
                other is Decoded &&
                    sampleRate == other.sampleRate &&
                    samples.contentEquals(other.samples)
                )

        override fun hashCode(): Int = 31 * samples.contentHashCode() + sampleRate

        override fun toString(): String = "Decoded(${samples.size} samples, ${sampleRate} Hz)"
    }

    /**
     * Reads a file written by [write].
     *
     * Validates the header rather than trusting it. A truncated file is the normal result of the
     * process being killed mid-write, and playing whatever bytes happened to land there would put
     * noise through a classroom speaker at full media volume.
     */
    fun read(file: File): Decoded? {
        if (!file.isFile || file.length() <= HEADER_BYTES) {
            Log.w(TAG, "${file.name}: missing or too short to hold audio")
            return null
        }
        return try {
            val bytes = file.readBytes()
            val buf = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)

            val riff = ByteArray(4).also { buf.get(it) }.toString(Charsets.US_ASCII)
            buf.int                                      // declared size, not trusted
            val wave = ByteArray(4).also { buf.get(it) }.toString(Charsets.US_ASCII)
            if (riff != "RIFF" || wave != "WAVE") {
                Log.w(TAG, "${file.name}: not a RIFF/WAVE file")
                return null
            }

            var sampleRate = 0
            var channels = 0
            var bits = 0
            var samples: ShortArray? = null

            // Walk the chunks. `fmt ` and `data` are the only two this writer emits, but a file
            // touched by anything else may carry LIST or fact chunks in between, and skipping an
            // unknown chunk is one line against a reader that fails on valid input.
            while (buf.remaining() >= 8) {
                val id = ByteArray(4).also { buf.get(it) }.toString(Charsets.US_ASCII)
                val size = buf.int
                if (size < 0 || size > buf.remaining()) {
                    Log.w(TAG, "${file.name}: chunk '$id' claims $size bytes, ${buf.remaining()} left")
                    break
                }
                when (id) {
                    "fmt " -> {
                        val format = buf.short.toInt()
                        channels = buf.short.toInt()
                        sampleRate = buf.int
                        buf.int                          // byte rate
                        buf.short                        // block align
                        bits = buf.short.toInt()
                        if (size > 16) buf.position(buf.position() + (size - 16))
                        if (format != PCM16) {
                            Log.w(TAG, "${file.name}: compressed WAV (format $format) unsupported")
                            return null
                        }
                    }
                    "data" -> {
                        val out = ShortArray(size / 2)
                        for (i in out.indices) out[i] = buf.short
                        // Odd-sized chunks are padded to an even boundary by the spec.
                        if (size % 2 == 1 && buf.remaining() >= 1) buf.get()
                        samples = out
                    }
                    else -> buf.position(buf.position() + size)
                }
            }

            if (channels != 1 || bits != 16) {
                Log.w(TAG, "${file.name}: expected mono/16-bit, got ${channels}ch/${bits}bit")
                return null
            }
            val data = samples
            if (data == null || data.isEmpty() || sampleRate <= 0) {
                Log.w(TAG, "${file.name}: no usable audio data")
                return null
            }
            Decoded(data, sampleRate)
        } catch (e: Throwable) {
            // Throwable: a corrupt file reaches this as BufferUnderflowException, which is an Error
            // in neither sense but is not an IOException either.
            Log.w(TAG, "could not read ${file.name}: ${e.javaClass.simpleName}")
            null
        }
    }

    /**
     * Self-check for the header arithmetic.
     *
     * Asserts what fails silently: that a file written here reads back with the same samples and the
     * same rate. A wrong byte-rate or block-align field still produces a playable file on some
     * players and a chipmunk voice on others, which no build or screenshot would reveal.
     */
    fun validate(dir: File): List<String> = buildList {
        val f = File(dir, "wav-selfcheck.wav")
        try {
            val original = shortArrayOf(0, 1000, -1000, 32767, -32768, 42)
            if (!write(f, original, 16_000)) {
                add("write failed")
                return@buildList
            }
            if (f.length() != (HEADER_BYTES + original.size * 2).toLong()) {
                add("file is ${f.length()} bytes, expected ${HEADER_BYTES + original.size * 2}")
            }
            val back = read(f)
            if (back == null) {
                add("read returned null for a file we just wrote")
                return@buildList
            }
            if (!back.samples.contentEquals(original)) add("samples did not survive the round trip")
            if (back.sampleRate != 16_000) add("sample rate came back as ${back.sampleRate}")
            // Rate must round-trip independently of the samples, or the two clips a turn produces
            // would play at each other's speed.
            if (write(f, original, 22_050) && read(f)?.sampleRate != 22_050) {
                add("a 22050 Hz file did not read back at 22050 Hz")
            }
        } finally {
            f.delete()
        }
    }
}
