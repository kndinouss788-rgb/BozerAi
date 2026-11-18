package ai.bozer.app

import org.junit.runner.RunWith
import org.junit.runners.Suite

/**
 * Runs the real-model conversation first, then independent Android integration
 * tests in one instrumentation process. ProcessRestartPersistenceTest is kept
 * separate because CI must force-stop BOZER between the two runs.
 */
@RunWith(Suite::class)
@Suite.SuiteClasses(
    BozerBrainEndToEndTest::class,
    ChatDatabaseInstrumentedTest::class,
    ChatRepositoryInstrumentedTest::class,
    FloatingBubbleServiceInstrumentedTest::class,
    PhoneActionInstrumentedTest::class,
    VoicePathInstrumentedTest::class
)
class PreRestartInstrumentedSuite
