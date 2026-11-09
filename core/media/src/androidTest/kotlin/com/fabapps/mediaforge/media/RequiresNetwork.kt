package com.fabapps.mediaforge.media

/**
 * Prueba que necesita Internet (sitios externos o GitHub). `test-device.sh --offline` las saltea:
 * un cambio en YouTube no tiene que tapar un error del conversor (main.md §12.4, T11.5).
 */
@Retention(AnnotationRetention.RUNTIME)
@Target(AnnotationTarget.FUNCTION, AnnotationTarget.CLASS)
annotation class RequiresNetwork
