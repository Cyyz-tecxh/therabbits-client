# TheRabbits Client

A black & white Fabric client for Minecraft 1.21.11.

## Stage 1 (this version)
- TRLH title screen (black & white)
- Mod menu: press **Right Shift** (categories: HUD, PvP, Visual, Utility)
- HUD editor: Mod menu -> **Edit HUD**, drag elements, they snap to edges/centre
- HUD: FPS, Ping, Coordinates, Direction, Keystrokes + CPS, Armor HUD
- PvP: Toggle Sprint, Zoom (hold **C**)
- Settings save to `.minecraft/config/therabbits.json`

## Build on your PC
1. Install JDK 21 (e.g. Temurin 21).
2. Open a terminal in this folder and run:
   - Windows: `gradlew.bat build`
   - Mac/Linux: `./gradlew build`
3. The mod is in `build/libs/TheRabbits-Client-1.0.0.jar` (not the `-sources` one).
4. Put it in `.minecraft/mods` together with Fabric API, launch with Fabric Loader.

If the build says a version can't be found, open https://fabricmc.net/develop/,
pick 1.21.11 and copy the Loader / Loom / Fabric API versions into `gradle.properties`.

## Build on GitHub
Push this folder to a GitHub repo. The included workflow builds it on every push;
download the jar from the Actions tab -> latest run -> Artifacts.
