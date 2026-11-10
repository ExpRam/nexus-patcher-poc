package ru.expram.patcher.patches;

import javassist.CannotCompileException;
import javassist.ClassPool;
import javassist.CtClass;
import javassist.CtMethod;
import javassist.expr.ExprEditor;
import javassist.expr.MethodCall;
import ru.expram.patcher.Patch;

public class OutreachCapabilityBooter implements Patch {

    @Override
    public byte[] process(ClassLoader loader, byte[] classfileBuffer, ClassPool classPool, CtClass ctClass) throws Exception {
        CtMethod bootMethod = ctClass.getDeclaredMethod("boot");

        boolean[] patched = {false};

        bootMethod.instrument(new ExprEditor() {
            @Override
            public void edit(MethodCall call) throws CannotCompileException {
                if (call.getMethodName().equals("maybeAddCapability")
                        && !patched[0]) {

                    String invocation =
                            "$proceed($1, $2, false, $4, $5)";

                    String replacement =
                            call.getSignature().endsWith(")V")
                                    ? "{ " + invocation + "; }"
                                    : "{ $_ = " + invocation + "; }";

                    call.replace(replacement);

                    patched[0] = true;
                }
            }
        });

        if (!patched[0]) {
            throw new IllegalStateException(
                    "maybeAddCapability invocation not found"
            );
        }

        return ctClass.toBytecode();
    }
}
