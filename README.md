# RAM Wiper 2.0.0-alpha.5

Независимая реализация по принципу RAM Wiper 1.2-1. Оригинальный APK не изменён. Новый applicationId: com.ramwiper.next (устанавливается отдельно).

## Реализовано
- Kotlin + Compose + MVVM, независимый JVM-domain и ViewModel с внедряемыми зависимостями, единый MemoryController, три стратегии (Native, Shizuku, Hybrid).
- Rust JNI: реестр непрозрачных дескрипторов, ограничение общего объёма и освобождение по владению. Нативное выделение/заполнение 16-МиБ блоков через mmap и освобождение через munmap; лимиты профилей 128/512/768 МиБ, резерв, проверки lowMemory, PSI и thermal (если доступны), кооперативная отмена.
- Shizuku UserService с ограниченным AIDL: am kill --user ID PACKAGE и dumpsys meminfo PACKAGE, без произвольного shell, force-stop и kill-all. Только явно выбранные сторонние launcher-приложения; максимум 12, исключения сохраняются. Код возврата команды не доказывает завершение процесса.
- Hybrid: сначала выбранные приложения (при наличии Shizuku), затем Native, если прирост меньше 128 МиБ и состояние позволяет. Без Shizuku — Native. Native-профили намеренно ограничены; эффект может быть меньше оригинального APK.
- Текущая доступная RAM, Swap (не равнозначно ZRAM), PSI/thermal при доступности, фактическое изменение доступной RAM, история до 100 записей. Изменение не доказывает причинность или прирост FPS.
- Опциональная автоочистка на открытом экране: два замера, порог 15–40%, минимальный интервал 1/5/15/30 мин, профиль SOFT, без автоматического Shizuku. Отключена по умолчанию.
- Опциональные фоновые уведомления через WorkManager: периодические замеры от 15 минут (Android может отложить), без фоновой очистки; требуется разрешение уведомлений.
- Виджет RAM с ручным обновлением и системным обновлением от 30 минут; плитка быстрых настроек показывает последний замер при открытии шторки и открывает приложение. Не запускает очистку без видимого экрана.
- Журнал с фильтром ручных/автоматических операций, результатами и медианой контрольного изменения RAM. Настройки и история остаются в SharedPreferences. Исключения действуют только на выборочные команды приложения; Native/LMKD их не гарантирует.

## Сборка
JDK 17, Gradle 8.9, Android SDK 35, NDK 27.2.12479018, Rust 1.90.0 (Android targets aarch64-linux-android, armv7-linux-androideabi, x86_64-linux-android). Установить указанные инструменты, указать ANDROID_HOME и выполнить из корня:

    gradle --no-daemon :domain:test :data:testDebugUnitTest :presentation:testDebugUnitTest :app:assembleDebug

APK: app/build/outputs/apk/debug/app-debug.apk. Gradle wrapper не включён, поскольку в окружении нет Gradle и доступа к загрузкам. Workflow .github/workflows/android.yml устанавливает Gradle и собирает debug APK в артефакт при запуске в GitHub Actions. Debug-подпись не совместима с оригиналом; новый пакет не требует удаления исходного приложения.

## Проверка
Host-проверка политики без Android SDK:

    kotlinc domain/src/main/kotlin/com/ramwiper/next/domain/{Policy,NativeLimits}.kt tools/PolicySmoke.kt -include-runtime -d /tmp/ram-policy.jar
    java -jar /tmp/ram-policy.jar

Android-сборка и тестирование на устройстве обязательны до релиза. Фоновая работа, виджет, плитка и разрешения требуют отдельной проверки на устройстве. Начинать с SOFT; проверять отмену, Shizuku (отказ/перезапуск), команды в выбранном профиле, давление и расход батареи. Любое давление на память может привести к завершению процессов или самого приложения; ограничения снижают риск, но не исключают его.

Эта отдельная ветка ram-wiper предназначена для независимой разработки RAM Wiper; не сливать с main Lenovo-Y70.

Для локальной сборки: rustup target add aarch64-linux-android armv7-linux-androideabi x86_64-linux-android; cargo test --manifest-path native/Cargo.toml. Gradle запускает tools/build-rust.sh в модуле :native-engine до preBuild. После сборки CI проверяет ELF ABI, JNI-экспорты и выравнивание сегментов в APK.

Alpha.3: scoped Rust session, explicit outcomes, immediate + 3-second measurement, Swap delta, 15-second cooldown and foreground cancellation. Legacy history retained. No guarantee of RAM improvement.

Alpha.4: пять Gradle-модулей, чистый JVM-domain, внедрение зависимостей, UI state + callbacks, проверка выбора при запуске, тесты совместимости журнала. См. docs/ARCHITECTURE.md.

Диагностика приложений: разбор PSS/RSS/Swap PSS по PID, агрегирование только доступных показателей, исходный вывод по запросу, тайм-аут команды. Неизвестные данные не подменяются нулями.

Ручные ограничения Rust: максимум 64–768 МиБ и удержание 0–350 мс (или значения профиля), с обязательным ограничением выбранным профилем. Автоматический режим остаётся SOFT с его стандартными пределами.
