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

## Alpha.6
AppMemorySampler revalidates the catalog, performs a read-only bounded sequential batch via PrivilegedAccess, and returns typed samples. RunCleanupUseCase optionally records paired measurements for explicitly selected apps. CSV generation is pure domain; Activity owns the SAF result contract and output stream, ViewModel freezes the records and owns single-flight export. Stored comparison keys are optional; no change to applicationId, JNI or native limits.

## Alpha.7
AdaptivePolicy is a pure domain planner used by controller and UI. NativeLimits retains opt-in adaptive flag. Actual allocated profile, plan budget, reason and starting available percentage are nullable history fields and CSV columns. StrategyAnalytics groups measured manual operations by observed allocation and accepted commands, not a claimed killed-process count.

## Alpha.8
SelectionPresets provides bounded reusable selections, validated on save and apply. PresetCodec stores names and packages in preferences; applying does not execute cleanup. Optional extended measurement lives in the existing controller and records separate nullable RAM/Swap results at approximately 10 seconds after the initial post-release sample. RetentionAnalytics compares only paired valid measurements. Automation retains default limits and no extra measurement.
