package ru.expram.patcher.patches;

import javassist.*;
import ru.expram.patcher.Patch;

import java.util.Arrays;
import java.util.List;
import java.util.UUID;

public class TrialLicenseManager implements Patch {

    private static final int EXPIRATION_YEAR = 3000;

    private static final String REPLACE_LICENSE_METHOD_1 = "installLicense";
    private static final String REPLACE_LICENSE_METHOD_2 = "verifyLicense";
    private static final String REPLACE_LICENSE_BODY = """
            { return this.basePatch(); }
            """;

    private static final String[] PACKAGES_TO_IMPORT = {
            "com.sonatype.nexus.bootstrap.entrypoint.licensing",
            "com.sonatype.nexus.bootstrap.entrypoint.analytics.telemetry",
            "org.sonatype.licensing.feature",
            "org.sonatype.licensing",
            "java.util"
    };

    private static final String PATCH_METHOD = """
    public LicenseKey basePatch() {
        LicenseKey key = (LicenseKey) this.%s.get();
        key.setContactName("ExpRam");
        key.setContactCompany("%s");
        key.setContactEmailAddress("%s@expram.ru");
        key.setContactTelephone("4242424242");
        key.setContactCountry("USA");
        key.setExpirationDate(new Date(%d, 1, 1));
        key.setEffectiveDate(new Date(115, 1, 1));
        key.setEvaluation(true);

        FeatureSet fSet = new FeatureSet();
        fSet.addFeature((Feature)new NexusProfessionalFeature());
        fSet.addFeature((Feature)new NexusClmFeature());
        fSet.addFeature((Feature)new NexusFirewallFeature());
        fSet.addFeature((Feature)new TelemetryOptOutFeature());

        key.setFeatureSet(fSet);
        return key;
    }
    """;

    @Override
    public byte[] process(ClassLoader loader, byte[] classfileBuffer, ClassPool classPool, CtClass ctClass) throws Exception {
        Arrays.stream(PACKAGES_TO_IMPORT)
                .forEach(classPool::importPackage);

        CtMethod patchMethod = CtNewMethod.make(
                PATCH_METHOD.formatted(
                        findLicenseKeyProviderField(ctClass),
                        UUID.randomUUID(), UUID.randomUUID(),
                        EXPIRATION_YEAR - 1900
                ),
                ctClass
        );

        ctClass.addMethod(patchMethod);

        replaceLicenseMethods(classPool, ctClass);
        return ctClass.toBytecode();
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
