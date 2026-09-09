package org.bolmitra.curriculum

import android.graphics.Bitmap
import android.graphics.Color
import android.util.Log
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel

/**
 * Turns a worksheet into a QR code a parent can photograph, entirely on device.
 *
 * ### Why there is no URL in here
 *
 * The obvious QR payload is a link, and that is exactly what this must not be. The landing screen
 * promises nothing leaves the tablet, and a QR pointing at a server would move the promise rather
 * than keep it — the data would leave as soon as anyone scanned it. So the payload is the worksheet
 * **content itself**, as plain text. Scanning it with any camera app shows the items; no app to
 * install, no network, nothing to host.
 *
 * ### The size ceiling is real and is enforced here
 *
 * QR version 40 at error-correction level L tops out near 2,900 bytes, and Devanagari is three bytes
 * per character in UTF-8 — so roughly 950 characters of Hindi, less once a target script is
 * interleaved. Past that `QRCodeWriter` throws. [encode] returns null instead of propagating, and
 * [payloadFor] truncates on an item boundary so a scanned sheet is never half an item.
 *
 * `ponytail:` Ceiling noted — plain text in a single QR is the laziest thing that genuinely works
 * for a one-page sheet. A multi-page worksheet needs either several QR codes or a file handed over
 * by Wi-Fi Direct, which is the documented pack-transfer route and a much larger job.
 */
object QrCode {

    private const val TAG = "BolMitra/qr"

    /** Comfortably inside version 40 / level L once UTF-8 expansion is accounted for. */
    const val MAX_PAYLOAD_CHARS = 800

    /**
     * Builds the scannable text for a worksheet.
     *
     * Truncates whole items rather than characters, and says so in the payload when it does, so a
     * parent who scans a shortened sheet can tell it is shortened.
     */
    fun payloadFor(title: String, items: List<GeneratedItem>): String {
        val header = "BolMitra — $title\n"
        val body = StringBuilder(header)
        var included = 0

        for ((index, item) in items.withIndex()) {
            val line = "${index + 1}. ${item.hiText}\n"
            if (body.length + line.length > MAX_PAYLOAD_CHARS - 40) break
            body.append(line)
            included++
        }
        if (included < items.size) {
            body.append("… ${items.size - included} more item(s) on the printed sheet\n")
        }
        return body.toString()
    }

    /**
     * Encodes [text] as a square QR bitmap, or null if it could not be encoded.
     *
     * Null rather than throwing: this is reached from a button on a screen in front of a class, and
     * "no QR appeared, here is why" is a working app where a propagated `WriterException` is not.
     */
    fun encode(text: String, sizePx: Int = 512): Bitmap? {
        if (text.isBlank()) {
            Log.w(TAG, "refusing to encode blank payload")
            return null
        }
        return try {
            val hints = mapOf(
                // UTF-8 explicitly: the default is ISO-8859-1, which cannot represent a single
                // Devanagari or Ol Chiki character and would silently mangle the whole payload.
                EncodeHintType.CHARACTER_SET to "UTF-8",
                // L, the lowest correction level, buys the most capacity. A worksheet QR is scanned
                // from a clean screen at arm's length, not read off a scuffed carton.
                EncodeHintType.ERROR_CORRECTION to ErrorCorrectionLevel.L,
                EncodeHintType.MARGIN to 1,
            )
            val matrix = QRCodeWriter().encode(text, BarcodeFormat.QR_CODE, sizePx, sizePx, hints)

            val w = matrix.width
            val h = matrix.height
            val pixels = IntArray(w * h)
            for (y in 0 until h) {
                val row = y * w
                for (x in 0 until w) {
                    pixels[row + x] = if (matrix[x, y]) Color.BLACK else Color.WHITE
                }
            }
            // ARGB_8888 rather than RGB_565: a QR is pure black and white, and 565's green bias
            // puts a tint on the modules that some scanners threshold badly.
            Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888).also {
                it.setPixels(pixels, 0, w, 0, 0, w, h)
            }
        } catch (t: Throwable) {
            // Throwable: WriterException for over-capacity input, but also OutOfMemoryError on a
            // FLOOR-tier tablet if sizePx is large, and neither may take the process down.
            Log.w(TAG, "QR encode failed for ${text.length} chars", t)
            null
        }
    }
}
