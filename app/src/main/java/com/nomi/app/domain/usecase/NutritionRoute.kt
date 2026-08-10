package com.nomi.app.domain.usecase

/**
 * Which path a nutrition request actually took.
 *
 * Recorded so the cost question — "did that edit really need a web search?" — has an answer in
 * the debug log rather than in a bill. Never shown on the normal screens: a user correcting a
 * portion should see a fast, correct number, not the routing that produced it.
 */
enum class NutritionRoute {
    /** A photo was described by the cheap vision model. No nutrition was looked up yet. */
    PHOTO_DESCRIPTION,

    /** A new food was researched on the web. */
    NEW_RESEARCH,

    /** An amount changed and was scaled in app code. No provider was asked for nutrition. */
    PORTION_SCALE,

    /** An edit changed the food itself, so the researched source no longer applied. */
    CONTENT_RERESEARCH,
    ;

    /** How the route was decided, for the debug row. */
    enum class Decision {
        /** Parsed in app code with no model call at all. */
        LOCAL,

        /** A cheap model classified the request. */
        CLASSIFIER,

        /** No routing decision was needed; the entry point determines the route. */
        DIRECT,
    }
}
