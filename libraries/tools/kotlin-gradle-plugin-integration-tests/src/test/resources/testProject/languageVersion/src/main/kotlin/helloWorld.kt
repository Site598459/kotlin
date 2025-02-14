fun test(a: Any) {
    when (a) {
        is Int if a > 10 -> {}
        else -> {}
    }
}
