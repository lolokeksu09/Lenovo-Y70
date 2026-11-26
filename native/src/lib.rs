use std::{collections::BTreeMap, ffi::c_void, sync::{Mutex, OnceLock}};
const BLOCK: usize = 16 * 1024 * 1024;
const MAX: usize = 768 * 1024 * 1024;

struct Mapping { address: usize, len: usize }
impl Drop for Mapping {
    fn drop(&mut self) { unsafe { libc::munmap(self.address as *mut c_void, self.len); } }
}
#[derive(Default)]
struct Pool { blocks: BTreeMap<i64, Mapping>, next: i64, bytes: usize }
impl Pool {
    fn allocate(&mut self, len: usize) -> i64 {
        if len != BLOCK || self.bytes > MAX - len { return 0; }
        let Some(id) = self.next.checked_add(1) else { return 0 };
        let ptr = unsafe { libc::mmap(std::ptr::null_mut(), len, libc::PROT_READ | libc::PROT_WRITE, libc::MAP_PRIVATE | libc::MAP_ANONYMOUS, -1, 0) };
        if ptr == libc::MAP_FAILED { return 0; }
        let mapping = Mapping { address: ptr as usize, len };
        // Fill committed pages with nonzero, varying values. mmap alone does not create physical memory pressure.
        let mut x = 0x9e3779b97f4a7c15u64 ^ mapping.address as u64;
        for i in 0..len / 8 {
            x ^= x << 13; x ^= x >> 7; x ^= x << 17;
            unsafe { (ptr as *mut u64).add(i).write_volatile(x); }
        }
        self.next = id;
        self.bytes += len;
        self.blocks.insert(id, mapping);
        id
    }
    fn release(&mut self, id: i64, len: usize) -> bool {
        let Some(m) = self.blocks.get(&id) else { return false };
        if m.len != len { return false; }
        // Only registry-owned mappings can be unmapped. Keep ownership on failure.
        if unsafe { libc::munmap(m.address as *mut c_void, len) } != 0 { return false; }
        if let Some(m) = self.blocks.remove(&id) { std::mem::forget(m); }
        self.bytes -= len;
        true
    }
    fn clear(&mut self) { self.blocks.clear(); self.bytes = 0; }
}
fn pool() -> &'static Mutex<Pool> {
    static POOL: OnceLock<Mutex<Pool>> = OnceLock::new();
    POOL.get_or_init(|| Mutex::new(Pool::default()))
}
#[no_mangle]
pub extern "system" fn Java_com_ramwiper_next_NativeWiper_allocate(_: *mut c_void, _: *mut c_void, bytes: i32) -> i64 {
    if bytes <= 0 { return 0; }
    pool().lock().map(|mut p| p.allocate(bytes as usize)).unwrap_or(0)
}
#[no_mangle]
pub extern "system" fn Java_com_ramwiper_next_NativeWiper_release(_: *mut c_void, _: *mut c_void, handle: i64, bytes: i32) -> u8 {
    if bytes <= 0 { return 0; }
    pool().lock().map(|mut p| p.release(handle, bytes as usize) as u8).unwrap_or(0)
}
#[no_mangle]
pub extern "system" fn Java_com_ramwiper_next_NativeWiper_releaseAll(_: *mut c_void, _: *mut c_void) {
    if let Ok(mut p) = pool().lock() { p.clear(); }
}
#[cfg(test)]
mod tests {
    use super::*;
    #[test] fn rejects_invalid_lengths() { let mut p = Pool::default(); assert_eq!(p.allocate(0),0); assert_eq!(p.allocate(BLOCK-1),0); assert_eq!(p.allocate(MAX+1),0); }
    #[test] fn handles_are_owned_and_single_use() {
        let mut p = Pool::default(); let id = p.allocate(BLOCK);
        assert!(id>0); assert_eq!(p.bytes,BLOCK); assert!(!p.release(id,BLOCK/2)); assert!(!p.release(id+1,BLOCK));
        assert!(p.release(id,BLOCK)); assert!(!p.release(id,BLOCK)); assert_eq!(p.bytes,0);
        let id2 = p.allocate(BLOCK); assert!(id2>id); p.clear(); assert_eq!(p.bytes,0); assert!(!p.release(id2,BLOCK));
    }
    #[test] fn cap_is_enforced_without_allocating_gigabytes() {
        let mut p = Pool { bytes: MAX, ..Default::default() }; assert_eq!(p.allocate(BLOCK),0);
    }
}
