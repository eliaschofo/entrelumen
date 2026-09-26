# First chapter · route composition

Read from top to bottom (redrawn 25 September 2026 with the quest-book grammar, [docs/design/quest-book.md](../docs/design/quest-book.md)). Arrival and the workbench form a short central trunk inside the shelter panel; storage forks left, stone tools right, and the furnace and torches hang below them. Kitchen (left) and cartography (right) are two framed panels under the shelter. The Atlas sits alone in the middle, and the travellers' table, the lens and the survey converge on the first signal, the act's large hexagon on a small sun.

- Kitchen: water → wheat → bread in the middle column, knife → cutting board on the left, bowls on the right.
- Cartography: copper and glass on top; the expedition kit below them joins bread (a line across the page) and torches.
- QoL: small round nodes beside the activities they belong to (left of the shelter, right of the kit), outside any panel.
- Grammar: hexagon 2 for milestones, hexagon 3 for the chapter's last milestone, square 1 for tasks, diamond 1 for optional items, circle 0.75 for informative checkmarks. Shape and size carry hierarchy without relying on colour; colour (theme tags) only adds story, guide or optional.
- Prose: bilingual route labels, then breathing space. The main narrative nodes separate the short story beat from the concrete action.

The source owns x/y, group, shape and size; the generator checks the grammar. No coordinates depend on array order. Validation rejects non-finite coordinates, unknown groups, node overlap, backwards dependency direction and a node whose shape or size does not match its role. Semantic IDs and every quest remain unchanged.

Acceptance still requires FTB screenshots in EN/ES at ordinary zoom: silhouettes, line crossings, description wrapping at the 320 px panel and keyboard/mouse selection. Geometry checks and the preview renders establish spacing, not rendered visual quality in the client.
