[app]

# =====================================================
# APP INFO
# =====================================================

title = ZAuto VIP
package.name = zauto
package.domain = org.zauto

source.dir = .

source.include_exts = py,png,jpg,jpeg,kv,json,xml,java,db,ttf,otf,txt,html,css,js
source.include_dirs = nodejs_backend
source.include_patterns = nodejs_backend/bin/*,nodejs_backend/node_modules/**

version = 7.0

# =====================================================
# REQUIREMENTS
# =====================================================

# FIX: Kivy updated to 2.3.0 for better Python 3.11.4 & NDK 25b compatibility
requirements = python3==3.11.9,kivy==2.3.0,kivymd==1.1.1,pillow==9.5.0,pyjnius,requests,plyer

# =====================================================
# SPLASH & ICON
# =====================================================

presplash.filename = profile.jpg
presplash.color = #FFFFFF
icon.filename = profile.jpg

# =====================================================
# DISPLAY
# =====================================================

orientation = portrait
fullscreen = 0

# =====================================================
# ANDROID PACKAGE
# =====================================================

android.release_artifact = apk
android.package_format = apk

# =====================================================
# SIGN APK
# =====================================================

android.keystore = zauto.keystore
android.keystore_password = zauto123
android.keyalias = zauto
android.keyalias_password = zauto123

# =====================================================
# ANDROID SDK
# =====================================================

android.api = 34
android.minapi = 24
android.ndk = 25b
android.accept_sdk_license = True
android.archs = arm64-v8a, armeabi-v7a
android.enable_androidx = True
android.allow_backup = False

# =====================================================
# PERMISSIONS
# =====================================================

android.permissions = INTERNET,WAKE_LOCK,FOREGROUND_SERVICE,FOREGROUND_SERVICE_DATA_SYNC,POST_NOTIFICATIONS,ACCESS_NETWORK_STATE,ACCESS_WIFI_STATE,RECEIVE_BOOT_COMPLETED,SYSTEM_ALERT_WINDOW,REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,REQUEST_INSTALL_PACKAGES

# =====================================================
# FOREGROUND SERVICE
# =====================================================

android.foreground_service = True
android.manifest_queries = com.zing.zalo

# =====================================================
# EXTRA JAVA / RESOURCES
# =====================================================

android.add_src = java
android.add_res = res

# =====================================================
# GRADLE
# =====================================================

android.gradle_dependencies = androidx.core:core:1.12.0,androidx.webkit:webkit:1.7.0
android.gradle_args = -Dorg.gradle.jvmargs=-Xmx4096m

# =====================================================
# EXTRA MANIFEST
# =====================================================

android.extra_manifest_application = %(source.dir)s/manifest_services.xml
android.extra_manifest_application_arguments = <provider android:name="androidx.core.content.FileProvider" android:authorities="org.zauto.zauto.fileprovider" android:exported="false" android:grantUriPermissions="true"><meta-data android:name="android.support.FILE_PROVIDER_PATHS" android:resource="@xml/file_paths"/></provider>

# =====================================================
# PERFORMANCE
# =====================================================

android.copy_libs = 1
android.skip_update = False
android.logcat_filters = python:D *:S

# =====================================================
# SOURCE EXCLUDE
# =====================================================

# FIX: Corrected **pycache** to __pycache__
source.exclude_dirs = venv,.venv,env,.git,.github,__pycache__,.buildozer,bin
source.exclude_patterns = *.pyc,*.pyo,*.log,*.tmp

# =====================================================
# PYTHON FOR ANDROID
# =====================================================

p4a.branch = master

# =====================================================
# BUILDOZER
# =====================================================

[buildozer]
log_level = 2
warn_on_root = 0
build_dir = .buildozer
bin_dir = bin
