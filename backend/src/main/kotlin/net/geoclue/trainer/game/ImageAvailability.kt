package net.geoclue.trainer.game

/**
 * What the game needs to know about the image cache in order to hand out a clue
 * that will actually display.
 *
 * The guide site rate-limits image requests hard, so a clue whose image is not
 * cached yet may simply fail to load. Rather than discover that after picking,
 * the game prefers clues it knows are already on disk.
 */
interface ImageAvailability {

    /** True when the image is on local disk: instant, and it cannot fail. */
    fun isCached(imagePath: String): Boolean

    /** True while the origin is rate-limiting us, so uncached images cannot load. */
    fun isThrottled(): Boolean

    companion object {
        /** For a game that does not care where its images come from. */
        val ALWAYS: ImageAvailability = object : ImageAvailability {
            override fun isCached(imagePath: String) = true
            override fun isThrottled() = false
        }
    }
}
