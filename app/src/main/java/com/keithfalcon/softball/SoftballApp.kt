package com.keithfalcon.softball

import android.app.Application
import com.keithfalcon.softball.data.AppDatabase

class SoftballApp : Application() {
    val database: AppDatabase by lazy { AppDatabase.build(this) }
}
