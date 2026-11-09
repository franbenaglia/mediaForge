package com.fabapps.mediaforge.media.engine

import android.content.Context
import java.io.File

/**
 * La misma carpeta que `Context.noBackupFilesDir`, sin tocar el disco: esa propiedad la crea si falta
 * y, como los motores se arman al iniciar la app, StrictMode marcaba lecturas de disco en el hilo
 * principal (T11.6). Quien escribe adentro ya crea sus subcarpetas con `mkdirs`.
 */
fun Context.noBackupDir(): File = File(applicationInfo.dataDir, "no_backup")
