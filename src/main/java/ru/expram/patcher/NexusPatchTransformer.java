package ru.expram.patcher;

import javassist.ClassPool;
import javassist.CtClass;
import javassist.LoaderClassPath;
import ru.expram.patcher.patches.*;

import java.io.ByteArrayInputStream;
import java.lang.instrument.ClassFileTransformer;
import java.lang.instrument.IllegalClassFormatException;
import java.security.ProtectionDomain;
import java.util.HashMap;
import java.util.Map;

public class NexusPatchTransformer implements ClassFileTransformer {

    private static final String TRIAL_LICENSE_MANAGER_CLASS = "org.sonatype.licensing.trial.internal.DefaultTrialLicenseManager";
    private static final String OUTREACH_CLASS = "com.sonatype.nexus.plugins.outreach.internal.events.OutreachCapabilityBooter";
    private static final String USER_AGENT_CLASS = "org.sonatype.nexus.utils.httpclient.UserAgentGenerator";
    private static final String NEXUS_LICENSE_MANAGER_CLASS = "com.sonatype.nexus.licensing.ext.bootstrap.internal.NexusLicenseManager";
    private static final String LICENSE_SERVICE_CLASS = "com.sonatype.nexus.plugins.healthcheck.pro.internal.LicenseServiceImpl";

    private final Map<String, Patch> patches = new HashMap<>();

    @Override
    public byte[] transform(ClassLoader loader, String className, Class<?> classBeingRedefined, ProtectionDomain protectionDomain, byte[] classfileBuffer) throws IllegalClassFormatException {
        if (className == null) {
            return null;
        }

        String transformedClassName = className.replace("/", ".");
        patches.put(TRIAL_LICENSE_MANAGER_CLASS, new TrialLicenseManager());
        patches.put(OUTREACH_CLASS, new OutreachCapabilityBooter());
        patches.put(USER_AGENT_CLASS, new UserAgentGenerator());
        patches.put(NEXUS_LICENSE_MANAGER_CLASS, new NexusLicenseManager());
        patches.put(LICENSE_SERVICE_CLASS, new LicenseService());

        return process(transformedClassName, loader, classfileBuffer);
    }

    private byte[] process(String className, ClassLoader loader, byte[] classfileBuffer) {
        if(patches.containsKey(className)) {
            System.out.printf("Found patch for %s%n", className);
            Patch patch = patches.get(className);
            ClassPool classPool = new ClassPool(true);

            if (loader != null) {
                classPool.insertClassPath(new LoaderClassPath(loader));
            }

            CtClass ctClass = null;
            try {
                ctClass = classPool.makeClass(
                        new ByteArrayInputStream(classfileBuffer)
                );

                byte[] result = patch.process(loader, classfileBuffer, classPool, ctClass);
                System.out.printf("%s patched.%n", className);
                return result;
            } catch (Exception e) {
                System.out.printf("Patch for %s failed%n", className);
                e.printStackTrace();
            } finally {
                if (ctClass != null) {
                    ctClass.detach();
                }
            }
        }

        return null;
    }

}
