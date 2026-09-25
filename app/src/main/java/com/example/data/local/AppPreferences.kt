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

    private val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    /**
     * Garante e retorna o diretório padrão de filmes interno do dispositivo.
     */
    fun ensureDefaultDirectory(): File {
        val moviesPublicDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MOVIES)
        val appAnimadorDir = File(moviesPublicDir, DEFAULT_FOLDER_NAME)
        if (!appAnimadorDir.exists()) {
            appAnimadorDir.mkdirs()
        }
        return appAnimadorDir
    }

    /**
     * Obtém o URI padrão de salvamento configurado pelo usuário (se houver).
     */
    fun getDefaultOutputDirUri(): String? {
        return prefs.getString(KEY_DEFAULT_OUTPUT_DIR_URI, null)
    }

    /**
     * Salva o URI do diretório selecionado pelo usuário como novo padrão global
     * para o projeto atual e todos os novos projetos futuros.
     */
    fun setDefaultOutputDirUri(uriString: String?) {
        prefs.edit().apply {
            if (uriString != null) {
                putString(KEY_DEFAULT_OUTPUT_DIR_URI, uriString)
            } else {
                remove(KEY_DEFAULT_OUTPUT_DIR_URI)
            }
            apply()
        }
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
