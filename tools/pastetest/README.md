# The paste test

A throwaway server-side mod that pastes every Observatory and Photo Booth blueprint into a world
through Structurize itself, so the blueprints can be checked as built rather than as designed.

    javac -encoding UTF-8 --release 21 -proc:none -cp "../../stubs:../../libs/*" -d build VoyagerTest.java
    mkdir -p res/META-INF && cp neoforge.mods.toml res/META-INF/ && jar cf voyagertest-1.0.0.jar -C build . -C res .

Drop the jar next to voyager, minecolonies, structurize, blockui, domum-ornamentum, exposure,
exposure-space, exposure_expanded and architectury in a NeoForge 21.1 dedicated server's mods/,
start it, wait for the fifty "placing" lines, `save-all flush`, then read the world back:

    python3 worldcheck.py /path/to/server/world     # diffs, frames, stands, and the access audit, per building

One building every two seconds from tick 100, 64 blocks apart, anchor at y=150, chunks forced.
