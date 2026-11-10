package ru.expram.patcher.patches;

import javassist.CannotCompileException;
import javassist.ClassPool;
import javassist.CtClass;
import javassist.CtMethod;
import javassist.expr.ExprEditor;
import javassist.expr.MethodCall;
import ru.expram.patcher.Patch;

public class UserAgentGenerator implements Patch {

    @Override
    public byte[] process(ClassLoader loader, byte[] classfileBuffer, ClassPool classPool, CtClass ctClass) throws Exception {
        CtMethod generate = ctClass.getDeclaredMethod("generate");

        boolean[] patched = {false};

        generate.instrument(new ExprEditor() {
            @Override
            public void edit(MethodCall call)
                    throws CannotCompileException {

                if (call.getClassName().equals("java.lang.String")
                        && call.getMethodName().equals("format")
                        && call.getSignature().equals(
                        "(Ljava/lang/String;[Ljava/lang/Object;)Ljava/lang/String;"
                )) {

                    call.replace(
                            "{ $2[1] = \"COMMUNITY\"; $_ = $proceed($$); }"
                    );

                    patched[0] = true;
                }
            }
        });

        if (!patched[0]) {
            throw new IllegalStateException(
                    "String.format invocation not found"
            );
        }

        CtMethod analytics = ctClass.getDeclaredMethod(
                "buildUserAgentForAnalytics"
        );

        analytics.setBody(
                "{ return generate().replace(\")\", \"; pae)\"); }"
        );

        return ctClass.toBytecode();
    }
}
