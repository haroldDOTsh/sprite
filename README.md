# sprite
<p align="center">
  <img width="160" height="160" alt="Portfolio_BE1" src="https://github.com/user-attachments/assets/5400a107-b3b2-45d2-86d0-bd940ab762f8" />
</p>


**sprite** is a tool for designers to explore Minecraft's newly introduced ability to display arbitrary Minecraft textures inside chat components, titles, and any other Adventure compatible surfaces. Throw it into a modern Paper server, and you'll get a searchable atlas browser, and a texture preview list that keeps itself updated with every release.
> [!TIP]
> sprite does **NOT** use resource packs! You can view all of this in plan vanilla!

> [!NOTE]
> sprite is currently tested against `1.21.9/1.21.10` and `Adventure 4.25.0`. These are the minimum requirements.

## What it does

- Unfortunately, Mojang's asset CDN doesn't release these files (and if they do, I might be blind), so we download and cache the matching Mojang client jar, extract `/atlases` JSON, then index the textures to result in the sprite keys.
- using `/sprite` will render a paginated chat GUI showing each atlas, its sprite count, and quick to all of  them.
- Click any `[icon]` button to throw the sprite into a title bar for a few seconds (duration is configurable).
- Shareable snippets: every sprite row includes click to copy buttons for MiniMessage tags and the raw JSON component payload.
- Safe refreshes: `/sprite reload atlascache` refetches Mojang data asynchronously, so if you ever need to update the caches for any reason...

## Showcase
<p align="center">
  <img width="1920" height="1080" alt="image" src="https://github.com/user-attachments/assets/cd2af2ff-6aca-4521-b082-0fbb1bddc091" />
</p>

#### the main menu
<p align="center">
  <img width="968" height="264" alt="image" src="https://github.com/user-attachments/assets/ed8c0565-4560-4915-8ae8-eceb00b9edd0" />
</p>

#### the `blocks` atlas, with a list of all the block textures
<p align="center">
  <img width="988" height="539" alt="image" src="https://github.com/user-attachments/assets/ca0d06f7-0144-4063-8a5b-5bbf169316f1" />
</p>

#### controls for both copying full minimessage tags, as well as the json payload, if you need to `/tellraw` something.
<p align="center">
  <img width="792" height="71" alt="image" src="https://github.com/user-attachments/assets/ebf890af-4eac-4f0b-a36c-00dc9b9cfa2a" />
</p>

#### demonstration of being able to display textures on the title (configurable time, default is 2 seconds), for better inspection
<p align="center">
  <img width="2560" height="1369" alt="image" src="https://github.com/user-attachments/assets/2115299c-a04f-4fce-bc59-ce15180f1d2e" />
</p>


## Commands you'll (hopefully) use

| Command                            | What it does                                                                  |
|------------------------------------|-------------------------------------------------------------------------------|
| `/sprite` or `/sprite view`        | Opens the root atlas list (use `/sprite page <n>` to jump around).            |
| `/sprite view <atlas> [page <n>]`  | Shows sprite groups for the given atlas; accepts `minecraft:` or simple IDs.  |
| `/sprite preview <atlas> <sprite>` | Pops the sprite into your title bar for the configured duration.              |
| `/sprite reload [all/atlascache]`  | Forces a cache refresh; always async, safe to use if Mojang updates textures. |


## Configuration knobs

`plugins/sprite/config.yml` regenerates automatically when the schema version changes. Important bits:

```yaml
population:
  mode: AUTOMATIC # switch to MANUAL if you stash atlas JSONs yourself
view:
  title-display-seconds: 2.0 # how long title previews remain on screen
```

- `AUTOMATIC` pulls the matching Mojang client jar, verifies SHA-1, extracts atlases, and writes a reusable `textures.index`.
- `MANUAL` skips downloads and expects your atlas files under `plugins/sprite/atlas-cache/`.
- Any negative or missing `title-display-seconds` falls back to the sane default defined in `SpriteConfig`.

## Build, run, repeat

1. `./gradlew clean build` –> compiles with the Java 21 toolchain, runs tests (when we add them), and emits a shaded jar in `build/libs/`.
2. Copy the jar into `plugins/` on a Paper 1.21.x server.
3. Use the included `./gradlew runServer` task to spin up a throwaway Paper instance for local poking; edit `build.gradle` if you need a different version.

> [!NOTE]
> I have noticed that some custom clients (I tested with Lunar) do not seem to render custom icons very well.

## Contributing:

- Keep things minimal: every new class should prove it earns its keep.
- Favor async interactions (`CompletionStage`, Bukkit schedulers, etc.) anytime I/O is involved.
- Tests? Name them `*Test`, mirror the package, and stick with JUnit 5 + AssertJ/JUnit assertions.
- Remember: types stay PascalCase, packages stay `sh.harold.sprite`, braces share the line, and indentation is four spaces.
