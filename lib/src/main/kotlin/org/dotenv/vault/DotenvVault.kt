package org.dotenv.vault

import io.github.cdimascio.dotenv.*
import io.github.cdimascio.dotenv.internal.DotenvParser
import java.util.Base64

private val DEVELOPMENT_VAULT_KEY = "DOTENV_VAULT_DEVELOPMENT"

class DotenvVault(private val dotenvDelegate: Dotenv) {
    private val vaultCrypto: VaultCrypto = VaultCrypto()
    private lateinit var vaultEntries: Map<String, String>
    private lateinit var vaultDotEnvEntries: List<DotenvEntry>


    init {
        decryptVolt()
    }

    fun printEntries() {
        for (e in dotenvDelegate.entries()) {
            println("${e.key} = ${e.value}")
        }
    }

    fun decodeEncryptedEnvVaultContent(): String {
        // QUESTION how to tell which of the environments to load if there's many present
        val devEntry = dotenvDelegate.entries().find { it.key == DEVELOPMENT_VAULT_KEY }
        devEntry?.let {
            val encryptedEnvContent = devEntry.value
            println("found development key in .env.vault: $encryptedEnvContent")
            //TODO decrypt content and add all found variables to the entries set
            return encryptedEnvContent


        }
        throw DotenvException("could get encrypted vault content")
    }

    @Throws(DotenvException::class)
    fun decryptVolt() {
        val encryptedVaultContent = decodeEncryptedEnvVaultContent()
        val foundEnvironmentVaultKey = findEnvironmentVaultKey()
        println("found vault key uri ${foundEnvironmentVaultKey}")
        val dotenvVaultKey = decodeKeyFromUri(foundEnvironmentVaultKey)

        val secretKey = vaultCrypto.createKeyFromBytes(dotenvVaultKey)
        val dotenvFileContent = vaultCrypto.decrypt(secretKey, Base64.getDecoder().decode(encryptedVaultContent))
        val reader = DotenvParser(
            DotenvVaultReader(dotenvFileContent),
            true,
            true
        )
        val env = reader.parse()
        // add decrypted variables to the dotenv entries
        env.forEach {
            //TODO comment this
            println("vault item: ${it.key} = ${it.value}")
        }
        val envVarsMap = env.map { it.key to it.value }.toMap()
        vaultEntries = envVarsMap
        vaultDotEnvEntries = env
    }

    private fun decodeKeyFromUri(findEnvironmentVaultKey: String): ByteArray {
        val keyPart = findEnvironmentVaultKey.substring(15, 15 + 64)
        println("decoding key ${keyPart} from hex string")
        return keyPart.fromHexString()
    }

    private fun findEnvironmentVaultKey(): String {
        dotenvDelegate.entries().forEach {
            println("${it}")
        }

        val keyEntry = dotenvDelegate.entries().find { it.key == "DOTENV_KEY_DEVELOPMENT" }
        if (keyEntry?.value != null) {
            println("found key entry in current system env vars ${keyEntry.value}")
            return keyEntry.value
        }

        println("cannot find key entry in current system env vars")
        val decodedKeyEntryFromFile = decodeEncryptedEnvVaultKeyFile()
        return decodedKeyEntryFromFile
    }

    private fun decodeEncryptedEnvVaultKeyFile(): String {
        val dotenvKeys = Dotenv
            .configure()
            .filename(".env.keys")

        val delegate = dotenvKeys.load()
        delegate.entries().forEach {
            println("${it}")
        }

        val keyEntry = delegate.entries().find { it.key == "DOTENV_KEY_DEVELOPMENT" }
        println("found key entry ${keyEntry}")
        return keyEntry?.value ?: throw DotenvException("could not find environment key")
    }


    /**
     * Returns the set of environment variables with values
     * @return the set of [DotenvEntry]s for all environment variables
     */
    fun entries(): Set<DotenvEntry> {
        return vaultDotEnvEntries.toSet() + dotenvDelegate.entries()
    }

    /**
     * Returns the set of [DotenvEntry]s matching the filter
     * @param filter the filter e.g. [Dotenv.Filter]
     * @return the set of [DotenvEntry]s for environment variables matching the [Dotenv.Filter]
     */
    fun entries(filter: Dotenv.Filter): Set<DotenvEntry> = vaultDotEnvEntries.toSet() + dotenvDelegate.entries(filter)

    /**
     * Retrieves the value of the environment variable specified by key
     * @param key the environment variable
     * @return the value of the environment variable
     */
    operator fun get(key: String): String = vaultEntries[key] ?: dotenvDelegate[key]

    /**
     * Retrieves the value of the environment variable specified by key.
     * If the key does not exist, then the default value is returned
     * @param key the environment variable
     * @param defaultValue the default value to return
     * @return the value of the environment variable or default value
     */
    operator fun get(key: String, defaultValue: String): String =
        vaultEntries[key] ?: dotenvDelegate.get(key, defaultValue)
}


fun dotenvVault(block: Configuration.() -> Unit = {}): DotenvVault {
    // TODO decrypt file and feed it to the dotenv tool
    // using public DotenvImpl(List<DotenvEntry> envVars) where envVars can be fed to the constructor
    // however the load method doesn't support it and the DotenvImpl is package private, so cannot be initialized from outside
    // go and check how the decryption works to see if it's feasible.
    val config = Configuration()
    block(config)
    val dotenv = Dotenv
        .configure()
        .directory(config.directory)
        .filename(".env.vault")

    if (config.ignoreIfMalformed) dotenv.ignoreIfMalformed()
    if (config.ignoreIfMissing) dotenv.ignoreIfMissing()
    if (config.systemProperties) dotenv.systemProperties()
    val delegate = dotenv.load()
    return DotenvVault(delegate)
}
