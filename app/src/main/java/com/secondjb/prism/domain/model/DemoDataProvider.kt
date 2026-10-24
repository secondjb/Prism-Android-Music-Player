package com.secondjb.prism.domain.model

/**
 * Sample tracks with synchronized LRC lyrics and high-resolution metadata
 * used for Demo Mode and Compose Previews.
 */
object DemoDataProvider {

    const val SAMPLE_LRC_1 = """[00:00.00]Prism Music Player
[00:04.50]Dancing in the shadows of the neon light
[00:09.20]Listening to waveforms cutting through the night
[00:14.00]Crystal clear frequencies, lossless in the air
[00:18.80]Twenty-four bit audio without a single care
[00:23.50]Feel the rhythm flowing right into your soul
[00:28.10]Prism Android player taking full control
[00:33.00]Swipe the player down to see the rhythm rise
[00:37.80]Synchronized lyrics right before your eyes
[00:43.00]Every single beat in harmony and grace
[00:48.20]Music companion in your pocket space"""

    const val SAMPLE_LRC_2 = """[00:00.00]Starfall Resonance
[00:05.10]Gazing at the starlight shining from afar
[00:10.40]Traveling through galaxies beyond where we are
[00:16.20]High-res soundscapes floating in the deep
[00:21.80]A melody of wonder that we forever keep
[00:27.50]Through the cosmic waves we ride
[00:33.00]With the Prism player by our side"""

    val demoTracks: List<Track> = listOf(
        Track(
            id = "demo_1",
            uri = "https://www.soundhelix.com/examples/mp3/SoundHelix-Song-1.mp3",
            title = "Neon Waveform",
            artist = "Prism Synthetics",
            album = "Material Echoes",
            durationMs = 212000L,
            sampleRate = 96000,
            bitDepth = 24,
            channels = 2,
            bitRateKbps = 2450,
            format = "FLAC",
            artworkUri = "https://picsum.photos/seed/prism1/600/600",
            lyrics = SAMPLE_LRC_1,
            genre = "Electronic",
            year = 2026
        ),
        Track(
            id = "demo_2",
            uri = "https://www.soundhelix.com/examples/mp3/SoundHelix-Song-2.mp3",
            title = "Starfall Resonance",
            artist = "Astral Horizon",
            album = "Deep Space Odyssey",
            durationMs = 184000L,
            sampleRate = 192000,
            bitDepth = 24,
            channels = 2,
            bitRateKbps = 4800,
            format = "FLAC",
            artworkUri = "https://picsum.photos/seed/prism2/600/600",
            lyrics = SAMPLE_LRC_2,
            genre = "Ambient Hi-Res",
            year = 2026
        ),
        Track(
            id = "demo_3",
            uri = "https://www.soundhelix.com/examples/mp3/SoundHelix-Song-3.mp3",
            title = "Midnight Expressway",
            artist = "Tokyo Nights",
            album = "Cyber Metro",
            durationMs = 245000L,
            sampleRate = 44100,
            bitDepth = 16,
            channels = 2,
            bitRateKbps = 320,
            format = "MP3",
            artworkUri = "https://picsum.photos/seed/prism3/600/600",
            genre = "Synthwave",
            year = 2025
        ),
        Track(
            id = "demo_4",
            uri = "https://www.soundhelix.com/examples/mp3/SoundHelix-Song-4.mp3",
            title = "Sunset Boulevard",
            artist = "Sunset Collective",
            album = "Golden Hour",
            durationMs = 195000L,
            sampleRate = 48000,
            bitDepth = 24,
            channels = 2,
            bitRateKbps = 1411,
            format = "FLAC",
            artworkUri = "https://picsum.photos/seed/prism4/600/600",
            genre = "Chillout",
            year = 2024
        )
    )
}
