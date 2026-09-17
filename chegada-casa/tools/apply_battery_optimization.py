#!/usr/bin/env python3
"""Apply the 2.3 low-power location profile to the existing proven monitor.

Fail if an expected source anchor has changed; never silently generate a broken APK.
Run before structural checks and Gradle build. Safe to run again after persistence.
"""
from pathlib import Path

root = Path(__file__).resolve().parents[1]
service_file = root / 'app/src/main/java/com/cilassouza/chegadacasa/ArrivalMonitorService.java'
tests_file = root / 'tools/check_arrival_invariants.py'
s = service_file.read_text(encoding='utf-8')

if 'private static final long DAY_INTERVAL_MS = 120000L;' not in s:
    def change(before, after):
        global s
        occurrences = s.count(before)
        if occurrences != 1:
            raise RuntimeError(f'Unexpected monitor source anchor (count={occurrences}): {before[:90]}')
        s = s.replace(before, after, 1)

    change('import com.google.android.gms.location.Priority;\n',
           'import com.google.android.gms.location.Priority;\nimport java.util.Calendar;\n')
    change('    private static final long INTERVAL_MS = 5000L;\n',
           '    private static final long INTERVAL_MS = 5000L;\n'
           '    private static final long HOME_INTERVAL_MS = 30000L;\n'
           '    private static final long DAY_INTERVAL_MS = 120000L;\n'
           '    private static final int MODE_DAY = 0, MODE_HOME = 1, MODE_FAST = 2;\n')
    change('    private int consecutiveOutside;\n',
           '    private int consecutiveOutside;\n    private int profileMode = -1;\n')
    change('        if (foreground && callback == null) beginUpdates();',
           '        if (foreground) ensureProfile();')

    helper = '''    private int desiredProfile() {
        int hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY);
        boolean night = hour >= 18 || hour < 6;
        if (prefs.getBoolean("so_noite", false) && !night) return MODE_DAY;
        if (prefs.getBoolean("dentro", false)) return MODE_HOME;
        // Never throttle the proven 5-second GPS on the way home at night.
        return MODE_FAST;
    }

    private long profileInterval(int mode) {
        return mode == MODE_DAY ? DAY_INTERVAL_MS
                : mode == MODE_HOME ? HOME_INTERVAL_MS : INTERVAL_MS;
    }

    private String profileName(int mode) {
        return mode == MODE_DAY ? "ECONÔMICO DIA (6h–18h)"
                : mode == MODE_HOME ? "ECONÔMICO EM CASA"
                : "GPS RÁPIDO FORA DE CASA";
    }

    private void ensureProfile() {
        if (!foreground || !prefs.getBoolean("ativa", false)) return;
        if (callback == null) beginUpdates();
        else if (profileMode != desiredProfile()) restartUpdates();
    }

'''
    change('    private final Runnable watchdog = new Runnable() {',
           helper + '    private final Runnable watchdog = new Runnable() {')
    start = s.index('    private final Runnable watchdog = new Runnable() {')
    end = s.index('    private void restartUpdates()', start)
    s = s[:start] + '''    private final Runnable watchdog = new Runnable() {
        @Override public void run() {
            if (!prefs.getBoolean("ativa", false)) { stopSelf(); return; }
            if (!foreground) return;
            long elapsed = SystemClock.elapsedRealtime();
            if (callback == null || profileMode != desiredProfile()) {
                // Clock transitions at 06:00/18:00 are checked every 30 seconds.
                restartUpdates();
            } else {
                long staleLimit = Math.max(STALE_MS, profileInterval(profileMode) * 3L);
                if ((lastFixElapsed == 0L && elapsed - serviceStartedElapsed > staleLimit)
                        || (lastFixElapsed > 0L && elapsed - lastFixElapsed > staleLimit)) {
                    prefs.edit().putString("monitor_state", "Localização atrasada; tentando recuperar")
                            .putString("monitor_error", "Sem localização recente").apply();
                    restartUpdates();
                }
            }
            handler.postDelayed(this, WATCHDOG_MS);
        }
    };

''' + s[end:]
    change('        lastFixElapsed = 0L;\n        serviceStartedElapsed = SystemClock.elapsedRealtime();',
           '        profileMode = -1;\n        lastFixElapsed = 0L;\n'
           '        serviceStartedElapsed = SystemClock.elapsedRealtime();')
    change('''        LocationRequest request = new LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, INTERVAL_MS)
                .setMinUpdateIntervalMillis(3000L).setMinUpdateDistanceMeters(0f)
                .setMaxUpdateDelayMillis(INTERVAL_MS).build();''',
           '''        int mode = desiredProfile();
        long period = profileInterval(mode);
        int priority = mode == MODE_FAST ? Priority.PRIORITY_HIGH_ACCURACY
                : Priority.PRIORITY_BALANCED_POWER_ACCURACY;
        LocationRequest request = new LocationRequest.Builder(priority, period)
                .setMinUpdateIntervalMillis(mode == MODE_FAST ? 3000L : period)
                .setMinUpdateDistanceMeters(0f)
                .setMaxUpdateDelayMillis(period).build();''')
    change('        callback = next;\n        try {',
           '''        callback = next;
        profileMode = mode;
        prefs.edit().putString("monitor_profile", profileName(mode))
                .putString("monitor_state", "Monitor: " + profileName(mode)).apply();
        notifyStatus(profileName(mode));
        try {''')
    change('        String status = (mock ? "GPS FICTÍCIO — portão bloqueado — " : "GPS OK — ")',
           '        String status = profileName(profileMode) + " — "\n                + (mock ? "GPS FICTÍCIO — portão bloqueado — " : "GPS OK — ")')
    change('''            }
            firstFix = false;
            return;
        }
        consecutiveOutside = 0;''',
           '''            }
            // Home -> outside: switch to fast immediately after two reliable outside fixes.
            handler.post(this::ensureProfile);
            firstFix = false;
            return;
        }
        consecutiveOutside = 0;''')
    change('''            }
        }
        firstFix = false;
    }

    private void createChannels()''',
           '''            }
            // Arrival -> home: drop the continuous high-accuracy GPS request.
            handler.post(this::ensureProfile);
        }
        firstFix = false;
    }

    private void createChannels()''')
    service_file.write_text(s, encoding='utf-8')
    print('PASS: GPS now adaptive: 120s balanced by day; 30s balanced at home; 5s precise outdoors in active hours.')
else:
    print('PASS: battery optimization already present; no second patch needed.')

t = tests_file.read_text(encoding='utf-8')
t = t.replace("\"versionName '2.2-android-auto-portao'\" in gradle and 'versionCode 7' in gradle",
              "\"versionName '2.3-gps-economico'\" in gradle and 'versionCode 8' in gradle")
check_anchor = "check('GPS rejects stale/inaccurate fixes and watchdog retries',"
if "check('Daytime and home use low-power location; nighttime outside retains fast GPS'," not in t:
    if t.count(check_anchor) != 1:
        raise RuntimeError('Could not locate structural checks anchor')
    t = t.replace(check_anchor, '''check('Daytime and home use low-power location; nighttime outside retains fast GPS',
      all(x in monitor for x in ['DAY_INTERVAL_MS = 120000L', 'HOME_INTERVAL_MS = 30000L',
                                'INTERVAL_MS = 5000L', 'PRIORITY_BALANCED_POWER_ACCURACY',
                                'PRIORITY_HIGH_ACCURACY', 'desiredProfile()',
                                'profileMode != desiredProfile()', 'handler.post(this::ensureProfile)',
                                'profileInterval(profileMode) * 3L']))
''' + check_anchor, 1)
tests_file.write_text(t, encoding='utf-8')
print('PASS: structural version and adaptive GPS assertions updated.')
