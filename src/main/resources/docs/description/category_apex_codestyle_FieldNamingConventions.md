Since: PMD 6.15.0

Configurable naming conventions for field declarations. This rule reports variable declarations
            which do not match the regex that applies to their specific kind ---e.g. constants (static final),
            static field, final field. Each regex can be configured through properties.

            By default this rule uses the standard Apex naming convention (Camel case).

Example(s):
```
public class Foo {
    Integer instanceField; // This is in camel case, so it's ok

    Integer INSTANCE_FIELD; // This will be reported unless you change the regex
}
```