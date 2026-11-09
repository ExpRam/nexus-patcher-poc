package ru.expram.patcher;

import javassist.*;
import javassist.expr.ExprEditor;
import javassist.expr.MethodCall;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.lang.instrument.ClassFileTransformer;
import java.lang.instrument.IllegalClassFormatException;
import java.security.ProtectionDomain;
import java.util.Arrays;
import java.util.List;

public class NexusPatchTransformer implements ClassFileTransformer {

    private static final int EXPIRATION_YEAR = 3000;

    private static final String[] PACKAGES_TO_IMPORT = {
            "com.sonatype.nexus.bootstrap.entrypoint.licensing",
            "com.sonatype.nexus.bootstrap.entrypoint.analytics.telemetry",
            "org.sonatype.licensing.feature",
            "org.sonatype.licensing",
            "java.util"
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
        fSet.addFeature((Feature)new TelemetryOptOutFeature());

        key.setFeatureSet(fSet);
        return key;
    }
    """;

    private static final String REPLACE_LICENSE_METHOD_1 = "installLicense";
    private static final String REPLACE_LICENSE_METHOD_2 = "verifyLicense";
    private static final String REPLACE_LICENSE_BODY = """
            { return this.basePatch(); }
            """;

    private static final String OUTREACH_CLASS =
            "com.sonatype.nexus.plugins.outreach.internal.events.OutreachCapabilityBooter";

    @Override
    public byte[] transform(ClassLoader loader, String className, Class<?> classBeingRedefined, ProtectionDomain protectionDomain, byte[] classfileBuffer) throws IllegalClassFormatException {
        if (className == null) {
            return null;
        }

        String transformedClassName = className.replace("/", ".");
        return switch (transformedClassName) {
            case LICENSE_MANAGER_CLASS:
                yield processLicenseManager(loader, classfileBuffer);
            case OUTREACH_CLASS:
                yield processOutreach(loader, classfileBuffer);
            default:
                yield null;
        };
    }


    private byte[] processOutreach(ClassLoader loader, byte[] classfileBuffer) {
        System.out.println("Found OutreachCapabilityBooter class.");

        ClassPool classPool = new ClassPool(true);

        if (loader != null) {
            classPool.insertClassPath(new LoaderClassPath(loader));
        }

        CtClass ctClass = null;

        try {
            ctClass = classPool.makeClass(
                    new ByteArrayInputStream(classfileBuffer)
            );

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

                        System.out.println("Outreach capability patched.");
                    }
                }
            });

            if (!patched[0]) {
                throw new IllegalStateException(
                        "maybeAddCapability invocation not found"
                );
            }

            return ctClass.toBytecode();

        } catch (Exception e) {
            System.out.println("Something went wrong...");
            e.printStackTrace();
            throw new RuntimeException(e);
        } finally {
            if (ctClass != null) {
                ctClass.detach();
            }
        }
    }


    private byte[] processLicenseManager(
            ClassLoader loader,
            byte[] classfileBuffer
    ) {
        System.out.println("Found DefaultTrialLicenseManager class.");

        ClassPool classPool = new ClassPool(true);

        if (loader != null) {
            classPool.insertClassPath(new LoaderClassPath(loader));
        }

        Arrays.stream(PACKAGES_TO_IMPORT)
                .forEach(classPool::importPackage);

        CtClass ctClass = null;

        try {
            ctClass = classPool.makeClass(
                    new ByteArrayInputStream(classfileBuffer)
            );

            CtMethod patchMethod = CtNewMethod.make(
                    PATCH_METHOD.formatted(
                            findLicenseKeyProviderField(ctClass),
                            EXPIRATION_YEAR - 1900
                    ),
                    ctClass
            );

            ctClass.addMethod(patchMethod);

            replaceLicenseMethods(classPool, ctClass);

            return ctClass.toBytecode();

        } catch (NotFoundException | CannotCompileException | IOException e) {
            System.out.println("Something went wrong...");
            e.printStackTrace();
            throw new RuntimeException(e);

        } finally {
            if (ctClass != null) {
                ctClass.detach();
            }
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
