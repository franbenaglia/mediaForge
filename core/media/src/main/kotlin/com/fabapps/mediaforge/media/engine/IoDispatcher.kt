package com.fabapps.mediaforge.media.engine

import javax.inject.Qualifier

/** Dispatcher para trabajo bloqueante (procesos, archivos). En pruebas se reemplaza. */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class IoDispatcher
