// !LANGUAGE: +NewInference
// !DIAGNOSTICS: -UNUSED_EXPRESSION
// SKIP_TXT

// TESTCASE NUMBER: 1
class Case1<T : Number> {
    inline fun <reified T : CharSequence>case_1(x: Any?) {
        if (x is T) {
            <!DEBUG_INFO_EXPRESSION_TYPE("T & kotlin.Any?")!>x<!>
            <!DEBUG_INFO_EXPRESSION_TYPE("T & kotlin.Any?")!>x<!>.<!INAPPLICABLE_CANDIDATE!>toByte<!>()
        }
    }
}
