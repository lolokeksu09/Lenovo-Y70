# RAM Wiper alpha.4 modular MVVM

```
                  :app (Activity + composition root)
                /      |          |          \
          :data   :native-engine   :presentation
                \      |          /
                        :domain (Kotlin/JVM)
```

- **domain:** JVM-only models, memory safety policy, cleanup orchestration, typed ports, target eligibility and RunCleanupUseCase for execution-time verification of selected apps. No Android, Compose, Shizuku or JNI dependencies. The controller accepts injected allocator, memory source, shell, delay and clock.
- **data:** Android memory sampling, application catalog, preferences and history storage, Shizuku AIDL adapter. Typed access states replace string comparisons.
- **native-engine:** JNI adapter and Rust library build/packaging for arm64-v8a, armeabi-v7a and x86_64. Retains the original JNI class name and session ownership.
- **presentation:** immutable ScreenState, ViewModel with injected domain contracts, lifecycle-aware Compose rendering. Screens receive events and state. No direct access to concrete adapters.
- **app:** composition root owns construction and injects dependencies using ViewModelProvider, no global service locator. Retains package, manifest/provider and existing resource theme.

## Compatibility and acceptance
Preserve applicationId com.ramwiper.next, preference file memory, existing preference keys and JSON record schema, selected-app limit, 15-second cooldown and foreground cancellation. No automatic cleanup or broader privileges. Initial preferences/history load off-main; mutations are serialized and errors visible. Revalidate selected targets before execution. CI runs pure JVM domain tests, data codec compatibility tests, presentation state guards, Rust tests, Android assembleDebug and packaged native ABI/export/alignment checks. The host JSON dependency is test-only; production still uses Android org.json. Invalid JSON root fails loading instead of silently overwriting the journal. Device behavior, APK update compatibility and visual verification require target testing. Module migration is not a claim that device behavior has been verified.

## Alpha.5
AutomationPolicy, AutomationGate and HistoryAnalytics are domain-only. WorkManager integration, AppWidgetProvider and TileService live in app; the worker reads the data adapter and cannot call native cleanup. BackgroundMonitor is a domain port injected into presentation. Existing module boundaries retained.

NativeLimits is part of UserSettings; the cleanup port receives validated overrides and the controller applies the stricter preset or user bound. MeminfoParser and diagnostic models live in domain; the bounded command remains in data and the structured view in presentation.
