package com.secondjb.prism

import com.secondjb.prism.core.data.PlaylistRepositoryTest
import com.secondjb.prism.core.metadata.AudioTagMetadataExtractorTest
import com.secondjb.prism.data.MediaStoreAudioRepositoryTest
import com.secondjb.prism.domain.DomainModelsTest
import com.secondjb.prism.playback.PlaybackEngineTest
import com.secondjb.prism.playback.PlaybackRegressionTest
import com.secondjb.prism.sync.SyncProtocolTest
import com.secondjb.prism.theme.PaletteThemingTest
import com.secondjb.prism.ui.ComponentsTest
import com.secondjb.prism.ui.LibraryUiStateTest
import com.secondjb.prism.ui.lyrics.LrcParserTest
import org.junit.runner.RunWith
import org.junit.runners.Suite

/**
 * Unified Prism Android Regression Test Suite.
 * Executes all domain, playback, theming, UI, lyrics, and sync tests
 * in a single sub-second pass.
 */
@RunWith(Suite::class)
@Suite.SuiteClasses(
    DomainModelsTest::class,
    PaletteThemingTest::class,
    PlaybackEngineTest::class,
    PlaybackRegressionTest::class,
    MediaStoreAudioRepositoryTest::class,
    LibraryUiStateTest::class,
    ComponentsTest::class,
    LrcParserTest::class,
    AudioTagMetadataExtractorTest::class,
    PlaylistRepositoryTest::class,
    SyncProtocolTest::class
)
class PrismRegressionTestSuite
