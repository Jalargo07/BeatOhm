package com.beatohm.data

/**
 * Excepción lanzada cuando se alcanza el límite máximo de escritura de tags (100 canciones).
 * Se lanza antes de escribir tags en finalizeMetadataUpdate() para bloquear escrituras
 * adicionales después del límite gratuito.
 */
class TagWriteLimitReachedException : Exception("Tag write limit reached (100 songs)")
