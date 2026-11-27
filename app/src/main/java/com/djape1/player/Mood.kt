package com.djape1.player

import android.graphics.Color

/** Χρώματα ενός θέματος για σκοτεινό ή φωτεινό φόντο. */
class Palette(
    val page: Int, val card: Int, val field: Int, val line: Int, val fg: Int, val sub: Int,
    val accent: Int, val accent2: Int, val accentFg: Int, val link: Int, val warn: Int,
    val cardRadiusDp: Float, val cardStroke: Int
)

/** Θέματα εμπνευσμένα από είδη μουσικής. Οι ίδιες τιμές με τον player του browser. */
object Mood {
    val IDS = listOf("auto", "ape1", "neon", "jazz", "rock", "lofi", "retro")
    val NAMES = mapOf(
        "auto" to "Auto", "ape1" to "Dj Ape1", "neon" to "Neon Club", "jazz" to "Jazz Lounge",
        "rock" to "Rock", "lofi" to "Lo-fi Chill", "retro" to "Retro Disco"
    )
    val ICONS = mapOf(
        "auto" to R.drawable.ic_sparkle, "ape1" to R.drawable.ic_radio, "neon" to R.drawable.ic_headphones,
        "jazz" to R.drawable.ic_coffee, "rock" to R.drawable.ic_fire, "lofi" to R.drawable.ic_moon, "retro" to R.drawable.ic_party
    )

    private val RULES = listOf(
        Regex("house|techno|trance|edm|electro|dance|club|progressive|dubstep|drum|bass|synth", RegexOption.IGNORE_CASE) to "neon",
        Regex("jazz|blues|soul|swing|bossa|lounge|r&b|rnb", RegexOption.IGNORE_CASE) to "jazz",
        Regex("rock|metal|punk|grunge|alternative|indie|hard", RegexOption.IGNORE_CASE) to "rock",
        Regex("lo-?fi|chill|ambient|downtempo|acoustic|folk|classical|piano", RegexOption.IGNORE_CASE) to "lofi",
        Regex("disco|funk|pop|80|70|oldies|retro|latin|reggae|ελλην|λαϊκ|έντεχν|rebetik", RegexOption.IGNORE_CASE) to "retro"
    )

    /** Επιλέγει θέμα για το «Αυτόματο»: πρώτα της ζώνης, μετά από το είδος του κομματιού. */
    fun auto(zoneTheme: String?, genre: String?, zone: String?): String {
        if (!zoneTheme.isNullOrBlank() && zoneTheme in IDS) return zoneTheme
        val text = (genre ?: "") + " " + (zone ?: "")
        for ((re, id) in RULES) if (re.containsMatchIn(text)) return id
        return "ape1"
    }

    fun iconFor(name: String?): Int = when (name) {
        "mic" -> R.drawable.ic_mic
        "headphones" -> R.drawable.ic_headphones
        "coffee" -> R.drawable.ic_coffee
        "sun" -> R.drawable.ic_sun
        "moon" -> R.drawable.ic_moon
        "fire" -> R.drawable.ic_fire
        "heart" -> R.drawable.ic_heart
        "star" -> R.drawable.ic_star
        "party" -> R.drawable.ic_party
        "car" -> R.drawable.ic_car
        "book" -> R.drawable.ic_book
        else -> R.drawable.ic_music
    }

    private fun c(s: String) = Color.parseColor(s)

    fun palette(id: String, dark: Boolean): Palette {
        val warn = c(if (dark) "#FF8A80" else "#B3261E")
        return when (id) {
            "neon" -> if (dark) Palette(c("#0A0B1A"), c("#12142B"), c("#0C0E22"), c("#262A55"), c("#F6F6F8"), c("#9BA3D6"),
                c("#00E5FF"), c("#FF2BD6"), c("#06091A"), c("#5AF0FF"), warn, 18f, c("#00E5FF"))
            else Palette(c("#F1F0F5"), c("#FFFFFF"), c("#F4F3F8"), c("#DEDCE8"), c("#18181D"), c("#5D5B6B"),
                c("#00C2D6"), c("#FF2BD6"), c("#FFFFFF"), c("#00A3B8"), warn, 18f, c("#00C2D6"))
            "jazz" -> if (dark) Palette(c("#16110D"), c("#241B14"), c("#1A130E"), c("#3D2F22"), c("#F6F1EA"), c("#BFAE98"),
                c("#E0A84A"), c("#B5651D"), c("#1A1006"), c("#F0C274"), warn, 14f, c("#E0A84A"))
            else Palette(c("#F4EFE8"), c("#FFFFFF"), c("#F6F1EA"), c("#E2D6C4"), c("#1E1710"), c("#6A5C4A"),
                c("#B8741A"), c("#E0A84A"), c("#FFFFFF"), c("#9A5D10"), warn, 14f, c("#B8741A"))
            "rock" -> if (dark) Palette(c("#0F0B0B"), c("#1C1313"), c("#150E0E"), c("#3A2222"), c("#F6F1F1"), c("#B9A3A3"),
                c("#FF3B30"), c("#FF8A00"), c("#FFFFFF"), c("#FF8A80"), warn, 6f, c("#FF3B30"))
            else Palette(c("#F3EFEF"), c("#FFFFFF"), c("#F6F2F2"), c("#E2D3D3"), c("#1D1313"), c("#6B5757"),
                c("#D92B21"), c("#FF7A00"), c("#FFFFFF"), c("#B3261E"), warn, 6f, c("#D92B21"))
            "lofi" -> if (dark) Palette(c("#1B1A2B"), c("#26253B"), c("#1F1E32"), c("#38375A"), c("#F4F3FA"), c("#A9A7C9"),
                c("#F4A6C0"), c("#8ECAE6"), c("#2A1A24"), c("#8ECAE6"), warn, 26f, c("#38375A"))
            else Palette(c("#F3F1F8"), c("#FFFFFF"), c("#F5F3FA"), c("#E0DCEE"), c("#201E30"), c("#5E5B78"),
                c("#D46A96"), c("#6FA8CF"), c("#FFFFFF"), c("#4B86B3"), warn, 26f, c("#E0DCEE"))
            "retro" -> if (dark) Palette(c("#1D0F2E"), c("#2B1745"), c("#220F38"), c("#4A2A73"), c("#F8F2FC"), c("#C3A8E0"),
                c("#FF6B9D"), c("#FFB347"), c("#260A1C"), c("#FFC2E0"), warn, 20f, c("#4A2A73"))
            else Palette(c("#F6F0F8"), c("#FFFFFF"), c("#F8F2FA"), c("#E6D6EE"), c("#25132E"), c("#6B5380"),
                c("#E0457B"), c("#FF9E3D"), c("#FFFFFF"), c("#B93A8F"), warn, 20f, c("#E6D6EE"))
            else -> if (dark) Palette(c("#17171A"), c("#26262C"), c("#1C1C21"), c("#33333B"), c("#F6F6F8"), c("#A9A7B6"),
                c("#9B5CFF"), c("#C08BFF"), c("#FFFFFF"), c("#42B8FF"), warn, 18f, c("#33333B"))
            else Palette(c("#F1F0F5"), c("#FFFFFF"), c("#F4F3F8"), c("#DEDCE8"), c("#18181D"), c("#5D5B6B"),
                c("#8A3FFC"), c("#C04BFF"), c("#FFFFFF"), c("#0A6FC2"), warn, 18f, c("#DEDCE8"))
        }
    }
}
