package com.factorybill.app.ui

import androidx.compose.runtime.staticCompositionLocalOf
import com.factorybill.app.data.Repository

/**
 * The app's single local repository, provided once by MainActivity so list/detail screens can
 * observe the database directly without creating a ViewModel for every route.
 */
val LocalRepository = staticCompositionLocalOf<Repository> {
    error("LocalRepository was not provided")
}
