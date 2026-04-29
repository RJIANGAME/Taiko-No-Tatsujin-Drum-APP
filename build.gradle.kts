plugins {
    id("com.android.application") version "9.1.1" apply false
}

tasks.register<Delete>("clean") {
    delete(layout.buildDirectory)
}
