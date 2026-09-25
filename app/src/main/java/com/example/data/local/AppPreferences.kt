package com.example.data.local

import android.content.Context
import android.content.SharedPreferences
import android.os.Environment
import java.io.File

/**
 * Gerenciador de preferências e persistência de configurações do aplicativo.
 * Garante que a pasta padrão interna /Movies/AppAnimador/ seja verificada e criada,
 * e que a escolha de pasta manual via SAF seja persistida para todos os projetos futuros.
 */
class AppPreferences(context: Context) {

    private val appContext: Context = context.applicationContext
    private val prefs: SharedPreferences =
        appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    init {
        val defaultDir = ensureDefaultDirectory()
        if (prefs.getString(KEY_DEFAULT_OUTPUT_DIR_URI, null).isNullOrBlank()) {
            prefs.edit().putString(KEY_DEFAULT_OUTPUT_DIR_URI, defaultDir.absolutePath).apply()
        }
    }

    /**
     * Garante, cria no dispositivo do usuário e retorna o diretório padrão de salvamento de vídeos.
     */
    fun ensureDefaultDirectory(): File {
        return try {
            val moviesPublicDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MOVIES)
            val appAnimadorDir = File(moviesPublicDir, DEFAULT_FOLDER_NAME)
            if (!appAnimadorDir.exists()) {
                appAnimadorDir.mkdirs()
            }
            // Também garante a criação no espaço externo do próprio app no dispositivo
            val appExternalMovies = appContext.getExternalFilesDir(Environment.DIRECTORY_MOVIES)
            if (appExternalMovies != null) {
                File(appExternalMovies, DEFAULT_FOLDER_NAME).mkdirs()
            }
            if (appAnimadorDir.exists() && appAnimadorDir.canWrite()) {
                appAnimadorDir
            } else if (appExternalMovies != null) {
                File(appExternalMovies, DEFAULT_FOLDER_NAME).apply { mkdirs() }
            } else {
                File(appContext.filesDir, DEFAULT_FOLDER_NAME).apply { mkdirs() }
            }
        } catch (_: Exception) {
            File(appContext.filesDir, DEFAULT_FOLDER_NAME).apply { mkdirs() }
        }
    }

    /**
     * Obtém o diretório recente ou padrão de salvamento configurado automaticamente ou pelo usuário.
     */
    fun getDefaultOutputDirUri(): String {
        val saved = prefs.getString(KEY_DEFAULT_OUTPUT_DIR_URI, null)
        if (!saved.isNullOrBlank()) {
            return saved
        }
        val created = ensureDefaultDirectory().absolutePath
        prefs.edit().putString(KEY_DEFAULT_OUTPUT_DIR_URI, created).apply()
        return created
    }

    /**
     * Salva o diretório recente selecionado pelo usuário como novo padrão global
     * para sempre salvar lá os vídeos.
     */
    fun setDefaultOutputDirUri(uriString: String?) {
        val effective = if (uriString.isNullOrBlank()) {
            ensureDefaultDirectory().absolutePath
        } else {
            uriString
        }
        prefs.edit().putString(KEY_DEFAULT_OUTPUT_DIR_URI, effective).apply()
    }

    companion object {
        private const val PREFS_NAME = "app_animador_preferences"
        private const val KEY_DEFAULT_OUTPUT_DIR_URI = "key_default_output_dir_uri"
        const val DEFAULT_FOLDER_NAME = "AppAnimador"

        @Volatile
        private var INSTANCE: AppPreferences? = null

        fun getInstance(context: Context): AppPreferences {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: AppPreferences(context.applicationContext).also { INSTANCE = it }
            }
        }
    }
}
