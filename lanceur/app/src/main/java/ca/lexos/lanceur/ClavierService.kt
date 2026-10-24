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
        "a01-gamer" to "🎮",
        "a02-lexos-pouce" to "👍",
        "c01-capuche-pouce" to "👍",
        "c02-capuche-triste" to "😢",
        "c03-capuche-rire" to "😆",
        "c04-capuche-fache" to "😠",
        "c05-capuche-cool" to "😎",
        "c06-capuche-amour" to "🥰",
        "c07-capuche-pense" to "🤔",
        "c08-capuche-clin" to "😉",
        "c09-capuche-dodo" to "😴",
        "c10-capuche-choc" to "😱",
        "f01-content" to "😄",
        "f02-amoureux" to "😍",
        "f03-mort-de-rire" to "😂",
        "f04-clin-oeil" to "😉",
        "f05-cool" to "😎",
        "f06-triste" to "😢",
        "f07-fache" to "😠",
        "f08-langue" to "😛",
        "f09-hmm" to "🤔",
        "f10-wow" to "😮",
        "p01-sourire" to "😊",
        "p02-mdr" to "😂",
        "p03-coeur-yeux" to "😍",
        "p04-clin" to "😉",
        "p05-langue" to "😋",
        "p06-lunettes" to "😎",
        "p07-dents" to "😁",
        "p08-sueur" to "😅",
        "p09-soulage" to "😌",
        "p10-ange" to "😇",
        "p11-reflechit" to "🤔",
        "p12-mefiant" to "🤨",
        "p13-neutre" to "😐",
        "p14-inquiet" to "😥",
        "p15-pleure" to "😭",
        "p16-colere" to "😡",
        "p17-jure" to "🤬",
        "p18-bisou" to "😘",
        "p19-calin-coeurs" to "🥰",
        "p20-etoiles-yeux" to "🤩",
        "p21-etourdi" to "😵‍💫",
        "p22-bouche-cousue" to "🤐",
        "p23-oups" to "🤭",
        "p24-surpris" to "😲",
        "p25-eclate" to "😆",
        "p26-plisse-langue" to "😝",
        "p27-fou" to "🤪",
        "p28-dodo" to "😴",
        "p29-serein" to "😌",
        "p30-fete" to "🥳",
        "p31-pouce-haut" to "👍",
        "p32-pouce-bas" to "👎",
        "p33-ok" to "👌",
        "p34-paix" to "✌️",
        "p35-doigts-croises" to "🤞",
        "p36-main" to "✋",
        "p37-poing" to "✊",
        "p38-montre" to "👉",
        "p39-rock" to "🤘",
        "p40-merci" to "🙏",
        "p41-coeur" to "❤️",
        "p42-coeur-brise" to "💔",
        "p43-coeur-feu" to "❤️‍🔥",
        "p44-coeurs" to "💕",
        "p45-deux-coeurs" to "💞",
        "p46-etoile" to "⭐",
        "p47-etoile-brille" to "🌟",
        "p48-feu" to "🔥",
        "p49-cent" to "💯",
        "p50-idee" to "💡",
        "p51-couronne" to "👑",
        "p52-trophee" to "🏆",
        "p53-cadeau" to "🎁",
        "p54-gateau" to "🎂",
        "p55-sapin" to "🎄",
        "p56-soleil" to "☀️",
        "p57-lune" to "🌙",
        "p58-nuage" to "☁️",
        "p59-pluie" to "🌧️",
        "p60-flocon" to "❄️"
    )

    fun changerClavier() {
        val imm = getSystemService(INPUT_METHOD_SERVICE) as android.view.inputmethod.InputMethodManager
        imm.showInputMethodPicker()
    }
}
