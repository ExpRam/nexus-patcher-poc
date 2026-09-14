package ru.expram.patcher;

import javassist.*;

import java.io.IOException;
import java.lang.instrument.ClassFileTransformer;
import java.lang.instrument.IllegalClassFormatException;
import java.security.ProtectionDomain;
import java.util.Arrays;
import java.util.List;

public class NexusPatchTransformer implements ClassFileTransformer {

    private static final int EXPIRATION_YEAR = 3000;

    private static final String[] PACKAGES_TO_IMPORT = {
      "com.sonatype.nexus.bootstrap.entrypoint.licensing.NexusClmFeature",
      "com.sonatype.nexus.bootstrap.entrypoint.licensing.NexusFirewallFeature",
      "com.sonatype.nexus.bootstrap.entrypoint.licensing.NexusProfessionalFeature",
      "org.sonatype.licensing.feature.Feature",
      "org.sonatype.licensing.feature.FeatureSet",
      "org.sonatype.licensing.LicenseKey",
      "java.util.Date"
    };

    private static final String LICENSE_MANAGER_CLASS = "org.sonatype.licensing.trial.internal.DefaultTrialLicenseManager";
    private static final String PATCH_METHOD = """
    public LicenseKey basePatch() {
        LicenseKey key = (LicenseKey) this.%s.get();
        key.setContactName("ExpRam");
        key.setContactCompany("ExpRam");
        key.setContactEmailAddress("cortex@expram.ru");
        key.setContactTelephone("4242424242");
        key.setContactCountry("USA");
        key.setExpirationDate(new Date(%d, 1, 1));
        key.setEffectiveDate(new Date(115, 1, 1));
        key.setEvaluation(true);

        FeatureSet fSet = new FeatureSet();
        fSet.addFeature((Feature)new NexusProfessionalFeature());
        fSet.addFeature((Feature)new NexusClmFeature());
        fSet.addFeature((Feature)new NexusFirewallFeature());

        key.setFeatureSet(fSet);
        return key;
    }
    """;

    private static final String REPLACE_LICENSE_METHOD_1 = "installLicense";
    private static final String REPLACE_LICENSE_METHOD_2 = "verifyLicense";
    private static final String REPLACE_LICENSE_BODY = """
            { return this.basePatch(); }
            """;

    @Override
    public byte[] transform(ClassLoader loader, String className, Class<?> classBeingRedefined, ProtectionDomain protectionDomain, byte[] classfileBuffer) throws IllegalClassFormatException {
        String transformedClassName = className.replace("/", ".");
        if(!transformedClassName.equals(LICENSE_MANAGER_CLASS)) {
            return null;
        }

        System.out.println("Found DefaultTrialLicenseManager class.");
        ClassPool classPool = ClassPool.getDefault();

        Arrays.stream(PACKAGES_TO_IMPORT).forEach(classPool::importPackage);

        try {
            CtClass ctClass = classPool.getCtClass(LICENSE_MANAGER_CLASS);
            CtMethod patchMethod = CtNewMethod.make(PATCH_METHOD.formatted(
                    findLicenseKeyProviderField(ctClass),
                    EXPIRATION_YEAR - 1900
            ), ctClass);
            ctClass.addMethod(patchMethod);

            replaceLicenseMethods(classPool, ctClass);

            byte[] code = ctClass.toBytecode();
            ctClass.detach();

            return code;
        } catch (NotFoundException | CannotCompileException | IOException e) {
            System.out.println("Something went wrong...");
            e.printStackTrace();
            throw new RuntimeException(e);
        }

    }

    private String findLicenseKeyProviderField(CtClass ctClass)
            throws NotFoundException {

        for (CtField field : ctClass.getDeclaredFields()) {
            // private final
            int modifiers = field.getModifiers();

            if (!Modifier.isPrivate(modifiers) || !Modifier.isFinal(modifiers)) {
                continue;
            }

            // Provider
            if (!field.getType().getName().endsWith(".Provider")) {
                continue;
            }

            // Provider<LicenseKey>
            String genericSignature = field.getGenericSignature();

            if (genericSignature != null
                    && genericSignature.contains("LicenseKey")) {
                return field.getName();
            }
        }

        throw new IllegalStateException(
                "private final Provider<LicenseKey> field not found"
        );
    }

    private void replaceLicenseMethods(ClassPool classPool, CtClass ctClass) throws NotFoundException, CannotCompileException {
        CtClass trialParam = classPool.get(
                "org.sonatype.licensing.trial.TrialLicenseParam"
        );

        CtClass file = classPool.get("java.io.File");

        List<CtMethod> methods = List.of(
                ctClass.getDeclaredMethod(
                        REPLACE_LICENSE_METHOD_1,
                        new CtClass[]{trialParam, file}
                ),
                ctClass.getDeclaredMethod(
                        REPLACE_LICENSE_METHOD_2,
                        new CtClass[]{trialParam, file}
                ),
                ctClass.getDeclaredMethod(
                        REPLACE_LICENSE_METHOD_2,
                        new CtClass[]{trialParam}
                )
        );

        for (CtMethod method : methods) {
            method.setBody(REPLACE_LICENSE_BODY);
        }

    }
}
