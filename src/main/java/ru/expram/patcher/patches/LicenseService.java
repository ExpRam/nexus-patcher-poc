package ru.expram.patcher.patches;

import javassist.ClassPool;
import javassist.CtClass;
import javassist.CtMethod;
import ru.expram.patcher.Patch;

public class LicenseService implements Patch {
    @Override
    public byte[] process(ClassLoader loader, byte[] classfileBuffer, ClassPool classPool, CtClass ctClass) throws Exception {
        CtMethod getLicenseId =
                ctClass.getDeclaredMethod("getLicenseId");

        getLicenseId.setBody("{ return null; }");

        // Disables pro for rhc
        // No difference between regular rhc and pro: pro endpoints validates X-CLM-Token from header on sonatype`s back so pro endpoints are useless in this case
        CtMethod getLicenseKey =
                ctClass.getDeclaredMethod("getLicenseKey");

        getLicenseKey.setBody("{ return null; }");

        return ctClass.toBytecode();
    }
}
