package ru.expram.patcher.patches;

import javassist.ClassPool;
import javassist.CtClass;
import javassist.CtMethod;
import ru.expram.patcher.Patch;

public class NexusLicenseManager implements Patch {

    private static final String REPLACE_LICENSE_FILE_BODY = """
            { return new byte[] { 42 }; }
            """;

    @Override
    public byte[] process(ClassLoader loader, byte[] classfileBuffer, ClassPool classPool, CtClass ctClass) throws Exception {
        CtMethod getLicenseMethod = ctClass.getDeclaredMethod("getLicenseFile",
                new CtClass[]{}
        );

        getLicenseMethod.setBody(REPLACE_LICENSE_FILE_BODY);

        return ctClass.toBytecode();
    }
}
