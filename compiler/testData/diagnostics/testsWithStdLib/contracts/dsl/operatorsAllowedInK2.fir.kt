// RUN_PIPELINE_TILL: BACKEND
// OPT_IN: kotlin.contracts.ExperimentalContracts

import kotlin.contracts.*

class A(var v: Int = 0)

// plus & unary plus

operator fun Boolean.plus(x: Boolean): Boolean {
    contract { returns() implies (x) }
    return x
}

operator fun Any.unaryPlus(): Boolean {
    contract {
        returns(true) implies (this@unaryPlus is Int)
    }
    return this is Int
}

fun test_plus(x: Any) {
    if (true + (x is String)) {
        x.length
    }
    if (+x) {
        x.toChar()
    }
}

// minus and unaryMinus

operator fun Boolean.minus(x: Boolean): Boolean {
    contract { returns() implies (x) }
    return x
}

operator fun Any.unaryMinus(): Boolean {
    contract {
        returns(true) implies (this@unaryMinus is String)
    }
    return this is String
}

fun test_minus(x: Any) {
    if (true - (x is Int)) {
        x.toChar()
    }
}

fun test_unaryMinus(x: Any) {
    if (-x) {
        x.length
    }
}

// not

operator fun Any.not(): Boolean {
    contract {
        returns(false) implies (this@not is Int)
    }
    return this !is Int
}

fun test_not(x: Any) {
    if (!!x) {
        x.toChar()
    }
}

// inc and dec

operator fun Any.inc(): Int {
    contract {
        returns() implies (this@inc is Int)
    }
    return (this as Int) + 1
}

operator fun Any.dec(): Int {
    contract {
        returns() implies (this@dec is Int)
    }
    return (this as Int) - 1
}

fun test_inc_dec(ix1: Any, ix2: Any) {
    var x1 = ix1
    x1++
    x1.toChar()
    var x2 = ix2
    x2--
    x2.toChar()
}

// invoke

operator fun A.invoke(i: Int?): Int {
    contract { returns() implies (i != null) }
    return v
}

fun test_invoke(a: A, i: Int?) {
    if (a(i) == 0) {
        i + 1
    }
}

// *Assign

operator fun A.plusAssign(body: () -> Int) {
    contract { callsInPlace(body, InvocationKind.EXACTLY_ONCE) }
    v += + body()
}

operator fun A.minusAssign(body: () -> Int) {
    contract { callsInPlace(body, InvocationKind.EXACTLY_ONCE) }
    v -= + body()
}

operator fun A.timesAssign(body: () -> Int) {
    contract { callsInPlace(body, InvocationKind.EXACTLY_ONCE) }
    v *= + body()
}

operator fun A.divAssign(body: () -> Int) {
    contract { callsInPlace(body, InvocationKind.EXACTLY_ONCE) }
    v /= + body()
}

operator fun A.remAssign(body: () -> Int) {
    contract { callsInPlace(body, InvocationKind.EXACTLY_ONCE) }
    v %= + body()
}

fun test_xAssign() {
    var a = A()
    val plus: Boolean; a += { plus = true; 1 }
    val minus: Boolean; a -= { minus = true; 1 }
    val times: Boolean; a *= { times = true; 1 }
    val div: Boolean; a /= { div = true; 1 }
    val rem: Boolean; a %= { rem = true; 1 }
}

// range and related operators

class Range(val from : A, val to: A?)

class It(val from: A, val to: A?) {
    var a = from.v

    operator fun next(): A {
        val next = A(a)
        a++
        return next
    }

    operator fun hasNext(): Boolean = a <= to?.v ?: 100
}

operator fun A.rangeTo(body: () -> A): Range {
    contract { callsInPlace(body, InvocationKind.EXACTLY_ONCE) }
    return Range(this, body())
}

operator fun A.rangeUntil(body: () -> A): Range {
    contract { callsInPlace(body, InvocationKind.EXACTLY_ONCE) }
    return Range(this, body())
}

operator fun Range.contains(element: A?): Boolean {
    contract { returns(true) implies (element != null) } // KT-34132
    if (element == null) return false
    return (from.v..(to?.v ?: 100)).contains(element.v)
}

operator fun Any.iterator(): It {
    contract { returns() implies (this@iterator is Range) }
    this@iterator as Range
    return It(from, to)
}

fun test_ranges(r: Any, aa: A?) {
    var a = A()
    val r1b: Boolean;
    val r1 = a..{ r1b = true; A(1) }
    val r2b: Boolean;
    val r2 = a..<{ r2b = true; A(1) }

    val x =
        if (aa in r1) aa.v //KT-34132
        else 0

    for (y in r) {
        r.from
    }
}

// indexed access

operator fun A.get(i: Int?): Int {
    contract { returns() implies (i != null) }
    return v
}

operator fun A?.set(i: Int, vnew: Int) {
    contract { returns() implies (this@set != null) }
    this!!
    v = vnew
}

fun test_indexed(a1: A, i: Int?, a2: A?) {
    if (a1[i] == 0) {
        i + 1
    }
    a2[0] = 1
    a2.v
}
