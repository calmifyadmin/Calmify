package com.lifo.write

import com.lifo.util.mvi.MviContract

object InspirationContract {

    /** Curated daily quotes from contemplative traditions, philosophy, literature. */
    val dailyQuotes = listOf(
        Quote("La vita non esaminata non e' degna di essere vissuta.", "Socrate"),
        Quote("Sii il cambiamento che vuoi vedere nel mondo.", "Mahatma Gandhi"),
        Quote("Il viaggio di mille miglia inizia con un singolo passo.", "Lao Tzu"),
        Quote("Conosci te stesso.", "Oracolo di Delfi"),
        Quote("La felicita' non e' qualcosa di gia' fatto. Viene dalle tue azioni.", "Dalai Lama"),
        Quote("Nel mezzo della difficolta' giace l'opportunita'.", "Albert Einstein"),
        Quote("Non e' perche' le cose sono difficili che non osiamo; e' perche' non osiamo che sono difficili.", "Seneca"),
        Quote("La bellezza salvera' il mondo.", "Fedor Dostoevskij"),
        Quote("Tutto cio' che sei e' il risultato di cio' che hai pensato.", "Buddha"),
        Quote("L'unico vero viaggio di scoperta consiste non nel cercare nuove terre, ma nell'avere nuovi occhi.", "Marcel Proust"),
        Quote("Chi guarda fuori sogna. Chi guarda dentro si sveglia.", "Carl Jung"),
        Quote("La semplicita' e' la sofisticatezza suprema.", "Leonardo da Vinci"),
        Quote("Dove c'e' amore c'e' vita.", "Mahatma Gandhi"),
        Quote("La vera misura di un uomo non e' come si comporta in momenti di comfort e convenienza, ma come si comporta in momenti di sfida e controversia.", "Martin Luther King Jr."),
        Quote("Fai della tua vita un capolavoro.", "Michelangelo"),
        Quote("Il silenzio e' un recinto intorno alla saggezza.", "Proverbio tedesco"),
        Quote("Non siamo esseri umani che vivono un'esperienza spirituale. Siamo esseri spirituali che vivono un'esperienza umana.", "Teilhard de Chardin"),
        Quote("La gratitudine trasforma cio' che abbiamo in abbastanza.", "Anonimo"),
        Quote("Ogni giorno e' un buon giorno.", "Yunmen Wenyan"),
        Quote("Il momento presente e' l'unico momento disponibile per vivere.", "Thich Nhat Hanh"),
        Quote("La pace viene da dentro. Non cercarla fuori.", "Buddha"),
        Quote("Ama e fa' cio' che vuoi.", "Sant'Agostino"),
        Quote("La creativita' richiede il coraggio di abbandonare le certezze.", "Erich Fromm"),
        Quote("Chi ha un perche' per vivere puo' sopportare quasi ogni come.", "Friedrich Nietzsche"),
        Quote("L'anima diventa tinta del colore dei suoi pensieri.", "Marco Aurelio"),
        Quote("Il segreto della salute sia del corpo che della mente non e' piangere per il passato, preoccuparsi del futuro, ma vivere il presente saggiamente.", "Buddha"),
        Quote("Nessun uomo e' un'isola.", "John Donne"),
        Quote("La vita e' quello che ti succede mentre sei impegnato a fare altri piani.", "John Lennon"),
        Quote("Non e' la specie piu' forte a sopravvivere, ma quella piu' reattiva ai cambiamenti.", "Charles Darwin"),
        Quote("Sii gentile, perche' ogni persona che incontri sta combattendo una dura battaglia.", "Platone"),
        Quote("La verita' e' come un leone; non devi difenderla. Lasciala libera; si difendera' da sola.", "Sant'Agostino"),
    )

    data class Quote(val text: String, val author: String)

    sealed interface Intent : MviContract.Intent {
        data object Load : Intent
        data class UpdateBeautyNote(val text: String) : Intent
        data object SaveBeautyNote : Intent
    }

    data class State(
        val todayQuote: Quote = dailyQuotes.first(),
        val beautyNote: String = "",
        val savedToday: Boolean = false,
    ) : MviContract.State

    sealed interface Effect : MviContract.Effect {
        data object BeautyNoteSaved : Effect
    }
}
