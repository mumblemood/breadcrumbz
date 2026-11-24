package de.breadcrumbz.app

import android.app.Application

class BreadcrumbzApp : Application() {
    val container: AppContainer by lazy { AppContainer(this) }
}
