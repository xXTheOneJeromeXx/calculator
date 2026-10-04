package com.tbce.calc

/** Every tunable for the hidden gesture, the lock, and key derivation lives here. */
object Config {
    /** How long the armed window stays open before it silently closes. */
    const val ARM_WINDOW_MS = 15_000L

    /** Idle time inside the vault before it locks. */
    const val IDLE_LOCK_MS = 60_000L

    const val MIN_CODE_DIGITS = 6
    const val GOOD_CODE_DIGITS = 8
    const val MAX_CODE_DIGITS = 16

    /** Wrong codes in a row before an opt-in wipe. */
    const val WIPE_AFTER = 10

    /** How long the app may sit behind the system file picker before it locks anyway. */
    const val PICKER_GRACE_MS = 120_000L

    /** Shortest passphrase accepted for a backup file. */
    const val MIN_PASSPHRASE = 8

    /** Argon2id parameters for new vaults. Stored with each vault, so they can change later. */
    const val KDF_MEMORY_KB = 64 * 1024
    const val KDF_ITERATIONS = 3
    const val KDF_PARALLELISM = 1
}
