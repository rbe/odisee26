// CodeNarc is the linter of Groovy. Checkstyle owns Java.
// The sets hold rules that find a defect. The layout of a file is not checked.
ruleset {
    ruleset('rulesets/basic.xml') {
        // The code masks bytes with & in conditions on purpose. The rule reports each of them.
        exclude 'BitwiseOperatorInConditional'
    }
    ruleset('rulesets/imports.xml')
    ruleset('rulesets/unused.xml')
    ruleset('rulesets/exceptions.xml')
}
