plugins {
	alias(libs.plugins.android.application)
	alias(libs.plugins.kotlin.compose)
	alias(libs.plugins.kotlin.serialization)
}

android {
	namespace = "com.secondjb.prism"
	compileSdk = 37

	defaultConfig {
		applicationId = "com.secondjb.prism"
		minSdk = 26
		targetSdk = 36
		versionCode = 8
		versionName = "1.7"

		testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
	}

	buildTypes {
		release {
			isMinifyEnabled = false
			proguardFiles(
				getDefaultProguardFile("proguard-android-optimize.txt"),
				"proguard-rules.pro"
			)
		}
	}
	compileOptions {
		sourceCompatibility = JavaVersion.VERSION_11
		targetCompatibility = JavaVersion.VERSION_11
	}
	buildFeatures {
		compose = true
	}
	testOptions {
		unitTests.isReturnDefaultValues = true
	}
}

dependencies {
	implementation(platform(libs.androidx.compose.bom))
	implementation(libs.androidx.activity.compose)
	implementation(libs.androidx.compose.material3)
	implementation(libs.androidx.compose.ui)
	implementation(libs.androidx.compose.ui.graphics)
	implementation(libs.androidx.compose.ui.tooling.preview)
	implementation(libs.androidx.material.icons.extended)
	implementation(libs.androidx.core.ktx)
	implementation(libs.androidx.lifecycle.runtime.ktx)
	implementation(libs.androidx.lifecycle.viewmodel.compose)

	// Audio & Media
	implementation(libs.androidx.media3.exoplayer)
	implementation(libs.androidx.media3.session)

	// Image loading & Dynamic Theming
	implementation(libs.coil.compose)
	implementation(libs.androidx.palette.ktx)

	// Navigation & Persistence
	implementation(libs.androidx.navigation.compose)
	implementation(libs.androidx.datastore.preferences)

	// Serialization, Networking & QR
	implementation(libs.kotlinx.serialization.json)
	implementation(libs.okhttp)
	implementation(libs.zxing.core)

	// CameraX Built-in QR Scanner
	implementation(libs.androidx.camera.core)
	implementation(libs.androidx.camera.camera2)
	implementation(libs.androidx.camera.lifecycle)
	implementation(libs.androidx.camera.view)

	// Testing
	testImplementation(libs.junit)
	testImplementation(libs.kotlinx.coroutines.test)
	androidTestImplementation(platform(libs.androidx.compose.bom))
	androidTestImplementation(libs.androidx.compose.ui.test.junit4)
	androidTestImplementation(libs.androidx.espresso.core)
	androidTestImplementation(libs.androidx.junit)
	debugImplementation(libs.androidx.compose.ui.test.manifest)
	debugImplementation(libs.androidx.compose.ui.tooling)
}

tasks.withType<Test> {
	testLogging {
		events("passed", "skipped", "failed")
		showStandardStreams = false
		exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL
	}
}

