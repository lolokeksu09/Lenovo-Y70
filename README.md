# RAM Wiper 2.0.0-alpha.3

Независимая реализация по принципу RAM Wiper 1.2-1. Оригинальный APK не изменён. Новый applicationId: com.ramwiper.next (устанавливается отдельно).

## Реализовано
- Kotlin + Compose + AndroidViewModel, единый MemoryController, три стратегии (Native, Shizuku, Hybrid).
- Rust JNI: реестр непрозрачных дескрипторов, ограничение общего объёма и освобождение по владению. Нативное выделение/заполнение 16-МиБ блоков через mmap и освобождение через munmap; лимиты профилей 128/512/768 МиБ, резерв, проверки lowMemory, PSI и thermal (если доступны), кооперативная отмена.
- Shizuku UserService с ограниченным AIDL: am kill --user ID PACKAGE и dumpsys meminfo PACKAGE, без произвольного shell, force-stop и kill-all. Только явно выбранные сторонние launcher-приложения; максимум 12, исключения сохраняются. Код возврата команды не доказывает завершение процесса.
- Hybrid: сначала выбранные приложения (при наличии Shizuku), затем Native, если прирост меньше 128 МиБ и состояние позволяет. Без Shizuku — Native. Native-профили намеренно ограничены; эффект может быть меньше оригинального APK.
- Текущая доступная RAM, Swap (не равнозначно ZRAM), PSI/thermal при доступности, фактическое изменение доступной RAM, история до 100 записей. Изменение не доказывает причинность или прирост FPS.
- Настройки и история в SharedPreferences (для альфы). Без постоянного сервиса, запуска при загрузке, автоочистки, виджетов и плитки. Это следующий этап, не реализованные возможности. Исключения действуют только на выборочные команды приложения; Native/LMKD их не гарантирует.

## Сборка
JDK 17, Gradle 8.9, Android SDK 35, NDK 27.2.12479018, Rust 1.90.0 (Android targets aarch64-linux-android, armv7-linux-androideabi, x86_64-linux-android). Установить указанные инструменты, указать ANDROID_HOME и выполнить из корня:

    gradle --no-daemon :app:testDebugUnitTest :app:assembleDebug

APK: app/build/outputs/apk/debug/app-debug.apk. Gradle wrapper не включён, поскольку в окружении нет Gradle и доступа к загрузкам. Workflow .github/workflows/android.yml устанавливает Gradle и собирает debug APK в артефакт при запуске в GitHub Actions. Debug-подпись не совместима с оригиналом; новый пакет не требует удаления исходного приложения.

## Проверка
Host-проверка политики без Android SDK:

    kotlinc app/src/main/java/com/ramwiper/next/Policy.kt tools/PolicySmoke.kt -include-runtime -d /tmp/ram-policy.jar
    java -jar /tmp/ram-policy.jar

Android-сборка и тестирование на устройстве обязательны до релиза. Начинать с SOFT; проверять отмену, Shizuku (отказ/перезапуск), команды в выбранном профиле, давление и расход батареи. Любое давление на память может привести к завершению процессов или самого приложения; ограничения снижают риск, но не исключают его.

Эта отдельная ветка ram-wiper предназначена для независимой разработки RAM Wiper; не сливать с main Lenovo-Y70.

Для локальной сборки: rustup target add aarch64-linux-android armv7-linux-androideabi x86_64-linux-android; cargo test --manifest-path native/Cargo.toml. Gradle запускает tools/build-rust.sh до preBuild.

Alpha.3: scoped Rust session, explicit outcomes, immediate + 3-second measurement, Swap delta, 15-second cooldown and foreground cancellation. Legacy history retained. No guarantee of RAM improvement.
