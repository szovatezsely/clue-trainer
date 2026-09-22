package net.geoclue.trainer.game

import kotlin.math.pow

/**
 * How much practice time each country deserves.
 *
 * Every covered country has a guide, but they do not turn up in a real game
 * anywhere near equally often: a round lands in Brazil or the United States far
 * more often than on São Tomé or the Pitcairn Islands, which have a road or two
 * of Street View each. Picking uniformly would spend a third of a run on
 * islands the player may never see again, so both the clue and its distractors
 * are drawn in proportion to these weights.
 *
 * The tiers are a judgement call from how big each country's coverage is and
 * how often it comes up on the popular world maps; a code missing here (a newly
 * scraped guide) gets [Tier.MINOR], so it is neither pushed nor buried.
 *
 * The bias is strongest at the start of a run, when the staples matter most,
 * and eases off over the first [RAMP_QUESTIONS] questions - the rare countries
 * never disappear, they just stop being a distraction early on.
 */
object CountryPopularity {

    enum class Tier(val weight: Double) {
        /** Huge coverage, in nearly every round: the countries to learn first. */
        MAJOR(8.0),

        /** Common, well covered countries. */
        COMMON(4.0),

        /** Small countries and territories with real but limited coverage. */
        MINOR(2.0),

        /** Islands, micro-states and a handful of roads: rarely worth the time early on. */
        RARE(1.0),
    }

    /** Over how many questions the bias eases from [Tier.weight] to its [LATE_EXPONENT] root. */
    const val RAMP_QUESTIONS = 150

    /** Late in a run MAJOR vs RARE is 8^0.5 ≈ 2.8 : 1 rather than 8 : 1. */
    const val LATE_EXPONENT = 0.5

    private val tiers: Map<String, Tier> = buildMap {
        fun put(tier: Tier, vararg codes: String) = codes.forEach { put(it, tier) }

        put(
            Tier.MAJOR,
            "US", "BR", "RU", "CA", "AU", "MX", "AR", "CL", "PE", "CO", "ZA", "JP", "IN", "ID",
            "TH", "MY", "PH", "KZ", "TR", "FR", "ES", "IT", "DE", "GB", "PL", "SE", "NO", "FI", "NZ",
        )
        put(
            Tier.COMMON,
            "PT", "RO", "UA", "NL", "BE", "AT", "CH", "CZ", "SK", "HU", "BG", "GR", "HR", "RS",
            "SI", "IE", "DK", "EE", "LV", "LT", "IS", "KR", "TW", "VN", "KH", "LA", "BD", "LK",
            "NP", "MN", "KG", "PK", "CN", "IL", "JO", "AE", "KE", "NG", "GH", "UG", "SN", "BW",
            "NA", "MG", "TN", "EG", "BO", "EC", "UY", "GT", "CR", "PA", "DO",
        )
        put(
            Tier.MINOR,
            "US-AK", "US-HI", "GL", "PR", "AL", "MK", "ME", "CY", "LU", "MT", "BY", "FO", "IM",
            "JE", "PT-AZ", "PT-MA", "IQ", "LB", "OM", "QA", "BT", "SG", "HK", "RW", "LS", "SZ",
            "ML", "TZ", "RE",
        )
        put(
            Tier.RARE,
            "AD", "LI", "MC", "SM", "GI", "SJ", "MO", "IO", "AQ", "AS", "GU", "MP", "CX", "CC",
            "PN", "VU", "BM", "PM", "MQ", "VI", "UM-MQ", "CW", "FK", "GS", "ST",
        )
    }

    fun tierOf(code: String): Tier = tiers[code] ?: Tier.MINOR

    /** False for a code the table does not know yet, which falls back to [Tier.MINOR]. */
    fun hasTier(code: String): Boolean = code in tiers

    /**
     * The weight of [code] for the question after [served] have been asked:
     * the full tier weight at the start of a run, flattening towards its
     * [LATE_EXPONENT] root by question [RAMP_QUESTIONS].
     */
    fun weight(code: String, served: Int): Double {
        val progress = (served.toDouble() / RAMP_QUESTIONS).coerceIn(0.0, 1.0)
        val exponent = 1.0 - progress * (1.0 - LATE_EXPONENT)
        return tierOf(code).weight.pow(exponent)
    }
}
