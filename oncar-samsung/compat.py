from pathlib import Path
p=Path('/tmp/oncar-samsung-build/OnCarDiagnostico/android/app/src/main/java/com/cilas/oncardiagnostico/CaptureService.kt')
s=p.read_text()
assert s.count('BluetoothProfile.PAN') == 2
# PAN e o perfil Bluetooth de ID 5. A constante PAN nao e publica em todos os SDKs.
s=s.replace('BluetoothProfile.PAN', '5')
p.write_text(s)
print('Compatibilidade de perfil PAN corrigida')
