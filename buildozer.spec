[app]

# (str) Title of your application
title = ZAuto VIP

# (str) Package name
package.name = zauto
package.domain = org.zauto

# (str) Source code where the main.py live
source.dir = .

# (list) Source files to include
source.include_exts = py,png,jpg,jpeg,kv,json,xml,java,db,ttf,otf,txt,html,css,js
source.include_dirs = nodejs_backend
source.include_patterns = nodejs_backend/bin/*, nodejs_backend/node_modules/**

# (str) Application versioning
version = 7.0

# (list) Application requirements
requirements = python3==3.11.4,hostpython3==3.11.4,kivy==2.3.0,kivymd==1.1.1,pyjnius,requests,plyer

# (str) Presplash of the application
presplash.filename = profile.jpg
presplash.color = #FFFFFF

# (str) Icon of the application
icon.filename = profile.jpg

# (list) Supported orientations
orientation = portrait

# (bool) Indicate if the application should be fullscreen
fullscreen = 0

# (str) Android Keystore configuration (THÊM ĐOẠN NÀY)
android.release_artifact = apk
android.package_format = apk
android.keystore = zauto.keystore
android.keystore_password = zauto123
android.keyalias = zauto
android.keyalias_password = zauto123

# =====================================================
# ANDROID CONFIGURATION
# =====================================================
android.api = 34
android.minapi = 24
android.ndk = 25b
android.accept_sdk_license = True
android.archs = arm64-v8a, armeabi-v7a
android.enable_androidx = True

# =====================================================
# PERMISSIONS & SERVICES
# =====================================================
android.permissions = INTERNET,WAKE_LOCK,FOREGROUND_SERVICE,FOREGROUND_SERVICE_DATA_SYNC,POST_NOTIFICATIONS,ACCESS_NETWORK_STATE,ACCESS_WIFI_STATE,RECEIVE_BOOT_COMPLETED,SYSTEM_ALERT_WINDOW,REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,REQUEST_INSTALL_PACKAGES

android.foreground_service = True
android.manifest_queries = com.zing.zalo

# =====================================================
# EXTRA CONFIG
# =====================================================
android.add_src = java
android.add_res = ./res
android.gradle_dependencies = androidx.core:core:1.12.0,androidx.webkit:webkit:1.7.0
android.gradle_args = -Xmx4096m

android.extra_manifest_application = %(source.dir)s/manifest_services.xml
android.extra_manifest_application_arguments = <provider android:name="androidx.core.content.FileProvider" android:authorities="org.zauto.zauto.fileprovider" android:exported="false" android:grantUriPermissions="true"><meta-data android:name="android.support.FILE_PROVIDER_PATHS" android:resource="@xml/file_paths"/></provider>

# =====================================================
# PERFORMANCE & BUILD
# =====================================================
android.copy_libs = 1
android.skip_update = False
android.logcat_filters = python:D *:S

source.exclude_dirs = venv,.venv,env,.git,.github,**pycache**,.buildozer
source.exclude_patterns = *.pyc,*.pyo,*.log,*.tmp

[buildozer]
log_level = 2
warn_on_root = 0
build_dir = ./.buildozer
bin_dir = ./bin
