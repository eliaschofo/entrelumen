# ENTRELUMEN — The Living Atlas / El Atlas Vivo

An original Minecraft 1.21.1 NeoForge kitchen sink about recovering lost knowledge and reconnecting a fractured world. Technology, magic, nature and exploration contribute to a six-act campaign and the Ark of Horizons.

**Development status:** implementation in progress. This repository is not yet a playable release. Performance targets and the 150–200 hour campaign are design goals until measured in playtests.

The current prototype contains 25 bilingual opening quests, a server-authoritative team campaign, an Atlas interface, a pinned 119-mod client / 94-mod server dependency selection, and original item/block artwork. The client has entered a test world; the dedicated server has passed startup, resource reload, save and orderly shutdown. Eleven embedded-server GameTests cover campaign and network service behavior. Full campaign integration and acceptance testing remain in progress; see [verification evidence](docs/verification/client-first-entry.md) and [server checks](docs/verification/server-reload-stop.md).

## Design commitments
- Six directed acts with independent team campaigns and unrestricted item trading.
- Extensive early quality-of-life tools and approachable, purposeful automation.
- Original English and Spanish quests, tutorials and narrative.
- Staged resource farms without universal EMC conversion.
- Six complementary Ark modules, recoverable commissioning and no offline decay.
- Target: 16 GB system RAM, at most 8 GB Java heap, no default shaders.

## Project layout
- `companion/`: the NeoForge integration mod and campaign domain.
- `catalog/`: curated dependency inventory and provenance.
- `content/`: original quest and narrative sources.
- `pack/`: original configuration, KubeJS, quests and resource overrides.
- `tools/`: reproducible generation, installation and validation.
- `docs/delivery/`: the acceptance contract and verified progress.

Dependencies retain their own licenses. Original pack content is source available under [LICENSE](LICENSE); public redistribution or repackaging requires permission except for the limited platform rights described there.

## Español
ENTRELUMEN es un kitchen sink original sobre recuperar conocimientos y reconstruir una red de mundos. Su campaña combina tecnología, magia, naturaleza, exploración y construcción, con progreso por equipo e intercambio libre.

**Estado:** implementación en curso; todavía no es una versión jugable publicada. Las metas de rendimiento y duración requieren mediciones y pruebas reales.

El prototipo incluye 25 quests iniciales bilingües, campaña por equipo, interfaz del Atlas y arte propio. Ya arrancó en cliente y servidor; la campaña completa, su balance y la publicación siguen pendientes.

Las quests, la historia y las ayudas propias se desarrollan en inglés y español. El seguimiento del trabajo distingue implementación, pruebas y publicación.
