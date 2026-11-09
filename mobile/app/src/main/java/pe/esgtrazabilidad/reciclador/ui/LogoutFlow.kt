package pe.esgtrazabilidad.reciclador.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.BasicAlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import pe.esgtrazabilidad.reciclador.session.DiscardTicket

/** What the logout dialog is asking, one step at a time. */
sealed interface LogoutDialog {
    /** Pending records exist: three ways out, nothing deleted yet. */
    data class Decision(val count: Int) : LogoutDialog

    /**
     * "Descartar" was chosen: the second confirmation repeats the count and says it cannot be undone. Only this
     * [ticket] (frozen by the first step) can discard.
     */
    data class ConfirmDiscard(val ticket: DiscardTicket) : LogoutDialog

    /** "Sincronizar ahora" did not confirm everything: nothing was closed. */
    data class SyncFailed(val remaining: Int, val reason: String) : LogoutDialog
}

fun pendingLabel(count: Int): String = if (count == 1) "1 registro sin subir" else "$count registros sin subir"

private fun recordsWord(count: Int) = if (count == 1) "registro" else "registros"

@Composable
fun LogoutDialogs(
    dialog: LogoutDialog?,
    busy: Boolean,
    error: String?,
    onSync: () -> Unit,
    onCancel: () -> Unit,
    onAskDiscard: () -> Unit,
    onConfirmDiscard: (DiscardTicket) -> Unit,
    onBackFromDiscard: () -> Unit,
    onDismissFailure: () -> Unit,
) {
    when (dialog) {
        null -> Unit
        is LogoutDialog.Decision -> DialogSheet(onDismiss = { if (!busy) onCancel() }) {
            Text(pendingLabel(dialog.count), style = MaterialTheme.typography.titleLarge, modifier = Modifier.semantics { heading() })
            Text(
                "Todavía no se subieron al servidor. Si cierras la sesión sin subirlos, se pueden perder. ¿Qué quieres hacer?",
                style = MaterialTheme.typography.bodyLarge,
            )
            error?.let { Problem(it) }
            PrimaryButton(if (busy) "Sincronizando…" else "Sincronizar ahora", enabled = !busy, onClick = onSync)
            SecondaryButton("Cancelar", enabled = !busy, onClick = onCancel)
            SecondaryButton("Descartar…", enabled = !busy, onClick = onAskDiscard)
        }
        is LogoutDialog.ConfirmDiscard -> {
            val count = dialog.ticket.count
            DialogSheet(onDismiss = { if (!busy) onBackFromDiscard() }) {
                Text("¿Descartar $count ${recordsWord(count)}?", style = MaterialTheme.typography.titleLarge, modifier = Modifier.semantics { heading() })
                Text(
                    "Vas a borrar ${pendingLabel(count)} de este teléfono. Esto no se puede deshacer.",
                    style = MaterialTheme.typography.bodyLarge,
                )
                error?.let { Problem(it) }
                // The safe way out carries the emphasis; the destructive action is the quiet, outlined one.
                PrimaryButton("No, volver", enabled = !busy, onClick = onBackFromDiscard)
                SecondaryButton(
                    if (busy) "Descartando…" else "Sí, descartar $count ${recordsWord(count)}",
                    enabled = !busy,
                    onClick = { onConfirmDiscard(dialog.ticket) },
                )
            }
        }
        is LogoutDialog.SyncFailed -> DialogSheet(onDismiss = onDismissFailure) {
            Text("No se pudo subir todo", style = MaterialTheme.typography.titleLarge, modifier = Modifier.semantics { heading() })
            Text(
                "Quedan ${pendingLabel(dialog.remaining)}. ${dialog.reason}",
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
            )
            PrimaryButton("Entendido", onClick = onDismissFailure)
        }
    }
}

/** The body of a dialog: a flat paper sheet with a hard border, like the web `sheet-flat`. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DialogSheet(onDismiss: () -> Unit, content: @Composable () -> Unit) {
    BasicAlertDialog(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(Tokens.Paper)
                .border(BorderStroke(2.dp, Tokens.Ink))
                .padding(Tokens.Space4),
            verticalArrangement = Arrangement.spacedBy(Tokens.Space3),
        ) {
            content()
        }
    }
}

/**
 * The session was revoked or the account turned off, and records are still on the phone. Blocking: nothing is
 * discarded automatically. Two exits: ask the staff for a new code (the same account reactivated on this phone
 * uploads them) or discard them after the double confirmation. Errors of those actions show here.
 */
@Composable
fun RevokedScreen(count: Int, error: String?, onNewCode: () -> Unit, onDiscard: () -> Unit) {
    Screen {
        PageTitle("${pendingLabel(count)}; la sesión fue revocada")
        Sheet {
            Text(
                "Tus registros siguen guardados y cifrados en este teléfono. Pide al personal un código nuevo para esta misma cuenta: " +
                    "al activarlo, los registros se suben.",
                style = MaterialTheme.typography.bodyLarge,
            )
            Text("Nada se descarta de forma automática.", style = MaterialTheme.typography.bodySmall)
        }
        error?.let { Problem(it) }
        PrimaryButton("Pedir un código nuevo al personal", onClick = onNewCode)
        SecondaryButton("Descartar…", onClick = onDiscard)
    }
}
