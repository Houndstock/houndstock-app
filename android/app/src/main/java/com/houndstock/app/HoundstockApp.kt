package com.houndstock.app

import android.app.Application
import dagger.hilt.android.HiltAndroidApp

/**
 * Application entry point. The @HiltAndroidApp annotation triggers Hilt's
 * code generation and creates the application-level DI container.
 */
@HiltAndroidApp
class HoundstockApp : Application()
