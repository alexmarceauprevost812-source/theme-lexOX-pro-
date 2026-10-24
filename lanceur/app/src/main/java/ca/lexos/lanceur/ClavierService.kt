package ca.lexos.lanceur

import android.content.ClipDescription
import android.inputmethodservice.InputMethodService
import androidx.core.content.FileProvider
import androidx.core.view.inputmethod.EditorInfoCompat
import androidx.core.view.inputmethod.InputConnectionCompat
import androidx.core.view.inputmethod.InputContentInfoCompat
import java.io.File
import android.os.SystemClock
import android.text.InputType
import android.view.KeyEvent
import android.view.View
import android.view.inputmethod.EditorInfo

/**
 * Clavier LexOS PRO — service de saisie Android.
 * L'affichage et les touches sont dans ClavierView ; ici on envoie le texte
 * et les touches spéciales (Ctrl, Esc, Tab, flèches…) à l'appli active.
 */
class ClavierService : InputMethodService() {

    private var vue: ClavierView? = null

    override fun onCreateInputView(): View {
        val v = ClavierView(this)
        v.service = this
        vue = v
        return v
    }

    override fun onStartInputView(info: EditorInfo?, restarting: Boolean) {
        super.onStartInputView(info, restarting)
        val classe = (info?.inputType ?: 0) and InputType.TYPE_MASK_CLASS
        val chiffres = classe == InputType.TYPE_CLASS_NUMBER || classe == InputType.TYPE_CLASS_PHONE
        vue?.preparer(chiffres)
    }

    fun ecrire(texte: String) {
        currentInputConnection?.commitText(texte, 1)
    }

    fun touche(code: Int) {
        sendDownUpKeyEvents(code)
    }

    /** Ctrl + lettre (ex. Ctrl+C dans Termux). */
    fun ctrl(lettre: Char) {
        val ic = currentInputConnection ?: return
        val code = KeyEvent.keyCodeFromString("KEYCODE_" + lettre.uppercaseChar())
        if (code == KeyEvent.KEYCODE_UNKNOWN) { ecrire(lettre.toString()); return }
        val meta = KeyEvent.META_CTRL_ON or KeyEvent.META_CTRL_LEFT_ON
        val t = SystemClock.uptimeMillis()
        ic.sendKeyEvent(KeyEvent(t, t, KeyEvent.ACTION_DOWN, code, 0, meta))
        ic.sendKeyEvent(KeyEvent(t, t, KeyEvent.ACTION_UP, code, 0, meta))
    }

    fun effacer() {
        val ic = currentInputConnection ?: return
        val sel = ic.getSelectedText(0)
        if (sel != null && sel.isNotEmpty()) ic.commitText("", 1) else sendDownUpKeyEvents(KeyEvent.KEYCODE_DEL)
    }

    fun entree() {
        val ic = currentInputConnection ?: return
        val ei = currentInputEditorInfo
        val action = (ei?.imeOptions ?: 0) and EditorInfo.IME_MASK_ACTION
        val sansAction = ((ei?.imeOptions ?: 0) and EditorInfo.IME_FLAG_NO_ENTER_ACTION) != 0
        val multiligne = ((ei?.inputType ?: 0) and InputType.TYPE_TEXT_FLAG_MULTI_LINE) != 0
        if (!sansAction && !multiligne && action != EditorInfo.IME_ACTION_NONE && action != EditorInfo.IME_ACTION_UNSPECIFIED) {
            ic.performEditorAction(action)
        } else {
            sendDownUpKeyEvents(KeyEvent.KEYCODE_ENTER)
        }
    }

    /** Émoji LexOS : envoyé comme image si l'appli l'accepte, sinon l'émoji classique équivalent. */
    fun envoyerEmoji(nom: String) {
        val ic = currentInputConnection ?: return
        val ei = currentInputEditorInfo ?: return
        val types = EditorInfoCompat.getContentMimeTypes(ei)
        val accepteImage = types.any { ClipDescription.compareMimeTypes("image/png", it) }
        if (accepteImage) {
            try {
                val f = File(cacheDir, "emojis/$nom.png")
                if (!f.exists()) {
                    f.parentFile?.mkdirs()
                    assets.open("emojis/$nom.png").use { i -> f.outputStream().use { o -> i.copyTo(o) } }
                }
                val uri = FileProvider.getUriForFile(this, "$packageName.emojis", f)
                val info = InputContentInfoCompat(uri, ClipDescription("LexOS $nom", arrayOf("image/png")), null)
                if (InputConnectionCompat.commitContent(ic, ei, info, InputConnectionCompat.INPUT_CONTENT_GRANT_READ_URI_PERMISSION, null)) return
            } catch (_: Exception) {}
        }
        ecrire(EQUIVALENTS[nom] ?: "🙂")
    }

    private val EQUIVALENTS = mapOf(
        "capuche-pouce" to "👍", "capuche-triste" to "😢", "capuche-rire" to "😆", "capuche-fache" to "😠",
        "capuche-cool" to "😎", "capuche-amour" to "🥰", "capuche-pense" to "🤔", "capuche-clin" to "😉",
        "capuche-dodo" to "😴", "capuche-choc" to "😱", "content" to "😄", "amoureux" to "😍",
        "mort-de-rire" to "😂", "clin-oeil" to "😉", "cool" to "😎", "triste" to "😢", "fache" to "😠",
        "langue" to "😛", "hmm" to "🤔", "wow" to "😮"
    )

    fun changerClavier() {
        val imm = getSystemService(INPUT_METHOD_SERVICE) as android.view.inputmethod.InputMethodManager
        imm.showInputMethodPicker()
    }
}
