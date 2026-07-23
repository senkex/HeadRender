package com.github.senkex.headrender;

import org.junit.platform.launcher.Launcher;
import org.junit.platform.launcher.LauncherDiscoveryRequest;
import org.junit.platform.launcher.core.LauncherFactory;
import org.junit.platform.launcher.listeners.SummaryGeneratingListener;
import org.junit.platform.launcher.listeners.TestExecutionSummary;

import java.io.PrintWriter;

import static org.junit.platform.engine.discovery.DiscoverySelectors.selectPackage;
import static org.junit.platform.launcher.core.LauncherDiscoveryRequestBuilder.request;

/**
 * Runs the whole suite from a plain {@code java -jar}, no Gradle required.
 *
 * <pre>{@code
 * ./gradlew smokeJar
 * java -jar build/libs/HeadRender-<version>-smoke.jar
 * }</pre>
 *
 * <p>Handy because the Gradle wrapper here needs a JDK 17/21 while the machine
 * default may be newer; the produced jar runs on any JDK 17+.</p>
 *
 * <p>Exits {@code 0} when everything passed and {@code 1} otherwise, so it can
 * gate a release.</p>
 *
 * <p>Developed by <b>Senkex</b></p>
 */
public final class SmokeTest {

    private SmokeTest() {
        throw new UnsupportedOperationException("Entry point");
    }

    /**
     * Runs every test in the library and prints a summary.
     *
     * @param args ignored
     */
    public static void main(final String[] args) {
        final LauncherDiscoveryRequest discovery = request()
                .selectors(selectPackage("com.github.senkex.headrender"))
                .build();

        final Launcher launcher = LauncherFactory.create();
        final SummaryGeneratingListener listener = new SummaryGeneratingListener();
        launcher.execute(discovery, listener);

        final TestExecutionSummary summary = listener.getSummary();
        final PrintWriter out = new PrintWriter(System.out, true);

        summary.printTo(out);
        if (summary.getTotalFailureCount() > 0) {
            summary.printFailuresTo(out);
        }

        out.printf("%n  %d/%d tests passed in %d ms%n",
                summary.getTestsSucceededCount(),
                summary.getTestsFoundCount(),
                summary.getTimeFinished() - summary.getTimeStarted());
        out.println(summary.getTotalFailureCount() == 0 ? "  RESULT: ALL PASS" : "  RESULT: FAILURES");

        System.exit(summary.getTotalFailureCount() == 0 ? 0 : 1);
    }
}
