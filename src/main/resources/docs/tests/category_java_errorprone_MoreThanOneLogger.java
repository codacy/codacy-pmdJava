//#Patterns: category_java_errorprone_MoreThanOneLogger

//#Warn: category_java_errorprone_MoreThanOneLogger
public class Foo {
    Logger log = Logger.getLogger(Foo.class.getName());
    // It is very rare to see two loggers on a class, normally
    // log information is multiplexed by levels

    Logger log2 = Logger.getLogger(Foo.class.getName());
}
