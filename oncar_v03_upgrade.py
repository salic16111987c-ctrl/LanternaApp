from pathlib import Path

root = Path('/tmp/oncar-src/OnCarDiagnostico/android/app')
manifest = root / 'src/main/AndroidManifest.xml'
s = manifest.read_text(encoding='utf-8')
needle = '    <uses-permission android:name="android.permission.ACCESS_WIFI_STATE" />'
assert needle in s, 'Permissao ACCESS_WIFI_STATE nao encontrada'
assert 'android.permission.CHANGE_WIFI_STATE' not in s, 'CHANGE_WIFI_STATE ja existe'
s = s.replace(needle, needle + '\n    <uses-permission android:name="android.permission.CHANGE_WIFI_STATE" />', 1)
manifest.write_text(s, encoding='utf-8')

kt = root / 'src/main/java/com/cilas/oncardiagnostico/MainActivity.kt'
s = kt.read_text(encoding='utf-8')
assert '=== ONCAR DIAGNÓSTICO v0.2 ===' in s
s = s.replace('=== ONCAR DIAGNÓSTICO v0.2 ===', '=== ONCAR DIAGNÓSTICO v0.3 ===', 1)
s = s.replace('v0.2 • Redmi Note 13', 'v0.3 • Redmi Note 13', 1)
s = s.replace('Relatório OnCar v0.2', 'Relatório OnCar v0.3', 1)
old = '    private fun wifiDirectInfo() {\n        try {'
new = '''    private fun wifiDirectInfo() {
        if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.NEARBY_WIFI_DEVICES) != PackageManager.PERMISSION_GRANTED) {
            appendLog("Wi-Fi Direct: permissao DISPOSITIVOS WI-FI PROXIMOS nao concedida; toque em 1. CONCEDER PERMISSOES.")
            return
        }
        try {'''
assert old in s, 'Funcao wifiDirectInfo nao encontrada'
s = s.replace(old, new, 1)
kt.write_text(s, encoding='utf-8')

build = root / 'build.gradle.kts'
s = build.read_text(encoding='utf-8')
assert 'versionCode = 2' in s and 'versionName = "0.2"' in s
s = s.replace('versionCode = 2', 'versionCode = 3', 1).replace('versionName = "0.2"', 'versionName = "0.3"', 1)
build.write_text(s, encoding='utf-8')
print('ONCAR v0.3: manifest CHANGE_WIFI_STATE, runtime validation, build version OK')
