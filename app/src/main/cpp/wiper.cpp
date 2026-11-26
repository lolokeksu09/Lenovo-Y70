#include <jni.h>
#include <sys/mman.h>
#include <cstdint>
#include <cstddef>

extern "C" JNIEXPORT jlong JNICALL
Java_com_ramwiper_next_NativeWiper_allocate(JNIEnv*, jobject, jint bytes) {
    // Limit each allocation; caller releases every successful mapping in finally.
    if (bytes <= 0 || bytes > 32 * 1024 * 1024 || bytes % 4096 != 0) return 0;
    void* ptr = mmap(nullptr, static_cast<size_t>(bytes), PROT_READ | PROT_WRITE,
                     MAP_PRIVATE | MAP_ANONYMOUS, -1, 0);
    if (ptr == MAP_FAILED) return 0;
    auto* out = static_cast<volatile uint64_t*>(ptr);
    uint64_t x = 0x9e3779b97f4a7c15ULL ^ reinterpret_cast<uintptr_t>(ptr);
    // Commit pages with incompressible-ish data, not merely virtual address space.
    for (size_t i = 0; i < static_cast<size_t>(bytes) / sizeof(uint64_t); ++i) {
        x ^= x << 13; x ^= x >> 7; x ^= x << 17; out[i] = x;
    }
    return static_cast<jlong>(reinterpret_cast<uintptr_t>(ptr));
}
extern "C" JNIEXPORT jboolean JNICALL
Java_com_ramwiper_next_NativeWiper_release(JNIEnv*, jobject, jlong address, jint bytes) {
    if (address == 0 || bytes <= 0 || bytes > 32 * 1024 * 1024 || bytes % 4096 != 0) return JNI_FALSE;
    return munmap(reinterpret_cast<void*>(static_cast<uintptr_t>(address)), static_cast<size_t>(bytes)) == 0;
}
