//#Patterns: category_java_codestyle_IfElseStmtsMustUseBraces
public class Foo {

    public void bar() {


        // this is OK
        if (foo) {
            x = x + 1;
        }
        else {
            x = x - 1;
        }

        if (foo)
            //#Warn: category_java_codestyle_IfElseStmtsMustUseBraces
            x = x+1;
        else
            //#Warn: category_java_codestyle_IfElseStmtsMustUseBraces
            x = x-1;
    }

}