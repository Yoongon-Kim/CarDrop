package com.cardrop.crew

import kotlin.random.Random

/**
 * Owner: Member A (added by the lead). A new crew's 6-digit join code, 100000-999999 (no leading 0, so it
 * always shows as "482 917"). Every crew the real crew creates gets its own, so two hosts (e.g. two
 * emulators both driving 아빠 차) never end up in the same crew. Two crews draw the same code about once in
 * 900,000 times.
 */
fun newCrewCode(random: Random = Random.Default): String = random.nextInt(100_000, 1_000_000).toString()
