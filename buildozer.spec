[app]

title = ZAuto VIP

package.name = zauto
package.domain = org.zauto

source.dir = .

source.include_exts = py,png,jpg,jpeg,kv,json,xml,java,db,ttf,otf,txt,html,css,js

version = 7.0

android.version_code = 45
fullscreen = 0

icon.filename = profile.jpg
presplash.filename = profile.jpg
presplash.color = #FFFFFF

# =====================================================
# PYTHON / KIVY
# =====================================================
# ĐÃ KHÓA CỨNG PYTHON 3.11.4 ĐỂ CHỐNG LỖI SERVER
requirements = python3==3.11.4,hostpython3==3.11.4,kivy==2.2.1,kivymd==1.1.1,pyjnius,requests

# =====================================================
# LOG
# =====================================================
log_level = 2
warn_on_root = 0

# =====================================================
# ANDROID
# =====================================================
android.api = 34
android.minapi = 24
android.ndk = 25b

android.accept_sdk_license = True
android.archs = arm64-v8a

# =====================================================
# P4A
# =====================================================
p4a.bootstrap = sdl2

# =====================================================
# ANDROIDX
# =====================================================
android.enable_androidx = True

# =====================================================
# JAVA / RES
# =====================================================
android.add_src = java
android.add_res = ./res

# =====================================================
# GRADLE
# =====================================================
android.gradle_dependencies = androidx.core:core:1.12.0,androidx.webkit:webkit:1.7.0
android.gradle_args = -Xmx4096m

# =====================================================
# APK
# =====================================================
android.release_artifact = apk
android.package_format = apk
android.keystore = zauto.keystore
android.keystore_password = zauto123
android.keyalias = zauto
android.keyalias_password = zauto123

# =====================================================
# FOREGROUND SERVICE
# =====================================================
android.foreground_service = True

# =====================================================
# QUERY PACKAGE
# =====================================================
android.manifest_queries = com.zing.zalo

# =====================================================
# PERMISSIONS
# =====================================================
android.permissions = INTERNET,WAKE_LOCK,FOREGROUND_SERVICE,FOREGROUND_SERVICE_DATA_SYNC,POST_NOTIFICATIONS,ACCESS_NETWORK_STATE,ACCESS_WIFI_STATE,RECEIVE_BOOT_COMPLETED,SYSTEM_ALERT_WINDOW,REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,REQUEST_INSTALL_PACKAGES

# =====================================================
# EXTRA MANIFEST
# =====================================================
android.extra_manifest_application = %(source.dir)s/manifest_services.xml

# =====================================================
# SERVICES
# =====================================================
# ĐÃ VÔ HIỆU HÓA: Dòng này được tắt để tránh xung đột với file manifest_services.xml
# services = ZaloForegroundService:java

# =====================================================
# OPENGL
# =====================================================
android.opengl_es_version = 2

# =====================================================
# PERFORMANCE
# =====================================================
android.copy_libs = 1

# =====================================================
# DEBUG
# =====================================================
android.logcat_filters = python:D *:S

# =====================================================
# BUILD FIX
# =====================================================
android.skip_update = False

# =====================================================
# EXCLUDE
# =====================================================
source.exclude_dirs = venv,.venv,env,.git,.github,**pycache**,bin,.buildozer
source.exclude_patterns = *.pyc,*.pyo,*.log,*.tmp

# =====================================================
# ASSETS
# =====================================================
# android.add_assets = .

# =====================================================
# SDL2
# =====================================================
sdl2_gradle_dependencies = True

[buildozer]

log_level = 2
warn_on_root = 0

build_dir = ./.buildozer
bin_dir = ./bin
