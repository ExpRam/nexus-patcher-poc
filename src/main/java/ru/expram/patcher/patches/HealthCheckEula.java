package ru.expram.patcher.patches;

import javassist.ClassPool;
import javassist.CtClass;
import javassist.CtMethod;
import ru.expram.patcher.Patch;

public class HealthCheckEula implements Patch {
    @Override
    public byte[] process(ClassLoader loader, byte[] classfileBuffer, ClassPool classPool, CtClass ctClass) throws Exception {
        // nxrm cluster does not sync eula state between instances
        CtMethod m = ctClass.getDeclaredMethod("isEulaAccepted");
        m.setBody("{ return true; }");
        return ctClass.toBytecode();
    }
}