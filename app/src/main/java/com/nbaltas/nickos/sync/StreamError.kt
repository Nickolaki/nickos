package com.nbaltas.nickos.sync

/**
 * Turns a player error on a streamed movie into the one short code the tablet reports. A code only,
 * never the error text, because that text can hold the address with the token in it.
 */
object StreamError {
    const val CANT_REACH = "cant_reach"
    const val AUTH = "auth"
    const val UNSUPPORTED = "unsupported"
    const val TIMEOUT = "timeout"

    /** The first load may take this long to become ready. */
    const val LOAD_TIMEOUT_MS = 20_000L
    /** A stream that has played and then buffers for longer than this has failed. */
    const val REBUFFER_TIMEOUT_MS = 30_000L

    /**
     * [errorCode] is the player's PlaybackException code and [httpStatus] the server's answer when the
     * error came from one, else 0. 2xxx are network and server errors, 3xxx are a broken file, 4xxx are
     * the decoder.
     */
    fun code(errorCode: Int, httpStatus: Int): String = when {
        httpStatus == 401 || httpStatus == 403 -> AUTH
        errorCode in 2000..2999 -> CANT_REACH
        else -> UNSUPPORTED
    }
}
