use std::{ffi::c_void, sync::{Mutex, OnceLock}};
const BLOCK: usize = 16 * 1024 * 1024;
const MAX: usize = 768 * 1024 * 1024;

struct Mapping { address: usize, len: usize }
impl Mapping {
    fn new(len: usize) -> Option<Self> {
        let ptr = unsafe { libc::mmap(std::ptr::null_mut(), len, libc::PROT_READ | libc::PROT_WRITE, libc::MAP_PRIVATE | libc::MAP_ANONYMOUS, -1, 0) };
        if ptr == libc::MAP_FAILED { return None; }
        let mapping = Self { address: ptr as usize, len };
        let mut x = 0x9e3779b97f4a7c15u64 ^ mapping.address as u64;
        for i in 0..len / 8 {
            x ^= x << 13; x ^= x >> 7; x ^= x << 17;
            unsafe { (ptr as *mut u64).add(i).write_volatile(x); }
        }
        Some(mapping)
    }
    fn unmap(&mut self) -> bool {
        if self.len == 0 { return true; }
        if unsafe { libc::munmap(self.address as *mut c_void, self.len) } != 0 { return false; }
        self.len = 0;
        true
    }
}
impl Drop for Mapping {
    fn drop(&mut self) { let _ = self.unmap(); }
}
struct Session { token: i64, blocks: Vec<Mapping>, bytes: usize }
#[derive(Default)]
struct Registry { active: Option<Session>, next: i64 }
impl Registry {
    fn begin(&mut self) -> i64 {
        if self.active.is_some() { return 0; }
        let Some(token) = self.next.checked_add(1) else { return 0 };
        self.next = token;
        self.active = Some(Session { token, blocks: Vec::new(), bytes: 0 });
        token
    }
    fn append(&mut self, token: i64, len: usize) -> bool {
        let Some(session) = self.active.as_mut() else { return false; };
        if token != session.token || len != BLOCK || session.bytes > MAX - len { return false; }
        let Some(mapping) = Mapping::new(len) else { return false; };
        session.blocks.push(mapping);
        session.bytes += len;
        true
    }
    fn finish(&mut self, token: i64) -> bool {
        let Some(session) = self.active.as_mut() else { return false; };
        if token != session.token { return false; }
        // Keep failed mappings owned by the registry. Never release a different session.
        session.blocks.retain_mut(|mapping| !mapping.unmap());
        session.bytes = session.blocks.iter().map(|m| m.len).sum();
        if !session.blocks.is_empty() { return false; }
        self.active = None;
        true
    }
}
fn registry() -> &'static Mutex<Registry> {
    static REGISTRY: OnceLock<Mutex<Registry>> = OnceLock::new();
    REGISTRY.get_or_init(|| Mutex::new(Registry::default()))
}
#[no_mangle]
pub extern "system" fn Java_com_ramwiper_next_NativeWiper_begin(_: *mut c_void, _: *mut c_void) -> i64 {
    registry().lock().map(|mut p| p.begin()).unwrap_or(0)
}
#[no_mangle]
pub extern "system" fn Java_com_ramwiper_next_NativeWiper_append(_: *mut c_void, _: *mut c_void, token: i64, bytes: i32) -> u8 {
    if bytes <= 0 { return 0; }
    registry().lock().map(|mut p| p.append(token, bytes as usize) as u8).unwrap_or(0)
}
#[no_mangle]
pub extern "system" fn Java_com_ramwiper_next_NativeWiper_finish(_: *mut c_void, _: *mut c_void, token: i64) -> u8 {
    registry().lock().map(|mut p| p.finish(token) as u8).unwrap_or(0)
}
#[cfg(test)]
mod tests {
    use super::*;
    #[test] fn session_is_exclusive_and_tokens_are_not_reused() {
        let mut r = Registry::default();
        let a = r.begin(); assert!(a > 0);
        assert_eq!(r.begin(), 0);
        assert!(r.append(a, BLOCK));
        assert!(!r.finish(a + 1));
        assert!(r.finish(a)); assert!(!r.finish(a));
        let b = r.begin(); assert!(b > a);
        assert!(!r.append(a, BLOCK)); assert!(!r.finish(a));
        assert!(r.append(b, BLOCK)); assert!(r.finish(b));
    }
    #[test] fn invalid_sizes_and_cap_are_rejected() {
        let mut r = Registry::default();
        let a = r.begin();
        for n in [0, BLOCK - 1, BLOCK + 1, usize::MAX] { assert!(!r.append(a, n)); }
        r.active.as_mut().unwrap().bytes = MAX;
        assert!(!r.append(a, BLOCK));
        assert!(r.finish(a));
    }
    #[test] fn finish_releases_all_owned_mappings() {
        let mut r = Registry::default();
        let a = r.begin(); assert!(r.append(a, BLOCK)); assert!(r.append(a, BLOCK));
        assert_eq!(r.active.as_ref().unwrap().bytes, BLOCK * 2);
        assert!(r.finish(a)); assert!(r.active.is_none());
        assert!(!r.append(a, BLOCK));
    }
}
