run-rigtest.ps1 -Name configcheck -Mode defaults picks this folder instead of rigconfig/.

It holds no voyager-server.toml on purpose: nothing is seeded, NeoForge writes the file from the
mod's own defaults, and the probe checks that the getters return the defaults of the 0.4.0 plan.
