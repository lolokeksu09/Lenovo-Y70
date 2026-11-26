package com.ramwiper.next
fun main() {
    val n = Snapshot(16L*1024*MIB,6L*1024*MIB,256*MIB,false)
    check(Policy.budget(n,Profile.BALANCED)==512*MIB)
    check(Policy.budget(n,Profile.SOFT)==128*MIB)
    check(Policy.budget(n,Profile.STRONG)==768*MIB)
    for (s in listOf(n.copy(low=true),n.copy(thermal=3),n.copy(psiSome=10.0),n.copy(available=512*MIB))) { check(Policy.budget(s,Profile.STRONG)==0L); check(Policy.next(s,64*MIB)==0) }
    check(Policy.next(n,MIB)==0)
    check(Policy.next(n,16*MIB)==Policy.BLOCK)
    check(!Policy.shouldRunNative(n,n.copy(available=n.available+128*MIB)))
    check(Policy.shouldRunNative(n,n.copy(available=n.available+127*MIB)))
    println("Policy smoke: 15 assertions passed")
}
