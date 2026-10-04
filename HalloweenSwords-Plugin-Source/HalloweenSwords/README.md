# Halloween Swords (Paper 1.21.11)

7 custom swords, 2 abilities each. Reaper's Edge intentionally left out.

## Build
Requires JDK 21 + Maven.

    mvn clean package

The jar is at `target/HalloweenSwords-1.0.0.jar`. Drop it in `plugins/` on a Paper 1.21.11 server.

## Use
- `/hsword give <sword|all> [player]`, `/hsword list`, `/hsword reload`, `/hsword resetcd [player]` (permission `halloweenswords.admin`, op by default)
- **Right-click** = first ability, **Sneak + Right-click** = second ability.
- Config: `plugins/HalloweenSwords/config.yml` (cooldowns, damage, durations, which model each sword uses).

| Sword | Right-click | Sneak + Right-click | Model |
|---|---|---|---|
| Jack-O-Blade | Pumpkin Bomb (45s) | Trick or Treat (70s) | Hollow Lantern |
| Phantom Fang | Haunt (50s) | Ghostwalk (75s) | Bone Ripper (shared) |
| Gravekeeper | Grave Grip (55s) | Rise From Below (80s) | Bone Ripper |
| Bloodmoon Blade | Blood Hunt (55s) | Blood Moon (90s) | Candy Carver |
| Nightfang | Bat Swarm (45s) | Night Flight (65s) | Arachnid Fang (shared) |
| Cursed Blade | Hex (60s) | Possession (90s) | Witch's Thorn |
| Widowmaker | Web Shot (45s) | Spider's Feast (75s) | Arachnid Fang |

## Models
The swords use the `item_model` component (`halloween:<name>`). Install the separate
**HalloweenSwords-ResourcePack.zip** on the server/clients so the models show up.

## No Maven? Build with GitHub
Push this folder to a GitHub repo; the included workflow builds the jar (Actions tab -> artifact).
